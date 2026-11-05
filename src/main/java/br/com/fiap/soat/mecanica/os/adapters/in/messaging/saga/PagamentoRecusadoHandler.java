package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PagamentoRecusado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Trata {@code PagamentoRecusado}. */
@Component
public class PagamentoRecusadoHandler extends SagaHandler<PagamentoRecusado> {

    private final SagaOrquestrador orquestrador;

    public PagamentoRecusadoHandler(ObjectMapper objectMapper, SagaOrquestrador orquestrador) {
        super("PagamentoRecusado", PagamentoRecusado.class, objectMapper);
        this.orquestrador = orquestrador;
    }

    @Override
    protected void tratar(UUID messageId, PagamentoRecusado payload) {
        orquestrador.pagamentoRecusado(messageId, payload);
    }
}
