package br.com.fisioclinica.controller;

import br.com.fisioclinica.dto.AgendaDtos.*;
import br.com.fisioclinica.service.AgendaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/agendamentos") @RequiredArgsConstructor @Validated
@SecurityRequirement(name = "bearerAuth")
public class AgendamentoController {
    private final AgendaService agenda;
    @GetMapping("/meus")
    public Pagina<AgendamentoResposta> meus(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return agenda.meus(UUID.fromString(jwt.getSubject()), pagina, tamanho);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoResposta criar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Criar dto) {
        return agenda.criar(UUID.fromString(jwt.getSubject()), dto);
    }
    @PutMapping("/{id}")
    public AgendamentoResposta atualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody Atualizar dto) {
        return agenda.atualizar(UUID.fromString(jwt.getSubject()), id, dto);
    }
}
