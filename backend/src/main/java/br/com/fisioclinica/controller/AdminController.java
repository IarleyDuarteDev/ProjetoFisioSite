package br.com.fisioclinica.controller;

import br.com.fisioclinica.dto.AdminDtos.*;
import br.com.fisioclinica.dto.AgendaDtos.Pagina;
import br.com.fisioclinica.dto.AgendaDtos.ProfissionalResposta;
import br.com.fisioclinica.service.AdminService;
import br.com.fisioclinica.service.AdminService.AgendamentoAdmin;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/** Rotas exclusivas do administrador (SecurityConfig exige o papel ADMIN em /admin/**). Cada alteração fica no log. */
@RestController @RequestMapping("/admin") @RequiredArgsConstructor @Validated @Slf4j
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private final AdminService admin;

    @GetMapping("/agendamentos")
    public Pagina<AgendamentoAdmin> agendamentos(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return admin.agendamentos(pagina, tamanho);
    }
    @PutMapping("/agendamentos/{id}/cancelar")
    public AgendamentoAdmin cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        log.info("auditoria admin={} acao=cancelar-agendamento id={}", jwt.getSubject(), id);
        return admin.cancelar(id);
    }
    @PostMapping("/profissionais") @ResponseStatus(HttpStatus.CREATED)
    public ProfissionalResposta criarProfissional(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NovoProfissional dto) {
        log.info("auditoria admin={} acao=criar-profissional", jwt.getSubject());
        return admin.criarProfissional(dto);
    }
    @GetMapping("/horarios")
    public List<HorarioAdmin> horarios(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return admin.horariosDoDia(data);
    }
    @PostMapping("/horarios") @ResponseStatus(HttpStatus.CREATED)
    public List<HorarioAdmin> criarHorarios(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NovosHorarios dto) {
        log.info("auditoria admin={} acao=criar-horarios profissional={} data={} quantidade={}", jwt.getSubject(), dto.profissionalId(), dto.data(), dto.inicios().size());
        return admin.criarHorarios(dto);
    }
    @DeleteMapping("/horarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerHorario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        log.info("auditoria admin={} acao=remover-horario id={}", jwt.getSubject(), id);
        admin.removerHorario(id);
    }
}
