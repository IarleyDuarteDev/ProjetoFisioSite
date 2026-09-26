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
    private final String dummyHash;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UsuarioRepository usuarios, RecuperacaoSenhaRepository recuperacoes,
                       PasswordEncoder encoder, TokenService tokens, EmailService emails, Clock clock) {
        this.usuarios = usuarios; this.recuperacoes = recuperacoes; this.encoder = encoder;
        this.tokens = tokens; this.emails = emails; this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public Sessao cadastrar(Cadastro dto) {
        validarSenha(dto.senha());
        String email = normalizarEmail(dto.email());
        if (usuarios.existsByEmail(email)) throw ApiException.conflict("Este e-mail já está cadastrado.");
        var usuario = new Usuario();
        usuario.setNome(dto.nome().strip());
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(dto.senha()));
        return tokens.criar(usuarios.saveAndFlush(usuario));
    }
    @Transactional(readOnly = true)
    public Sessao login(Login dto) {
        validarSenha(dto.senha());
        var usuario = usuarios.findByEmail(normalizarEmail(dto.email()));
        boolean valid = encoder.matches(dto.senha(), usuario.map(Usuario::getSenhaHash).orElse(dummyHash));
        if (usuario.isEmpty() || !valid) throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        return tokens.criar(usuario.get());
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
        recuperacoes.delete(reset);
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
