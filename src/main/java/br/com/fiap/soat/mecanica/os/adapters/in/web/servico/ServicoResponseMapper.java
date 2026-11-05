package br.com.fiap.soat.mecanica.os.adapters.in.web.servico;

import br.com.fiap.soat.mecanica.os.adapters.in.web.servico.dto.ServicoResponse;
import br.com.fiap.soat.mecanica.os.domain.servico.Servico;

final class ServicoResponseMapper {

    private ServicoResponseMapper() {
    }

    static ServicoResponse toResponse(Servico servico) {
        return new ServicoResponse(servico.getId(), servico.getStatus(), servico.getNome(), servico.getDescricao());
    }
}
