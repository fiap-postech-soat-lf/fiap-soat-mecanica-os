package br.com.fiap.soat.mecanica.os.config.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessEventLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(BusinessEventLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void anexar() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void remover() {
        logger.detachAppender(appender);
    }

    private ILoggingEvent unico() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    private static Object valor(ILoggingEvent evento, String chave) {
        for (KeyValuePair par : evento.getKeyValuePairs()) {
            if (par.key.equals(chave)) {
                return par.value;
            }
        }
        return null;
    }

    @Test
    void ordemServicoCriada() {
        UUID os = UUID.randomUUID();
        BusinessEventLogger.ordemServicoCriada(os, UUID.randomUUID(), UUID.randomUUID());

        assertThat(valor(unico(), "event")).isEqualTo("order_service_created");
        assertThat(valor(unico(), "orderServiceId")).isEqualTo(os);
    }

    @Test
    void situacaoAlterada() {
        BusinessEventLogger.situacaoOrdemServicoAlterada(UUID.randomUUID(), null, SituacaoOrdemServico.RECEBIDA);

        assertThat(valor(unico(), "event")).isEqualTo("order_service_status_changed");
        assertThat(valor(unico(), "previousStatus")).isNull();
        assertThat(valor(unico(), "newStatus")).isEqualTo(SituacaoOrdemServico.RECEBIDA);
    }

    @Test
    void ordemServicoCancelada() {
        BusinessEventLogger.ordemServicoCancelada(UUID.randomUUID(), "desistência");

        assertThat(valor(unico(), "event")).isEqualTo("order_service_cancelled");
        assertThat(valor(unico(), "reason")).isEqualTo("desistência");
    }

    @Test
    void sagaPasso() {
        BusinessEventLogger.sagaPasso(UUID.randomUUID(), UUID.randomUUID(), PassoSaga.RESERVAR_PECAS,
                "ReservarPecas", "ENVIADO");

        assertThat(valor(unico(), "event")).isEqualTo("saga_passo");
        assertThat(valor(unico(), "passo")).isEqualTo(PassoSaga.RESERVAR_PECAS);
        assertThat(valor(unico(), "direcao")).isEqualTo("ENVIADO");
    }

    @Test
    void sagaFinalizada() {
        BusinessEventLogger.sagaFinalizada(UUID.randomUUID(), UUID.randomUUID(), SagaStatus.COMPENSADA);

        assertThat(valor(unico(), "event")).isEqualTo("saga_finalizada");
        assertThat(valor(unico(), "status")).isEqualTo(SagaStatus.COMPENSADA);
    }

    @Test
    void naoRepeteSagaIdEOsIdQuandoOMdcJaOsTem() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();

        try (var ignored = Mdc.abrir(java.util.Map.of(Mdc.SAGA_ID, saga, Mdc.OS_ID, os))) {
            BusinessEventLogger.sagaPasso(saga, os, PassoSaga.RESERVAR_PECAS, "PecasReservadas", "RECEBIDO");
            BusinessEventLogger.sagaFalhou(saga, os, PassoSaga.RESERVAR_PECAS, 3);
            BusinessEventLogger.sagaFinalizada(saga, os, SagaStatus.CONCLUIDA);
        }

        assertThat(appender.list).hasSize(3).allSatisfy(evento -> {
            assertThat(valor(evento, "sagaId")).isNull();
            assertThat(valor(evento, "osId")).isNull();
            assertThat(evento.getMDCPropertyMap()).containsEntry("sagaId", saga.toString())
                    .containsEntry("osId", os.toString());
        });
    }

    @Test
    void incluiSagaIdEOsIdQuandoOMdcEstaVazio() {
        UUID saga = UUID.randomUUID();
        UUID os = UUID.randomUUID();

        BusinessEventLogger.sagaPasso(saga, os, PassoSaga.RESERVAR_PECAS, "ReservarPecas", "ENVIADO");

        assertThat(valor(unico(), "sagaId")).isEqualTo(saga);
        assertThat(valor(unico(), "osId")).isEqualTo(os);
    }
}
