-- Catalogo de servicos do seed (os mesmos UUIDs do V9 do monolito). As OS do V9 nao entram (F4-l):
-- sem Saga, o Execucao e o Billing nao as conheceriam; o BDD, a demo e a F7 criam as OS pela API.
INSERT INTO servicos (id, status, nome, descricao)
VALUES ('77777777-7777-7777-7777-777777777777', 'ATIVO', 'Troca de oleo', 'Substituicao de oleo e filtro'),
       ('77777777-7777-7777-7777-777777777778', 'ATIVO', 'Revisao de freios',
        'Inspecao e manutencao do sistema de freios');
