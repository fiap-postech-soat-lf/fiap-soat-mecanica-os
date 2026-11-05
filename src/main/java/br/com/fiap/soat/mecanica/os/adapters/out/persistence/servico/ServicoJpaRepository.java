package br.com.fiap.soat.mecanica.os.adapters.out.persistence.servico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicoJpaRepository extends JpaRepository<ServicoEntity, UUID> {

    Optional<ServicoEntity> findByNome(String nome);

    List<ServicoEntity> findAllByOrderByNomeAsc();
}
