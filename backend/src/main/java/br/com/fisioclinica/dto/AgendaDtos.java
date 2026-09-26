package br.com.fisioclinica.dto;

import br.com.fisioclinica.model.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class AgendaDtos {
    private AgendaDtos() {}
    public enum Acao { CANCELAR, REMARCAR }
    public record Criar(@NotNull UUID horarioId, @Size(max = 1000) String observacoes) {}
    public record Atualizar(@NotNull Acao acao, UUID novoHorarioId) {
        @AssertTrue(message = "REMARCAR exige novoHorarioId; CANCELAR não aceita novoHorarioId")
        public boolean isAcaoValida() {
            return acao == null || (acao == Acao.REMARCAR ? novoHorarioId != null : novoHorarioId == null);
        }
    }
    public record ProfissionalResposta(UUID id, String nome, String especialidade) {
        public static ProfissionalResposta from(Profissional p) {
            return new ProfissionalResposta(p.getId(), p.getNome(), p.getEspecialidade());
        }
    }
    public record HorarioResposta(UUID id, LocalDate data, LocalTime horaInicio, LocalTime horaFim,
                                  TipoSessao tipoSessao, ProfissionalResposta profissional) {
        public static HorarioResposta from(Horario h) {
            return new HorarioResposta(h.getId(), h.getData(), h.getHoraInicio(), h.getHoraFim(),
                    h.getTipoSessao(), ProfissionalResposta.from(h.getProfissional()));
        }
    }
    public record AgendamentoResposta(UUID id, StatusAgendamento status, String observacoes, HorarioResposta horario) {
        public static AgendamentoResposta from(Agendamento a) {
            return new AgendamentoResposta(a.getId(), a.getStatus(), a.getObservacoes(), HorarioResposta.from(a.getHorario()));
        }
    }
    public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {}
}
