package br.com.fiap.soat.mecanica.os.domain.saga;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estado auxiliar da Saga, guardado como JSONB. Os itens seguem o formato do comando GerarOrcamento.
 * O equals e obrigatorio: o Hibernate compara o valor com a copia que guardou na leitura e, sem ele, trataria
 * a coluna como alterada em todo flush (UPDATE a toa, com a versao incrementada).
 */
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
public class ContextoSaga {

    /** Itens do ultimo DiagnosticoConcluido. */
    private List<ItemDiagnostico> itens = new ArrayList<>();
    /** Pecas enviadas no ultimo ReservarPecas. */
    private List<PecaReservada> pecasReservadas = new ArrayList<>();
    /** Ha reserva de pecas ativa no Execucao (a compensar se a OS for cancelada). */
    private boolean reservaAtiva;
    /** Um LiberarReservaPecas foi enviado e a ReservaPecasLiberada ainda nao chegou (F4-g). */
    private boolean liberacaoPendente;
    /** Novo diagnostico guardado ate a liberacao pendente terminar (F4-g); nulo quando nao ha. */
    private List<ItemDiagnostico> diagnosticoEmEspera;
    private UUID orcamentoId;
    private UUID pagamentoId;
    /** Ultimo comando enviado, para o reenvio por timeout (F4-h). */
    private ComandoEnviado ultimoComando;

    public record ItemDiagnostico(UUID servicoId, String descricao, BigDecimal precoMaoDeObra,
                                  List<PecaItem> pecas) {
    }

    public record PecaItem(UUID pecaId, String descricao, int quantidade, BigDecimal valorUnitario) {
    }

    public record PecaReservada(UUID pecaId, int quantidade) {
    }

    public record ComandoEnviado(String tipo, UUID messageId) {
    }
}
