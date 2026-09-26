package br.com.fisioclinica.controller;

import br.com.fisioclinica.dto.AuthDtos.*;
import br.com.fisioclinica.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Sessao cadastrar(@Valid @RequestBody Cadastro dto) { return auth.cadastrar(dto); }
    @PostMapping("/login")
    public Sessao login(@Valid @RequestBody Login dto) { return auth.login(dto); }
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED)
    public Mensagem recuperar(@Valid @RequestBody Recuperar dto) {
        auth.recuperar(dto);
        return new Mensagem("Se o e-mail estiver cadastrado, você receberá um link para redefinir sua senha.");
    }
    @PostMapping("/reset-password")
    public Mensagem redefinir(@Valid @RequestBody Redefinir dto) {
        auth.redefinir(dto);
        return new Mensagem("Senha alterada. Entre novamente com a nova senha.");
    }
}
