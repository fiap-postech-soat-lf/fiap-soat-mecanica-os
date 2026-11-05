package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PagamentoConfirmado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code PagamentoConfirmado}. */
@Component
public class PagamentoConfirmadoHandler extends SagaHandler<PagamentoConfirmado> {

    private final SagaOrquestrador orquestrador;

    public PagamentoConfirmadoHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("PagamentoConfirmado", PagamentoConfirmado.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, PagamentoConfirmado payload) {
        orquestrador.pagamentoConfirmado(messageId, payload);
    }
}
