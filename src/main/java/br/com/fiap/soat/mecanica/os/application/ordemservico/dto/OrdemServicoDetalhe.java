package br.com.fiap.soat.mecanica.os.application.ordemservico.dto;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;

/** A OS com a Saga que a conduz; {@code saga} e nula se a OS ainda nao tiver uma. */
public record OrdemServicoDetalhe(OrdemServico ordemServico, SagaInstancia saga) {
}
