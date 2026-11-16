package br.com.fisioclinica.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Com APP_PRODUCAO=true o servidor não sobe se alguma configuração for a de desenvolvimento. Nunca mostra os valores. */
@Component
public class VerificacaoProducao implements InitializingBean {
    private final Environment env;

    public VerificacaoProducao(Environment env) { this.env = env; }

    @Override
    public void afterPropertiesSet() {
        if (!env.getProperty("app.producao", Boolean.class, false)) return;
        var problemas = problemas();
        if (!problemas.isEmpty()) throw new IllegalStateException("Configuração insegura para produção:\n - " + String.join("\n - ", problemas));
    }

    List<String> problemas() {
        var lista = new ArrayList<String>();
        if (fraco(env.getProperty("app.jwt.secret"), 48)) lista.add("JWT_SECRET precisa ser aleatório, ter pelo menos 48 caracteres e ser diferente dos exemplos do repositório.");
        if (fraco(env.getProperty("app.cpf.key"), 48)) lista.add("CPF_KEY precisa ser aleatória, ter pelo menos 48 caracteres e ser diferente dos exemplos do repositório.");
        if (fraco(env.getProperty("spring.datasource.password"), 16)) lista.add("A senha do banco (DB_PASSWORD) precisa ter pelo menos 16 caracteres e não pode ser a de desenvolvimento.");
        if (env.getProperty("app.demo-data", Boolean.class, false)) lista.add("DEMO_DATA precisa estar desligado.");
        if (env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false) || env.getProperty("springdoc.api-docs.enabled", Boolean.class, false))
            lista.add("SWAGGER_ENABLED precisa estar desligado.");
        if (!env.getProperty("app.frontend-url", "").startsWith("https://")) lista.add("FRONTEND_URL precisa começar com https://.");
        if (env.getProperty("spring.mail.host", "localhost").equals("localhost")) lista.add("SMTP_HOST precisa apontar para um servidor de e-mail real.");
        if (!env.getProperty("app.limites.ativo", Boolean.class, true)) lista.add("O limite de requisições (LIMITES_ATIVO) precisa estar ligado.");
        return lista;
    }

    private static boolean fraco(String valor, int minimo) {
        if (valor == null || valor.strip().length() < minimo) return true;
        String v = valor.toLowerCase();
        return v.contains("substitua") || v.contains("desenvolvimento") || v.contains("troque-em-producao") || v.contains("local-dev") || v.contains("exemplo");
    }
}
