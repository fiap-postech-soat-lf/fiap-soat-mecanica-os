package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.CobrancaCriada;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code CobrancaCriada}. */
@Component
public class CobrancaCriadaHandler extends SagaHandler<CobrancaCriada> {

    private final SagaOrquestrador orquestrador;

    public CobrancaCriadaHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("CobrancaCriada", CobrancaCriada.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, CobrancaCriada payload) {
        orquestrador.cobrancaCriada(messageId, payload);
    }
}
