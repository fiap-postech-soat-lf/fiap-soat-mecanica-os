package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.DiagnosticoAberto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code DiagnosticoAberto}. */
@Component
public class DiagnosticoAbertoHandler extends SagaHandler<DiagnosticoAberto> {

    private final SagaOrquestrador orquestrador;

    public DiagnosticoAbertoHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("DiagnosticoAberto", DiagnosticoAberto.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, DiagnosticoAberto payload) {
        orquestrador.diagnosticoAberto(messageId, payload);
    }
}
