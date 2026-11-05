package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico;

import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.HistoricoSituacaoResponse;
import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.OrdemServicoResponse;
import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.SituacaoPublicaResponse;
import br.com.fiap.soat.mecanica.os.application.ordemservico.dto.OrdemServicoDetalhe;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;

final class OrdemServicoResponseMapper {

    private OrdemServicoResponseMapper() {
    }

    static OrdemServicoResponse toResponse(OrdemServico os) {
        return toResponse(os, null);
    }

    static OrdemServicoResponse toResponse(OrdemServicoDetalhe detalhe) {
        return toResponse(detalhe.ordemServico(), detalhe.saga());
    }

    private static OrdemServicoResponse toResponse(OrdemServico os, SagaInstancia saga) {
        return new OrdemServicoResponse(os.getId(), os.getSituacao(), os.getDataRecebida(), os.getDataDiagnostico(),
                os.getDataAguardandoAprovacao(), os.getDataExecucao(), os.getDataFinalizada(),
                os.getDataAguardandoPagamento(), os.getDataEntregue(), os.getDataCancelada(),
                os.getMotivoCancelamento(), os.getPago(), os.getValorTotal(), os.getObservacao(), os.getVeiculoId(),
                os.getClienteId(), os.getPlaca(), os.getUsuarioAberturaId(),
                saga == null ? null : saga.getId(),
                saga == null ? null : saga.getStatus(),
                saga == null ? null : saga.getPassoAtual(),
                saga == null ? null : saga.getContexto().getOrcamentoId());
    }

    static HistoricoSituacaoResponse toResponse(HistoricoSituacao historico) {
        return new HistoricoSituacaoResponse(historico.de(), historico.para(), historico.motivo(),
                historico.ocorridaEm());
    }

    static SituacaoPublicaResponse toSituacaoPublica(OrdemServico os) {
        return new SituacaoPublicaResponse(os.getSituacao(), os.getDataRecebida(), os.getDataDiagnostico(),
                os.getDataAguardandoAprovacao(), os.getDataExecucao(), os.getDataFinalizada(),
                os.getDataAguardandoPagamento(), os.getDataEntregue(), os.getDataCancelada());
    }
}
