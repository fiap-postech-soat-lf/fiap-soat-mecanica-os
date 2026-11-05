package br.com.fiap.soat.mecanica.os.adapters.out.persistence.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.Pagina;
import br.com.fiap.soat.mecanica.os.domain.Paginacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.MudancaSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrdemServicoRepositoryImpl implements OrdemServicoRepository {

    private static final Set<SituacaoOrdemServico> ENCERRADAS =
            Set.of(SituacaoOrdemServico.ENTREGUE, SituacaoOrdemServico.CANCELADA);

    private final OrdemServicoJpaRepository jpaRepository;
    private final HistoricoSituacaoJpaRepository historicoJpaRepository;
    private final OrdemServicoMapper mapper;

    @Override
    public OrdemServico salvar(OrdemServico ordemServico) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(ordemServico)));
    }

    @Override
    public Optional<OrdemServico> buscarPorId(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<OrdemServico> buscarPorPlaca(String placa) {
        return jpaRepository.findByPlacaOrderByDataRecebidaDescIdDesc(placa).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<OrdemServico> buscarPorClienteId(UUID clienteId) {
        return jpaRepository.findByClienteIdOrderByDataRecebidaDescIdDesc(clienteId).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Pagina<OrdemServico> listar(Paginacao paginacao) {
        Page<OrdemServicoEntity> resultado = jpaRepository.findBySituacaoNotInOrderByDataRecebidaDescIdDesc(
                ENCERRADAS, PageRequest.of(paginacao.page(), paginacao.size()));
        return new Pagina<>(
                resultado.getContent().stream().map(mapper::toDomain).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    @Override
    public void registrarHistorico(UUID osId, MudancaSituacao mudanca) {
        HistoricoSituacaoEntity entity = new HistoricoSituacaoEntity();
        entity.setId(UUID.randomUUID());
        entity.setOsId(osId);
        entity.setDe(mudanca.de());
        entity.setPara(mudanca.para());
        entity.setMotivo(mudanca.motivo());
        entity.setOcorridaEm(mudanca.em());
        historicoJpaRepository.save(entity);
    }

    @Override
    public List<HistoricoSituacao> buscarHistorico(UUID osId) {
        return historicoJpaRepository.findByOsIdOrderByOcorridaEmAsc(osId).stream().map(mapper::toDomain).toList();
    }
}
