package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Lê até 20 instâncias vencidas, travadas com {@code FOR UPDATE SKIP LOCKED} (o OS roda com 2 réplicas), e trata cada
 * uma na mesma transação. Se uma delas falhar, a rodada inteira é desfeita e repetida no próximo ciclo.
 */
@Service
@RequiredArgsConstructor
public class TratarTimeoutsSagaUseCase {

    public static final int LOTE = 20;

    private final SagaRepository sagaRepository;
    private final SagaOrquestrador orquestrador;
    private final Clock clock;

    /** @return quantas instâncias foram tratadas nesta rodada */
    @Transactional
    public int executar() {
        return executar(LOTE);
    }

    @Transactional
    public int executar(int limite) {
        List<SagaInstancia> vencidas = sagaRepository.travarVencidas(clock.instant(), limite);
        vencidas.forEach(orquestrador::tratarTimeout);
        return vencidas.size();
    }
}
