package br.com.fiap.soat.mecanica.os.adapters.in.web.servico.dto;

import br.com.fiap.soat.mecanica.os.domain.enums.StatusRecursoEnum;

import java.util.UUID;

public record ServicoResponse(UUID id, StatusRecursoEnum status, String nome, String descricao) {
}
