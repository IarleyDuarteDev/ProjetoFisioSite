package br.com.fisioclinica.controller;

import br.com.fisioclinica.dto.AgendaDtos.*;
import br.com.fisioclinica.model.TipoSessao;
import br.com.fisioclinica.service.AgendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequiredArgsConstructor
public class HorarioController {
    private final AgendaService agenda;
    @GetMapping("/profissionais")
    public List<ProfissionalResposta> profissionais() { return agenda.profissionais(); }
    @GetMapping("/horarios/disponiveis")
    public List<HorarioResposta> disponiveis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) UUID profissionalId,
            @RequestParam(required = false) TipoSessao tipoSessao) {
        return agenda.disponiveis(data, profissionalId, tipoSessao);
    }
}
