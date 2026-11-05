package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.UUID;

/** Billing -> OS: pagamento aprovado. */
public record PagamentoConfirmado(UUID osId, UUID pagamentoId, String mpPaymentId, String status) {
}
