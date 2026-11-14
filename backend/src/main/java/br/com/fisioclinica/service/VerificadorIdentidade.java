package br.com.fisioclinica.service;

import br.com.fisioclinica.model.Provedor;
import java.util.Map;

/** Confere o token de identidade (ID token) emitido pelo Google ou pela Apple. Nos testes é substituído por um falso. */
public interface VerificadorIdentidade {
    record Identidade(Provedor provedor, String subject, String email, boolean emailVerificado, String nome) {}

    Identidade verificar(Provedor provedor, String token);

    /** Client IDs públicos que o front usa para montar os botões; vazio = provedor desligado. */
    default Map<String, String> clientes() { return Map.of(); }
}
