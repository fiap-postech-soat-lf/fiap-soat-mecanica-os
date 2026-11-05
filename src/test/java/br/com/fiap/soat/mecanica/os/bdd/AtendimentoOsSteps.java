package br.com.fiap.soat.mecanica.os.bdd;

import br.com.fiap.soat.mecanica.os.bdd.ParticipantesSimulados.Participante;
import br.com.fiap.soat.mecanica.os.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.os.support.ValidadorContratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.bdd.ParticipantesSimulados.Participante.BILLING;
import static br.com.fiap.soat.mecanica.os.bdd.ParticipantesSimulados.Participante.EXECUCAO;
import static br.com.fiap.soat.mecanica.os.support.IntegracaoTest.IDENTITY;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.ATENDENTE_ID;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Passos do {@code atendimento_os.feature}. Cada passo espera o efeito observável (mensagem na fila, linha em
 * {@code saga_passo}, situação na API), nunca um {@code sleep}. Uma instância por cenário.
 */
public class AtendimentoOsSteps {

    private static final BigDecimal MAO_DE_OBRA = new BigDecimal("80.00");
    private static final BigDecimal VALOR_PECA = new BigDecimal("35.50");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SqsClient sqs;
    @Autowired
    private SnsClient sns;

    private ParticipantesSimulados participantes;
    private NotificacoesObservadas notificacoes;

    private final Map<Participante, JsonNode> ultimoComando = new EnumMap<>(Participante.class);
    private String token;
    private String veiculoId;
    private UUID osId;
    private UUID orcamentoId;
    private UUID pagamentoId;
    private ArrayNode itens;
    private BigDecimal valorTotalDoDiagnostico;

    @Before
    public void preparar() {
        IDENTITY.resetAll();
        participantes = new ParticipantesSimulados(sqs, sns, mapper);
        notificacoes = new NotificacoesObservadas(sqs, mapper);
        participantes.limpar();
        notificacoes.limpar();
    }

    /** Espera a outbox publicar o que sobrou e remove a OS, porque os outros ITs partem de um banco sem OS. */
    @After
    public void limpar() {
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

    // ---------------------------------------------------------------- contexto e abertura

    @Dado("que o Identity conhece o veículo {string} do cliente {string} com a placa {string}")
    public void identityConheceOVeiculo(String veiculoId, String clienteId, String placa) {
        this.veiculoId = veiculoId;
        IDENTITY.stubFor(get(urlEqualTo("/veiculos/" + veiculoId)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id":"%s","status":"ATIVO","placa":"%s","marca":"VW","modelo":"Gol","ano":"2020",
                         "quantidadeEixos":2,"clienteId":"%s"}""".formatted(veiculoId, placa, clienteId))));
    }

    @E("que um atendente está autenticado")
    public void atendenteAutenticado() {
        token = "Bearer " + JwtTestTokens.funcionario(ATENDENTE_ID, "ATENDENTE");
    }

    @Quando("o atendente abre uma OS para o veículo")
    public void abrirOs() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/ordem-servicos").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veiculoId\":\"%s\",\"observacao\":\"Barulho no motor\"}".formatted(veiculoId)))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(resultado.getResponse().getContentAsString())
                .isEqualTo(201);
        osId = UUID.fromString(mapper.readTree(resultado.getResponse().getContentAsString()).path("id").asText());
    }

    // ---------------------------------------------------------------- comandos recebidos pelos participantes

    @Entao("o Execução recebe o comando {string}")
    public void execucaoRecebe(String tipo) {
        receber(EXECUCAO, tipo);
    }

    @Entao("o Billing recebe o comando {string}")
    public void billingRecebe(String tipo) {
        receber(BILLING, tipo);
    }

    @Entao("o Billing recebe o comando {string} com o valor total do diagnóstico")
    public void billingRecebeComValorTotal(String tipo) {
        JsonNode comando = receber(BILLING, tipo);
        assertThat(comando.path("payload").path("valorTotal").decimalValue())
                .isEqualByComparingTo(valorTotalDoDiagnostico);
    }

    @E("o Execução recebe o comando {string} com o motivo {string}")
    public void execucaoRecebeComMotivo(String tipo, String motivo) {
        JsonNode comando = receber(EXECUCAO, tipo);
        assertThat(comando.path("payload").path("motivo").asText()).isEqualTo(motivo);
    }

    @E("o Billing não recebe nenhum comando")
    public void billingNaoRecebe() {
        // a resposta já foi processada (e a transação confirmada): se houvesse comando, estaria na outbox
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE destino = 'billing-comandos' "
                + "AND envelope ->> 'osId' = ?", Integer.class, osId.toString())).isZero();
        assertThat(sqs.receiveMessage(r -> r.queueUrl(
                br.com.fiap.soat.mecanica.os.support.IntegracaoTest.MENSAGERIA.billingComandos())).messages())
                .isEmpty();
    }

    private JsonNode receber(Participante participante, String tipo) {
        JsonNode comando = participantes.receberComando(participante, tipo);
        assertThat(comando.path("osId").asText()).isEqualTo(osId.toString());
        ultimoComando.put(participante, comando);
        return comando;
    }

    // ---------------------------------------------------------------- respostas dos participantes

    @Quando("o Execução responde {string}")
    public void execucaoResponde(String tipo) {
        responder(EXECUCAO, tipo);
    }

    @Quando("o Execução responde {string} com {int} serviço e {int} peça")
    public void execucaoRespondeDiagnostico(String tipo, int servicos, int pecas) {
        diagnostico(tipo, servicos, pecas);
    }

    @Quando("o Billing responde {string}")
    public void billingResponde(String tipo) {
        responder(BILLING, tipo);
    }

    @Quando("o Billing responde {string} com uma nova cobrança")
    public void billingRespondeNovaCobranca(String tipo) {
        responder(BILLING, tipo);
    }

    private void diagnostico(String tipo, int servicos, int pecas) {
        itens = mapper.createArrayNode();
        valorTotalDoDiagnostico = BigDecimal.ZERO;
        for (int i = 0; i < servicos; i++) {
            ObjectNode item = itens.addObject();
            item.put("servicoId", UUID.randomUUID().toString());
            item.put("descricao", "Servico " + (i + 1));
            item.put("precoMaoDeObra", MAO_DE_OBRA);
            valorTotalDoDiagnostico = valorTotalDoDiagnostico.add(MAO_DE_OBRA);
            ArrayNode lista = item.putArray("pecas");
            for (int p = 0; p < pecas; p++) {
                ObjectNode peca = lista.addObject();
                peca.put("pecaId", UUID.randomUUID().toString());
                peca.put("descricao", "Peca " + (p + 1));
                peca.put("quantidade", 1);
                peca.put("valorUnitario", VALOR_PECA);
                valorTotalDoDiagnostico = valorTotalDoDiagnostico.add(VALOR_PECA);
            }
        }
        responder(EXECUCAO, tipo);
    }

    /** Publica a resposta ao último comando do participante e espera a Saga processá-la. */
    private void responder(Participante participante, String tipo) {
        JsonNode comando = ultimoComando.get(participante);
        assertThat(comando).as("comando anterior do %s", participante).isNotNull();
        UUID messageId = participantes.responder(participante, comando, tipo, payload -> ajustar(tipo, comando, payload));
        aguardarProcessamento(messageId, tipo);
    }

    private void ajustar(String tipo, JsonNode comando, ObjectNode payload) {
        switch (tipo) {
            case "DiagnosticoConcluido" -> payload.set("itens", itens);
            case "PecasReservadas" -> payload.set("pecas", comando.path("payload").path("pecas"));
            case "ReservaPecasRecusada" -> {
                ArrayNode faltantes = payload.putArray("faltantes");
                for (JsonNode peca : comando.path("payload").path("pecas")) {
                    ObjectNode faltante = faltantes.addObject();
                    faltante.put("pecaId", peca.path("pecaId").asText());
                    faltante.put("solicitada", peca.path("quantidade").asInt());
                    faltante.put("disponivel", 0);
                }
            }
            case "OrcamentoGerado" -> {
                orcamentoId = UUID.randomUUID();
                payload.put("orcamentoId", orcamentoId.toString());
                payload.put("valorTotal", comando.path("payload").path("valorTotal").decimalValue());
                payload.put("expiraEm", Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString());
            }
            case "OrcamentoAprovado", "OrcamentoRecusado", "OrcamentoExpirado" ->
                    payload.put("orcamentoId", orcamentoId.toString());
            case "CobrancaCriada" -> {
                pagamentoId = UUID.randomUUID();
                payload.put("pagamentoId", pagamentoId.toString());
            }
            case "PagamentoConfirmado", "PagamentoRecusado" -> payload.put("pagamentoId", pagamentoId.toString());
            case "ExecucaoFinalizada" -> {
                ArrayNode tempos = payload.putArray("tempos");
                for (JsonNode item : itens) {
                    tempos.addObject().put("servicoId", item.path("servicoId").asText()).put("minutos", 30);
                }
            }
            default -> {
                // DiagnosticoAberto, ExecucaoIniciada, ReservaPecasLiberada: o exemplo já serve
            }
        }
    }

    /** A resposta foi tratada quando a Saga grava o passo com o messageId dela, na mesma transação do efeito. */
    private void aguardarProcessamento(UUID messageId, String tipo) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM saga_passo WHERE message_id = ?", Integer.class, messageId) > 0);
        assertThat(jdbc.queryForObject("SELECT direcao FROM saga_passo WHERE message_id = ?", String.class, messageId))
                .as("a Saga deve aceitar %s no passo em que está", tipo).isEqualTo("RECEBIDO");
    }

    // ---------------------------------------------------------------- pontos de partida dos cenários de falha

    @Dado("uma OS em diagnóstico com {int} peça")
    public void osEmDiagnosticoComPecas(int pecas) throws Exception {
        abrirOs();
        receber(EXECUCAO, "AbrirDiagnostico");
        responder(EXECUCAO, "DiagnosticoAberto");
        diagnostico("DiagnosticoConcluido", 1, pecas);
        receber(EXECUCAO, "ReservarPecas");
    }

    @Dado("uma OS aguardando aprovação com peças reservadas")
    public void osAguardandoAprovacao() throws Exception {
        osEmDiagnosticoComPecas(1);
        responder(EXECUCAO, "PecasReservadas");
        receber(BILLING, "GerarOrcamento");
        responder(BILLING, "OrcamentoGerado");
        assertThat(detalhe().path("situacao").asText()).isEqualTo("AGUARDANDO_APROVACAO");
    }

    @Dado("uma OS aguardando pagamento")
    public void osAguardandoPagamento() throws Exception {
        osAguardandoAprovacao();
        responder(BILLING, "OrcamentoAprovado");
        receber(EXECUCAO, "IniciarExecucao");
        responder(EXECUCAO, "ExecucaoIniciada");
        responder(EXECUCAO, "ExecucaoFinalizada");
        receber(BILLING, "SolicitarPagamento");
        responder(BILLING, "CobrancaCriada");
        assertThat(detalhe().path("situacao").asText()).isEqualTo("AGUARDANDO_PAGAMENTO");
    }

    // ---------------------------------------------------------------- verificações

    @Entao("a OS fica {string} e a Saga {string}")
    public void osESaga(String situacao, String saga) throws Exception {
        JsonNode os = detalhe();
        assertThat(os.path("situacao").asText()).isEqualTo(situacao);
        assertThat(os.path("sagaStatus").asText()).isEqualTo(saga);
    }

    @Entao("a OS continua {string}")
    public void osContinua(String situacao) throws Exception {
        assertThat(detalhe().path("situacao").asText()).isEqualTo(situacao);
    }

    @Entao("a OS fica {string} com o motivo {string}")
    public void osComMotivo(String situacao, String motivo) throws Exception {
        assertThat(detalhe().path("situacao").asText()).isEqualTo(situacao);
        JsonNode historico = consultar("/ordem-servicos/%s/historico".formatted(osId), 200);
        JsonNode ultima = historico.get(historico.size() - 1);
        assertThat(ultima.path("para").asText()).isEqualTo(situacao);
        assertThat(ultima.path("motivo").asText()).contains(motivo);
    }

    @Entao("a Saga fica {string}")
    public void sagaFica(String status) throws Exception {
        assertThat(detalhe().path("sagaStatus").asText()).isEqualTo(status);
    }

    @E("o cliente foi notificado das situações {string}")
    public void clienteNotificadoDasSituacoes(String lista) {
        List<String> esperadas = Arrays.stream(lista.split(",")).map(String::trim).toList();
        var mensagens = notificacoes.aguardar(osId, lidas -> NotificacoesObservadas.situacoes(lidas).size() >= esperadas.size());
        assertThat(NotificacoesObservadas.situacoes(mensagens)).containsExactlyElementsOf(esperadas);
    }

    @E("o cliente foi notificado do motivo")
    public void clienteNotificadoDoMotivo() {
        var mensagens = notificacoes.aguardar(osId, lidas -> lidas.stream()
                .anyMatch(m -> m.path("payload").hasNonNull("motivo")));
        assertThat(mensagens).anyMatch(m -> "SituacaoOrdemServicoAlterada".equals(m.path("type").asText())
                && "EM_DIAGNOSTICO".equals(m.path("payload").path("para").asText())
                && m.path("payload").path("motivo").asText().contains("peças sem estoque"));
    }

    @E("o cliente recebeu {string}")
    public void clienteRecebeu(String tipo) {
        var mensagens = notificacoes.aguardar(osId, lidas -> lidas.stream()
                .anyMatch(m -> tipo.equals(m.path("type").asText())));
        JsonNode evento = mensagens.stream().filter(m -> tipo.equals(m.path("type").asText())).findFirst().orElseThrow();
        assertThat(ValidadorContratos.validar(evento.toString())).isEmpty();
        assertThat(evento.path("payload").path("motivo").asText()).isNotBlank();
    }

    private JsonNode detalhe() throws Exception {
        return consultar("/ordem-servicos/" + osId, 200);
    }

    private JsonNode consultar(String url, int statusEsperado) throws Exception {
        MvcResult resultado = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).header("Authorization", token))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(statusEsperado);
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }
}
