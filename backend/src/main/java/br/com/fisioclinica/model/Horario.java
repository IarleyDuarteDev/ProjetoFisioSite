package br.com.fisioclinica.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "horarios") @Getter @Setter @NoArgsConstructor
public class Horario {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private LocalDate data;
    @Column(nullable = false)
    private LocalTime horaInicio;
    @Column(nullable = false)
    private LocalTime horaFim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "profissional_id")
    private Profissional profissional;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private TipoSessao tipoSessao;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StatusHorario status = StatusHorario.DISPONIVEL;
}
