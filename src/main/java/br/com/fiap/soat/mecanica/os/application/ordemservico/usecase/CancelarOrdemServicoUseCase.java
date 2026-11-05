package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.application.ordemservico.dto.OrdemServicoDetalhe;
import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cancela a OS até o orçamento ser aprovado (compensações na Saga). */
@Service
@RequiredArgsConstructor
public class CancelarOrdemServicoUseCase {

    private final SagaOrquestrador orquestrador;

    @Transactional
    public OrdemServicoDetalhe executar(UUID osId, String motivo) {
        return orquestrador.cancelar(osId, motivo);
    }
}
