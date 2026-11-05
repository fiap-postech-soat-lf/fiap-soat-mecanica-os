package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoRecusado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code OrcamentoRecusado}. */
@Component
public class OrcamentoRecusadoHandler extends SagaHandler<OrcamentoRecusado> {

    private final SagaOrquestrador orquestrador;

    public OrcamentoRecusadoHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("OrcamentoRecusado", OrcamentoRecusado.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, OrcamentoRecusado payload) {
        orquestrador.orcamentoRecusado(messageId, payload);
    }
}
