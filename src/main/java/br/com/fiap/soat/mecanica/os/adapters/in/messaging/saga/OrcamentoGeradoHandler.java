package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoGerado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code OrcamentoGerado}. */
@Component
public class OrcamentoGeradoHandler extends SagaHandler<OrcamentoGerado> {

    private final SagaOrquestrador orquestrador;

    public OrcamentoGeradoHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("OrcamentoGerado", OrcamentoGerado.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, OrcamentoGerado payload) {
        orquestrador.orcamentoGerado(messageId, payload);
    }
}
