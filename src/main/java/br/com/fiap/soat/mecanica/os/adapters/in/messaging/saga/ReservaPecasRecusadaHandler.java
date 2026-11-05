package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ReservaPecasRecusada;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code ReservaPecasRecusada}. */
@Component
public class ReservaPecasRecusadaHandler extends SagaHandler<ReservaPecasRecusada> {

    private final SagaOrquestrador orquestrador;

    public ReservaPecasRecusadaHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("ReservaPecasRecusada", ReservaPecasRecusada.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, ReservaPecasRecusada payload) {
        orquestrador.reservaPecasRecusada(messageId, payload);
    }
}
