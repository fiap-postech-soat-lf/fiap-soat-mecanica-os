package br.com.fiap.soat.mecanica.os.adapters.in.scheduler;

import br.com.fiap.soat.mecanica.os.application.saga.TratarTimeoutsSagaUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Varre os prazos da Saga a cada {@code app.saga.timeout.intervalo} (30 s). Desligável nos testes de integração. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.saga.timeout.scheduler-habilitado", havingValue = "true", matchIfMissing = true)
public class SagaTimeoutScheduler {

    private final TratarTimeoutsSagaUseCase useCase;

    @Scheduled(fixedDelayString = "${app.saga.timeout.intervalo:PT30S}")
    public void agendado() {
        try {
            int tratadas = useCase.executar();
            if (tratadas > 0) {
                log.atInfo().addKeyValue("event", "saga_timeouts_tratados")
                        .addKeyValue("quantidade", tratadas).log("Timeouts da Saga tratados");
            }
        } catch (RuntimeException e) {
            log.atError().addKeyValue("event", "saga_timeouts_falhou").setCause(e)
                    .log("Falha ao tratar os timeouts da Saga; nova tentativa no próximo ciclo");
        }
    }
}
