package br.com.fiap.soat.mecanica.os.application.ordemservico.exception;

/** O Identity recusou o token repassado, 401 ou 403 (F4-c: 502 INTEGRACAO_IDENTITY). */
public class IntegracaoIdentityException extends RuntimeException {

    public IntegracaoIdentityException(String mensagem) {
        super(mensagem);
    }

    public IntegracaoIdentityException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
