package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PecasReservadas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code PecasReservadas}. */
@Component
public class PecasReservadasHandler extends SagaHandler<PecasReservadas> {

    private final SagaOrquestrador orquestrador;

    public PecasReservadasHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("PecasReservadas", PecasReservadas.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, PecasReservadas payload) {
        orquestrador.pecasReservadas(messageId, payload);
    }
}
