package br.com.fisioclinica.dto;

import br.com.fisioclinica.dto.AgendaDtos.HorarioResposta;
import br.com.fisioclinica.model.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class AdminDtos {
    private AdminDtos() {}

    public record NovoProfissional(@NotBlank @Size(max = 120) String nome, @NotBlank @Size(max = 120) String especialidade) {}

    /** Cria vários horários de um profissional no mesmo dia: cada início ganha a mesma duração. */
    public record NovosHorarios(@NotNull UUID profissionalId, @NotNull LocalDate data, @NotNull TipoSessao tipoSessao,
                                @NotEmpty @Size(max = 24) List<@NotNull LocalTime> inicios,
                                @Min(10) @Max(180) int duracaoMinutos) {}

    public record HorarioAdmin(HorarioResposta horario, StatusHorario status) {
        public static HorarioAdmin from(Horario h) { return new HorarioAdmin(HorarioResposta.from(h), h.getStatus()); }
    }
}
