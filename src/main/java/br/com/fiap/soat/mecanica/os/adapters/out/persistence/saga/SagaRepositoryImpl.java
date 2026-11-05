package br.com.fiap.soat.mecanica.os.adapters.out.persistence.saga;

import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SagaRepositoryImpl implements SagaRepository {

    private final SagaInstanciaJpaRepository instanciaJpaRepository;
    private final SagaPassoJpaRepository passoJpaRepository;
    private final SagaMapper mapper;

    /** Grava e faz flush: a versao devolvida ja e a nova, e um conflito de versao aparece aqui. */
    @Override
    public SagaInstancia salvar(SagaInstancia saga) {
        return mapper.toDomain(instanciaJpaRepository.saveAndFlush(mapper.toEntity(saga)));
    }

    @Override
    public Optional<SagaInstancia> buscarPorOsId(UUID osId) {
        return instanciaJpaRepository.findByOsId(osId).map(mapper::toDomain);
    }

    @Override
    public List<SagaInstancia> travarVencidas(Instant agora, int limite) {
        return instanciaJpaRepository.travarVencidas(agora, limite).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void registrarPasso(SagaPasso passo) {
        passoJpaRepository.save(mapper.toEntity(passo));
    }

    @Override
    public List<SagaPasso> buscarPassos(UUID sagaId) {
        return passoJpaRepository.findBySagaIdOrderByOcorridoEmAsc(sagaId).stream().map(mapper::toDomain).toList();
    }
}
