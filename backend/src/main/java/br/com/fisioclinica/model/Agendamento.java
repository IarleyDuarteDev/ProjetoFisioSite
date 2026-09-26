package br.com.fisioclinica.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name = "agendamentos") @Getter @Setter @NoArgsConstructor
public class Agendamento {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id")
    private Usuario cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "horario_id")
    private Horario horario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StatusAgendamento status = StatusAgendamento.CONFIRMADO;
    @Column(length = 1000)
    private String observacoes;
}
