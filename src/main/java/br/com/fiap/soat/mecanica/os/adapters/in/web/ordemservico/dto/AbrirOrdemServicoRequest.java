package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AbrirOrdemServicoRequest(
        @NotNull(message = "O veículo é obrigatório")
        UUID veiculoId,

        String observacao
) {
}
