package br.com.fiap.soat.mecanica.os.adapters.out.rest.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Resposta de {@code GET /veiculos/{id}} do Identity; só os campos usados. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VeiculoResponse(UUID id, String status, String placa, UUID clienteId) {
}
