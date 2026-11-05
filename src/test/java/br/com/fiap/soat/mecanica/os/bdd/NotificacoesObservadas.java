package br.com.fiap.soat.mecanica.os.bdd;

import br.com.fiap.soat.mecanica.os.support.ValidadorContratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static br.com.fiap.soat.mecanica.os.support.IntegracaoTest.MENSAGERIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Lê a fila {@code mecanica-identity-notificacoes} do teste (assinante raw do {@code os-eventos}) e confere os
 * eventos {@code SituacaoOrdemServicoAlterada} e {@code OrdemServicoCancelada} de uma OS.
 */
class NotificacoesObservadas {

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final List<JsonNode> lidas = new ArrayList<>();

    NotificacoesObservadas(SqsClient sqs, ObjectMapper mapper) {
        this.sqs = sqs;
        this.mapper = mapper;
    }

    void limpar() {
        lidas.clear();
        while (!ler().isEmpty()) {
            // esvazia o que sobrou de outro teste
        }
        lidas.clear();
    }

    /** Espera (até 10 s) por mensagens da OS que satisfaçam a condição e devolve todas as da OS lidas até agora. */
    List<JsonNode> aguardar(UUID osId, Predicate<List<JsonNode>> condicao) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).until(() -> {
            lidas.addAll(ler());
            return condicao.test(daOs(osId));
        });
        return daOs(osId);
    }

    /** As situações ("para") notificadas, na ordem em que ocorreram. */
    static List<String> situacoes(List<JsonNode> mensagens) {
        return mensagens.stream()
                .filter(m -> "SituacaoOrdemServicoAlterada".equals(m.path("type").asText()))
                .sorted(Comparator.comparing(m -> Instant.parse(m.path("payload").path("ocorridaEm").asText())))
                .map(m -> m.path("payload").path("para").asText())
                .toList();
    }

    private List<JsonNode> daOs(UUID osId) {
        return lidas.stream().filter(m -> osId.toString().equals(m.path("osId").asText())).toList();
    }

    private List<JsonNode> ler() {
        var mensagens = sqs.receiveMessage(r -> r.queueUrl(MENSAGERIA.notificacoes())
                .maxNumberOfMessages(10).waitTimeSeconds(1)).messages();
        List<JsonNode> envelopes = new ArrayList<>();
        for (var mensagem : mensagens) {
            sqs.deleteMessage(r -> r.queueUrl(MENSAGERIA.notificacoes()).receiptHandle(mensagem.receiptHandle()));
            try {
                JsonNode envelope = mapper.readTree(mensagem.body());
                assertThat(ValidadorContratos.validar(envelope.toString()))
                        .as("%s contra o schema do OS", envelope.path("type").asText()).isEmpty();
                envelopes.add(envelope);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return envelopes;
    }
}
