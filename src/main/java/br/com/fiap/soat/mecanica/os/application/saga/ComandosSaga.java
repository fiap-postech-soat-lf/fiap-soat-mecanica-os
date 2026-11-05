package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ComandoEnviado;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ItemDiagnostico;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.PecaItem;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.PecaReservada;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Monta os 7 comandos da Saga (envelope com sagaId e osId). Quem registra na outbox é o orquestrador. */
@Component
@RequiredArgsConstructor
public class ComandosSaga {

    public static final String DESTINO_EXECUCAO = "execucao-comandos";
    public static final String DESTINO_BILLING = "billing-comandos";

    /** Valores aceitos pelo schema do LiberarReservaPecas. */
    public enum MotivoLiberacao {
        ORCAMENTO_FALHOU, ORCAMENTO_RECUSADO, ORCAMENTO_EXPIRADO, DESISTENCIA, REVISAO
    }

    private final MensagemFactory fabrica;

    public MensagemEnvelope abrirDiagnostico(UUID sagaId, UUID osId, UUID veiculoId, String placa) {
        return fabrica.criar("AbrirDiagnostico", 1, sagaId, osId,
                Map.of("osId", osId, "veiculoId", veiculoId, "placa", placa));
    }

    public MensagemEnvelope reservarPecas(UUID sagaId, UUID osId, List<PecaReservada> pecas) {
        return fabrica.criar("ReservarPecas", 1, sagaId, osId, Map.of("osId", osId, "pecas", pecas));
    }

    public MensagemEnvelope gerarOrcamento(UUID sagaId, UUID osId, UUID clienteId, List<ItemDiagnostico> itens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("osId", osId);
        payload.put("clienteId", clienteId);
        payload.put("itens", itens);
        payload.put("valorTotal", valorTotal(itens));
        return fabrica.criar("GerarOrcamento", 1, sagaId, osId, payload);
    }

    public MensagemEnvelope liberarReservaPecas(UUID sagaId, UUID osId, MotivoLiberacao motivo) {
        return fabrica.criar("LiberarReservaPecas", 1, sagaId, osId, Map.of("osId", osId, "motivo", motivo.name()));
    }

    public MensagemEnvelope iniciarExecucao(UUID sagaId, UUID osId) {
        return fabrica.criar("IniciarExecucao", 1, sagaId, osId, Map.of("osId", osId));
    }

    public MensagemEnvelope solicitarPagamento(UUID sagaId, UUID osId, UUID orcamentoId, BigDecimal valor) {
        return fabrica.criar("SolicitarPagamento", 1, sagaId, osId,
                Map.of("osId", osId, "orcamentoId", orcamentoId, "valor", valor));
    }

    public MensagemEnvelope cancelarOrcamento(UUID sagaId, UUID osId, UUID orcamentoId, String motivo) {
        return fabrica.criar("CancelarOrcamento", 1, sagaId, osId,
                Map.of("osId", osId, "orcamentoId", orcamentoId, "motivo", motivo));
    }

    /** Para onde vai cada comando. */
    public static String destinoDe(String tipo) {
        return switch (tipo) {
            case "AbrirDiagnostico", "ReservarPecas", "LiberarReservaPecas", "IniciarExecucao" -> DESTINO_EXECUCAO;
            default -> DESTINO_BILLING;
        };
    }

    /** Mesmo comando num envelope novo: messageId novo e causationId = o comando anterior (F4-h). */
    public MensagemEnvelope reenviar(ComandoEnviado original, UUID sagaId, UUID osId) {
        MensagemEnvelope novo = fabrica.criar(original.tipo(), 1, sagaId, osId, original.payload());
        return new MensagemEnvelope(novo.messageId(), novo.type(), novo.version(), novo.sagaId(), novo.osId(),
                novo.correlationId(), original.messageId(), novo.occurredAt(), novo.traceparent(), novo.payload());
    }

    /** Soma de {@code precoMaoDeObra} + {@code quantidade x valorUnitario} de todas as peças. */
    public static BigDecimal valorTotal(List<ItemDiagnostico> itens) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemDiagnostico item : itens) {
            total = total.add(item.precoMaoDeObra());
            for (var peca : pecasDe(item)) {
                total = total.add(peca.valorUnitario().multiply(BigDecimal.valueOf(peca.quantidade())));
            }
        }
        return total;
    }

    /** Peças do item, nunca nulo. */
    public static List<PecaItem> pecasDe(ItemDiagnostico item) {
        return item.pecas() == null ? List.of() : item.pecas();
    }
}
