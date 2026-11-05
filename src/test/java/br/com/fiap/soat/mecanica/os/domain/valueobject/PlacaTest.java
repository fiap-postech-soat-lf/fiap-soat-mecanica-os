package br.com.fiap.soat.mecanica.os.domain.valueobject;

import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacaTest {

    @ParameterizedTest
    @CsvSource({"ABC1234,ABC1234", "abc1d23,ABC1D23", "ABC-1234,ABC1234", " abc 1d23 ,ABC1D23"})
    void normalizaPlacasValidas(String entrada, String esperada) {
        assertThat(new Placa(entrada).getValue()).isEqualTo(esperada);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB12345", "ABCD123", "1234567", "ABC12", "ABC1D234", "ABC1DD3"})
    void recusaFormatoInvalido(String placa) {
        assertThatThrownBy(() -> new Placa(placa))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Placa inválida");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void recusaPlacaVazia(String placa) {
        assertThatThrownBy(() -> new Placa(placa))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Placa é obrigatória");
    }

    @Test
    void placaMercosulComLetraNoQuintoCaractere() {
        assertThat(new Placa("RIO2A19").getValue()).isEqualTo("RIO2A19");
    }
}
