package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;

import java.time.Instant;

public record HistoricoSituacaoResponse(SituacaoOrdemServico de, SituacaoOrdemServico para, String motivo,
                                        Instant ocorridaEm) {
}
