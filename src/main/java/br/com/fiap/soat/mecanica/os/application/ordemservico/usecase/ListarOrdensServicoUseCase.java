package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.domain.Pagina;
import br.com.fiap.soat.mecanica.os.domain.Paginacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListarOrdensServicoUseCase {

    private final OrdemServicoRepository ordemServicoRepository;

    /** OS em andamento (sem ENTREGUE e CANCELADA), da mais recente para a mais antiga. */
    public Pagina<OrdemServico> executar(Paginacao paginacao) {
        return ordemServicoRepository.listar(paginacao);
    }
}
