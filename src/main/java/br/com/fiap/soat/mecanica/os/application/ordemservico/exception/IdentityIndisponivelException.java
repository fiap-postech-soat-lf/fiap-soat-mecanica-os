package br.com.fiap.soat.mecanica.os.application.ordemservico.exception;

/** Retries esgotados na chamada ao Identity (F4-c: 503 IDENTITY_INDISPONIVEL). */
public class IdentityIndisponivelException extends RuntimeException {

    public IdentityIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public IdentityIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
