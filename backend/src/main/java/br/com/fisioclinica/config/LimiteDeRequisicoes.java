package br.com.fisioclinica.config;

import br.com.fisioclinica.service.LimitadorJanela;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Limita, por IP, as rotas de autenticação que um robô usaria para adivinhar senhas ou disparar e-mails.
 * Atrás do Nginx/Caddy o IP real vem de X-Forwarded-For (server.forward-headers-strategy=native, só de proxies da rede privada).
 */
@Component
@ConditionalOnProperty(name = "app.limites.ativo", havingValue = "true", matchIfMissing = true)
public class LimiteDeRequisicoes extends OncePerRequestFilter {
    private record Regra(String caminho, int maximo, Duration janela) {}
    private static final List<Regra> REGRAS = List.of(
            new Regra("/auth/register", 10, Duration.ofHours(1)),
            new Regra("/auth/forgot-password", 5, Duration.ofHours(1)),
            new Regra("/auth/resend-confirmation", 5, Duration.ofHours(1)),
            new Regra("/auth/login", 30, Duration.ofMinutes(15)),
            new Regra("/auth/google", 30, Duration.ofMinutes(15)),
            new Regra("/auth/apple", 30, Duration.ofMinutes(15)));
    /** Decodifica a URL como o Spring MVC faz: "/auth/%6cogin" e "/auth/login" são a mesma rota e dividem a mesma cota. */
    private static final UrlPathHelper CAMINHOS = new UrlPathHelper();
    private final LimitadorJanela limitador;

    public LimiteDeRequisicoes(LimitadorJanela limitador) { this.limitador = limitador; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            String caminho = CAMINHOS.getPathWithinApplication(request);
            for (Regra regra : REGRAS) {
                if (!regra.caminho().equals(caminho)) continue;
                if (!limitador.permitir(regra.caminho() + "|" + request.getRemoteAddr(), regra.maximo(), regra.janela())) {
                    response.setStatus(429);
                    response.setContentType("application/json;charset=UTF-8");
                    response.setHeader("Retry-After", String.valueOf(regra.janela().toSeconds()));
                    response.getWriter().write("{\"status\":429,\"detail\":\"Muitas requisições. Tente novamente mais tarde.\"}");
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }
}
