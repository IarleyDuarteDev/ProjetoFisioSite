package br.com.fisioclinica;

import br.com.fisioclinica.config.VerificacaoProducao;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class VerificacaoProducaoTest {
    private static final String CHAVE = "Qm9yYS1lLXVtLXNlZ3JlZG8tbXVpdG8tZ3JhbmRlLWUtYWxlYXRvcmlvLTEyMzQ1Njc4OTA";

    private MockEnvironment producaoSegura() {
        return new MockEnvironment().withProperty("app.producao", "true").withProperty("app.jwt.secret", CHAVE)
                .withProperty("app.cpf.key", CHAVE + "outra").withProperty("spring.datasource.password", "Xk29fj38Ahs72Hd91Pq")
                .withProperty("app.frontend-url", "https://clinica.exemplo.com.br").withProperty("spring.mail.host", "smtp.provedor.com");
    }

    @Test void configuracaoSeguraSobe() {
        assertThatCode(() -> new VerificacaoProducao(producaoSegura()).afterPropertiesSet()).doesNotThrowAnyException();
    }
    @Test void recusaSegredosDeExemploDemoSwaggerEHttp() {
        var env = producaoSegura().withProperty("app.jwt.secret", "chave-exclusiva-de-desenvolvimento-troque-em-producao-2026")
                .withProperty("app.demo-data", "true").withProperty("springdoc.swagger-ui.enabled", "true").withProperty("app.frontend-url", "http://localhost:5173");
        assertThatThrownBy(() -> new VerificacaoProducao(env).afterPropertiesSet()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET").hasMessageContaining("DEMO_DATA").hasMessageContaining("SWAGGER_ENABLED").hasMessageContaining("https://")
                .hasMessageNotContaining("troque-em-producao");
    }
    @Test void foraDeProducaoNaoExigeNada() {
        var env = new MockEnvironment().withProperty("app.jwt.secret", "curta");
        assertThatCode(() -> new VerificacaoProducao(env).afterPropertiesSet()).doesNotThrowAnyException();
    }
}
