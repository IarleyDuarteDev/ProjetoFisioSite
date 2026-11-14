package br.com.fisioclinica.service;

import br.com.fisioclinica.dto.AdminDtos.*;
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
public class AdminService {
    private final AgendamentoRepository agendamentos;
    private final HorarioRepository horarios;
    private final ProfissionalRepository profissionais;
    private final CpfCofre cofre;
    private final Clock clock;

    /** O CPF sai sempre mascarado, mesmo para o administrador. */
    public record AgendamentoAdmin(AgendamentoResposta agendamento, String clienteNome, String clienteEmail, String clienteCpf) {}

    @Transactional(readOnly = true)
    public Pagina<AgendamentoAdmin> agendamentos(int pagina, int tamanho) {
        var result = agendamentos.findAllBy(PageRequest.of(pagina, tamanho, Sort.by(Sort.Order.desc("horario.data"),
                Sort.Order.desc("horario.horaInicio"), Sort.Order.asc("id"))));
        var itens = result.getContent().stream().map(this::paraAdmin).toList();
        return new Pagina<>(itens, pagina, tamanho, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AgendamentoAdmin cancelar(UUID id) {
        var reserva = agendamentos.findLockedById(id).orElseThrow(() -> ApiException.notFound("Agendamento não encontrado."));
        if (reserva.getStatus() == StatusAgendamento.CANCELADO) throw ApiException.conflict("Este agendamento já foi cancelado.");
        var horario = horarios.findLockedById(reserva.getHorario().getId()).orElseThrow(() -> ApiException.notFound("Horário não encontrado."));
        reserva.setStatus(StatusAgendamento.CANCELADO);
        horario.setStatus(StatusHorario.DISPONIVEL);
        agendamentos.flush();
        return paraAdmin(reserva);
    }

    @Transactional
    public ProfissionalResposta criarProfissional(NovoProfissional dto) {
        var profissional = new Profissional();
        profissional.setNome(dto.nome().strip());
        profissional.setEspecialidade(dto.especialidade().strip());
        return ProfissionalResposta.from(profissionais.save(profissional));
    }

    @Transactional(readOnly = true)
    public List<HorarioAdmin> horariosDoDia(LocalDate data) {
        return horarios.doDia(data).stream().map(HorarioAdmin::from).toList();
    }

    /** Tudo ou nada: se um horário passou, termina depois da meia-noite ou se sobrepõe a outro, nenhum é criado. */
    @Transactional
    public List<HorarioAdmin> criarHorarios(NovosHorarios dto) {
        var profissional = profissionais.findById(dto.profissionalId()).orElseThrow(() -> ApiException.notFound("Profissional não encontrado."));
        var agora = LocalDateTime.now(clock);
        var ocupados = new ArrayList<>(horarios.findByProfissionalIdAndData(profissional.getId(), dto.data()));
        var novos = new ArrayList<Horario>();
        for (LocalTime inicio : dto.inicios().stream().distinct().sorted().toList()) {
            LocalTime fim = inicio.plusMinutes(dto.duracaoMinutos());
            if (!fim.isAfter(inicio)) throw ApiException.badRequest("O horário das " + inicio + " termina depois da meia-noite.");
            if (!LocalDateTime.of(dto.data(), inicio).isAfter(agora)) throw ApiException.badRequest("O horário das " + inicio + " já passou.");
            if (ocupados.stream().anyMatch(o -> inicio.isBefore(o.getHoraFim()) && fim.isAfter(o.getHoraInicio())))
                throw ApiException.conflict("O horário das " + inicio + " se sobrepõe a outro deste profissional.");
            var horario = new Horario();
            horario.setProfissional(profissional);
            horario.setData(dto.data());
            horario.setHoraInicio(inicio);
            horario.setHoraFim(fim);
            horario.setTipoSessao(dto.tipoSessao());
            novos.add(horario);
            ocupados.add(horario);
        }
        return horarios.saveAll(novos).stream().map(HorarioAdmin::from).toList();
    }

    @Transactional
    public void removerHorario(UUID id) {
        var horario = horarios.findLockedById(id).orElseThrow(() -> ApiException.notFound("Horário não encontrado."));
        if (horario.getStatus() != StatusHorario.DISPONIVEL) throw ApiException.conflict("Cancele o agendamento antes de remover este horário.");
        if (agendamentos.existsByHorarioId(id)) throw ApiException.conflict("Este horário tem histórico de agendamentos e não pode ser removido.");
        horarios.delete(horario);
    }

    private AgendamentoAdmin paraAdmin(Agendamento a) {
        var cliente = a.getCliente();
        return new AgendamentoAdmin(AgendamentoResposta.from(a), cliente.getNome(), cliente.getEmail(),
                cliente.getCpfCifrado() == null ? null : Cpf.mascarar(cofre.decifrar(cliente.getCpfCifrado())));
    }
}
