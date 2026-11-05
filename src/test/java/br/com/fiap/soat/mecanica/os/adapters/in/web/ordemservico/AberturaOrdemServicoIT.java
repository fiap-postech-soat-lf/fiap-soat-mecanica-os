package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.os.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.os.support.ValidadorContratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.ATENDENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.CLIENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.PLACA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.VEICULO_ID;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Abertura da OS de ponta a ponta: Identity no WireMock, banco real e a outbox publicando no LocalStack
 * (fila mecanica-execucao-comandos e tópico mecanica-os-eventos). Sem @Transactional: a publicação só
 * acontece depois do commit.
 */
class AberturaOrdemServicoIT extends IntegracaoTest {

    private static final String URL_VEICULO = "/veiculos/" + VEICULO_ID;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrdemServicoRepository ordemServicoRepository;
    @Autowired
    private SagaRepository sagaRepository;

    @BeforeEach
    void limparFilas() {
        IDENTITY.resetAll();
        receber(MENSAGERIA.execucaoComandos());
        receber(MENSAGERIA.notificacoes());
    }

    /** Teste sem transacao: remove o que gravou, porque os outros ITs partem de um banco sem OS. */
    @AfterEach
    void limparBanco() {
        jdbc.execute("DELETE FROM saga_passo");
        jdbc.execute("DELETE FROM saga_instancia");
        jdbc.execute("DELETE FROM historico_situacao_os");
        jdbc.execute("DELETE FROM ordem_servicos");
    }

    private static String token() {
        return "Bearer " + JwtTestTokens.funcionario(ATENDENTE_ID, "ATENDENTE");
    }

    private static void veiculoAtivo() {
        IDENTITY.stubFor(get(urlEqualTo(URL_VEICULO)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id":"%s","status":"ATIVO","placa":"%s","marca":"VW","modelo":"Gol","ano":"2020",
                         "quantidadeEixos":2,"clienteId":"%s"}""".formatted(VEICULO_ID, PLACA, CLIENTE_ID))));
    }

    private MvcResult abrir(String token) throws Exception {
        return mockMvc.perform(post("/ordem-servicos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"veiculoId\":\"%s\",\"observacao\":\"Barulho no motor\"}".formatted(VEICULO_ID)))
                .andReturn();
    }

    private List<Message> aguardar(String fila, int quantidade) {
        List<Message> recebidas = new ArrayList<>();
        await().until(() -> {
            recebidas.addAll(receber(fila));
            return recebidas.size() >= quantidade;
        });
        return recebidas;
    }

    @Test
    void abreAOsEPublicaOComandoEoEventoComEnvelopesValidos() throws Exception {
        veiculoAtivo();
        String token = token();

        MvcResult resultado = mockMvc.perform(post("/ordem-servicos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"veiculoId\":\"%s\",\"observacao\":\"Barulho no motor\"}".formatted(VEICULO_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.situacao").value("RECEBIDA"))
                .andExpect(jsonPath("$.clienteId").value(CLIENTE_ID.toString()))
                .andExpect(jsonPath("$.placa").value(PLACA))
                .andReturn();
        UUID osId = UUID.fromString(objectMapper.readTree(resultado.getResponse().getContentAsString())
                .path("id").asText());

        // o JWT do usuario foi repassado ao Identity
        IDENTITY.verify(getRequestedFor(urlEqualTo(URL_VEICULO)).withHeader("Authorization", equalTo(token)));

        // OS, historico e Saga gravados
        assertThat(ordemServicoRepository.buscarHistorico(osId)).hasSize(1);
        var saga = sagaRepository.buscarPorOsId(osId).orElseThrow();
        assertThat(saga.getPassoAtual()).isEqualTo(PassoSaga.ABRIR_DIAGNOSTICO);
        assertThat(saga.getPrazoEm()).isNotNull();

        // comando na fila do Execucao
        JsonNode comando = objectMapper.readTree(aguardar(MENSAGERIA.execucaoComandos(), 1).get(0).body());
        assertThat(ValidadorContratos.validar(comando.toString())).isEmpty();
        assertThat(comando.path("type").asText()).isEqualTo("AbrirDiagnostico");
        assertThat(comando.path("sagaId").asText()).isEqualTo(saga.getId().toString());
        assertThat(comando.path("osId").asText()).isEqualTo(osId.toString());
        assertThat(comando.path("payload").path("veiculoId").asText()).isEqualTo(VEICULO_ID.toString());
        assertThat(comando.path("payload").path("placa").asText()).isEqualTo(PLACA);
        assertThat(saga.getContexto().getUltimoComando().messageId().toString())
                .isEqualTo(comando.path("messageId").asText());

        // evento no topico (chega na fila de notificacoes do Identity)
        JsonNode evento = objectMapper.readTree(aguardar(MENSAGERIA.notificacoes(), 1).get(0).body());
        assertThat(ValidadorContratos.validar(evento.toString())).isEmpty();
        assertThat(evento.path("type").asText()).isEqualTo("SituacaoOrdemServicoAlterada");
        assertThat(evento.path("payload").path("de").isNull()).isTrue();
        assertThat(evento.path("payload").path("para").asText()).isEqualTo("RECEBIDA");
        assertThat(evento.path("payload").path("clienteId").asText()).isEqualTo(CLIENTE_ID.toString());
    }

    @Test
    void veiculoInexistenteResponde422ENadaEGravadoNemPublicado() throws Exception {
        IDENTITY.stubFor(get(urlEqualTo(URL_VEICULO)).willReturn(aResponse().withStatus(404)));
        int osAntes = jdbc.queryForObject("SELECT count(*) FROM ordem_servicos", Integer.class);
        int outboxAntes = jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class);

        abrirEEsperar422();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordem_servicos", Integer.class)).isEqualTo(osAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isEqualTo(outboxAntes);
        assertThat(receber(MENSAGERIA.execucaoComandos())).isEmpty();
    }

    private void abrirEEsperar422() throws Exception {
        MvcResult resultado = abrir(token());
        assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
        assertThat(objectMapper.readTree(resultado.getResponse().getContentAsString()).path("erro").asText())
                .isEqualTo("VEICULO_INVALIDO");
    }

    @Test
    void identityForaDoArResponde503DepoisDosRetries() throws Exception {
        IDENTITY.stubFor(get(urlEqualTo(URL_VEICULO)).willReturn(aResponse().withStatus(503)));

        MvcResult resultado = abrir(token());

        assertThat(resultado.getResponse().getStatus()).isEqualTo(503);
        assertThat(objectMapper.readTree(resultado.getResponse().getContentAsString()).path("erro").asText())
                .isEqualTo("IDENTITY_INDISPONIVEL");
        IDENTITY.verify(3, getRequestedFor(urlEqualTo(URL_VEICULO)));
    }
}
