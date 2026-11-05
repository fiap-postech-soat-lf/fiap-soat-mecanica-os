package br.com.fiap.soat.mecanica.os.adapters.out.persistence.saga;

import br.com.fiap.soat.mecanica.os.domain.saga.DirecaoPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "saga_passo")
@Getter
@Setter
@NoArgsConstructor
public class SagaPassoEntity {

    @Id
    private UUID id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "passo", nullable = false)
    private PassoSaga passo;

    @Column(name = "tipo_mensagem")
    private String tipoMensagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "direcao", nullable = false)
    private DirecaoPasso direcao;

    @Column(name = "message_id")
    private UUID messageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalhe", columnDefinition = "jsonb")
    private Map<String, Object> detalhe;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;
}
