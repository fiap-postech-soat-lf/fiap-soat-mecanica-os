package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.os.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.os.application.ordemservico.dto.OrdemServicoDetalhe;
import br.com.fiap.soat.mecanica.os.application.saga.ComandosSaga.MotivoLiberacao;
import br.com.fiap.soat.mecanica.os.application.saga.dto.CobrancaCriada;
import br.com.fiap.soat.mecanica.os.application.saga.dto.DiagnosticoAberto;
import br.com.fiap.soat.mecanica.os.application.saga.dto.DiagnosticoConcluido;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ExecucaoFinalizada;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ExecucaoIniciada;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoAprovado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoExpirado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoFalhou;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoGerado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoRecusado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PagamentoConfirmado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PagamentoRecusado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PecasReservadas;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ReservaPecasLiberada;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ReservaPecasRecusada;
import br.com.fiap.soat.mecanica.os.config.SagaTimeoutProperties;
import br.com.fiap.soat.mecanica.os.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.os.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.MudancaSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ComandoEnviado;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ItemDiagnostico;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.PecaReservada;
import br.com.fiap.soat.mecanica.os.domain.saga.DirecaoPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.ABRIR_DIAGNOSTICO;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.AGUARDAR_APROVACAO;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.AGUARDAR_DIAGNOSTICO;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.AGUARDAR_EXECUCAO;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.AGUARDAR_PAGAMENTO;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.FIM;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.GERAR_ORCAMENTO;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.INICIAR_EXECUCAO;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.RESERVAR_PECAS;
import static br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga.SOLICITAR_PAGAMENTO;

/**
 * Orquestrador da Saga: uma operação por linha da tabela 6.2. Roda dentro da transação do chamador (o
 * {@code ProcessadorMensagemSql} ou o caso de uso REST), porque a outbox exige transação. Não conhece SQS nem JPA.
 * <p>
 * Convenção: o passo muda <b>antes</b> de os comandos serem enviados, para a trilha ({@code saga_passo})
 * registrar cada envio no passo que passa a esperar a resposta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrquestrador {

    private static final int LIMITE_MOTIVO = 500;
    private static final String MOTIVO_DESISTENCIA = "Cancelada a pedido do cliente";
    private static final String MOTIVO_REVISAO = "Revisão do orçamento pedida pelo cliente";

    private final SagaRepository sagaRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final OutboxPort outbox;
    private final ComandosSaga comandos;
    private final EventosOrdemServico eventos;
    private final SagaTimeoutProperties timeouts;
    private final Clock clock;

    // ---------------------------------------------------------------- respostas do Execução

    public void diagnosticoAberto(UUID messageId, DiagnosticoAberto resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "DiagnosticoAberto", messageId, ABRIR_DIAGNOSTICO);
        if (saga == null) {
            return;
        }
        OrdemServico os = carregarOs(saga);
        aplicar(os, saga, os.iniciarDiagnostico(clock));
        saga.avancarPara(AGUARDAR_DIAGNOSTICO, null, clock);
        sagaRepository.salvar(saga);
    }

    public void diagnosticoConcluido(UUID messageId, DiagnosticoConcluido resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "DiagnosticoConcluido", messageId, AGUARDAR_DIAGNOSTICO);
        if (saga == null) {
            return;
        }
        ContextoSaga ctx = saga.getContexto();
        if (ctx.isLiberacaoPendente()) {
            // F4-g: a nova reserva só sai depois da ReservaPecasLiberada (filas padrão não garantem ordem)
            ctx.setDiagnosticoEmEspera(resposta.itens());
            tocar(saga);
        } else {
            seguirDiagnostico(saga, resposta.itens());
        }
        sagaRepository.salvar(saga);
    }

    public void pecasReservadas(UUID messageId, PecasReservadas resposta) {
        Optional<SagaInstancia> existente = sagaRepository.buscarPorOsId(resposta.osId());
        if (existente.isEmpty()) {
            sagaInexistente(resposta.osId(), "PecasReservadas");
            return;
        }
        SagaInstancia saga = existente.get();
        if (!aguarda(saga, RESERVAR_PECAS)) {
            descartar(saga, "PecasReservadas", messageId);
            if (cancelada(saga)) {
                // F4-f: a reserva nasceu depois do cancelamento; desfazer
                liberarReserva(saga, MotivoLiberacao.DESISTENCIA);
                saga.mudarStatus(SagaStatus.COMPENSANDO, clock);
                sagaRepository.salvar(saga);
            }
            return;
        }
        receber(saga, "PecasReservadas", messageId);
        OrdemServico os = carregarOs(saga);
        saga.getContexto().setReservaAtiva(true);
        saga.avancarPara(GERAR_ORCAMENTO, prazoComando(), clock);
        enviar(saga, ComandosSaga.DESTINO_BILLING,
                comandos.gerarOrcamento(saga.getId(), os.getId(), os.getClienteId(), saga.getContexto().getItens()));
        sagaRepository.salvar(saga);
    }

    public void reservaPecasRecusada(UUID messageId, ReservaPecasRecusada resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "ReservaPecasRecusada", messageId, RESERVAR_PECAS);
        if (saga == null) {
            return;
        }
        OrdemServico os = carregarOs(saga);
        aplicar(os, saga, os.registrarProblemaNoDiagnostico(limitar(motivoEstoque(resposta)), clock));
        saga.avancarPara(AGUARDAR_DIAGNOSTICO, null, clock);
        sagaRepository.salvar(saga);
    }

    public void reservaPecasLiberada(UUID messageId, ReservaPecasLiberada resposta) {
        Optional<SagaInstancia> existente = sagaRepository.buscarPorOsId(resposta.osId());
        if (existente.isEmpty()) {
            sagaInexistente(resposta.osId(), "ReservaPecasLiberada");
            return;
        }
        SagaInstancia saga = existente.get();
        ContextoSaga ctx = saga.getContexto();
        if (!ctx.isLiberacaoPendente()) {
            descartar(saga, "ReservaPecasLiberada", messageId);
            return;
        }
        receber(saga, "ReservaPecasLiberada", messageId);
        ctx.setLiberacaoPendente(false);
        ctx.setReservaAtiva(false);
        saga.definirPrazo(null, clock);
        if (saga.getStatus() == SagaStatus.COMPENSANDO) {
            saga.mudarStatus(SagaStatus.COMPENSADA, clock);
            BusinessEventLogger.sagaFinalizada(saga.getId(), saga.getOsId(), saga.getStatus());
        } else if (ctx.getDiagnosticoEmEspera() != null && saga.getPassoAtual() == AGUARDAR_DIAGNOSTICO) {
            List<ItemDiagnostico> itens = ctx.getDiagnosticoEmEspera();
            ctx.setDiagnosticoEmEspera(null);
            seguirDiagnostico(saga, itens);
        }
        sagaRepository.salvar(saga);
    }

    public void execucaoIniciada(UUID messageId, ExecucaoIniciada resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "ExecucaoIniciada", messageId, INICIAR_EXECUCAO);
        if (saga == null) {
            return;
        }
        OrdemServico os = carregarOs(saga);
        aplicar(os, saga, os.iniciarExecucao(clock));
        saga.getContexto().setReservaAtiva(false); // a reserva virou baixa
        saga.avancarPara(AGUARDAR_EXECUCAO, null, clock);
        sagaRepository.salvar(saga);
    }

    public void execucaoFinalizada(UUID messageId, ExecucaoFinalizada resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "ExecucaoFinalizada", messageId, AGUARDAR_EXECUCAO);
        if (saga == null) {
            return;
        }
        OrdemServico os = carregarOs(saga);
        aplicar(os, saga, os.finalizar(clock));
        saga.avancarPara(SOLICITAR_PAGAMENTO, prazoComando(), clock);
        enviar(saga, ComandosSaga.DESTINO_BILLING, comandos.solicitarPagamento(saga.getId(), os.getId(),
                saga.getContexto().getOrcamentoId(), os.getValorTotal()));
        sagaRepository.salvar(saga);
    }

    // ---------------------------------------------------------------- respostas do Billing

    public void orcamentoGerado(UUID messageId, OrcamentoGerado resposta) {
        Optional<SagaInstancia> existente = sagaRepository.buscarPorOsId(resposta.osId());
        if (existente.isEmpty()) {
            sagaInexistente(resposta.osId(), "OrcamentoGerado");
            return;
        }
        SagaInstancia saga = existente.get();
        if (!aguarda(saga, GERAR_ORCAMENTO)) {
            descartar(saga, "OrcamentoGerado", messageId);
            if (cancelada(saga)) {
                // F4-f: o orçamento nasceu depois do cancelamento; desfazer
                enviar(saga, ComandosSaga.DESTINO_BILLING, comandos.cancelarOrcamento(saga.getId(), saga.getOsId(),
                        resposta.orcamentoId(), MOTIVO_DESISTENCIA));
                sagaRepository.salvar(saga);
            }
            return;
        }
        receber(saga, "OrcamentoGerado", messageId);
        OrdemServico os = carregarOs(saga);
        aplicar(os, saga, os.enviarParaAprovacao(resposta.valorTotal(), clock));
        saga.getContexto().setOrcamentoId(resposta.orcamentoId());
        saga.avancarPara(AGUARDAR_APROVACAO, resposta.expiraEm().plus(timeouts.margemAprovacao()), clock);
        sagaRepository.salvar(saga);
    }

    public void orcamentoFalhou(UUID messageId, OrcamentoFalhou resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "OrcamentoFalhou", messageId, GERAR_ORCAMENTO);
        if (saga == null) {
            return;
        }
        OrdemServico os = carregarOs(saga);
        String motivo = limitar(resposta.motivo() == null ? "Falha ao gerar o orçamento" : resposta.motivo());
        aplicar(os, saga, os.registrarProblemaNoDiagnostico(motivo, clock));
        saga.avancarPara(AGUARDAR_DIAGNOSTICO, null, clock);
        if (saga.getContexto().isReservaAtiva()) {
            liberarReserva(saga, MotivoLiberacao.ORCAMENTO_FALHOU);
        }
        sagaRepository.salvar(saga);
    }

    public void orcamentoAprovado(UUID messageId, OrcamentoAprovado resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "OrcamentoAprovado", messageId, AGUARDAR_APROVACAO);
        if (saga == null) {
            return;
        }
        saga.getContexto().setOrcamentoId(resposta.orcamentoId());
        saga.avancarPara(INICIAR_EXECUCAO, prazoComando(), clock); // pivô: depois daqui só há recuperação para frente
        enviar(saga, ComandosSaga.DESTINO_EXECUCAO, comandos.iniciarExecucao(saga.getId(), saga.getOsId()));
        sagaRepository.salvar(saga);
    }

    public void orcamentoRecusado(UUID messageId, OrcamentoRecusado resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "OrcamentoRecusado", messageId, AGUARDAR_APROVACAO);
        if (saga != null) {
            cancelarPorDecisao(saga, "Orçamento recusado pelo cliente", MotivoLiberacao.ORCAMENTO_RECUSADO);
        }
    }

    public void orcamentoExpirado(UUID messageId, OrcamentoExpirado resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "OrcamentoExpirado", messageId, AGUARDAR_APROVACAO);
        if (saga != null) {
            cancelarPorDecisao(saga, "Orçamento expirado sem resposta do cliente", MotivoLiberacao.ORCAMENTO_EXPIRADO);
        }
    }

    public void cobrancaCriada(UUID messageId, CobrancaCriada resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "CobrancaCriada", messageId,
                SOLICITAR_PAGAMENTO, AGUARDAR_PAGAMENTO);
        if (saga == null) {
            return;
        }
        saga.getContexto().setPagamentoId(resposta.pagamentoId());
        if (saga.getPassoAtual() == SOLICITAR_PAGAMENTO) { // só a primeira cobrança muda a OS
            OrdemServico os = carregarOs(saga);
            aplicar(os, saga, os.aguardarPagamento(clock));
            saga.avancarPara(AGUARDAR_PAGAMENTO, null, clock);
        } else {
            tocar(saga);
        }
        sagaRepository.salvar(saga);
    }

    public void pagamentoRecusado(UUID messageId, PagamentoRecusado resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "PagamentoRecusado", messageId, AGUARDAR_PAGAMENTO);
        if (saga == null) {
            return;
        }
        OrdemServico os = carregarOs(saga);
        String motivo = limitar(resposta.motivo() == null ? "Pagamento recusado" : resposta.motivo());
        aplicar(os, saga, os.registrarPagamentoRecusado(motivo, clock));
        tocar(saga); // continua em AGUARDAR_PAGAMENTO: o atendente pede uma nova cobrança no Billing
        sagaRepository.salvar(saga);
    }

    public void pagamentoConfirmado(UUID messageId, PagamentoConfirmado resposta) {
        SagaInstancia saga = aceitar(resposta.osId(), "PagamentoConfirmado", messageId, AGUARDAR_PAGAMENTO);
        if (saga == null) {
            return;
        }
        OrdemServico os = carregarOs(saga);
        aplicar(os, saga, os.entregar(clock));
        saga.getContexto().setPagamentoId(resposta.pagamentoId());
        saga.avancarPara(FIM, null, clock);
        saga.mudarStatus(SagaStatus.CONCLUIDA, clock);
        BusinessEventLogger.sagaFinalizada(saga.getId(), saga.getOsId(), saga.getStatus());
        sagaRepository.salvar(saga);
    }

    // ---------------------------------------------------------------- ações REST

    /** {@code PATCH /cancelar}: até AGUARDAR_APROVACAO (inclusive). Depois disso, 422. */
    public OrdemServicoDetalhe cancelar(UUID osId, String motivo) {
        OrdemServico os = ordemServicoRepository.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de serviço não encontrada"));
        SagaInstancia saga = sagaRepository.buscarPorOsId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Saga da ordem de serviço não encontrada"));
        if (saga.estaEncerrada() || saga.getPassoAtual().ordinal() > AGUARDAR_APROVACAO.ordinal()) {
            throw new RegraNegocioException(
                    "Ordem de serviço só pode ser cancelada até o orçamento ser aprovado (passo atual: %s)"
                            .formatted(saga.getPassoAtual().name()));
        }
        ContextoSaga ctx = saga.getContexto();
        boolean liberar = ctx.isReservaAtiva() || saga.getPassoAtual() == RESERVAR_PECAS;
        UUID orcamentoId = ctx.getOrcamentoId();
        String motivoFinal = motivo == null || motivo.isBlank() ? MOTIVO_DESISTENCIA : limitar(motivo);

        cancelarOs(os, saga, motivoFinal);
        ctx.setDiagnosticoEmEspera(null);
        saga.avancarPara(FIM, ctx.isLiberacaoPendente() ? saga.getPrazoEm() : null, clock);
        if (orcamentoId != null) {
            enviar(saga, ComandosSaga.DESTINO_BILLING,
                    comandos.cancelarOrcamento(saga.getId(), osId, orcamentoId, motivoFinal));
        }
        if (liberar) {
            liberarReserva(saga, MotivoLiberacao.DESISTENCIA);
        }
        encerrarCancelamento(saga);
        return new OrdemServicoDetalhe(os, sagaRepository.salvar(saga));
    }

    /** {@code PATCH /revisar-orcamento}: só em AGUARDAR_APROVACAO. Depois do pivô, 422. */
    public OrdemServicoDetalhe revisarOrcamento(UUID osId, String motivo) {
        OrdemServico os = ordemServicoRepository.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de serviço não encontrada"));
        SagaInstancia saga = sagaRepository.buscarPorOsId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Saga da ordem de serviço não encontrada"));
        if (saga.estaEncerrada() || saga.getPassoAtual() != AGUARDAR_APROVACAO) {
            throw new RegraNegocioException(
                    "O orçamento só pode ser revisado enquanto aguarda a aprovação (passo atual: %s)"
                            .formatted(saga.getPassoAtual().name()));
        }
        ContextoSaga ctx = saga.getContexto();
        String motivoFinal = motivo == null || motivo.isBlank() ? MOTIVO_REVISAO : limitar(motivo);
        UUID orcamentoId = ctx.getOrcamentoId();

        aplicar(os, saga, os.voltarParaDiagnostico(motivoFinal, clock));
        saga.avancarPara(AGUARDAR_DIAGNOSTICO, null, clock);
        if (orcamentoId != null) {
            enviar(saga, ComandosSaga.DESTINO_BILLING,
                    comandos.cancelarOrcamento(saga.getId(), osId, orcamentoId, motivoFinal));
            ctx.setOrcamentoId(null); // já cancelado; um cancelamento posterior não deve repetir
        }
        if (ctx.isReservaAtiva()) {
            liberarReserva(saga, MotivoLiberacao.REVISAO);
        }
        return new OrdemServicoDetalhe(os, sagaRepository.salvar(saga));
    }

    // ---------------------------------------------------------------- internos

    /** Recusa ou expiração do orçamento: a OS vira CANCELADA na hora e a reserva é desfeita (F4-e). */
    private void cancelarPorDecisao(SagaInstancia saga, String motivo, MotivoLiberacao motivoLiberacao) {
        OrdemServico os = carregarOs(saga);
        cancelarOs(os, saga, motivo);
        saga.avancarPara(FIM, null, clock);
        if (saga.getContexto().isReservaAtiva()) {
            liberarReserva(saga, motivoLiberacao);
        }
        encerrarCancelamento(saga);
        sagaRepository.salvar(saga);
    }

    private void encerrarCancelamento(SagaInstancia saga) {
        boolean pendente = saga.getContexto().isLiberacaoPendente();
        saga.mudarStatus(pendente ? SagaStatus.COMPENSANDO : SagaStatus.COMPENSADA, clock);
        if (!pendente) {
            BusinessEventLogger.sagaFinalizada(saga.getId(), saga.getOsId(), saga.getStatus());
        }
    }

    /** Sem peças no diagnóstico a reserva é pulada (F4-g). */
    private void seguirDiagnostico(SagaInstancia saga, List<ItemDiagnostico> itens) {
        ContextoSaga ctx = saga.getContexto();
        ctx.setItens(itens);
        ctx.setDiagnosticoEmEspera(null);
        List<PecaReservada> pecas = agregarPecas(itens);
        if (pecas.isEmpty()) {
            OrdemServico os = carregarOs(saga);
            saga.avancarPara(GERAR_ORCAMENTO, prazoComando(), clock);
            enviar(saga, ComandosSaga.DESTINO_BILLING,
                    comandos.gerarOrcamento(saga.getId(), os.getId(), os.getClienteId(), itens));
        } else {
            ctx.setPecasReservadas(pecas);
            saga.avancarPara(RESERVAR_PECAS, prazoComando(), clock);
            enviar(saga, ComandosSaga.DESTINO_EXECUCAO, comandos.reservarPecas(saga.getId(), saga.getOsId(), pecas));
        }
    }

    private static List<PecaReservada> agregarPecas(List<ItemDiagnostico> itens) {
        Map<UUID, Integer> total = new LinkedHashMap<>();
        for (ItemDiagnostico item : itens) {
            for (var peca : ComandosSaga.pecasDe(item)) {
                total.merge(peca.pecaId(), peca.quantidade(), Integer::sum);
            }
        }
        return total.entrySet().stream().map(e -> new PecaReservada(e.getKey(), e.getValue())).toList();
    }

    /** Envia o LiberarReservaPecas e passa o prazo da instância a ser o da liberação (nenhum passo tem prazo próprio). */
    private void liberarReserva(SagaInstancia saga, MotivoLiberacao motivo) {
        ContextoSaga ctx = saga.getContexto();
        ctx.setLiberacaoPendente(true);
        ctx.setReservaAtiva(false);
        enviar(saga, ComandosSaga.DESTINO_EXECUCAO, comandos.liberarReservaPecas(saga.getId(), saga.getOsId(), motivo));
        saga.definirPrazo(prazoComando(), clock);
    }

    private void cancelarOs(OrdemServico os, SagaInstancia saga, String motivo) {
        MudancaSituacao mudanca = os.cancelar(motivo, clock);
        ordemServicoRepository.salvar(os);
        ordemServicoRepository.registrarHistorico(os.getId(), mudanca);
        outbox.registrar(EventosOrdemServico.DESTINO, eventos.ordemServicoCancelada(saga.getId(), os, mudanca));
        BusinessEventLogger.situacaoOrdemServicoAlterada(os.getId(), mudanca.de(), mudanca.para());
        BusinessEventLogger.ordemServicoCancelada(os.getId(), motivo);
    }

    private void aplicar(OrdemServico os, SagaInstancia saga, MudancaSituacao mudanca) {
        ordemServicoRepository.salvar(os);
        ordemServicoRepository.registrarHistorico(os.getId(), mudanca);
        outbox.registrar(EventosOrdemServico.DESTINO, eventos.situacaoAlterada(saga.getId(), os, mudanca));
        BusinessEventLogger.situacaoOrdemServicoAlterada(os.getId(), mudanca.de(), mudanca.para());
    }

    private void enviar(SagaInstancia saga, String destino, MensagemEnvelope comando) {
        outbox.registrar(destino, comando);
        saga.getContexto().setUltimoComando(new ComandoEnviado(comando.type(), comando.messageId()));
        registrarPasso(saga, comando.type(), DirecaoPasso.ENVIADO, comando.messageId());
    }

    /** Acha a Saga e confere o passo; registra o RECEBIDO. Devolve {@code null} se a resposta foi tratada (descarte). */
    private SagaInstancia aceitar(UUID osId, String tipo, UUID messageId, PassoSaga... esperados) {
        Optional<SagaInstancia> existente = sagaRepository.buscarPorOsId(osId);
        if (existente.isEmpty()) {
            sagaInexistente(osId, tipo);
            return null;
        }
        SagaInstancia saga = existente.get();
        if (!aguarda(saga, esperados)) {
            descartar(saga, tipo, messageId);
            return null;
        }
        receber(saga, tipo, messageId);
        return saga;
    }

    private static boolean aguarda(SagaInstancia saga, PassoSaga... esperados) {
        if (saga.estaEncerrada()) {
            return false;
        }
        for (PassoSaga esperado : esperados) {
            if (saga.getPassoAtual() == esperado) {
                return true;
            }
        }
        return false;
    }

    private static boolean cancelada(SagaInstancia saga) {
        return saga.getStatus() == SagaStatus.COMPENSANDO || saga.getStatus() == SagaStatus.COMPENSADA;
    }

    private void receber(SagaInstancia saga, String tipo, UUID messageId) {
        registrarPasso(saga, tipo, DirecaoPasso.RECEBIDO, messageId);
    }

    private void descartar(SagaInstancia saga, String tipo, UUID messageId) {
        log.atInfo().addKeyValue("event", "resposta_fora_do_passo")
                .addKeyValue("sagaId", saga.getId()).addKeyValue("osId", saga.getOsId())
                .addKeyValue("tipo", tipo).addKeyValue("passo", saga.getPassoAtual())
                .addKeyValue("status", saga.getStatus())
                .log("Resposta fora do passo; descartada");
        registrarPasso(saga, tipo, DirecaoPasso.DESCARTADA, messageId);
    }

    private void sagaInexistente(UUID osId, String tipo) {
        log.atWarn().addKeyValue("event", "saga_inexistente")
                .addKeyValue("osId", osId).addKeyValue("tipo", tipo)
                .log("Mensagem para uma OS sem Saga; concluída sem efeito");
    }

    private void registrarPasso(SagaInstancia saga, String tipo, DirecaoPasso direcao, UUID messageId) {
        sagaRepository.registrarPasso(SagaPasso.novo(saga.getId(), saga.getPassoAtual(), tipo, direcao, messageId,
                Map.of(), clock.instant()));
        BusinessEventLogger.sagaPasso(saga.getId(), saga.getOsId(), saga.getPassoAtual(), tipo, direcao.name());
    }

    private OrdemServico carregarOs(SagaInstancia saga) {
        return ordemServicoRepository.buscarPorId(saga.getOsId()).orElseThrow(() ->
                new IllegalStateException("Saga %s sem a ordem de serviço %s".formatted(saga.getId(), saga.getOsId())));
    }

    /** Marca a instância como alterada (atualizadaEm), para o {@code @Version} avançar mesmo sem mudar de passo. */
    private void tocar(SagaInstancia saga) {
        saga.definirPrazo(saga.getPrazoEm(), clock);
    }

    private Instant prazoComando() {
        return clock.instant().plus(timeouts.comando());
    }

    private static String motivoEstoque(ReservaPecasRecusada resposta) {
        StringBuilder motivo = new StringBuilder("peças sem estoque: ");
        List<ReservaPecasRecusada.Faltante> faltantes = resposta.faltantes() == null ? List.of() : resposta.faltantes();
        for (int i = 0; i < faltantes.size(); i++) {
            var f = faltantes.get(i);
            motivo.append(i > 0 ? "; " : "").append("peça %s (solicitada %d, disponível %d)"
                    .formatted(f.pecaId(), f.solicitada(), f.disponivel()));
        }
        return motivo.toString();
    }

    private static String limitar(String texto) {
        return texto.length() <= LIMITE_MOTIVO ? texto : texto.substring(0, LIMITE_MOTIVO);
    }
}
