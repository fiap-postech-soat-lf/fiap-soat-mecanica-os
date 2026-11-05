package br.com.fiap.soat.mecanica.os.adapters.out.persistence.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import org.springframework.stereotype.Component;

@Component
public class OrdemServicoMapper {

    public OrdemServicoEntity toEntity(OrdemServico os) {
        OrdemServicoEntity entity = new OrdemServicoEntity();
        entity.setId(os.getId());
        entity.setSituacao(os.getSituacao());
        entity.setDataRecebida(os.getDataRecebida());
        entity.setDataDiagnostico(os.getDataDiagnostico());
        entity.setDataAguardandoAprovacao(os.getDataAguardandoAprovacao());
        entity.setDataExecucao(os.getDataExecucao());
        entity.setDataFinalizada(os.getDataFinalizada());
        entity.setDataAguardandoPagamento(os.getDataAguardandoPagamento());
        entity.setDataEntregue(os.getDataEntregue());
        entity.setDataCancelada(os.getDataCancelada());
        entity.setMotivoCancelamento(os.getMotivoCancelamento());
        entity.setPago(os.getPago());
        entity.setValorTotal(os.getValorTotal());
        entity.setObservacao(os.getObservacao());
        entity.setVeiculoId(os.getVeiculoId());
        entity.setClienteId(os.getClienteId());
        entity.setPlaca(os.getPlaca());
        entity.setUsuarioAberturaId(os.getUsuarioAberturaId());
        return entity;
    }

    public OrdemServico toDomain(OrdemServicoEntity entity) {
        return OrdemServico.reconstruir(entity.getId(), entity.getSituacao(), entity.getDataRecebida(),
                entity.getDataDiagnostico(), entity.getDataAguardandoAprovacao(), entity.getDataExecucao(),
                entity.getDataFinalizada(), entity.getDataAguardandoPagamento(), entity.getDataEntregue(),
                entity.getDataCancelada(), entity.getMotivoCancelamento(), entity.getPago(),
                entity.getValorTotal(), entity.getObservacao(), entity.getVeiculoId(), entity.getClienteId(),
                entity.getPlaca(), entity.getUsuarioAberturaId());
    }

    public HistoricoSituacao toDomain(HistoricoSituacaoEntity entity) {
        return new HistoricoSituacao(entity.getId(), entity.getOsId(), entity.getDe(), entity.getPara(),
                entity.getMotivo(), entity.getOcorridaEm());
    }
}
