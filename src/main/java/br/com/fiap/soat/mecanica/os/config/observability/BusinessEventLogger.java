package br.com.fiap.soat.mecanica.os.config.observability;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/** Eventos de negocio em log estruturado (as queries de log da Fase 3 continuam valendo). */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BusinessEventLogger {

    public static void ordemServicoCriada(UUID ordemServicoId, UUID veiculoId, UUID usuarioId) {
        log.atInfo()
                .addKeyValue("event", "order_service_created")
                .addKeyValue("orderServiceId", ordemServicoId)
                .addKeyValue("vehicleId", veiculoId)
                .addKeyValue("userId", usuarioId)
                .log("Order service created");
    }

    /** {@code situacaoAnterior} e nula na abertura. */
    public static void situacaoOrdemServicoAlterada(UUID ordemServicoId, SituacaoOrdemServico situacaoAnterior,
                                                    SituacaoOrdemServico novaSituacao) {
        log.atInfo()
                .addKeyValue("event", "order_service_status_changed")
                .addKeyValue("orderServiceId", ordemServicoId)
                .addKeyValue("previousStatus", situacaoAnterior)
                .addKeyValue("newStatus", novaSituacao)
                .log("Order service status changed");
    }

    public static void ordemServicoCancelada(UUID ordemServicoId, String motivo) {
        log.atInfo()
                .addKeyValue("event", "order_service_cancelled")
                .addKeyValue("orderServiceId", ordemServicoId)
                .addKeyValue("reason", motivo)
                .log("Order service cancelled");
    }

    public static void sagaPasso(UUID sagaId, UUID ordemServicoId, PassoSaga passo, String tipoMensagem,
                                 String direcao) {
        Mdc.comSagaEOs(log.atInfo()
                .addKeyValue("event", "saga_passo"),
                sagaId, ordemServicoId)
                .addKeyValue("passo", passo)
                .addKeyValue("tipoMensagem", tipoMensagem)
                .addKeyValue("direcao", direcao)
                .log("Saga step");
    }

    /** Saga que esgotou as tentativas: precisa de ação manual (alerta). */
    public static void sagaFalhou(UUID sagaId, UUID ordemServicoId, PassoSaga passo, int tentativas) {
        Mdc.comSagaEOs(log.atError()
                .addKeyValue("event", "saga_falhou"),
                sagaId, ordemServicoId)
                .addKeyValue("passo", passo)
                .addKeyValue("tentativas", tentativas)
                .log("Saga failed");
    }

    public static void sagaFinalizada(UUID sagaId, UUID ordemServicoId, SagaStatus status) {
        Mdc.comSagaEOs(log.atInfo()
                .addKeyValue("event", "saga_finalizada"),
                sagaId, ordemServicoId)
                .addKeyValue("status", status)
                .log("Saga finished");
    }
}
