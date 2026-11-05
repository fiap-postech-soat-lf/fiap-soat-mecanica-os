package br.com.fiap.soat.mecanica.os.adapters.in.web.servico.dto;

import jakarta.validation.constraints.NotBlank;

public record ServicoRequest(
        @NotBlank(message = "O nome não pode ser nulo nem vazio")
        String nome,

        String descricao
) {
}
