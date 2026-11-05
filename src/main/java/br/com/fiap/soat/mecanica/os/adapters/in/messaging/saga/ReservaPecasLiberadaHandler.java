package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ReservaPecasLiberada;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code ReservaPecasLiberada}. */
@Component
public class ReservaPecasLiberadaHandler extends SagaHandler<ReservaPecasLiberada> {

    private final SagaOrquestrador orquestrador;

    public ReservaPecasLiberadaHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("ReservaPecasLiberada", ReservaPecasLiberada.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, ReservaPecasLiberada payload) {
        orquestrador.reservaPecasLiberada(messageId, payload);
    }
}
