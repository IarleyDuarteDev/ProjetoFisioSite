package br.com.fisioclinica.service;

import br.com.fisioclinica.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bloqueia por 15 minutos uma chave (ex.: o e-mail) depois de 5 falhas seguidas. Fica na memória: reinicia com o servidor. */
@Component
public class LimitadorTentativas {
    private record Estado(int falhas, Instant inicio, Instant bloqueadoAte) {}
    private static final int MAXIMO = 5;
    private static final Duration JANELA = Duration.ofMinutes(15);
    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimitadorTentativas(Clock clock) { this.clock = clock; }

    public void exigir(String chave) {
        var estado = estados.get(chave);
        if (estado != null && estado.bloqueadoAte() != null && estado.bloqueadoAte().isAfter(clock.instant()))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
    }
    public void falha(String chave) {
        var agora = clock.instant();
        estados.merge(chave, new Estado(1, agora, null), (atual, novo) -> {
            boolean expirou = atual.inicio().plus(JANELA).isBefore(agora)
                    || (atual.bloqueadoAte() != null && !atual.bloqueadoAte().isAfter(agora));
            if (expirou) return novo;
            int falhas = atual.falhas() + 1;
            return new Estado(falhas, atual.inicio(), falhas >= MAXIMO ? agora.plus(JANELA) : null);
        });
        if (estados.size() > 10_000) estados.entrySet().removeIf(e -> e.getValue().inicio().plus(JANELA).isBefore(agora));
    }
    public void sucesso(String chave) { estados.remove(chave); }
}
