package br.com.fiap.soat.mecanica.os.domain.valueobject;

import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import lombok.Getter;

import java.util.regex.Pattern;

/** Placa sem hifen, no padrao antigo (ABC1234) ou Mercosul (ABC1D23); mesmo formato dos schemas. */
@Getter
public class Placa {

    private static final Pattern FORMATO = Pattern.compile("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$");

    private final String value;

    public Placa(String value) {
        if (value == null || value.isBlank()) {
            throw new RegraNegocioException("Placa é obrigatória");
        }
        String normalizada = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (!FORMATO.matcher(normalizada).matches()) {
            throw new RegraNegocioException("Placa inválida");
        }
        this.value = normalizada;
    }
}
