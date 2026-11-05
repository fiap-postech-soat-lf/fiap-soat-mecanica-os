package br.com.fiap.soat.mecanica.os.application.servico.usecase;

import br.com.fiap.soat.mecanica.os.domain.servico.Servico;
import br.com.fiap.soat.mecanica.os.domain.servico.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarServicosUseCase {

    private final ServicoRepository servicoRepository;

    /** So os ativos, a menos que {@code incluirInativos}. */
    public List<Servico> executar(boolean incluirInativos) {
        List<Servico> todos = servicoRepository.buscarTodos();
        return incluirInativos ? todos : todos.stream().filter(Servico::isAtivo).toList();
    }
}
