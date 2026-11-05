package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SagaMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SagaMetrics metricas = new SagaMetrics(registry);

    @Test
    @DisplayName("os quatro contadores levam a tag passo")
    void contadores() {
        metricas.iniciada(PassoSaga.ABRIR_DIAGNOSTICO);
        metricas.concluida(PassoSaga.FIM);
        metricas.compensada(PassoSaga.AGUARDAR_APROVACAO);
        metricas.compensada(PassoSaga.AGUARDAR_APROVACAO);
        metricas.falhou(PassoSaga.INICIAR_EXECUCAO);

        assertThat(registry.counter("saga.iniciada", "passo", "ABRIR_DIAGNOSTICO").count()).isEqualTo(1.0);
        assertThat(registry.counter("saga.concluida", "passo", "FIM").count()).isEqualTo(1.0);
        assertThat(registry.counter("saga.compensada", "passo", "AGUARDAR_APROVACAO").count()).isEqualTo(2.0);
        assertThat(registry.counter("saga.falhou", "passo", "INICIAR_EXECUCAO").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("o timer os.tempo_por_situacao soma o tempo por situação")
    void timer() {
        metricas.tempoNaSituacao(SituacaoOrdemServico.EM_DIAGNOSTICO, Duration.ofMinutes(10));
        metricas.tempoNaSituacao(SituacaoOrdemServico.EM_DIAGNOSTICO, Duration.ofMinutes(20));
        metricas.tempoNaSituacao(SituacaoOrdemServico.RECEBIDA, Duration.ofSeconds(-5));

        var timer = registry.timer("os.tempo_por_situacao", "situacao", "EM_DIAGNOSTICO");
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.MINUTES)).isEqualTo(30.0);
        assertThat(registry.timer("os.tempo_por_situacao", "situacao", "RECEBIDA").totalTime(TimeUnit.SECONDS))
                .isZero();
    }
}
