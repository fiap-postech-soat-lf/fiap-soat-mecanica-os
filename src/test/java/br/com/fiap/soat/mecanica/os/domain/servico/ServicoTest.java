package br.com.fiap.soat.mecanica.os.domain.servico;

import br.com.fiap.soat.mecanica.os.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicoTest {

    @Test
    @DisplayName("Deve criar o serviço ativo, com nome e descrição")
    void deveCriarAtivo() {
        Servico servico = new Servico("Troca de oleo", "Oleo e filtro");

        assertThat(servico.getNome()).isEqualTo("Troca de oleo");
        assertThat(servico.getDescricao()).isEqualTo("Oleo e filtro");
        assertThat(servico.isAtivo()).isTrue();
        assertThat(servico.getId()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("Deve recusar nome vazio")
    void deveRecusarNomeVazio(String nome) {
        assertThatThrownBy(() -> new Servico(nome, "x"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Nome do serviço é obrigatório");
    }

    @Test
    @DisplayName("Deve reconstruir com o id e o status da persistência")
    void deveReconstruir() {
        UUID id = UUID.randomUUID();

        Servico servico = Servico.reconstruir(id, StatusRecursoEnum.INATIVO, "Alinhamento", null);

        assertThat(servico.getId()).isEqualTo(id);
        assertThat(servico.isInativo()).isTrue();
    }

    @Test
    @DisplayName("Deve alterar nome e descrição; nome em branco mantém o atual")
    void deveAlterar() {
        Servico servico = new Servico("Antigo", "d1");

        servico.alterar("Novo", "d2");
        assertThat(servico.getNome()).isEqualTo("Novo");
        assertThat(servico.getDescricao()).isEqualTo("d2");

        servico.alterar(" ", "d3");
        assertThat(servico.getNome()).isEqualTo("Novo");
        assertThat(servico.getDescricao()).isEqualTo("d3");

        servico.alterar(null, null);
        assertThat(servico.getNome()).isEqualTo("Novo");
    }
}
