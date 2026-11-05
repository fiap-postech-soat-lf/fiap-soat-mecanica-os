package br.com.fiap.soat.mecanica.os.application.ordemservico.exception;

/** Veículo inexistente ou inativo (F4-c: 422 VEICULO_INVALIDO). */
public class VeiculoInvalidoException extends RuntimeException {

    public VeiculoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public VeiculoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
