-- Sem as FKs para veiculo e usuario: eles moram no Identity (o OS guarda so o snapshot, F4-d).
-- Sem a coluna status: o soft delete fica so nos cadastros (decisao fixa #10, F4-l).
CREATE TABLE ordem_servicos
(
    id                        UUID PRIMARY KEY,
    situacao                  VARCHAR(50)    NOT NULL
        CONSTRAINT ck_ordem_servicos_situacao CHECK (situacao IN (
            'RECEBIDA', 'EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'EM_EXECUCAO',
            'FINALIZADA', 'AGUARDANDO_PAGAMENTO', 'ENTREGUE', 'CANCELADA')),
    data_recebida             TIMESTAMPTZ    NOT NULL,
    data_diagnostico          TIMESTAMPTZ,
    data_aguardando_aprovacao TIMESTAMPTZ,
    data_execucao             TIMESTAMPTZ,
    data_finalizada           TIMESTAMPTZ,
    data_aguardando_pagamento TIMESTAMPTZ,
    data_entregue             TIMESTAMPTZ,
    data_cancelada            TIMESTAMPTZ,
    motivo_cancelamento       TEXT,
    pago                      BOOLEAN,
    valor_total               NUMERIC(10, 2) NOT NULL,
    observacao                TEXT,
    veiculo_id                UUID           NOT NULL,
    cliente_id                UUID           NOT NULL,
    placa                     VARCHAR(7)     NOT NULL,
    usuario_abertura_id       UUID           NOT NULL
);

CREATE INDEX idx_ordem_servicos_placa ON ordem_servicos (placa);
CREATE INDEX idx_ordem_servicos_cliente_id ON ordem_servicos (cliente_id);
CREATE INDEX idx_ordem_servicos_veiculo_id ON ordem_servicos (veiculo_id);
