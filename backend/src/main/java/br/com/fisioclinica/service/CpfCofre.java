package br.com.fisioclinica.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/** Guarda o CPF cifrado (AES-GCM) e gera um hash com chave (HMAC) só para checar duplicidade. Não troque a CPF_KEY depois de ter usuários. */
@Service
public class CpfCofre {
    private final SecretKeySpec chaveCifra;
    private final SecretKeySpec chaveHash;
    private final SecureRandom random = new SecureRandom();

    public CpfCofre(@Value("${app.cpf.key}") String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalArgumentException("CPF_KEY deve conter pelo menos 32 bytes aleatórios.");
        chaveCifra = new SecretKeySpec(derivar(bytes, "cifra"), "AES");
        chaveHash = new SecretKeySpec(derivar(bytes, "hash"), "HmacSHA256");
    }
    public String cifrar(String cpf) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chaveCifra, new GCMParameterSpec(128, iv));
            byte[] dados = cipher.doFinal(cpf.getBytes(StandardCharsets.UTF_8));
            byte[] saida = Arrays.copyOf(iv, iv.length + dados.length);
            System.arraycopy(dados, 0, saida, iv.length, dados.length);
            return Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    public String decifrar(String valor) {
        try {
            byte[] bytes = Base64.getDecoder().decode(valor);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chaveCifra, new GCMParameterSpec(128, bytes, 0, 12));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    public String hash(String cpf) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHash);
            return HexFormat.of().formatHex(mac.doFinal(cpf.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    private static byte[] derivar(byte[] segredo, String uso) {
        try {
            var sha = MessageDigest.getInstance("SHA-256");
            sha.update(uso.getBytes(StandardCharsets.UTF_8));
            return sha.digest(segredo);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
