package br.com.fiap.soat.mecanica.os.adapters.out.persistence.servico;

import br.com.fiap.soat.mecanica.os.domain.servico.Servico;
import br.com.fiap.soat.mecanica.os.domain.servico.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ServicoRepositoryImpl implements ServicoRepository {

    private final ServicoJpaRepository jpaRepository;
    private final ServicoMapper mapper;

    @Override
    public Servico salvar(Servico servico) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(servico)));
    }

    @Override
    public Optional<Servico> buscarPorId(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Servico> buscarPorNome(String nome) {
        return jpaRepository.findByNome(nome).map(mapper::toDomain);
    }

    @Override
    public List<Servico> buscarTodos() {
        return jpaRepository.findAllByOrderByNomeAsc().stream().map(mapper::toDomain).toList();
    }
}
