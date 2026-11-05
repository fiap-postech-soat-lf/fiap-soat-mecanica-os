package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.valueobject.Placa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultarSituacaoPorPlacaUseCase {

    private final OrdemServicoRepository ordemServicoRepository;

    /** Placa invalida lanca RegraNegocioException; placa sem OS devolve lista vazia (rota publica). */
    public List<OrdemServico> executar(String placa) {
        return ordemServicoRepository.buscarPorPlaca(new Placa(placa).getValue());
    }
}
