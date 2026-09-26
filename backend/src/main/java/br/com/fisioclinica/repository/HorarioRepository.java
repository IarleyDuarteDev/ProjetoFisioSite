package br.com.fisioclinica.repository;

import br.com.fisioclinica.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.time.*;
import java.util.*;

public interface HorarioRepository extends JpaRepository<Horario, UUID> {
    @Query("""
        select h from Horario h join fetch h.profissional
        where h.status = br.com.fisioclinica.model.StatusHorario.DISPONIVEL
          and h.data = :data and (h.data > :hoje or h.horaInicio > :agora)
          and (:profissionalId is null or h.profissional.id = :profissionalId)
          and (:tipoSessao is null or h.tipoSessao = :tipoSessao)
        order by h.horaInicio, h.profissional.nome
        """)
    List<Horario> disponiveis(LocalDate data, LocalDate hoje, LocalTime agora,
                             UUID profissionalId, TipoSessao tipoSessao, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Horario h where h.id = :id")
    Optional<Horario> findLockedById(UUID id);
}
