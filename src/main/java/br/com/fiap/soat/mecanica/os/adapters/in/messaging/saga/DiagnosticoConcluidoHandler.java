package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.DiagnosticoConcluido;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code DiagnosticoConcluido}. */
@Component
public class DiagnosticoConcluidoHandler extends SagaHandler<DiagnosticoConcluido> {

    private final SagaOrquestrador orquestrador;

    public DiagnosticoConcluidoHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("DiagnosticoConcluido", DiagnosticoConcluido.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, DiagnosticoConcluido payload) {
        orquestrador.diagnosticoConcluido(messageId, payload);
    }
}
