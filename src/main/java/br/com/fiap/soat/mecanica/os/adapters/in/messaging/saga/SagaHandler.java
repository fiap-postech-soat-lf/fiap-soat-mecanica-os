package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Base dos handlers de resposta da Saga: desserializa o payload no record do tipo e entrega ao orquestrador.
 * Payload ilegível lança exceção (a mensagem volta para a fila e, esgotadas as tentativas, vai para a DLQ).
 */
abstract class SagaHandler<P> implements MensagemHandler {

    private final String tipo;
    private final Class<P> classe;
    private final ObjectMapper objectMapper;

    protected SagaHandler(String tipo, Class<P> classe, ObjectMapper objectMapper) {
        this.tipo = tipo;
        this.classe = classe;
        this.objectMapper = objectMapper;
    }

    @Override
    public String tipo() {
        return tipo;
    }

    @Override
    public void processar(MensagemEnvelope envelope) {
        P payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), classe);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload inválido em " + tipo + ": " + e.getOriginalMessage(), e);
        }
        tratar(envelope.messageId(), payload);
    }

    protected abstract void tratar(UUID messageId, P payload);
}
