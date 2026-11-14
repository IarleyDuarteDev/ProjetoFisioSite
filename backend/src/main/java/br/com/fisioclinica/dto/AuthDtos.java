package br.com.fisioclinica.dto;

import br.com.fisioclinica.model.Papel;
import br.com.fisioclinica.model.Usuario;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record Cadastro(@NotBlank @Size(max = 120) String nome,
                           @NotBlank @Email @Size(max = 254) String email,
                           @NotBlank @Size(max = 14) String cpf,
                           @NotBlank @Size(min = 8, max = 72) String senha) {
        @Override public String toString() { return "Cadastro[campos protegidos]"; }
    }
    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 72) String senha) {
        @Override public String toString() { return "Login[campos protegidos]"; }
    }
    public record Recuperar(@NotBlank @Email @Size(max = 254) String email) {
        @Override public String toString() { return "Recuperar[campos protegidos]"; }
    }
    public record LoginSocial(@NotBlank @Size(max = 4096) String token, @Size(max = 120) String nome) {
        @Override public String toString() { return "LoginSocial[campos protegidos]"; }
    }
    public record InformarCpf(@NotBlank @Size(max = 14) String cpf) {
        @Override public String toString() { return "InformarCpf[campos protegidos]"; }
    }
    public record Redefinir(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                            @NotBlank @Size(min = 8, max = 72) String novaSenha) {
        @Override public String toString() { return "Redefinir[campos protegidos]"; }
    }
    public record Cliente(UUID id, String nome, String email, Papel papel, boolean cpfPendente) {
        public static Cliente from(Usuario u) {
            return new Cliente(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.getPapel() == Papel.CLIENTE && u.getCpfHash() == null);
        }
    }
    public record Sessao(String accessToken, String tokenType, long expiresIn, Cliente usuario) {
        @Override public String toString() { return "Sessao[campos protegidos]"; }
    }
    public record Mensagem(String mensagem) {}
}
