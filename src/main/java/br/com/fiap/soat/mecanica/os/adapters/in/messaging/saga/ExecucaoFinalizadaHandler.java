package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ExecucaoFinalizada;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code ExecucaoFinalizada}. */
@Component
public class ExecucaoFinalizadaHandler extends SagaHandler<ExecucaoFinalizada> {

    private final SagaOrquestrador orquestrador;

    public ExecucaoFinalizadaHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("ExecucaoFinalizada", ExecucaoFinalizada.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, ExecucaoFinalizada payload) {
        orquestrador.execucaoFinalizada(messageId, payload);
    }
}
