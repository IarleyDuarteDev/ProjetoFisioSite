package br.com.fisioclinica;

import br.com.fisioclinica.config.LimiteDeRequisicoes;
import br.com.fisioclinica.service.LimitadorJanela;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.Clock;
import static org.assertj.core.api.Assertions.assertThat;

class LimiteDeRequisicoesTest {
    private final LimiteDeRequisicoes filtro = new LimiteDeRequisicoes(new LimitadorJanela(Clock.systemUTC()));

    private int enviar(String metodo, String uri, String ip) throws Exception {
        var req = new MockHttpServletRequest(metodo, uri);
        req.setRemoteAddr(ip);
        var res = new MockHttpServletResponse();
        filtro.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test void recusaDepoisDoLimiteMesmoComALetraCodificadaNaUrl() throws Exception {
        for (int i = 0; i < 5; i++) assertThat(enviar("POST", "/auth/forgot-password", "203.0.113.9")).isEqualTo(200);
        assertThat(enviar("POST", "/auth/forgot-password", "203.0.113.9")).isEqualTo(429);
        assertThat(enviar("POST", "/auth/forgot-p%61ssword", "203.0.113.9")).as("a URL codificada é a mesma rota").isEqualTo(429);
        assertThat(enviar("POST", "/auth//forgot-password;x=1", "203.0.113.9")).as("barra dupla e ponto e vírgula").isEqualTo(429);
    }
    @Test void outroIpOutroMetodoEOutraRotaNaoSaoAfetados() throws Exception {
        for (int i = 0; i < 6; i++) enviar("POST", "/auth/forgot-password", "203.0.113.9");
        assertThat(enviar("POST", "/auth/forgot-password", "198.51.100.7")).isEqualTo(200);
        assertThat(enviar("GET", "/auth/forgot-password", "203.0.113.9")).isEqualTo(200);
        assertThat(enviar("GET", "/profissionais", "203.0.113.9")).isEqualTo(200);
    }
}
