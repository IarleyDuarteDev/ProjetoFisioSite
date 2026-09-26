package br.com.fisioclinica.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name = "profissionais") @Getter @Setter @NoArgsConstructor
public class Profissional {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 120)
    private String nome;
    @Column(nullable = false, length = 120)
    private String especialidade;
}
