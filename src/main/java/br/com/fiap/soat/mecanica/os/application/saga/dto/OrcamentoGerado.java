package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Billing -> OS: orçamento pronto para a aprovação do cliente. */
public record OrcamentoGerado(UUID osId, UUID orcamentoId, BigDecimal valorTotal, Instant expiraEm) {
}
