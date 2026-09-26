package br.com.fisioclinica.repository;

import br.com.fisioclinica.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, UUID> {
    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);
    Optional<RecuperacaoSenha> findByUsuarioId(UUID usuarioId);
    @Query("select r.usuario.id from RecuperacaoSenha r where r.tokenHash = :hash")
    Optional<UUID> findUsuarioIdByTokenHash(String hash);
}
