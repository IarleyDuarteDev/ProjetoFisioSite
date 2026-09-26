package br.com.fisioclinica.service;

import br.com.fisioclinica.dto.AgendaDtos.*;
import br.com.fisioclinica.exception.ApiException;
import br.com.fisioclinica.model.*;
import br.com.fisioclinica.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AgendaService {
    private final HorarioRepository horarios;
    private final AgendamentoRepository agendamentos;
    private final UsuarioRepository usuarios;
    private final ProfissionalRepository profissionais;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ProfissionalResposta> profissionais() {
        return profissionais.findAllByOrderByNomeAsc().stream().map(ProfissionalResposta::from).toList();
    }
    @Transactional(readOnly = true)
    public List<HorarioResposta> disponiveis(LocalDate data, UUID profissionalId, TipoSessao tipoSessao) {
        var now = LocalDateTime.now(clock);
        if (data.isBefore(now.toLocalDate())) return List.of();
        return horarios.disponiveis(data, now.toLocalDate(), now.toLocalTime(), profissionalId, tipoSessao, PageRequest.of(0, 200))
                .stream().map(HorarioResposta::from).toList();
    }
    @Transactional(readOnly = true)
    public Pagina<AgendamentoResposta> meus(UUID clienteId, int pagina, int tamanho) {
        var result = agendamentos.findByClienteId(clienteId, PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Order.desc("horario.data"), Sort.Order.desc("horario.horaInicio"), Sort.Order.asc("id"))));
        return new Pagina<>(result.map(AgendamentoResposta::from).getContent(), pagina, tamanho, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional
    public AgendamentoResposta criar(UUID clienteId, Criar dto) {
        var horario = bloquear(dto.horarioId());
        exigirDisponivel(horario);
        var reserva = new Agendamento();
        reserva.setCliente(usuarios.getReferenceById(clienteId));
        reserva.setHorario(horario);
        reserva.setObservacoes(dto.observacoes());
        horario.setStatus(StatusHorario.RESERVADO);
        return AgendamentoResposta.from(agendamentos.saveAndFlush(reserva));
    }
    @Transactional
    public AgendamentoResposta atualizar(UUID clienteId, UUID id, Atualizar dto) {
        var reserva = agendamentos.findOwnedLocked(id, clienteId)
                .orElseThrow(() -> ApiException.notFound("Agendamento não encontrado."));
        if (reserva.getStatus() == StatusAgendamento.CANCELADO) throw ApiException.conflict("Este agendamento já foi cancelado.");
        UUID atualId = reserva.getHorario().getId();
        if (dto.acao() == Acao.CANCELAR) {
            var atual = bloquear(atualId);
            exigirFuturo(atual);
            atual.setStatus(StatusHorario.DISPONIVEL);
            reserva.setStatus(StatusAgendamento.CANCELADO);
        } else {
            if (atualId.equals(dto.novoHorarioId())) throw ApiException.badRequest("Escolha um horário diferente do atual.");
            // Ordem estável evita deadlocks em remarcações que disputam os mesmos horários.
            var ids = new ArrayList<>(List.of(atualId, dto.novoHorarioId()));
            ids.sort(Comparator.naturalOrder());
            var bloqueados = new HashMap<UUID, Horario>();
            ids.forEach(hid -> bloqueados.put(hid, bloquear(hid)));
            var atual = bloqueados.get(atualId);
            var novo = bloqueados.get(dto.novoHorarioId());
            exigirFuturo(atual);
            exigirDisponivel(novo);
            atual.setStatus(StatusHorario.DISPONIVEL);
            novo.setStatus(StatusHorario.RESERVADO);
            reserva.setHorario(novo);
        }
        agendamentos.flush();
        return AgendamentoResposta.from(reserva);
    }
    private Horario bloquear(UUID id) {
        return horarios.findLockedById(id).orElseThrow(() -> ApiException.notFound("Horário não encontrado."));
    }
    private void exigirDisponivel(Horario horario) {
        exigirFuturo(horario);
        if (horario.getStatus() != StatusHorario.DISPONIVEL) throw ApiException.conflict("Este horário já foi reservado. Escolha outro.");
    }
    private void exigirFuturo(Horario horario) {
        if (!LocalDateTime.of(horario.getData(), horario.getHoraInicio()).isAfter(LocalDateTime.now(clock)))
            throw ApiException.conflict("Não é possível alterar ou reservar uma sessão que já começou.");
    }
}
