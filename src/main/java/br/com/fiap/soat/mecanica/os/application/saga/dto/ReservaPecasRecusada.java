package br.com.fiap.soat.mecanica.os.application.saga.dto;

import java.util.List;
import java.util.UUID;

/** Execução -> OS: faltou estoque. */
public record ReservaPecasRecusada(UUID osId, List<Faltante> faltantes) {

    public record Faltante(UUID pecaId, int solicitada, int disponivel) {
    }
}
