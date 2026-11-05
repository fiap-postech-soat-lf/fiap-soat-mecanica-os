package br.com.fiap.soat.mecanica.os.domain.servico;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicoRepository {

    Servico salvar(Servico servico);

    Optional<Servico> buscarPorId(UUID id);

    Optional<Servico> buscarPorNome(String nome);

    /** Todos os servicos, ordenados por nome. */
    List<Servico> buscarTodos();
}
