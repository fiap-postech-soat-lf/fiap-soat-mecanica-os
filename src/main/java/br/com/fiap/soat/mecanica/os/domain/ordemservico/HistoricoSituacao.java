package br.com.fiap.soat.mecanica.os.domain.ordemservico;

import java.time.Instant;
import java.util.UUID;

public record HistoricoSituacao(UUID id, UUID osId, SituacaoOrdemServico de, SituacaoOrdemServico para,
                                String motivo, Instant ocorridaEm) {
}
