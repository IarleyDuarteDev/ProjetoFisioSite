package br.com.fisioclinica.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/** Liga uma conta do Google/Apple (pelo "sub" estável do provedor, nunca pelo e-mail) a um usuário. */
@Entity @Table(name = "identidades_sociais") @Getter @Setter @NoArgsConstructor
public class IdentidadeSocial {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Provedor provedor;
    @Column(nullable = false, length = 255)
    private String subject;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
