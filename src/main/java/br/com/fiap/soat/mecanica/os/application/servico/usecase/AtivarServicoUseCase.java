package br.com.fiap.soat.mecanica.os.application.servico.usecase;

import br.com.fiap.soat.mecanica.os.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.os.domain.servico.Servico;
import br.com.fiap.soat.mecanica.os.domain.servico.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AtivarServicoUseCase {

    private final ServicoRepository servicoRepository;

    public Servico executar(UUID id) {
        Servico servico = servicoRepository.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
        servico.ativar();
        return servicoRepository.salvar(servico);
    }
}
