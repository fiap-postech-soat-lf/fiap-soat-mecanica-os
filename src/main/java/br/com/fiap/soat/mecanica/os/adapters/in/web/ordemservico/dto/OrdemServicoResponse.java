package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Os campos da Saga so aparecem no GET /ordem-servicos/{id}. */
public record OrdemServicoResponse(
        UUID id,
        SituacaoOrdemServico situacao,
        Instant dataRecebida,
        Instant dataDiagnostico,
        Instant dataAguardandoAprovacao,
        Instant dataExecucao,
        Instant dataFinalizada,
        Instant dataAguardandoPagamento,
        Instant dataEntregue,
        Instant dataCancelada,
        String motivoCancelamento,
        Boolean pago,
        BigDecimal valorTotal,
        String observacao,
        UUID veiculoId,
        UUID clienteId,
        String placa,
        UUID usuarioAberturaId,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID sagaId,
        @JsonInclude(JsonInclude.Include.NON_NULL) SagaStatus sagaStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) PassoSaga passoAtual,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID orcamentoId
) {
}
