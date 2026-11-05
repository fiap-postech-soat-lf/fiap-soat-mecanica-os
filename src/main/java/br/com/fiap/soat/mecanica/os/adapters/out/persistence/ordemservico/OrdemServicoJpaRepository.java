package br.com.fiap.soat.mecanica.os.adapters.out.persistence.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrdemServicoJpaRepository extends JpaRepository<OrdemServicoEntity, UUID> {

    List<OrdemServicoEntity> findByPlacaOrderByDataRecebidaDescIdDesc(String placa);

    List<OrdemServicoEntity> findByClienteIdOrderByDataRecebidaDescIdDesc(UUID clienteId);

    Page<OrdemServicoEntity> findBySituacaoNotInOrderByDataRecebidaDescIdDesc(
            Collection<SituacaoOrdemServico> situacoes, Pageable pageable);
}
