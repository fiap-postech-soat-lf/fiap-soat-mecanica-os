package br.com.fiap.soat.mecanica.os.domain.saga;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaRepository {

    /** Grava a instancia; uma versao desatualizada lanca OptimisticLockException (a transacao desfaz tudo). */
    SagaInstancia salvar(SagaInstancia saga);

    Optional<SagaInstancia> buscarPorOsId(UUID osId);

    void registrarPasso(SagaPasso passo);

    /** Trilha de auditoria em ordem cronologica. */
    List<SagaPasso> buscarPassos(UUID sagaId);
}
