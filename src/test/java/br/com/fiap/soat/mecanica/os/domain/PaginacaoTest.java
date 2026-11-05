package br.com.fiap.soat.mecanica.os.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginacaoTest {

    @Test
    void aceitaPaginaZeroETamanhoAteCem() {
        assertThat(new Paginacao(0, 1).size()).isEqualTo(1);
        assertThat(new Paginacao(3, 100).page()).isEqualTo(3);
    }

    @Test
    void recusaPaginaNegativaETamanhoForaDe1a100() {
        assertThatThrownBy(() -> new Paginacao(-1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paginacao(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paginacao(0, 101)).isInstanceOf(IllegalArgumentException.class);
    }
}
