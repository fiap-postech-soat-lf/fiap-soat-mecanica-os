package br.com.fiap.soat.mecanica.os.adapters.out.persistence.saga;

import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaPasso;
import org.springframework.stereotype.Component;

@Component
public class SagaMapper {

    public SagaInstanciaEntity toEntity(SagaInstancia saga) {
        SagaInstanciaEntity entity = new SagaInstanciaEntity();
        entity.setId(saga.getId());
        entity.setOsId(saga.getOsId());
        entity.setPassoAtual(saga.getPassoAtual());
        entity.setStatus(saga.getStatus());
        entity.setContexto(saga.getContexto());
        entity.setTentativas(saga.getTentativas());
        entity.setPrazoEm(saga.getPrazoEm());
        entity.setVersao(saga.getVersao());
        entity.setCriadaEm(saga.getCriadaEm());
        entity.setAtualizadaEm(saga.getAtualizadaEm());
        return entity;
    }

    public SagaInstancia toDomain(SagaInstanciaEntity entity) {
        return SagaInstancia.reconstruir(entity.getId(), entity.getOsId(), entity.getPassoAtual(),
                entity.getStatus(), entity.getContexto(), entity.getTentativas(), entity.getPrazoEm(),
                entity.getVersao(), entity.getCriadaEm(), entity.getAtualizadaEm());
    }

    public SagaPassoEntity toEntity(SagaPasso passo) {
        SagaPassoEntity entity = new SagaPassoEntity();
        entity.setId(passo.id());
        entity.setSagaId(passo.sagaId());
        entity.setPasso(passo.passo());
        entity.setTipoMensagem(passo.tipoMensagem());
        entity.setDirecao(passo.direcao());
        entity.setMessageId(passo.messageId());
        entity.setDetalhe(passo.detalhe());
        entity.setOcorridoEm(passo.ocorridoEm());
        return entity;
    }

    public SagaPasso toDomain(SagaPassoEntity entity) {
        return new SagaPasso(entity.getId(), entity.getSagaId(), entity.getPasso(), entity.getTipoMensagem(),
                entity.getDirecao(), entity.getMessageId(), entity.getDetalhe(), entity.getOcorridoEm());
    }
}
