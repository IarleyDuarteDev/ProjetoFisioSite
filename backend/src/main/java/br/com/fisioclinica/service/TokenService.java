package br.com.fisioclinica.service;

import br.com.fisioclinica.dto.AuthDtos.*;
import br.com.fisioclinica.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration duration;
    public TokenService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.issuer}") String issuer,
                        @Value("${app.jwt.duration}") Duration duration) {
        this.encoder = encoder; this.clock = clock; this.issuer = issuer; this.duration = duration;
    }
    public Sessao criar(Usuario usuario) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(usuario.getId().toString())
            .issuedAt(now).expiresAt(now.plus(duration)).claim("ver", usuario.getTokenVersion()).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Sessao(jwt, "Bearer", duration.toSeconds(), Cliente.from(usuario));
    }
}
