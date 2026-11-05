package br.com.fiap.soat.mecanica.os.domain.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_APROVACAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_PAGAMENTO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.CANCELADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_DIAGNOSTICO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_EXECUCAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.ENTREGUE;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.FINALIZADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.RECEBIDA;

/**
 * Ordem de servico. A situacao e a verdade do negocio (F4-e) e so muda por uma das transicoes abaixo,
 * cada uma validando a situacao de origem e devolvendo a {@link MudancaSituacao}. Sem soft delete
 * (decisao fixa #10): cancelar leva a CANCELADA.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrdemServico {

    private UUID id;
    private SituacaoOrdemServico situacao;

    private Instant dataRecebida;
    private Instant dataDiagnostico;
    private Instant dataAguardandoAprovacao;
    private Instant dataExecucao;
    private Instant dataFinalizada;
    private Instant dataAguardandoPagamento;
    private Instant dataEntregue;
    private Instant dataCancelada;
    private String motivoCancelamento;

    private Boolean pago;
    private BigDecimal valorTotal;
    private String observacao;
    private UUID veiculoId;
    /** Snapshot do Identity na abertura (F4-d). */
    private UUID clienteId;
    private String placa;
    /** sub do JWT de quem abriu, sem validacao remota. */
    private UUID usuarioAberturaId;

    public OrdemServico(String observacao, UUID veiculoId, UUID clienteId, String placa,
                        UUID usuarioAberturaId, Clock clock) {
        if (veiculoId == null) {
            throw new RegraNegocioException("O veículo é obrigatório");
        }
        if (clienteId == null) {
            throw new RegraNegocioException("O cliente é obrigatório");
        }
        if (placa == null || placa.isBlank()) {
            throw new RegraNegocioException("A placa é obrigatória");
        }
        if (usuarioAberturaId == null) {
            throw new RegraNegocioException("O usuário de abertura é obrigatório");
        }
        this.observacao = observacao;
        this.veiculoId = veiculoId;
        this.clienteId = clienteId;
        this.placa = placa;
        this.usuarioAberturaId = usuarioAberturaId;
        this.valorTotal = BigDecimal.ZERO;
        this.situacao = RECEBIDA;
        this.dataRecebida = clock.instant();
    }

    @SuppressWarnings("java:S107") // reconstrucao a partir da persistencia: um parametro por coluna
    public static OrdemServico reconstruir(UUID id, SituacaoOrdemServico situacao, Instant dataRecebida,
                                           Instant dataDiagnostico, Instant dataAguardandoAprovacao,
                                           Instant dataExecucao, Instant dataFinalizada,
                                           Instant dataAguardandoPagamento, Instant dataEntregue,
                                           Instant dataCancelada, String motivoCancelamento, Boolean pago,
                                           BigDecimal valorTotal, String observacao, UUID veiculoId,
                                           UUID clienteId, String placa, UUID usuarioAberturaId) {
        OrdemServico os = new OrdemServico();
        os.id = id;
        os.situacao = situacao;
        os.dataRecebida = dataRecebida;
        os.dataDiagnostico = dataDiagnostico;
        os.dataAguardandoAprovacao = dataAguardandoAprovacao;
        os.dataExecucao = dataExecucao;
        os.dataFinalizada = dataFinalizada;
        os.dataAguardandoPagamento = dataAguardandoPagamento;
        os.dataEntregue = dataEntregue;
        os.dataCancelada = dataCancelada;
        os.motivoCancelamento = motivoCancelamento;
        os.pago = pago;
        os.valorTotal = valorTotal;
        os.observacao = observacao;
        os.veiculoId = veiculoId;
        os.clienteId = clienteId;
        os.placa = placa;
        os.usuarioAberturaId = usuarioAberturaId;
        return os;
    }

    /** Primeira linha do historico: {@code de} nulo, para RECEBIDA. */
    public MudancaSituacao mudancaDeAbertura() {
        return new MudancaSituacao(null, RECEBIDA, null, dataRecebida);
    }

    public MudancaSituacao iniciarDiagnostico(Clock clock) {
        exigir(RECEBIDA, "iniciar o diagnóstico");
        this.dataDiagnostico = clock.instant();
        return mudar(EM_DIAGNOSTICO, null, dataDiagnostico);
    }

    /** Peca sem estoque ou orcamento que falhou: continua em diagnostico, com o motivo no historico. */
    public MudancaSituacao registrarProblemaNoDiagnostico(String motivo, Clock clock) {
        exigir(EM_DIAGNOSTICO, "registrar problema no diagnóstico");
        return mudar(EM_DIAGNOSTICO, motivo, clock.instant());
    }

    public MudancaSituacao enviarParaAprovacao(BigDecimal valorTotal, Clock clock) {
        exigir(EM_DIAGNOSTICO, "ser enviada para aprovação");
        if (valorTotal == null || valorTotal.signum() < 0) {
            throw new RegraNegocioException("Valor total inválido");
        }
        this.valorTotal = valorTotal;
        this.dataAguardandoAprovacao = clock.instant();
        return mudar(AGUARDANDO_APROVACAO, null, dataAguardandoAprovacao);
    }

    public MudancaSituacao voltarParaDiagnostico(String motivo, Clock clock) {
        exigir(AGUARDANDO_APROVACAO, "voltar ao diagnóstico");
        return mudar(EM_DIAGNOSTICO, motivo, clock.instant());
    }

    public MudancaSituacao iniciarExecucao(Clock clock) {
        exigir(AGUARDANDO_APROVACAO, "iniciar a execução");
        this.dataExecucao = clock.instant();
        this.pago = false;
        return mudar(EM_EXECUCAO, null, dataExecucao);
    }

    public MudancaSituacao finalizar(Clock clock) {
        exigir(EM_EXECUCAO, "ser finalizada");
        this.dataFinalizada = clock.instant();
        return mudar(FINALIZADA, null, dataFinalizada);
    }

    public MudancaSituacao aguardarPagamento(Clock clock) {
        exigir(FINALIZADA, "aguardar o pagamento");
        this.dataAguardandoPagamento = clock.instant();
        return mudar(AGUARDANDO_PAGAMENTO, null, dataAguardandoPagamento);
    }

    /** Pagamento recusado: continua aguardando pagamento, com o motivo no historico. */
    public MudancaSituacao registrarPagamentoRecusado(String motivo, Clock clock) {
        exigir(AGUARDANDO_PAGAMENTO, "registrar pagamento recusado");
        return mudar(AGUARDANDO_PAGAMENTO, motivo, clock.instant());
    }

    public MudancaSituacao entregar(Clock clock) {
        exigir(AGUARDANDO_PAGAMENTO, "ser entregue");
        this.pago = true;
        this.dataEntregue = clock.instant();
        return mudar(ENTREGUE, null, dataEntregue);
    }

    public MudancaSituacao cancelar(String motivo, Clock clock) {
        if (!situacao.permiteCancelamento()) {
            throw new RegraNegocioException(
                    "Ordem de serviço só pode ser cancelada até o orçamento ser aprovado (situação atual: %s)"
                            .formatted(situacao.name()));
        }
        this.dataCancelada = clock.instant();
        this.motivoCancelamento = motivo;
        return mudar(CANCELADA, motivo, dataCancelada);
    }

    public boolean isCancelada() {
        return situacao == CANCELADA;
    }

    private void exigir(SituacaoOrdemServico esperada, String acao) {
        if (situacao != esperada) {
            throw new RegraNegocioException("Ordem de serviço deve estar %s para %s (situação atual: %s)"
                    .formatted(esperada.name(), acao, situacao.name()));
        }
    }

    private MudancaSituacao mudar(SituacaoOrdemServico para, String motivo, Instant em) {
        SituacaoOrdemServico de = this.situacao;
        this.situacao = para;
        return new MudancaSituacao(de, para, motivo, em);
    }
}
