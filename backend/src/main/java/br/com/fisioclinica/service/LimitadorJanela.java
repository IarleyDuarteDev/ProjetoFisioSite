package br.com.fisioclinica.service;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Janela deslizante: permite no máximo N eventos por chave dentro de um período. Fica na memória (reinicia com o servidor). */
@Component
public class LimitadorJanela {
    private final Map<String, Deque<Instant>> eventos = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimitadorJanela(Clock clock) { this.clock = clock; }

    /** Registra a tentativa e diz se ela cabe no limite. Tentativas recusadas não ocupam espaço na janela. */
    public boolean permitir(String chave, int maximo, Duration janela) {
        var agora = clock.instant();
        if (eventos.size() > 10_000) limpar(agora);
        var fila = eventos.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && fila.peekFirst().plus(janela).isBefore(agora)) fila.pollFirst();
            if (fila.size() >= maximo) return false;
            fila.addLast(agora);
            return true;
        }
    }

    private void limpar(Instant agora) {
        eventos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                var ultimo = e.getValue().peekLast();
                return ultimo == null || ultimo.plus(Duration.ofHours(2)).isBefore(agora);
            }
        });
    }
}
