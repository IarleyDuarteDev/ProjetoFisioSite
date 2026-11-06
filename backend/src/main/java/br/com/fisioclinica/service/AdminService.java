package br.com.fisioclinica.service;

import br.com.fisioclinica.dto.AgendaDtos.*;
import br.com.fisioclinica.repository.AgendamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdminService {
    private final AgendamentoRepository agendamentos;
    private final CpfCofre cofre;

    /** O CPF sai sempre mascarado, mesmo para o administrador. */
    public record AgendamentoAdmin(AgendamentoResposta agendamento, String clienteNome, String clienteEmail, String clienteCpf) {}

    @Transactional(readOnly = true)
    public Pagina<AgendamentoAdmin> agendamentos(int pagina, int tamanho) {
        var result = agendamentos.findAllBy(PageRequest.of(pagina, tamanho, Sort.by(Sort.Order.desc("horario.data"),
                Sort.Order.desc("horario.horaInicio"), Sort.Order.asc("id"))));
        var itens = result.getContent().stream().map(a -> new AgendamentoAdmin(AgendamentoResposta.from(a),
                a.getCliente().getNome(), a.getCliente().getEmail(),
                a.getCliente().getCpfCifrado() == null ? null : Cpf.mascarar(cofre.decifrar(a.getCliente().getCpfCifrado())))).toList();
        return new Pagina<>(itens, pagina, tamanho, result.getTotalElements(), result.getTotalPages());
    }
}
