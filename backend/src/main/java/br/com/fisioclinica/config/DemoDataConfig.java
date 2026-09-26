package br.com.fisioclinica.config;

import br.com.fisioclinica.model.*;
import br.com.fisioclinica.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Component @ConditionalOnProperty(name = "app.demo-data", havingValue = "true") @RequiredArgsConstructor
public class DemoDataConfig implements CommandLineRunner {
    private final ProfissionalRepository profissionais;
    private final HorarioRepository horarios;
    private final Clock clock;
    @Override @Transactional
    public void run(String... args) {
        if (profissionais.count() > 0 || horarios.count() > 0) return;
        var nomes = List.of("Marina Costa", "Rafael Almeida", "Clara Mendes");
        var especialidades = List.of("Fisioterapia ortopédica", "Reabilitação esportiva", "Pilates clínico");
        for (int i = 0; i < nomes.size(); i++) {
            var p = new Profissional();
            p.setNome(nomes.get(i)); p.setEspecialidade(especialidades.get(i));
            profissionais.save(p);
            for (int dia = 1; dia <= 14; dia++) {
                var data = LocalDate.now(clock).plusDays(dia);
                if (data.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                for (int hora : new int[]{8, 9, 10, 14, 15, 16}) {
                    var h = new Horario();
                    h.setData(data); h.setHoraInicio(LocalTime.of(hora, 0)); h.setHoraFim(LocalTime.of(hora, 50));
                    h.setProfissional(p);
                    h.setTipoSessao(i == 2 ? TipoSessao.PILATES : (hora == 8 ? TipoSessao.AVALIACAO : TipoSessao.FISIOTERAPIA));
                    horarios.save(h);
                }
            }
        }
    }
}
