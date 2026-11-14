package br.com.fisioclinica.service;

import br.com.fisioclinica.dto.AuthDtos.*;
import br.com.fisioclinica.exception.ApiException;
import br.com.fisioclinica.model.*;
import br.com.fisioclinica.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @Slf4j
public class AuthService {
    private final UsuarioRepository usuarios;
    private final RecuperacaoSenhaRepository recuperacoes;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final EmailService emails;
    private final Clock clock;
    private final CpfCofre cofre;
    private final LimitadorTentativas limitador;
    private final ConfirmacaoEmailRepository confirmacoes;
    private final String dummyHash;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UsuarioRepository usuarios, RecuperacaoSenhaRepository recuperacoes,
                       PasswordEncoder encoder, TokenService tokens, EmailService emails, Clock clock,
                       CpfCofre cofre, LimitadorTentativas limitador, ConfirmacaoEmailRepository confirmacoes) {
        this.usuarios = usuarios; this.recuperacoes = recuperacoes; this.encoder = encoder;
        this.tokens = tokens; this.emails = emails; this.clock = clock;
        this.cofre = cofre; this.limitador = limitador; this.confirmacoes = confirmacoes;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public Sessao cadastrar(Cadastro dto) {
        validarSenha(dto.senha());
        String email = normalizarEmail(dto.email());
        String cpf = cpfValido(dto.cpf());
        String cpfHash = cofre.hash(cpf);
        if (usuarios.existsByEmail(email) || usuarios.existsByCpfHash(cpfHash))
            throw ApiException.conflict("Este e-mail ou CPF já está cadastrado.");
        var usuario = new Usuario();
        usuario.setNome(dto.nome().strip());
        usuario.setEmail(email);
        usuario.setCpfCifrado(cofre.cifrar(cpf));
        usuario.setCpfHash(cpfHash);
        usuario.setSenhaHash(encoder.encode(dto.senha()));
        var salvo = usuarios.saveAndFlush(usuario);
        emitirConfirmacao(salvo);
        return tokens.criar(salvo);
    }
    @Transactional(readOnly = true)
    public Sessao login(Login dto) {
        validarSenha(dto.senha());
        String email = normalizarEmail(dto.email());
        String chave = "login:" + email;
        limitador.exigir(chave);
        var usuario = usuarios.findByEmail(email);
        boolean valid = encoder.matches(dto.senha(), usuario.map(Usuario::getSenhaHash).orElse(dummyHash));
        if (usuario.isEmpty() || !valid) {
            limitador.falha(chave);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        limitador.sucesso(chave);
        return tokens.criar(usuario.get());
    }
    /** Contas antigas, sem CPF, informam o CPF uma única vez; depois ele não pode ser trocado por aqui. */
    @Transactional
    public Cliente informarCpf(UUID usuarioId, InformarCpf dto) {
        String cpf = cpfValido(dto.cpf());
        var usuario = usuarios.findLockedById(usuarioId).orElseThrow(() -> ApiException.notFound("Conta não encontrada."));
        if (usuario.getCpfHash() != null) throw ApiException.conflict("O CPF desta conta já foi informado.");
        String cpfHash = cofre.hash(cpf);
        if (usuarios.existsByCpfHash(cpfHash)) throw ApiException.conflict("Este CPF já está vinculado a outra conta.");
        usuario.setCpfCifrado(cofre.cifrar(cpf));
        usuario.setCpfHash(cpfHash);
        return Cliente.from(usuario);
    }
    @Transactional
    public void recuperar(Recuperar dto) {
        var found = usuarios.findByEmail(normalizarEmail(dto.email()));
        if (found.isEmpty()) return;
        var usuario = usuarios.findLockedById(found.get().getId()).orElseThrow();
        var reset = recuperacoes.findByUsuarioId(usuario.getId()).orElseGet(RecuperacaoSenha::new);
        // Uma solicitação por minuto por conta, mesmo quando há falha de SMTP.
        if (reset.getExpiraEm() != null && reset.getExpiraEm().isAfter(clock.instant().plusSeconds(840))) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        reset.setUsuario(usuario);
        reset.setTokenHash(hash(token));
        reset.setExpiraEm(clock.instant().plusSeconds(900));
        recuperacoes.saveAndFlush(reset);
        try {
            emails.enviarRecuperacao(usuario.getEmail(), token);
        } catch (MailException ex) {
            // A resposta pública não revela se a conta existe ou se o provedor falhou.
            log.warn("Falha no envio de recuperação de senha. Verifique a configuração SMTP.");
        }
    }
    @Transactional
    public void redefinir(Redefinir dto) {
        validarSenha(dto.novaSenha());
        String tokenHash = hash(dto.token());
        UUID usuarioId = recuperacoes.findUsuarioIdByTokenHash(tokenHash).orElseThrow(AuthService::tokenInvalido);
        // Serializa consumo e reemissão; a segunda requisição verá o token já removido.
        var usuario = usuarios.findLockedById(usuarioId).orElseThrow(AuthService::tokenInvalido);
        var reset = recuperacoes.findByTokenHash(tokenHash).orElseThrow(AuthService::tokenInvalido);
        if (!reset.getExpiraEm().isAfter(clock.instant())) throw tokenInvalido();
        usuario.setSenhaHash(encoder.encode(dto.novaSenha()));
        usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        usuario.setEmailVerificado(true); // clicar no link enviado ao e-mail prova que a pessoa controla a caixa
        recuperacoes.delete(reset);
    }
    /** Gera um link de confirmação válido por 24 horas. Falha de SMTP não derruba o cadastro: dá para pedir reenvio. */
    private void emitirConfirmacao(Usuario usuario) {
        var confirmacao = confirmacoes.findByUsuarioId(usuario.getId()).orElseGet(ConfirmacaoEmail::new);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        confirmacao.setUsuario(usuario);
        confirmacao.setTokenHash(hash(token));
        confirmacao.setExpiraEm(clock.instant().plus(Duration.ofHours(24)));
        confirmacoes.saveAndFlush(confirmacao);
        try {
            emails.enviarConfirmacao(usuario.getEmail(), token);
        } catch (MailException ex) {
            log.warn("Falha no envio da confirmação de e-mail. Verifique a configuração SMTP.");
        }
    }
    /** No máximo um envio por minuto por conta. */
    @Transactional
    public void reenviarConfirmacao(UUID usuarioId) {
        var usuario = usuarios.findLockedById(usuarioId).orElseThrow(() -> ApiException.notFound("Conta não encontrada."));
        if (usuario.isEmailVerificado()) return;
        var atual = confirmacoes.findByUsuarioId(usuarioId);
        if (atual.isPresent() && atual.get().getExpiraEm().isAfter(clock.instant().plus(Duration.ofHours(24)).minusSeconds(60))) return;
        emitirConfirmacao(usuario);
    }
    @Transactional
    public Confirmado confirmarEmail(ConfirmarEmail dto) {
        String tokenHash = hash(dto.token());
        UUID usuarioId = confirmacoes.findUsuarioIdByTokenHash(tokenHash).orElseThrow(AuthService::linkInvalido);
        var usuario = usuarios.findLockedById(usuarioId).orElseThrow(AuthService::linkInvalido);
        var confirmacao = confirmacoes.findByTokenHash(tokenHash).orElseThrow(AuthService::linkInvalido);
        if (!confirmacao.getExpiraEm().isAfter(clock.instant())) throw linkInvalido();
        usuario.setEmailVerificado(true);
        confirmacoes.delete(confirmacao);
        return new Confirmado(usuario.getId());
    }
    private static ApiException linkInvalido() { return ApiException.badRequest("Link de confirmação inválido ou expirado."); }
    private static String cpfValido(String informado) {
        String cpf = Cpf.normalizar(informado);
        if (!Cpf.valido(cpf)) throw ApiException.badRequest("Informe um CPF válido.");
        return cpf;
    }
    public static String normalizarEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private static void validarSenha(String senha) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) throw ApiException.badRequest("A senha deve ter no máximo 72 bytes em UTF-8.");
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static ApiException tokenInvalido() { return ApiException.badRequest("Link de recuperação inválido ou expirado."); }
}
