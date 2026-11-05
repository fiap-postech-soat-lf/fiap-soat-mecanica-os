package br.com.fiap.soat.mecanica.os.domain.saga;

public enum SagaStatus {
    INICIADA,
    COMPENSANDO,
    COMPENSADA,
    CONCLUIDA,
    FALHOU
}
