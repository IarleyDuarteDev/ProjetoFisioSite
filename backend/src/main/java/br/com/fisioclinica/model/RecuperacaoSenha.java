package br.com.fisioclinica.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "recuperacoes_senha") @Getter @Setter @NoArgsConstructor
public class RecuperacaoSenha {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiraEm;
}
