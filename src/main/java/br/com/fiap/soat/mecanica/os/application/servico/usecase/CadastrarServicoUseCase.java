package br.com.fiap.soat.mecanica.os.application.servico.usecase;

import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.os.domain.servico.Servico;
import br.com.fiap.soat.mecanica.os.domain.servico.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CadastrarServicoUseCase {

    private final ServicoRepository servicoRepository;

    public Servico executar(String nome, String descricao) {
        servicoRepository.buscarPorNome(nome).ifPresent(servico -> {
            throw new RegraNegocioException("Serviço com esse nome já existe");
        });
        return servicoRepository.salvar(new Servico(nome, descricao));
    }
}
