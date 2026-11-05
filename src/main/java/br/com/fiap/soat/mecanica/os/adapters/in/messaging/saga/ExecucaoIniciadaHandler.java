package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ExecucaoIniciada;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code ExecucaoIniciada}. */
@Component
public class ExecucaoIniciadaHandler extends SagaHandler<ExecucaoIniciada> {

    private final SagaOrquestrador orquestrador;

    public ExecucaoIniciadaHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("ExecucaoIniciada", ExecucaoIniciada.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, ExecucaoIniciada payload) {
        orquestrador.execucaoIniciada(messageId, payload);
    }
}
