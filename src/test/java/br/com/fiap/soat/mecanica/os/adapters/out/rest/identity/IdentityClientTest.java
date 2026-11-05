package br.com.fiap.soat.mecanica.os.adapters.out.rest.identity;

import br.com.fiap.soat.mecanica.os.application.ordemservico.exception.IdentityIndisponivelException;
import br.com.fiap.soat.mecanica.os.application.ordemservico.exception.IntegracaoIdentityException;
import br.com.fiap.soat.mecanica.os.application.ordemservico.exception.VeiculoInvalidoException;
import br.com.fiap.soat.mecanica.os.application.ordemservico.port.Veiculo;
import br.com.fiap.soat.mecanica.os.config.RestClientConfig;
import br.com.fiap.soat.mecanica.os.config.observability.Mdc;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityClientTest {

    private static final UUID VEICULO = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENTE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String URL = "/veiculos/" + VEICULO;

    private static WireMockServer identity;
    private IdentityClient client;

    @BeforeAll
    static void subir() {
        identity = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        identity.start();
    }

    @AfterAll
    static void derrubar() {
        identity.stop();
    }

    @BeforeEach
    void preparar() {
        identity.resetAll();
        RestClient.Builder builder = RestClient.builder();
        new RestClientConfig().timeoutsECorrelacao().customize(builder);
        IdentityProperties propriedades = new IdentityProperties(identity.baseUrl(),
                new IdentityProperties.Retry(3, Duration.ofMillis(10), 2));
        client = new IdentityClient(new IdentityClientConfig().identityRestClient(builder, propriedades), propriedades);
    }

    @AfterEach
    void limpar() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    private static String corpo(String status) {
        return """
                {"id":"%s","status":"%s","placa":"ABC1D23","marca":"VW","modelo":"Gol","ano":"2020",
                 "quantidadeEixos":2,"clienteId":"%s"}""".formatted(VEICULO, status, CLIENTE);
    }

    private static void stubStatus(int status) {
        identity.stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(status)));
    }

    @Test
    void devolveOVeiculoAtivo() {
        identity.stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(corpo("ATIVO"))));

        Veiculo veiculo = client.consultar(VEICULO);

        assertThat(veiculo).isEqualTo(new Veiculo(VEICULO, "ABC1D23", CLIENTE, true));
    }

    @Test
    void veiculoInexistenteEInvalidoSemRetry() {
        stubStatus(404);

        assertThatThrownBy(() -> client.consultar(VEICULO)).isInstanceOf(VeiculoInvalidoException.class);
        identity.verify(1, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void veiculoInativoEInvalido() {
        identity.stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(corpo("INATIVO"))));

        assertThatThrownBy(() -> client.consultar(VEICULO)).isInstanceOf(VeiculoInvalidoException.class);
    }

    @Test
    void sucessoNaTerceiraTentativaDepoisDeDois503() {
        identity.stubFor(get(urlEqualTo(URL)).inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("segunda"));
        identity.stubFor(get(urlEqualTo(URL)).inScenario("retry").whenScenarioStateIs("segunda")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("terceira"));
        identity.stubFor(get(urlEqualTo(URL)).inScenario("retry").whenScenarioStateIs("terceira")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(corpo("ATIVO"))));

        assertThat(client.consultar(VEICULO).clienteId()).isEqualTo(CLIENTE);
        identity.verify(3, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void tresVezes503EsgotamOsRetries() {
        stubStatus(503);

        assertThatThrownBy(() -> client.consultar(VEICULO)).isInstanceOf(IdentityIndisponivelException.class);
        identity.verify(3, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void atrasoDeTresSegundosEstouraOTimeoutEFazRetry() {
        identity.stubFor(get(urlEqualTo(URL)).inScenario("lento").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200).withFixedDelay(3000)).willSetStateTo("rapido"));
        identity.stubFor(get(urlEqualTo(URL)).inScenario("lento").whenScenarioStateIs("rapido")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(corpo("ATIVO"))));

        assertThat(client.consultar(VEICULO).placa()).isEqualTo("ABC1D23");
        identity.verify(2, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void tokenRecusadoVira502SemRetry() {
        for (int status : new int[]{401, 403}) {
            identity.resetAll();
            stubStatus(status);

            assertThatThrownBy(() -> client.consultar(VEICULO)).isInstanceOf(IntegracaoIdentityException.class);
            identity.verify(1, getRequestedFor(urlEqualTo(URL)));
        }
    }

    @Test
    void repassaOJwtDaRequisicaoEOCorrelationId() {
        MockHttpServletRequest entrada = new MockHttpServletRequest();
        entrada.addHeader("Authorization", "Bearer token-do-usuario");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(entrada));
        MDC.put(Mdc.CORRELATION_ID, "corr-abertura");
        identity.stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(corpo("ATIVO"))));

        client.consultar(VEICULO);

        identity.verify(getRequestedFor(urlEqualTo(URL))
                .withHeader("Authorization", equalTo("Bearer token-do-usuario"))
                .withHeader("X-Correlation-Id", equalTo("corr-abertura")));
    }
}
