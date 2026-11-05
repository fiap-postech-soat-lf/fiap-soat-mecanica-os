package br.com.fiap.soat.mecanica.os.adapters.out.persistence.ordemservico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoSituacaoJpaRepository extends JpaRepository<HistoricoSituacaoEntity, UUID> {

    List<HistoricoSituacaoEntity> findByOsIdOrderByOcorridaEmAsc(UUID osId);
}
