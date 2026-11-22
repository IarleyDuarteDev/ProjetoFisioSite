package br.com.fisioclinica.repository;

import br.com.fisioclinica.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import br.com.fisioclinica.model.Papel;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);
    List<Usuario> findByPapelAndEmailNot(Papel papel, String email);
    boolean existsByEmail(String email);
    boolean existsByCpfHash(String cpfHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> findLockedById(UUID id);
}
