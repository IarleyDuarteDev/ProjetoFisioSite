package br.com.fisioclinica.repository;

import br.com.fisioclinica.model.ConfirmacaoEmail;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ConfirmacaoEmailRepository extends JpaRepository<ConfirmacaoEmail, UUID> {
    Optional<ConfirmacaoEmail> findByTokenHash(String tokenHash);
    Optional<ConfirmacaoEmail> findByUsuarioId(UUID usuarioId);
    @Query("select c.usuario.id from ConfirmacaoEmail c where c.tokenHash = :hash")
    Optional<UUID> findUsuarioIdByTokenHash(String hash);
}
