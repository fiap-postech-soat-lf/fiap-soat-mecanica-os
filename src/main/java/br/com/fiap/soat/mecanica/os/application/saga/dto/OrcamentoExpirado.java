package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.UUID;

/** Billing -> OS: o orçamento venceu sem resposta. */
public record OrcamentoExpirado(UUID osId, UUID orcamentoId) {
}
