package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarMinhasOrdensServicoUseCase {

    private final OrdemServicoRepository ordemServicoRepository;
    private final UsuarioAutenticadoPort usuarioAutenticado;

    /** As OS do cliente autenticado (cliente_id = sub), da mais recente para a mais antiga. */
    public List<OrdemServico> executar() {
        return ordemServicoRepository.buscarPorClienteId(usuarioAutenticado.sub());
    }
}
