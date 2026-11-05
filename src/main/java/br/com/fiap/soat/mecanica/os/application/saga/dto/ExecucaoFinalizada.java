package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Execução -> OS: a execução terminou. */
public record ExecucaoFinalizada(UUID osId, Instant finalizadaEm, List<TempoServico> tempos) {

    public record TempoServico(UUID servicoId, int minutos) {
    }
}
