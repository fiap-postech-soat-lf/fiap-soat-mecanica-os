package br.com.fiap.soat.mecanica.os.adapters.out.persistence.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SagaPassoJpaRepository extends JpaRepository<SagaPassoEntity, UUID> {

    List<SagaPassoEntity> findBySagaIdOrderByOcorridoEmAsc(UUID sagaId);
}
