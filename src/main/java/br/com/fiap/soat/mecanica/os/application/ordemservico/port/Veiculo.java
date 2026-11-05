package br.com.fiap.soat.mecanica.os.application.ordemservico.port;

import java.util.UUID;

/** O que a OS precisa saber do veículo, vindo do Identity. */
public record Veiculo(UUID id, String placa, UUID clienteId, boolean ativo) {
}
