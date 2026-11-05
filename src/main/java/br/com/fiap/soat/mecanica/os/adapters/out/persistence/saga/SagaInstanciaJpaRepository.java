package br.com.fiap.soat.mecanica.os.adapters.out.persistence.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaInstanciaJpaRepository extends JpaRepository<SagaInstanciaEntity, UUID> {

    Optional<SagaInstanciaEntity> findByOsId(UUID osId);

    /** Mais vencidas primeiro; SKIP LOCKED para duas réplicas não tratarem a mesma instância. */
    @Query(value = """
            SELECT * FROM saga_instancia
            WHERE prazo_em IS NOT NULL AND prazo_em <= :agora
              AND status IN ('INICIADA', 'COMPENSANDO')
            ORDER BY prazo_em
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SagaInstanciaEntity> travarVencidas(@Param("agora") Instant agora, @Param("limite") int limite);
}
