package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoAprovado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code OrcamentoAprovado}. */
@Component
public class OrcamentoAprovadoHandler extends SagaHandler<OrcamentoAprovado> {

    private final SagaOrquestrador orquestrador;

    public OrcamentoAprovadoHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("OrcamentoAprovado", OrcamentoAprovado.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, OrcamentoAprovado payload) {
        orquestrador.orcamentoAprovado(messageId, payload);
    }
}
