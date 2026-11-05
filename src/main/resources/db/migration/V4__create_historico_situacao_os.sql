CREATE TABLE historico_situacao_os
(
    id          UUID PRIMARY KEY,
    os_id       UUID         NOT NULL REFERENCES ordem_servicos (id),
    de          VARCHAR(50),
    para        VARCHAR(50)  NOT NULL,
    motivo      TEXT,
    ocorrida_em TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_historico_situacao_os_os_id_ocorrida_em ON historico_situacao_os (os_id, ocorrida_em);
