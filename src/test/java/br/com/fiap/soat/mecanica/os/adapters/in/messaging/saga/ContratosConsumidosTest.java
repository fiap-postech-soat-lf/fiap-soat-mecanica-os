package br.com.fiap.soat.mecanica.os.adapters.in.messaging.saga;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Cada exemplo copiado do Execução e do Billing (src/test/resources/contratos-consumidos/) desserializa no record do
 * handler do seu tipo e chega ao orquestrador, com o messageId do envelope.
 */
class ContratosConsumidosTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final SagaOrquestrador orquestrador = mock(SagaOrquestrador.class);

    private List<MensagemHandler> handlers() {
        return List.of(
                new DiagnosticoAbertoHandler(MAPPER, orquestrador), new DiagnosticoConcluidoHandler(MAPPER, orquestrador),
                new PecasReservadasHandler(MAPPER, orquestrador), new ReservaPecasRecusadaHandler(MAPPER, orquestrador),
                new ReservaPecasLiberadaHandler(MAPPER, orquestrador), new ExecucaoIniciadaHandler(MAPPER, orquestrador),
                new ExecucaoFinalizadaHandler(MAPPER, orquestrador), new OrcamentoGeradoHandler(MAPPER, orquestrador),
                new OrcamentoFalhouHandler(MAPPER, orquestrador), new OrcamentoAprovadoHandler(MAPPER, orquestrador),
                new OrcamentoRecusadoHandler(MAPPER, orquestrador), new OrcamentoExpiradoHandler(MAPPER, orquestrador),
                new CobrancaCriadaHandler(MAPPER, orquestrador), new PagamentoRecusadoHandler(MAPPER, orquestrador),
                new PagamentoConfirmadoHandler(MAPPER, orquestrador));
    }

    static Stream<String> tipos() {
        return Stream.of("DiagnosticoAberto", "DiagnosticoConcluido", "PecasReservadas", "ReservaPecasRecusada",
                "ReservaPecasLiberada", "ExecucaoIniciada", "ExecucaoFinalizada", "OrcamentoGerado", "OrcamentoFalhou",
                "OrcamentoAprovado", "OrcamentoRecusado", "OrcamentoExpirado", "CobrancaCriada", "PagamentoRecusado",
                "PagamentoConfirmado");
    }

    private static MensagemEnvelope exemplo(String tipo) throws IOException {
        String json = new ClassPathResource("contratos-consumidos/%s.v1.json".formatted(tipo))
                .getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, MensagemEnvelope.class);
    }

    @Test
    @DisplayName("há um handler por tipo, 7 do Execução e 8 do Billing")
    void umHandlerPorTipo() {
        assertThat(handlers()).extracting(MensagemHandler::tipo).containsExactlyInAnyOrderElementsOf(tipos().toList());
        assertThat(handlers()).hasSize(15);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipos")
    @DisplayName("o exemplo desserializa e chega ao orquestrador")
    void desserializa(String tipo) throws IOException {
        MensagemEnvelope envelope = exemplo(tipo);
        MensagemHandler handler = handlers().stream().filter(h -> h.tipo().equals(tipo)).findFirst().orElseThrow();
        assertThat(envelope.type()).isEqualTo(tipo);

        handler.processar(envelope);

        var verificacao = verify(orquestrador);
        switch (tipo) {
            case "DiagnosticoAberto" -> verificacao.diagnosticoAberto(eq(envelope.messageId()), any());
            case "DiagnosticoConcluido" -> verificacao.diagnosticoConcluido(eq(envelope.messageId()), any());
            case "PecasReservadas" -> verificacao.pecasReservadas(eq(envelope.messageId()), any());
            case "ReservaPecasRecusada" -> verificacao.reservaPecasRecusada(eq(envelope.messageId()), any());
            case "ReservaPecasLiberada" -> verificacao.reservaPecasLiberada(eq(envelope.messageId()), any());
            case "ExecucaoIniciada" -> verificacao.execucaoIniciada(eq(envelope.messageId()), any());
            case "ExecucaoFinalizada" -> verificacao.execucaoFinalizada(eq(envelope.messageId()), any());
            case "OrcamentoGerado" -> verificacao.orcamentoGerado(eq(envelope.messageId()), any());
            case "OrcamentoFalhou" -> verificacao.orcamentoFalhou(eq(envelope.messageId()), any());
            case "OrcamentoAprovado" -> verificacao.orcamentoAprovado(eq(envelope.messageId()), any());
            case "OrcamentoRecusado" -> verificacao.orcamentoRecusado(eq(envelope.messageId()), any());
            case "OrcamentoExpirado" -> verificacao.orcamentoExpirado(eq(envelope.messageId()), any());
            case "CobrancaCriada" -> verificacao.cobrancaCriada(eq(envelope.messageId()), any());
            case "PagamentoRecusado" -> verificacao.pagamentoRecusado(eq(envelope.messageId()), any());
            case "PagamentoConfirmado" -> verificacao.pagamentoConfirmado(eq(envelope.messageId()), any());
            default -> throw new AssertionError(tipo);
        }
        verifyNoMoreInteractions(orquestrador);
    }

    @Test
    @DisplayName("os campos do exemplo chegam preenchidos (DiagnosticoConcluido e OrcamentoGerado)")
    void camposChegam() throws IOException {
        var capturaDiagnostico = org.mockito.ArgumentCaptor
                .forClass(br.com.fiap.soat.mecanica.os.application.saga.dto.DiagnosticoConcluido.class);
        new DiagnosticoConcluidoHandler(MAPPER, orquestrador).processar(exemplo("DiagnosticoConcluido"));
        verify(orquestrador).diagnosticoConcluido(any(), capturaDiagnostico.capture());
        var itens = capturaDiagnostico.getValue().itens();
        assertThat(itens).hasSize(2);
        assertThat(itens.get(0).pecas()).hasSize(2);
        assertThat(itens.get(0).pecas().get(0).quantidade()).isEqualTo(4);
        assertThat(itens.get(1).pecas()).isEmpty();

        var capturaOrcamento = org.mockito.ArgumentCaptor
                .forClass(br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoGerado.class);
        new OrcamentoGeradoHandler(MAPPER, orquestrador).processar(exemplo("OrcamentoGerado"));
        verify(orquestrador).orcamentoGerado(any(), capturaOrcamento.capture());
        assertThat(capturaOrcamento.getValue().valorTotal()).isEqualByComparingTo("407.1");
        assertThat(capturaOrcamento.getValue().expiraEm()).isNotNull();
        assertThat(capturaOrcamento.getValue().orcamentoId()).isInstanceOf(UUID.class);
    }

    @Test
    @DisplayName("payload ilegível lança exceção (a mensagem volta para a fila)")
    void payloadInvalido() throws IOException {
        MensagemEnvelope envelope = MAPPER.readValue("""
                {"messageId":"%s","type":"OrcamentoGerado","version":1,"occurredAt":"2026-11-06T12:00:00Z",
                 "payload":{"osId":"nao-e-uuid"}}""".formatted(UUID.randomUUID()), MensagemEnvelope.class);

        assertThatThrownBy(() -> new OrcamentoGeradoHandler(MAPPER, orquestrador).processar(envelope))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OrcamentoGerado");
    }
}
