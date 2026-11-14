package br.com.fisioclinica.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name = "usuarios") @Getter @Setter @NoArgsConstructor
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 120)
    private String nome;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 100)
    private String senhaHash;
    @Column(nullable = false)
    private long tokenVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Papel papel = Papel.CLIENTE;
    @Column(nullable = false)
    private boolean emailVerificado;
    @Column(length = 200)
    private String cpfCifrado;
    @Column(length = 64)
    private String cpfHash;
}
