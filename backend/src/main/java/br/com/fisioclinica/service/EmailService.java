package br.com.fisioclinica.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender sender;
    private final String from;
    private final String frontendUrl;
    public EmailService(JavaMailSender sender, @Value("${app.mail-from}") String from,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.sender = sender; this.from = from; this.frontendUrl = frontendUrl;
    }
    public void enviarRecuperacao(String email, String token) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Fisio Clínica — redefina sua senha");
        // Fragmento não é enviado ao servidor web nem registrado no access log.
        message.setText("Use o link abaixo em até 15 minutos para criar uma nova senha:\n\n"
                + frontendUrl + "/#redefinir=" + token + "\n\nSe você não solicitou, ignore este e-mail.");
        sender.send(message);
    }
}
