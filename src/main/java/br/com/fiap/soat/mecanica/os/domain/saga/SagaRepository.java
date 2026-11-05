package br.com.fiap.soat.mecanica.os.domain.saga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaRepository {

    /** Grava a instancia; uma versao desatualizada lanca OptimisticLockException (a transacao desfaz tudo). */
    SagaInstancia salvar(SagaInstancia saga);

    Optional<SagaInstancia> buscarPorOsId(UUID osId);

    /**
     * Instâncias INICIADA ou COMPENSANDO com o prazo vencido em {@code agora}, travadas até o fim da transação
     * (SKIP LOCKED: cada réplica pega um lote diferente). Exige transação ativa.
     */
    List<SagaInstancia> travarVencidas(Instant agora, int limite);

    void registrarPasso(SagaPasso passo);

    /** Trilha de auditoria em ordem cronologica. */
    List<SagaPasso> buscarPassos(UUID sagaId);
}
