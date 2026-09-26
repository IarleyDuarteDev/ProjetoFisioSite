package br.com.fisioclinica;

import br.com.fisioclinica.dto.AgendaDtos.Criar;
import br.com.fisioclinica.exception.ApiException;
import br.com.fisioclinica.model.*;
import br.com.fisioclinica.repository.*;
import br.com.fisioclinica.service.*;
import com.fasterxml.jackson.databind.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ApiIntegrationTest {
    static final EmbeddedPostgres POSTGRES;
    static {
        try { POSTGRES = EmbeddedPostgres.builder().setPort(0).start(); }
        catch (IOException ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("app.jwt.secret", () -> "integration-tests-only-secret-with-at-least-32-bytes");
        registry.add("app.demo-data", () -> false);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ProfissionalRepository profissionais;
    @Autowired HorarioRepository horarios;
    @Autowired RecuperacaoSenhaRepository resets;
    @Autowired AgendaService agenda;
    @Autowired Clock clock;
    @MockitoBean EmailService emails;
    Horario primeiro;
    Horario segundo;
    JsonNode alice;

    @BeforeEach
    void setup() throws Exception {
        jdbc.execute("TRUNCATE fisio.recuperacoes_senha, fisio.agendamentos, fisio.horarios, fisio.profissionais, fisio.usuarios CASCADE");
        var p = new Profissional(); p.setNome("Profissional de teste"); p.setEspecialidade("Ortopedia"); profissionais.save(p);
        primeiro = horario(p, 9, TipoSessao.AVALIACAO);
        segundo = horario(p, 10, TipoSessao.FISIOTERAPIA);
        alice = cadastrar("ALICE@example.com");
    }
    Horario horario(Profissional p, int hora, TipoSessao tipo) {
        var h = new Horario(); h.setData(LocalDate.now(clock).plusDays(1));
        h.setHoraInicio(LocalTime.of(hora, 0)); h.setHoraFim(LocalTime.of(hora, 50));
        h.setProfissional(p); h.setTipoSessao(tipo); return horarios.save(h);
    }
    JsonNode cadastrar(String email) throws Exception {
        return json(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("nome", "Cliente Teste", "email", email, "senha", "senha-segura-123"))))
                .andExpect(status().isCreated()).andReturn());
    }
    JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    String bearer(JsonNode session) { return "Bearer " + session.get("accessToken").asText(); }
    JsonNode reservar(JsonNode session, UUID horarioId) throws Exception {
        return json(mvc.perform(post("/agendamentos").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("horarioId", horarioId))))
                .andExpect(status().isCreated()).andReturn());
    }
    @Test void cadastroNormalizaEmailUsaBCryptELoginFunciona() throws Exception {
        var user = usuarios.findByEmail("alice@example.com").orElseThrow();
        assertThat(user.getSenhaHash()).startsWith("$2").doesNotContain("senha-segura");
        assertThat(alice.has("senhaHash")).isFalse();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\",\"senha\":\"senha-segura-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\",\"senha\":\"senha-incorreta\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void rejeitaCadastroDuplicadoEEntradaInvalida() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Outra pessoa\",\"email\":\"Alice@example.com\",\"senha\":\"12345678\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"email\":\"invalido\",\"senha\":\"123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.email").exists());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("nome", "Teste", "email", "outra@example.com", "senha", "á".repeat(37)))))
                .andExpect(status().isBadRequest());
    }
    @Test void protegeRotasERejeitaJwtAdulterado() throws Exception {
        mvc.perform(get("/agendamentos/meus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/agendamentos/meus").header("Authorization", bearer(alice) + "x"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/agendamentos/meus").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itens").isEmpty());
    }
    @Test void filtraHorariosEValidaParametros() throws Exception {
        mvc.perform(get("/horarios/disponiveis").param("data", primeiro.getData().toString())
                .param("profissionalId", primeiro.getProfissional().getId().toString()).param("tipoSessao", "AVALIACAO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/horarios/disponiveis").param("data", "2020-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/horarios/disponiveis").param("data", "invalida")).andExpect(status().isBadRequest());
        mvc.perform(get("/agendamentos/meus").param("tamanho", "101").header("Authorization", bearer(alice)))
                .andExpect(status().isBadRequest());
    }
    @Test void reservaRemarcaECancelaAtomicamente() throws Exception {
        var reserva = reservar(alice, primeiro.getId());
        assertThat(horarios.findById(primeiro.getId()).orElseThrow().getStatus()).isEqualTo(StatusHorario.RESERVADO);
        mvc.perform(put("/agendamentos/" + reserva.get("id").asText()).header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("acao", "REMARCAR", "novoHorarioId", segundo.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.horario.id").value(segundo.getId().toString()));
        assertThat(horarios.findById(primeiro.getId()).orElseThrow().getStatus()).isEqualTo(StatusHorario.DISPONIVEL);
        mvc.perform(put("/agendamentos/" + reserva.get("id").asText()).header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"CANCELAR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADO"));
        assertThat(horarios.findById(segundo.getId()).orElseThrow().getStatus()).isEqualTo(StatusHorario.DISPONIVEL);
        reservar(alice, segundo.getId());
    }
    @Test void naoExpoeNemAlteraAgendamentosDeOutroCliente() throws Exception {
        var reserva = reservar(alice, primeiro.getId());
        var bob = cadastrar("bob@example.com");
        mvc.perform(get("/agendamentos/meus").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(put("/agendamentos/" + reserva.get("id").asText()).header("Authorization", bearer(bob))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"CANCELAR\"}"))
                .andExpect(status().isNotFound());
    }
    @Test void conflitoNaRemarcacaoMantemReservaOriginal() throws Exception {
        var reserva = reservar(alice, primeiro.getId());
        reservar(alice, segundo.getId());
        mvc.perform(put("/agendamentos/" + reserva.get("id").asText()).header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("acao", "REMARCAR", "novoHorarioId", segundo.getId()))))
                .andExpect(status().isConflict());
        assertThat(horarios.findById(primeiro.getId()).orElseThrow().getStatus()).isEqualTo(StatusHorario.RESERVADO);
        mvc.perform(put("/agendamentos/" + reserva.get("id").asText()).header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"REMARCAR\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void reservasConcorrentesTemApenasUmVencedor() throws Exception {
        UUID userId = UUID.fromString(alice.get("usuario").get("id").asText());
        var gate = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            gate.await();
            try { agenda.criar(userId, new Criar(primeiro.getId(), null)); return true; }
            catch (ApiException ex) { assertThat(ex.getStatus().value()).isEqualTo(409); return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(attempt); var b = executor.submit(attempt); gate.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("select count(*) from fisio.agendamentos where status='CONFIRMADO'", Long.class)).isEqualTo(1);
    }
    @Test void rejeitaHorarioPassado() throws Exception {
        primeiro.setData(LocalDate.now(clock).minusDays(1)); horarios.save(primeiro);
        mvc.perform(post("/agendamentos").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("horarioId", primeiro.getId())))).andExpect(status().isConflict());
    }
    @Test void recuperacaoUsaTokenUnicoRevogaJwtETrocaSenha() throws Exception {
        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isAccepted());
        var captor = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarRecuperacao(eq("alice@example.com"), captor.capture());
        String raw = captor.getValue();
        assertThat(resets.findAll().getFirst().getTokenHash()).isNotEqualTo(raw).isEqualTo(AuthService.hash(raw));
        String payload = mapper.writeValueAsString(Map.of("token", raw, "novaSenha", "nova-senha-123"));
        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        mvc.perform(get("/agendamentos/meus").header("Authorization", bearer(alice))).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\",\"senha\":\"nova-senha-123\"}"))
                .andExpect(status().isOk());
    }
    @Test void recuperacaoNaoRevelaContasERejeitaTokenExpirado() throws Exception {
        var known = mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn();
        var unknown = mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ninguem@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn();
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        var captor = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarRecuperacao(eq("alice@example.com"), captor.capture());
        jdbc.update("update fisio.recuperacoes_senha set expira_em = now() - interval '1 minute'");
        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", captor.getValue(), "novaSenha", "outra-senha-123")))).andExpect(status().isBadRequest());
    }
    @Test void documentacaoAbertaEEsquemaBearerDisponivel() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
