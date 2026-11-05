package br.com.fiap.soat.mecanica.os.domain.saga;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Uma linha da trilha de auditoria da Saga: comando enviado, resposta recebida, timeout ou descarte. */
public record SagaPasso(UUID id, UUID sagaId, PassoSaga passo, String tipoMensagem, DirecaoPasso direcao,
                        UUID messageId, Map<String, Object> detalhe, Instant ocorridoEm) {

    public static SagaPasso novo(UUID sagaId, PassoSaga passo, String tipoMensagem, DirecaoPasso direcao,
                                 UUID messageId, Map<String, Object> detalhe, Instant ocorridoEm) {
        return new SagaPasso(UUID.randomUUID(), sagaId, passo, tipoMensagem, direcao, messageId, detalhe, ocorridoEm);
    }
}
