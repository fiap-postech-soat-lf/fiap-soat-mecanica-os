package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoFalhou;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code OrcamentoFalhou}. */
@Component
public class OrcamentoFalhouHandler extends SagaHandler<OrcamentoFalhou> {

    private final SagaOrquestrador orquestrador;

    public OrcamentoFalhouHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("OrcamentoFalhou", OrcamentoFalhou.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, OrcamentoFalhou payload) {
        orquestrador.orcamentoFalhou(messageId, payload);
    }
}
