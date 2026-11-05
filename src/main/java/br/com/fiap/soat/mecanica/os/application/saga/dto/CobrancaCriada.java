package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.UUID;

/** Billing -> OS: cobrança criada no gateway (a primeira ou uma nova). */
public record CobrancaCriada(UUID osId, UUID pagamentoId, String link, String status) {
}
