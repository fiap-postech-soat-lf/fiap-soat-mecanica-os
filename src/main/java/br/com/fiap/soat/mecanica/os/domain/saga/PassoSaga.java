package br.com.fiap.soat.mecanica.os.domain.saga;

/** Passo em que a Saga esta (o que ela espera). INICIAR_EXECUCAO e o pivo: depois dele so ha recuperacao para frente. */
public enum PassoSaga {
    ABRIR_DIAGNOSTICO,
    AGUARDAR_DIAGNOSTICO,
    RESERVAR_PECAS,
    GERAR_ORCAMENTO,
    AGUARDAR_APROVACAO,
    INICIAR_EXECUCAO,
    AGUARDAR_EXECUCAO,
    SOLICITAR_PAGAMENTO,
    AGUARDAR_PAGAMENTO,
    FIM
}
