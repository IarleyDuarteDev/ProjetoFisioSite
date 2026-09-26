package br.com.fisioclinica;

import br.com.fisioclinica.dto.AgendaDtos.Criar;
import br.com.fisioclinica.exception.ApiException;
import br.com.fisioclinica.model.*;
import br.com.fisioclinica.repository.*;
import br.com.fisioclinica.service.AgendaService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgendaServiceTest {
    final HorarioRepository horarios = mock(HorarioRepository.class);
    final AgendamentoRepository agendamentos = mock(AgendamentoRepository.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-09-25T15:00:00Z"), ZoneId.of("America/Rio_Branco"));
    final AgendaService agenda = new AgendaService(horarios, agendamentos, mock(UsuarioRepository.class), mock(ProfissionalRepository.class), clock);

    @Test void horarioInexistenteRetorna404() {
        UUID id = UUID.randomUUID();
        when(horarios.findLockedById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> agenda.criar(UUID.randomUUID(), new Criar(id, null)))
                .isInstanceOf(ApiException.class).hasMessage("Horário não encontrado.");
        verifyNoInteractions(agendamentos);
    }
    @Test void sessaoQueComecaAgoraNaoPodeSerReservada() {
        var horario = new Horario(); horario.setId(UUID.randomUUID());
        horario.setData(LocalDate.of(2026, 9, 25)); horario.setHoraInicio(LocalTime.of(10, 0));
        when(horarios.findLockedById(horario.getId())).thenReturn(Optional.of(horario));
        assertThatThrownBy(() -> agenda.criar(UUID.randomUUID(), new Criar(horario.getId(), null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("já começou");
        verifyNoInteractions(agendamentos);
    }
}
