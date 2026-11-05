package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Métricas da Saga: contadores {@code saga.iniciada}, {@code saga.concluida}, {@code saga.compensada} e
 * {@code saga.falhou} (tag {@code passo}) e o timer {@code os.tempo_por_situacao} (tag {@code situacao}).
 */
@Component
@RequiredArgsConstructor
public class SagaMetrics {

    private final MeterRegistry registry;

    public void iniciada(PassoSaga passo) {
        contar("saga.iniciada", passo);
    }

    public void concluida(PassoSaga passo) {
        contar("saga.concluida", passo);
    }

    public void compensada(PassoSaga passo) {
        contar("saga.compensada", passo);
    }

    public void falhou(PassoSaga passo) {
        contar("saga.falhou", passo);
    }

    /** Tempo que a OS passou em {@code situacao} antes de sair dela. */
    public void tempoNaSituacao(SituacaoOrdemServico situacao, Duration tempo) {
        Timer.builder("os.tempo_por_situacao").tag("situacao", situacao.name()).register(registry)
                .record(tempo.isNegative() ? Duration.ZERO : tempo);
    }

    private void contar(String nome, PassoSaga passo) {
        registry.counter(nome, "passo", passo.name()).increment();
    }
}
