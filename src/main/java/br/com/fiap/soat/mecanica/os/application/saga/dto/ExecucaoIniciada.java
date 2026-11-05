package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.time.Instant;
import java.util.UUID;

/** Execução -> OS: a execução começou (a reserva virou baixa). */
public record ExecucaoIniciada(UUID osId, Instant iniciadaEm) {
}
