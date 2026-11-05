package br.com.fiap.soat.mecanica.os.bdd;

import br.com.fiap.soat.mecanica.os.support.ValidadorContratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static br.com.fiap.soat.mecanica.os.support.IntegracaoTest.MENSAGERIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Faz o papel do Execução e do Billing (F4-m): lê o próximo comando da fila do participante, confere o tipo e o
 * contrato e publica a resposta no tópico dele. A resposta parte do exemplo copiado para
 * {@code contratos-consumidos/}, trocando os ids.
 */
class ParticipantesSimulados {

    enum Participante {
        EXECUCAO, BILLING;

        String filaDeComandos() {
            return this == EXECUCAO ? MENSAGERIA.execucaoComandos() : MENSAGERIA.billingComandos();
        }

        String topicoDeEventos() {
            return this == EXECUCAO ? MENSAGERIA.execucaoEventos() : MENSAGERIA.billingEventos();
        }
    }

    private static final Duration ESPERA = Duration.ofSeconds(10);

    private final SqsClient sqs;
    private final SnsClient sns;
    private final ObjectMapper mapper;

    ParticipantesSimulados(SqsClient sqs, SnsClient sns, ObjectMapper mapper) {
        this.sqs = sqs;
        this.sns = sns;
        this.mapper = mapper;
    }

    /** Esvazia as filas de comando, para o cenário não ver o que sobrou de outro teste. */
    void limpar() {
        for (Participante participante : Participante.values()) {
            JsonNode mensagem;
            do {
                mensagem = proximo(participante.filaDeComandos(), Duration.ofMillis(200));
            } while (mensagem != null);
        }
    }

    /** O próximo comando do participante (até 10 s), com o tipo e o contrato conferidos. */
    JsonNode receberComando(Participante participante, String tipoEsperado) {
        JsonNode[] recebido = new JsonNode[1];
        await().atMost(ESPERA).pollInterval(Duration.ofMillis(100)).until(() -> {
            recebido[0] = proximo(participante.filaDeComandos(), Duration.ofSeconds(1));
            return recebido[0] != null;
        });
        JsonNode comando = recebido[0];
        assertThat(comando.path("type").asText()).as("comando recebido pelo %s", participante).isEqualTo(tipoEsperado);
        assertThat(ValidadorContratos.validar(comando.toString()))
                .as("%s contra o schema do OS", tipoEsperado).isEmpty();
        return comando;
    }

    /** Publica a resposta no tópico do participante; devolve o messageId dela. */
    UUID responder(Participante participante, JsonNode comando, String tipo, Consumer<ObjectNode> ajustarPayload) {
        ObjectNode envelope = exemplo(tipo);
        UUID messageId = UUID.randomUUID();
        envelope.put("messageId", messageId.toString());
        envelope.put("sagaId", comando.path("sagaId").asText());
        envelope.put("osId", comando.path("osId").asText());
        envelope.put("causationId", comando.path("messageId").asText());
        envelope.put("occurredAt", Instant.now().toString());
        envelope.remove("traceparent");
        if (comando.hasNonNull("correlationId")) {
            envelope.put("correlationId", comando.path("correlationId").asText());
        }
        ObjectNode payload = (ObjectNode) envelope.path("payload");
        payload.put("osId", comando.path("osId").asText());
        ajustarPayload.accept(payload);
        sns.publish(r -> r.topicArn(participante.topicoDeEventos()).message(envelope.toString()));
        return messageId;
    }

    private ObjectNode exemplo(String tipo) {
        try {
            String json = new ClassPathResource("contratos-consumidos/%s.v1.json".formatted(tipo))
                    .getContentAsString(StandardCharsets.UTF_8);
            return (ObjectNode) mapper.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonNode proximo(String fila, Duration espera) {
        var mensagens = sqs.receiveMessage(r -> r.queueUrl(fila).maxNumberOfMessages(1)
                .waitTimeSeconds((int) Math.max(0, espera.toSeconds()))).messages();
        if (mensagens.isEmpty()) {
            return null;
        }
        Message mensagem = mensagens.get(0);
        sqs.deleteMessage(r -> r.queueUrl(fila).receiptHandle(mensagem.receiptHandle()));
        try {
            return mapper.readTree(mensagem.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
