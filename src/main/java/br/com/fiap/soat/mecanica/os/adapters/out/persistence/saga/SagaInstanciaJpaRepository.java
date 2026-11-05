package br.com.fiap.soat.mecanica.os.adapters.out.persistence.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SagaInstanciaJpaRepository extends JpaRepository<SagaInstanciaEntity, UUID> {

    Optional<SagaInstanciaEntity> findByOsId(UUID osId);
}
