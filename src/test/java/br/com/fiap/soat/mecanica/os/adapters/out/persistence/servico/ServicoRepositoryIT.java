package br.com.fiap.soat.mecanica.os.adapters.out.persistence.servico;

import br.com.fiap.soat.mecanica.os.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.os.domain.servico.Servico;
import br.com.fiap.soat.mecanica.os.domain.servico.ServicoRepository;
import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ServicoRepositoryIT extends IntegracaoTest {

    private static final UUID TROCA_DE_OLEO = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Autowired
    private ServicoRepository repository;

    @Test
    void salvaNovoServicoComIdGeradoEAtivo() {
        Servico salvo = repository.salvar(new Servico("Alinhamento", "Geometria"));

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.isAtivo()).isTrue();
        assertThat(repository.buscarPorId(salvo.getId())).get()
                .extracting(Servico::getNome, Servico::getDescricao).containsExactly("Alinhamento", "Geometria");
    }

    @Test
    void alteraEInativaUmServicoExistente() {
        Servico servico = repository.buscarPorId(TROCA_DE_OLEO).orElseThrow();
        servico.alterar("Troca de óleo e filtro", "Novo texto");
        servico.inativar();

        repository.salvar(servico);

        Servico lido = repository.buscarPorId(TROCA_DE_OLEO).orElseThrow();
        assertThat(lido.getNome()).isEqualTo("Troca de óleo e filtro");
        assertThat(lido.getStatus()).isEqualTo(StatusRecursoEnum.INATIVO);
    }

    @Test
    void buscaPorNome() {
        assertThat(repository.buscarPorNome("Troca de oleo")).get().extracting(Servico::getId)
                .isEqualTo(TROCA_DE_OLEO);
        assertThat(repository.buscarPorNome("Inexistente")).isEmpty();
    }

    @Test
    void buscarTodosTrazOsDoSeedEOsNovosOrdenadosPorNome() {
        repository.salvar(new Servico("Alinhamento", null));

        assertThat(repository.buscarTodos()).extracting(Servico::getNome)
                .containsExactly("Alinhamento", "Revisao de freios", "Troca de oleo");
    }
}
