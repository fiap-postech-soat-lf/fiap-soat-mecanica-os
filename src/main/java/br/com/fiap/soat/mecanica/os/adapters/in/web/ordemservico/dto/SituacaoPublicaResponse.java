package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;

import java.time.Instant;

/** Resposta da rota publica por placa: so a situacao e as datas, sem id, cliente, valores nem observacao. */
public record SituacaoPublicaResponse(
        SituacaoOrdemServico situacao,
        Instant dataRecebida,
        Instant dataDiagnostico,
        Instant dataAguardandoAprovacao,
        Instant dataExecucao,
        Instant dataFinalizada,
        Instant dataAguardandoPagamento,
        Instant dataEntregue,
        Instant dataCancelada
) {
}
