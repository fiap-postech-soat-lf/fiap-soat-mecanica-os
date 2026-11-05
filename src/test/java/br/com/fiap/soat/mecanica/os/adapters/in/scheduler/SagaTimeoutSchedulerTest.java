package br.com.fiap.soat.mecanica.os.adapters.in.scheduler;

import br.com.fiap.soat.mecanica.os.application.saga.TratarTimeoutsSagaUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaTimeoutSchedulerTest {

    private final TratarTimeoutsSagaUseCase useCase = mock(TratarTimeoutsSagaUseCase.class);
    private final SagaTimeoutScheduler scheduler = new SagaTimeoutScheduler(useCase);

    @Test
    @DisplayName("cada ciclo chama o caso de uso")
    void chamaOCasoDeUso() {
        when(useCase.executar()).thenReturn(2);

        scheduler.agendado();

        verify(useCase).executar();
    }

    @Test
    @DisplayName("uma falha não derruba o agendador: o próximo ciclo tenta de novo")
    void falhaNaoPropaga() {
        when(useCase.executar()).thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(scheduler::agendado).doesNotThrowAnyException();
    }
}
