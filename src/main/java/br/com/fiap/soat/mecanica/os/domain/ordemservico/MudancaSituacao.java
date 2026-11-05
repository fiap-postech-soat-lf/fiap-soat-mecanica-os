package br.com.fiap.soat.mecanica.os.domain.ordemservico;

import java.time.Instant;

/** O que uma transicao da OS produziu; vira historico e SituacaoOrdemServicoAlterada. {@code de} e nulo na abertura. */
public record MudancaSituacao(SituacaoOrdemServico de, SituacaoOrdemServico para, String motivo, Instant em) {
}
