package br.com.fiap.soat.mecanica.os.adapters.out.persistence.saga;

import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_instancia")
@Getter
@Setter
@NoArgsConstructor
public class SagaInstanciaEntity {

    /** Gerado pelo dominio: e o sagaId dos envelopes. */
    @Id
    private UUID id;

    @Column(name = "os_id", nullable = false, unique = true)
    private UUID osId;

    @Enumerated(EnumType.STRING)
    @Column(name = "passo_atual", nullable = false)
    private PassoSaga passoAtual;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SagaStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto", nullable = false, columnDefinition = "jsonb")
    private ContextoSaga contexto;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "prazo_em")
    private Instant prazoEm;

    /** Controle otimista: duas respostas concorrentes na mesma Saga nao se sobrescrevem. */
    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;
}
