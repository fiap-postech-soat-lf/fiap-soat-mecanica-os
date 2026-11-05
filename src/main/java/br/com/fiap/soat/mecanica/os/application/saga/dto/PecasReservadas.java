package br.com.fiap.soat.mecanica.os.application.saga.dto;

import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.PecaReservada;

import java.util.List;
import java.util.UUID;

/** Execução -> OS: as peças foram reservadas. */
public record PecasReservadas(UUID osId, List<PecaReservada> pecas) {
}
