package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.UUID;

/** Billing -> OS: o cliente recusou o orçamento. */
public record OrcamentoRecusado(UUID osId, UUID orcamentoId, UUID recusadoPor) {
}
