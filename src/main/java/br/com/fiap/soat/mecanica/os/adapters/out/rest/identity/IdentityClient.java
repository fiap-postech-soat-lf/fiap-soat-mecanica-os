package br.com.fiap.soat.mecanica.os.adapters.out.rest.identity;

import br.com.fiap.soat.mecanica.os.application.ordemservico.exception.IdentityIndisponivelException;
import br.com.fiap.soat.mecanica.os.application.ordemservico.exception.IntegracaoIdentityException;
import br.com.fiap.soat.mecanica.os.application.ordemservico.exception.VeiculoInvalidoException;
import br.com.fiap.soat.mecanica.os.application.ordemservico.port.Veiculo;
import br.com.fiap.soat.mecanica.os.application.ordemservico.port.VeiculoPort;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Consulta o veículo no Identity com timeout de 2 s e retry programático (F4-c): 3 tentativas, backoff
 * exponencial, só para erro de I/O, timeout ou 5xx. 404 e 401/403 não são repetidos.
 */
@Slf4j
@Component
public class IdentityClient implements VeiculoPort {

    private static final String STATUS_ATIVO = "ATIVO";

    private final RestClient restClient;
    private final Retry retry;

    public IdentityClient(RestClient identityRestClient, IdentityProperties propriedades) {
        this.restClient = identityRestClient;
        IdentityProperties.Retry config = propriedades.retry();
        this.retry = Retry.of("identity", RetryConfig.custom()
                .maxAttempts(config.maxTentativas())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(config.esperaInicial(), config.multiplicador()))
                .retryOnException(IdentityClient::repetivel)
                .build());
    }

    @Override
    public Veiculo consultar(UUID veiculoId) {
        try {
            VeiculoResponse resposta = Retry.decorateSupplier(retry, () -> buscar(veiculoId)).get();
            if (resposta == null || !STATUS_ATIVO.equalsIgnoreCase(resposta.status())) {
                throw new VeiculoInvalidoException("Veículo inativo: " + veiculoId);
            }
            return new Veiculo(resposta.id(), resposta.placa(), resposta.clienteId(), true);
        } catch (HttpClientErrorException.NotFound e) {
            throw new VeiculoInvalidoException("Veículo não encontrado: " + veiculoId);
        } catch (HttpClientErrorException e) {
            log.atWarn().addKeyValue("event", "identity_recusou_chamada")
                    .addKeyValue("status", e.getStatusCode().value()).log("Identity recusou a chamada");
            throw new IntegracaoIdentityException("O Identity recusou a chamada (HTTP %d)"
                    .formatted(e.getStatusCode().value()), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.atWarn().addKeyValue("event", "identity_indisponivel")
                    .addKeyValue("exceptionType", e.getClass().getSimpleName())
                    .log("Identity indisponível após os retries");
            throw new IdentityIndisponivelException("Identity indisponível", e);
        }
    }

    private VeiculoResponse buscar(UUID veiculoId) {
        return restClient.get().uri("/veiculos/{id}", veiculoId).retrieve().body(VeiculoResponse.class);
    }

    private static boolean repetivel(Throwable erro) {
        return erro instanceof ResourceAccessException || erro instanceof HttpServerErrorException;
    }
}
