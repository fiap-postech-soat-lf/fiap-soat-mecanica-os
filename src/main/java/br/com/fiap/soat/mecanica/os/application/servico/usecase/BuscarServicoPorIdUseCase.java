package br.com.fiap.soat.mecanica.os.application.servico.usecase;

import br.com.fiap.soat.mecanica.os.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.os.domain.servico.Servico;
import br.com.fiap.soat.mecanica.os.domain.servico.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Usado tambem pelo Execucao (F5), que repassa o JWT do mecanico. Devolve o servico mesmo inativo. */
@Service
@RequiredArgsConstructor
public class BuscarServicoPorIdUseCase {

    private final ServicoRepository servicoRepository;

    public Servico executar(UUID id) {
        return servicoRepository.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
    }
}
