CREATE TABLE saga_instancia
(
    id            UUID PRIMARY KEY,
    os_id         UUID        NOT NULL UNIQUE REFERENCES ordem_servicos (id),
    passo_atual   VARCHAR(40) NOT NULL,
    status        VARCHAR(20) NOT NULL
        CONSTRAINT ck_saga_instancia_status CHECK (status IN
            ('INICIADA', 'COMPENSANDO', 'COMPENSADA', 'CONCLUIDA', 'FALHOU')),
    contexto      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    tentativas    INT         NOT NULL DEFAULT 0,
    prazo_em      TIMESTAMPTZ,
    versao        BIGINT      NOT NULL DEFAULT 0,
    criada_em     TIMESTAMPTZ NOT NULL,
    atualizada_em TIMESTAMPTZ NOT NULL
);

-- Varredura do SagaTimeoutScheduler (F4-i): so as instancias com prazo.
CREATE INDEX ix_saga_instancia_prazo ON saga_instancia (prazo_em) WHERE prazo_em IS NOT NULL;

CREATE TABLE saga_passo
(
    id            UUID PRIMARY KEY,
    saga_id       UUID        NOT NULL REFERENCES saga_instancia (id),
    passo         VARCHAR(40) NOT NULL,
    tipo_mensagem VARCHAR(100),
    direcao       VARCHAR(20) NOT NULL
        CONSTRAINT ck_saga_passo_direcao CHECK (direcao IN ('ENVIADO', 'RECEBIDO', 'TIMEOUT', 'DESCARTADA')),
    message_id    UUID,
    detalhe       JSONB,
    ocorrido_em   TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_saga_passo_saga ON saga_passo (saga_id, ocorrido_em);
