package br.com.fiap.soat.mecanica.os.application.saga.dto;

import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ItemDiagnostico;

import java.util.List;
import java.util.UUID;

/** Execução -> OS: itens do diagnóstico (no formato do GerarOrcamento). */
public record DiagnosticoConcluido(UUID osId, List<ItemDiagnostico> itens) {
}
