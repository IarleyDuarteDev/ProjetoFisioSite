package br.com.fisioclinica.repository;

import br.com.fisioclinica.model.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {
    List<Profissional> findAllByOrderByNomeAsc();
}
