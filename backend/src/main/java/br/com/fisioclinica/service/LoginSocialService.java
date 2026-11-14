package br.com.fisioclinica.service;

import br.com.fisioclinica.dto.AuthDtos.Sessao;
import br.com.fisioclinica.exception.ApiException;
import br.com.fisioclinica.model.*;
import br.com.fisioclinica.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginSocialService {
    private final VerificadorIdentidade verificador;
    private final IdentidadeSocialRepository identidades;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public LoginSocialService(VerificadorIdentidade verificador, IdentidadeSocialRepository identidades, UsuarioRepository usuarios,
                              PasswordEncoder encoder, TokenService tokens) {
        this.verificador = verificador; this.identidades = identidades; this.usuarios = usuarios; this.encoder = encoder; this.tokens = tokens;
    }

    public Map<String, String> provedores() { return verificador.clientes(); }

    /**
     * 1) Já vinculado: entra. 2) E-mail novo: cria a conta (CPF é pedido antes do primeiro agendamento).
     * 3) E-mail de uma conta com senha ainda não verificada: vincula, mas troca a senha por uma aleatória e derruba as
     * sessões, porque essa conta pode ter sido criada por outra pessoa com o e-mail alheio. Quem é dono do e-mail
     * recupera o acesso por senha em "Esqueci minha senha".
     */
    @Transactional
    public Sessao entrar(Provedor provedor, String token, String nomeInformado) {
        var identidade = verificador.verificar(provedor, token);
        if (!identidade.emailVerificado() || identidade.email() == null || identidade.subject() == null)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "O provedor não confirmou o seu e-mail.");
        var existente = identidades.findByProvedorAndSubject(provedor, identidade.subject());
        if (existente.isPresent()) return tokens.criar(existente.get().getUsuario());

        String email = AuthService.normalizarEmail(identidade.email());
        var usuario = usuarios.findByEmail(email).orElse(null);
        if (usuario == null) {
            usuario = new Usuario();
            usuario.setNome(nome(identidade.nome(), nomeInformado, email));
            usuario.setEmail(email);
            usuario.setSenhaHash(encoder.encode(UUID.randomUUID().toString()));
        } else if (!usuario.isEmailVerificado()) {
            usuario.setSenhaHash(encoder.encode(UUID.randomUUID().toString()));
            usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        }
        usuario.setEmailVerificado(true);
        usuario = usuarios.saveAndFlush(usuario);
        var vinculo = new IdentidadeSocial();
        vinculo.setProvedor(provedor);
        vinculo.setSubject(identidade.subject());
        vinculo.setUsuario(usuario);
        identidades.save(vinculo);
        return tokens.criar(usuario);
    }

    private static String nome(String doProvedor, String informado, String email) {
        for (String candidato : new String[] {doProvedor, informado}) {
            if (candidato != null && !candidato.isBlank()) return candidato.strip().substring(0, Math.min(120, candidato.strip().length()));
        }
        return email.substring(0, email.indexOf('@'));
    }
}
