package br.com.fiap.soat.mecanica.os.domain.ordemservico;

/** Os mesmos 8 valores do schema SituacaoOrdemServicoAlterada. */
public enum SituacaoOrdemServico {
    RECEBIDA("Recebida"),
    EM_DIAGNOSTICO("Em diagnóstico"),
    AGUARDANDO_APROVACAO("Aguardando aprovação"),
    EM_EXECUCAO("Em execução"),
    FINALIZADA("Finalizada"),
    AGUARDANDO_PAGAMENTO("Aguardando pagamento"),
    ENTREGUE("Entregue"),
    CANCELADA("Cancelada");

    private final String descricao;

    SituacaoOrdemServico(String descricao) {
        this.descricao = descricao;
    }

    /** Descricao legivel; o nome do enum e o valor de contrato (use name()). */
    public String descricao() {
        return descricao;
    }

    /** Situacoes em que a OS ainda pode ser cancelada pela Saga. */
    public boolean permiteCancelamento() {
        return this == RECEBIDA || this == EM_DIAGNOSTICO || this == AGUARDANDO_APROVACAO;
    }
}
