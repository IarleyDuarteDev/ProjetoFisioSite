package br.com.fisioclinica.repository;

import br.com.fisioclinica.model.Agendamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {
    @EntityGraph(attributePaths = {"horario", "horario.profissional"})
    Page<Agendamento> findByClienteId(UUID clienteId, Pageable pageable);

    @EntityGraph(attributePaths = {"horario", "horario.profissional", "cliente"})
    Page<Agendamento> findAllBy(Pageable pageable);

    boolean existsByHorarioId(UUID horarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agendamento a where a.id = :id")
    Optional<Agendamento> findLockedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agendamento a where a.id = :id and a.cliente.id = :clienteId")
    Optional<Agendamento> findOwnedLocked(UUID id, UUID clienteId);
}
