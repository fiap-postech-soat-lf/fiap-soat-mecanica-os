package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.application.ordemservico.dto.OrdemServicoDetalhe;
import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Devolve a OS ao diagnóstico enquanto o orçamento aguarda a aprovação. */
@Service
@RequiredArgsConstructor
public class RevisarOrcamentoUseCase {

    private final SagaOrquestrador orquestrador;

    @Transactional
    public OrdemServicoDetalhe executar(UUID osId, String motivo) {
        return orquestrador.revisarOrcamento(osId, motivo);
    }
}
