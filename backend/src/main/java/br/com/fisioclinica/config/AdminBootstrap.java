package br.com.fisioclinica.config;

import br.com.fisioclinica.model.*;
import br.com.fisioclinica.repository.UsuarioRepository;
import br.com.fisioclinica.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Prepara a conta do administrador a partir de ADMIN_EMAIL. A senha é trocada por uma aleatória e as sessões
 * antigas caem, então só quem controla a caixa de e-mail consegue definir a senha ("Esqueci minha senha").
 * Assim, ninguém assume o papel de admin cadastrando esse e-mail antes.
 */
@Component @Slf4j
public class AdminBootstrap implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String adminEmail;

    public AdminBootstrap(UsuarioRepository usuarios, PasswordEncoder encoder, @Value("${app.admin-email:}") String adminEmail) {
        this.usuarios = usuarios; this.encoder = encoder; this.adminEmail = adminEmail;
    }
    @Override @Transactional
    public void run(String... args) {
        if (adminEmail.isBlank()) return;
        String email = AuthService.normalizarEmail(adminEmail);
        var usuario = usuarios.findByEmail(email).orElseGet(() -> {
            var novo = new Usuario();
            novo.setNome("Administrador");
            novo.setEmail(email);
            return novo;
        });
        if (usuario.getPapel() == Papel.ADMIN) return;
        usuario.setPapel(Papel.ADMIN);
        usuario.setSenhaHash(encoder.encode(UUID.randomUUID().toString()));
        usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        usuarios.save(usuario);
        log.info("Conta de administrador preparada. Defina a senha em 'Esqueci minha senha'.");
    }
}
