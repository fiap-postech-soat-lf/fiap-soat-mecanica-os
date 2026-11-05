package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.time.Instant;
import java.util.UUID;

/** Execução -> OS: o diagnóstico foi aberto. */
public record DiagnosticoAberto(UUID osId, Instant abertoEm) {
}
