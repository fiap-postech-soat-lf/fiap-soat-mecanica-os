package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoExpirado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code OrcamentoExpirado}. */
@Component
public class OrcamentoExpiradoHandler extends SagaHandler<OrcamentoExpirado> {

    private final SagaOrquestrador orquestrador;

    public OrcamentoExpiradoHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("OrcamentoExpirado", OrcamentoExpirado.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, OrcamentoExpirado payload) {
        orquestrador.orcamentoExpirado(messageId, payload);
    }
}
