package br.com.fisioclinica.controller;

import br.com.fisioclinica.dto.AgendaDtos.Pagina;
import br.com.fisioclinica.service.AdminService;
import br.com.fisioclinica.service.AdminService.AgendamentoAdmin;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Rotas exclusivas do administrador. O acesso é exigido em SecurityConfig (/admin/** exige o papel ADMIN). */
@RestController @RequestMapping("/admin") @RequiredArgsConstructor @Validated
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private final AdminService admin;

    @GetMapping("/agendamentos")
    public Pagina<AgendamentoAdmin> agendamentos(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return admin.agendamentos(pagina, tamanho);
    }
}
