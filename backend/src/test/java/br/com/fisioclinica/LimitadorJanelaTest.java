package br.com.fisioclinica;

import br.com.fisioclinica.service.LimitadorJanela;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;

class LimitadorJanelaTest {
    /** Relógio que anda quando o teste manda. */
    static class Relogio extends Clock {
        Instant agora = Instant.parse("2026-11-14T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    @Test void recusaDepoisDoLimiteELiberaQuandoAJanelaPassa() {
        var relogio = new Relogio();
        var limitador = new LimitadorJanela(relogio);
        for (int i = 0; i < 3; i++) assertThat(limitador.permitir("ip-1", 3, Duration.ofMinutes(10))).isTrue();
        assertThat(limitador.permitir("ip-1", 3, Duration.ofMinutes(10))).isFalse();
        assertThat(limitador.permitir("ip-2", 3, Duration.ofMinutes(10))).as("outro IP tem a própria cota").isTrue();
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(11));
        assertThat(limitador.permitir("ip-1", 3, Duration.ofMinutes(10))).as("a janela passou").isTrue();
    }
    @Test void tentativasRecusadasNaoPrologamOBloqueio() {
        var relogio = new Relogio();
        var limitador = new LimitadorJanela(relogio);
        assertThat(limitador.permitir("ip", 1, Duration.ofMinutes(10))).isTrue();
        for (int i = 0; i < 20; i++) { relogio.agora = relogio.agora.plusSeconds(20); limitador.permitir("ip", 1, Duration.ofMinutes(10)); }
        relogio.agora = Instant.parse("2026-11-14T12:10:30Z");
        assertThat(limitador.permitir("ip", 1, Duration.ofMinutes(10))).isTrue();
    }
}
