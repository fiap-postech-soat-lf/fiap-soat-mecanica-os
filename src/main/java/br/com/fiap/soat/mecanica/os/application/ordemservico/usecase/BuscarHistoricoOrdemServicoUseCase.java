package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BuscarHistoricoOrdemServicoUseCase {

    private final OrdemServicoRepository ordemServicoRepository;

    /** Em ordem cronologica. */
    public List<HistoricoSituacao> executar(UUID osId) {
        ordemServicoRepository.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de serviço não encontrada"));
        return ordemServicoRepository.buscarHistorico(osId);
    }
}
