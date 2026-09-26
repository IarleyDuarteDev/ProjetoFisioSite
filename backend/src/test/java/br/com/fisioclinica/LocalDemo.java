package br.com.fisioclinica;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;
import java.security.SecureRandom;
import java.util.*;

/** Ambiente descartável para estudo e inspeção da interface; nunca usado no JAR de produção. */
public class LocalDemo {
    public static void main(String[] args) throws Exception {
        var postgres = EmbeddedPostgres.builder().setPort(0).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { postgres.close(); } catch (Exception ignored) { /* Processo encerrando. */ }
        }));
        var secret = new byte[48]; new SecureRandom().nextBytes(secret);
        var app = new SpringApplication(FisioApplication.class);
        // Propriedades de teste substituem placeholders obrigatórios da configuração principal.
        var properties = new Properties();
        properties.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        properties.setProperty("spring.datasource.username", "postgres");
        properties.setProperty("spring.datasource.password", "postgres");
        properties.setProperty("app.jwt.secret", Base64.getEncoder().encodeToString(secret));
        properties.setProperty("app.demo-data", "true");
        properties.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
        app.run(args);
    }
}
