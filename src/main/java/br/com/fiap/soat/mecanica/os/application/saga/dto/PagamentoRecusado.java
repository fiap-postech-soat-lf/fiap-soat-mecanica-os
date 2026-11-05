package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.UUID;

/** Billing -> OS: o gateway recusou o pagamento. */
public record PagamentoRecusado(UUID osId, UUID pagamentoId, String mpPaymentId, String status, String motivo) {
}
