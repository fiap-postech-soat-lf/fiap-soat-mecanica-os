package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.UUID;

/** Billing -> OS: o cliente aprovou o orçamento (o pivô da Saga). */
public record OrcamentoAprovado(UUID osId, UUID orcamentoId, UUID aprovadoPor) {
}
