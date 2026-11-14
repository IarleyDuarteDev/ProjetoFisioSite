package br.com.fisioclinica.service;

import br.com.fisioclinica.exception.ApiException;
import br.com.fisioclinica.model.Provedor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Valida assinatura (chaves públicas do provedor), emissor, público (client ID) e validade do ID token. */
@Component
public class VerificadorOidc implements VerificadorIdentidade {
    private final Map<Provedor, String> clientes;
    private final Map<Provedor, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public VerificadorOidc(@Value("${app.google.client-id:}") String google, @Value("${app.apple.client-id:}") String apple) {
        clientes = Map.of(Provedor.GOOGLE, google.strip(), Provedor.APPLE, apple.strip());
    }

    @Override
    public Identidade verificar(Provedor provedor, String token) {
        String cliente = clientes.get(provedor);
        if (cliente.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "O login com " + (provedor == Provedor.GOOGLE ? "o Google" : "a Apple") + " não está configurado.");
        try {
            Jwt jwt = decoders.computeIfAbsent(provedor, p -> criar(p, cliente)).decode(token);
            Object verificado = jwt.getClaim("email_verified");
            return new Identidade(provedor, jwt.getSubject(), jwt.getClaimAsString("email"),
                    Boolean.TRUE.equals(verificado) || "true".equals(String.valueOf(verificado)), jwt.getClaimAsString("name"));
        } catch (JwtException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Não foi possível validar o login com o provedor.");
        }
    }

    @Override
    public Map<String, String> clientes() {
        return Map.of("google", clientes.get(Provedor.GOOGLE), "apple", clientes.get(Provedor.APPLE));
    }

    private static JwtDecoder criar(Provedor provedor, String cliente) {
        boolean google = provedor == Provedor.GOOGLE;
        var decoder = NimbusJwtDecoder.withJwkSetUri(google ? "https://www.googleapis.com/oauth2/v3/certs" : "https://appleid.apple.com/auth/keys")
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        List<String> emissores = google ? List.of("https://accounts.google.com", "accounts.google.com") : List.of("https://appleid.apple.com");
        OAuth2TokenValidator<Jwt> publico = jwt -> emissores.contains(jwt.getClaimAsString("iss")) && jwt.getAudience() != null && jwt.getAudience().contains(cliente)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emissor ou público inválido.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), publico));
        return decoder;
    }
}
