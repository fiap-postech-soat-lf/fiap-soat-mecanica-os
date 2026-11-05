package br.com.fiap.soat.mecanica.os.support;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Dados de teste dos testes unitarios (ids do seed do Identity). */
public final class TestDataFactory {

    public static final Instant AGORA = Instant.parse("2026-11-06T12:00:00Z");
    public static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    public static final UUID CLIENTE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID VEICULO_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final UUID ATENDENTE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final String PLACA = "ABC1D23";

    private TestDataFactory() {
    }

    /** OS recem-aberta (RECEBIDA, sem id), como a abertura a cria. */
    public static OrdemServico osNova() {
        return new OrdemServico("Barulho no motor", VEICULO_ID, CLIENTE_ID, PLACA, ATENDENTE_ID, RELOGIO);
    }

    /** OS ja persistida, na situacao pedida. */
    public static OrdemServico osEm(SituacaoOrdemServico situacao) {
        return OrdemServico.reconstruir(UUID.randomUUID(), situacao, AGORA.minusSeconds(3600), null, null, null,
                null, null, null, null, null, null, BigDecimal.ZERO, "Barulho no motor", VEICULO_ID, CLIENTE_ID,
                PLACA, ATENDENTE_ID);
    }
}
