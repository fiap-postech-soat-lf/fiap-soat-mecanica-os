package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.UUID;

/** Billing -> OS: não foi possível gerar o orçamento. */
public record OrcamentoFalhou(UUID osId, String motivo) {
}
