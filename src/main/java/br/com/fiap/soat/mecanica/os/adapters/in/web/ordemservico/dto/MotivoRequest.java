package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto;

import jakarta.validation.constraints.Size;

/** Corpo opcional de {@code cancelar} e {@code revisar-orcamento}. */
public record MotivoRequest(
        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
        String motivo
) {
}
