package br.com.fisioclinica.repository;

import br.com.fisioclinica.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface IdentidadeSocialRepository extends JpaRepository<IdentidadeSocial, UUID> {
    @EntityGraph(attributePaths = "usuario")
    Optional<IdentidadeSocial> findByProvedorAndSubject(Provedor provedor, String subject);
}
