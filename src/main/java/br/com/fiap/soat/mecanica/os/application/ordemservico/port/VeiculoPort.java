package br.com.fiap.soat.mecanica.os.application.ordemservico.port;

import java.util.UUID;

/** Consulta síncrona ao Identity (passo 1 da Saga, A5). */
public interface VeiculoPort {

    /**
     * @throws br.com.fiap.soat.mecanica.os.application.ordemservico.exception.VeiculoInvalidoException veículo inexistente ou inativo
     * @throws br.com.fiap.soat.mecanica.os.application.ordemservico.exception.IdentityIndisponivelException retries esgotados
     * @throws br.com.fiap.soat.mecanica.os.application.ordemservico.exception.IntegracaoIdentityException o Identity recusou o token
     */
    Veiculo consultar(UUID veiculoId);
}
