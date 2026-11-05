package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.os.application.mensageria.ProcessadorMensagem;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.os.support.JwtTestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.ATENDENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.CLIENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.PLACA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.VEICULO_ID;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A Saga de ponta a ponta no banco real (F4 T06), sem Cucumber: as respostas dos participantes entram pelo
 * {@link ProcessadorMensagem}, que é o que o consumidor SQS chama, e os comandos saem pela outbox. Cobre o que o BDD
 * não cobre: histórico e trilha completos, idempotência, descarte fora do passo, conflito de versão, timeout e cancelamento.
 */
class SagaOrquestradorIT extends IntegracaoTest {

    private static final String TOKEN = "Bearer " + JwtTestTokens.funcionario(ATENDENTE_ID, "ATENDENTE");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProcessadorMensagem processador;
    @Autowired
    private SagaRepository sagaRepository;
    @Autowired
    private OrdemServicoRepository ordemServicoRepository;
    @Autowired
    private TratarTimeoutsSagaUseCase tratarTimeouts;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID osId;

    @BeforeEach
    void preparar() {
        IDENTITY.resetAll();
        IDENTITY.stubFor(get(urlEqualTo("/veiculos/" + VEICULO_ID)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id":"%s","status":"ATIVO","placa":"%s","marca":"VW","modelo":"Gol","ano":"2020",
                         "quantidadeEixos":2,"clienteId":"%s"}""".formatted(VEICULO_ID, PLACA, CLIENTE_ID))));
        // sagas deixadas por outros ITs nao podem vencer durante os testes de timeout
        jdbc.update("UPDATE saga_instancia SET prazo_em = NULL");
        receber(MENSAGERIA.execucaoComandos());
        receber(MENSAGERIA.billingComandos());
        receber(MENSAGERIA.notificacoes());
        osId = null;
    }

    /** Espera a outbox publicar o que sobrou (para nao vazar para outro teste) e remove a OS. */
    @AfterEach
    void limpar() {
        if (osId == null) {
            return;
        }
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE publicado_em IS NULL AND envelope ->> 'osId' = ?",
                Integer.class, osId.toString()) == 0);
        jdbc.update("DELETE FROM saga_passo WHERE saga_id IN (SELECT id FROM saga_instancia WHERE os_id = ?)", osId);
        jdbc.update("DELETE FROM saga_instancia WHERE os_id = ?", osId);
        jdbc.update("DELETE FROM historico_situacao_os WHERE os_id = ?", osId);
        jdbc.update("DELETE FROM ordem_servicos WHERE id = ?", osId);
    }

    // ---------------------------------------------------------------- testes

    @Test
    void recebidaAteEntregueComHistoricoETrilhaCompletos() throws Exception {
        abrir();
        percorrerAteAguardarAprovacao();
        enviar("OrcamentoAprovado", p -> p.put("orcamentoId", ctx().get("orcamentoId").asText()));
        enviar("ExecucaoIniciada", p -> { });
        enviar("ExecucaoFinalizada", p -> p.putArray("tempos"));
        enviar("CobrancaCriada", p -> p.put("pagamentoId", UUID.randomUUID().toString()));
        enviar("PagamentoConfirmado", p -> p.put("pagamentoId", ctx().get("pagamentoId").asText()));

        assertThat(ordemServicoRepository.buscarHistorico(osId)).extracting(HistoricoSituacao::para)
                .containsExactly(SituacaoOrdemServico.RECEBIDA, SituacaoOrdemServico.EM_DIAGNOSTICO,
                        SituacaoOrdemServico.AGUARDANDO_APROVACAO, SituacaoOrdemServico.EM_EXECUCAO,
                        SituacaoOrdemServico.FINALIZADA, SituacaoOrdemServico.AGUARDANDO_PAGAMENTO,
                        SituacaoOrdemServico.ENTREGUE);
        SagaInstancia saga = saga();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.CONCLUIDA);
        assertThat(saga.getPassoAtual()).isEqualTo(PassoSaga.FIM);
        assertThat(saga.getPrazoEm()).isNull();
        assertThat(trilha("ENVIADO")).containsExactly("AbrirDiagnostico", "ReservarPecas", "GerarOrcamento",
                "IniciarExecucao", "SolicitarPagamento");
        assertThat(trilha("RECEBIDO")).containsExactly("DiagnosticoAberto", "DiagnosticoConcluido", "PecasReservadas",
                "OrcamentoGerado", "OrcamentoAprovado", "ExecucaoIniciada", "ExecucaoFinalizada", "CobrancaCriada",
                "PagamentoConfirmado");
        assertThat(trilha("DESCARTADA")).isEmpty();
    }

    @Test
    void amesmaRespostaEnviadaDuasVezesTemUmUnicoEfeito() throws Exception {
        abrir();
        MensagemEnvelope resposta = resposta("DiagnosticoAberto", p -> { });

        processador.processar(resposta);
        processador.processar(resposta);

        assertThat(ordemServicoRepository.buscarHistorico(osId)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_passo WHERE message_id = ?", Integer.class,
                resposta.messageId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_processada WHERE message_id = ?",
                Integer.class, resposta.messageId())).isEqualTo(1);
        assertThat(saga().getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
    }

    @Test
    void respostaForaDoPassoEDescartadaSemMudarAOs() throws Exception {
        abrir();

        MensagemEnvelope fora = resposta("ExecucaoFinalizada", p -> p.putArray("tempos"));
        processador.processar(fora);

        assertThat(jdbc.queryForObject("SELECT direcao FROM saga_passo WHERE message_id = ?", String.class,
                fora.messageId())).isEqualTo("DESCARTADA");
        assertThat(ordemServicoRepository.buscarHistorico(osId)).hasSize(1);
        assertThat(ordemServicoRepository.buscarPorId(osId).orElseThrow().getSituacao())
                .isEqualTo(SituacaoOrdemServico.RECEBIDA);
        assertThat(saga().getPassoAtual()).isEqualTo(PassoSaga.ABRIR_DIAGNOSTICO);
        assertThat(saga().getStatus()).isEqualTo(SagaStatus.INICIADA);
    }

    /**
     * Duas réplicas processam respostas da mesma Saga ao mesmo tempo: as duas leem a Saga antes de qualquer commit.
     * A transação que commita por último perde pelo {@code @Version}, nada dela fica gravado (nem o registro de
     * mensagem processada) e, na reentrega, a resposta é descartada. O estado final é o do processamento em série.
     */
    @Test
    void respostasConcorrentesDaMesmaSagaLevantamConflitoDeVersaoEConvergem() throws Exception {
        abrir();
        MensagemEnvelope primeira = resposta("DiagnosticoAberto", p -> { });
        MensagemEnvelope segunda = resposta("DiagnosticoAberto", p -> { });
        TransactionTemplate replicaB = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> replicaB.executeWithoutResult(status -> {
            // a réplica B lê a Saga e a OS antes de a A commitar
            sagaRepository.buscarPorOsId(osId).orElseThrow();
            ordemServicoRepository.buscarPorId(osId).orElseThrow();
            CompletableFuture.runAsync(() -> processador.processar(primeira)).join();
            processador.processar(segunda);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // a transação da B foi desfeita por inteiro
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_passo WHERE message_id = ?", Integer.class,
                segunda.messageId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_processada WHERE message_id = ?",
                Integer.class, segunda.messageId())).isZero();

        // reentrega: a mensagem volta para a fila e, agora, a Saga já avançou
        processador.processar(segunda);

        assertThat(jdbc.queryForObject("SELECT direcao FROM saga_passo WHERE message_id = ?", String.class,
                primeira.messageId())).isEqualTo("RECEBIDO");
        assertThat(jdbc.queryForObject("SELECT direcao FROM saga_passo WHERE message_id = ?", String.class,
                segunda.messageId())).isEqualTo("DESCARTADA");
        assertThat(ordemServicoRepository.buscarHistorico(osId)).extracting(HistoricoSituacao::para)
                .containsExactly(SituacaoOrdemServico.RECEBIDA, SituacaoOrdemServico.EM_DIAGNOSTICO);
        assertThat(saga().getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
    }

    @Test
    void timeoutDoAbrirDiagnosticoReenviaComMessageIdsDiferentesEDepoisCancelaAOs() throws Exception {
        abrir();

        // o prazo do comando é de 3 s (application-test.properties); cada vencimento é tratado pelo mesmo caso de uso do scheduler
        for (int rodada = 1; rodada <= 3; rodada++) {
            await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
                    .until(() -> tratarTimeouts.executar() > 0);
        }
        assertThat(timeouts()).isEqualTo(3);

        List<Map<String, Object>> envios = jdbc.queryForList("""
                SELECT message_id, envelope ->> 'causationId' AS causation FROM outbox
                WHERE tipo = 'AbrirDiagnostico' AND envelope ->> 'osId' = ? ORDER BY criado_em""", osId.toString());
        assertThat(envios).hasSize(3); // o original e dois reenvios: o total de tentativas inclui o envio original
        assertThat(envios).extracting(e -> e.get("message_id")).doesNotHaveDuplicates();
        assertThat(envios.get(1).get("causation")).isEqualTo(envios.get(0).get("message_id").toString());
        assertThat(envios.get(2).get("causation")).isEqualTo(envios.get(1).get("message_id").toString());

        SagaInstancia saga = saga();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.FALHOU);
        assertThat(saga.getPrazoEm()).isNull();
        var os = ordemServicoRepository.buscarPorId(osId).orElseThrow();
        assertThat(os.getSituacao()).isEqualTo(SituacaoOrdemServico.CANCELADA);
        assertThat(os.getMotivoCancelamento()).contains("Execução não respondeu");
    }

    @Test
    void cancelarEmAguardarAprovacaoLiberaAReservaECancelaOOrcamento() throws Exception {
        abrir();
        percorrerAteAguardarAprovacao();
        UUID orcamentoId = UUID.fromString(ctx().get("orcamentoId").asText());

        MvcResult resultado = mockMvc.perform(patch("/ordem-servicos/{id}/cancelar", osId)
                .header("Authorization", TOKEN)).andReturn();

        assertThat(resultado.getResponse().getStatus()).as(resultado.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode liberar = comandoDaOutbox("LiberarReservaPecas");
        assertThat(liberar.path("payload").path("motivo").asText()).isEqualTo("DESISTENCIA");
        JsonNode cancelar = comandoDaOutbox("CancelarOrcamento");
        assertThat(cancelar.path("payload").path("orcamentoId").asText()).isEqualTo(orcamentoId.toString());
        assertThat(ordemServicoRepository.buscarPorId(osId).orElseThrow().getSituacao())
                .isEqualTo(SituacaoOrdemServico.CANCELADA);
        // a Saga espera a ReservaPecasLiberada para fechar a compensação
        assertThat(saga().getStatus()).isEqualTo(SagaStatus.COMPENSANDO);

        enviar("ReservaPecasLiberada", p -> { });
        assertThat(saga().getStatus()).isEqualTo(SagaStatus.COMPENSADA);
    }

    // ---------------------------------------------------------------- apoio

    private void abrir() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/ordem-servicos").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veiculoId\":\"%s\",\"observacao\":\"Barulho no motor\"}".formatted(VEICULO_ID)))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(resultado.getResponse().getContentAsString())
                .isEqualTo(201);
        osId = UUID.fromString(mapper.readTree(resultado.getResponse().getContentAsString()).path("id").asText());
    }

    /** DiagnosticoAberto, DiagnosticoConcluido (1 serviço, 1 peça), PecasReservadas e OrcamentoGerado. */
    private void percorrerAteAguardarAprovacao() {
        enviar("DiagnosticoAberto", p -> { });
        enviar("DiagnosticoConcluido", p -> {
            ArrayNode itens = p.putArray("itens");
            ObjectNode item = itens.addObject();
            item.put("servicoId", UUID.randomUUID().toString());
            item.put("descricao", "Troca de oleo");
            item.put("precoMaoDeObra", new BigDecimal("80.00"));
            ObjectNode peca = item.putArray("pecas").addObject();
            peca.put("pecaId", UUID.randomUUID().toString());
            peca.put("descricao", "Filtro");
            peca.put("quantidade", 1);
            peca.put("valorUnitario", new BigDecimal("35.50"));
        });
        enviar("PecasReservadas", p -> p.set("pecas", ultimoComando().path("payload").path("pecas")));
        enviar("OrcamentoGerado", p -> {
            p.put("orcamentoId", UUID.randomUUID().toString());
            p.put("valorTotal", ultimoComando().path("payload").path("valorTotal").decimalValue());
            p.put("expiraEm", Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString());
        });
    }

    /** Monta a resposta (a partir do exemplo copiado) e a entrega ao processador, como o consumidor faria. */
    private void enviar(String tipo, Consumer<ObjectNode> payload) {
        processador.processar(resposta(tipo, payload));
    }

    private MensagemEnvelope resposta(String tipo, Consumer<ObjectNode> ajustarPayload) {
        try {
            String json = new ClassPathResource("contratos-consumidos/%s.v1.json".formatted(tipo))
                    .getContentAsString(StandardCharsets.UTF_8);
            ObjectNode envelope = (ObjectNode) mapper.readTree(json);
            SagaInstancia saga = saga();
            envelope.put("messageId", UUID.randomUUID().toString());
            envelope.put("sagaId", saga.getId().toString());
            envelope.put("osId", osId.toString());
            envelope.remove("causationId");
            envelope.remove("traceparent");
            ObjectNode payload = (ObjectNode) envelope.path("payload");
            payload.put("osId", osId.toString());
            ajustarPayload.accept(payload);
            return mapper.treeToValue(envelope, MensagemEnvelope.class);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private SagaInstancia saga() {
        return sagaRepository.buscarPorOsId(osId).orElseThrow();
    }

    /** O último comando enviado, no formato {"payload": ...}. */
    private JsonNode ultimoComando() {
        return mapper.createObjectNode().set("payload", saga().getContexto().getUltimoComando().payload());
    }

    /** Campos do contexto da Saga que o teste precisa reaproveitar (orcamentoId, pagamentoId). */
    private Map<String, JsonNode> ctx() {
        JsonNode contexto = mapper.valueToTree(saga().getContexto());
        return Map.of("orcamentoId", contexto.path("orcamentoId"), "pagamentoId", contexto.path("pagamentoId"));
    }

    private List<String> trilha(String direcao) {
        return jdbc.queryForList("""
                SELECT p.tipo_mensagem FROM saga_passo p JOIN saga_instancia s ON s.id = p.saga_id
                WHERE s.os_id = ? AND p.direcao = ? ORDER BY p.ocorrido_em, p.id""", String.class, osId, direcao);
    }

    private int timeouts() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM saga_passo p JOIN saga_instancia s ON s.id = p.saga_id
                WHERE s.os_id = ? AND p.direcao = 'TIMEOUT'""", Integer.class, osId);
    }

    private JsonNode comandoDaOutbox(String tipo) throws Exception {
        String envelope = jdbc.queryForObject("SELECT envelope::text FROM outbox WHERE tipo = ? AND envelope ->> 'osId' = ?",
                String.class, tipo, osId.toString());
        return mapper.readTree(envelope);
    }
}
