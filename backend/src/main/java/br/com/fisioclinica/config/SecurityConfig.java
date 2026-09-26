package br.com.fisioclinica.config;

import br.com.fisioclinica.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean Clock clock(@Value("${app.clinic-zone}") String zone) { return Clock.system(ZoneId.of(zone)); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET deve conter pelo menos 32 bytes aleatórios.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key, @Value("${app.jwt.issuer}") String issuer) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, UsuarioRepository usuarios, ObjectMapper mapper) throws Exception {
        // Bearer token explícito, sem cookies de autenticação: API stateless.
        http.csrf(csrf -> csrf.disable()).cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/forgot-password", "/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/horarios/disponiveis", "/profissionais").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                try {
                    var usuario = usuarios.findById(UUID.fromString(token.getSubject())).orElseThrow();
                    Number version = token.getClaim("ver");
                    if (version == null || version.longValue() != usuario.getTokenVersion()) throw new IllegalArgumentException();
                    return new JwtAuthenticationToken(token, List.of(), usuario.getId().toString());
                } catch (IllegalArgumentException | NoSuchElementException ex) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
                }
            })).authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(401);
                res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                mapper.writeValue(res.getOutputStream(), ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Faça login novamente para continuar."));
            }))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    mapper.writeValue(res.getOutputStream(), ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Autenticação necessária."));
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    mapper.writeValue(res.getOutputStream(), ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acesso não permitido."));
                }));
        return http.build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.frontend-url}") String origin) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
