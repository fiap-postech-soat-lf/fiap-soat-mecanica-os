package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.UUID;

/** Execução -> OS: a reserva foi desfeita. */
public record ReservaPecasLiberada(UUID osId) {
}
