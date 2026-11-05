package br.com.fiap.soat.mecanica.os.domain.saga;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static org.assertj.core.api.Assertions.assertThat;

class SagaInstanciaTest {

    private static final Duration PRAZO = Duration.ofMinutes(2);

    @Test
    void iniciaNoPassoDoDiagnosticoComPrazoDoComando() {
        UUID osId = UUID.randomUUID();

        SagaInstancia saga = SagaInstancia.iniciar(osId, PRAZO, RELOGIO);

        assertThat(saga.getId()).isNotNull();
        assertThat(saga.getOsId()).isEqualTo(osId);
        assertThat(saga.getPassoAtual()).isEqualTo(PassoSaga.ABRIR_DIAGNOSTICO);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.INICIADA);
        assertThat(saga.getTentativas()).isZero();
        assertThat(saga.getPrazoEm()).isEqualTo(AGORA.plus(PRAZO));
        assertThat(saga.getVersao()).isNull();
        assertThat(saga.getCriadaEm()).isEqualTo(AGORA);
        assertThat(saga.getContexto().isReservaAtiva()).isFalse();
        assertThat(saga.getContexto().isLiberacaoPendente()).isFalse();
        assertThat(saga.estaEncerrada()).isFalse();
    }

    @Test
    void avancarTrocaPassoZeraTentativasETrocaPrazo() {
        SagaInstancia saga = SagaInstancia.iniciar(UUID.randomUUID(), PRAZO, RELOGIO);
        saga.registrarTentativa(AGORA.plusSeconds(240), RELOGIO);
        Instant depois = AGORA.plusSeconds(10);

        saga.avancarPara(PassoSaga.AGUARDAR_DIAGNOSTICO, null, relogioEm(depois));

        assertThat(saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
        assertThat(saga.getTentativas()).isZero();
        assertThat(saga.getPrazoEm()).isNull();
        assertThat(saga.getAtualizadaEm()).isEqualTo(depois);
    }

    @Test
    void registrarTentativaContaEAgendaOProximoPrazo() {
        SagaInstancia saga = SagaInstancia.iniciar(UUID.randomUUID(), PRAZO, RELOGIO);
        Instant proximo = AGORA.plusSeconds(240);

        saga.registrarTentativa(proximo, RELOGIO);
        saga.registrarTentativa(proximo, RELOGIO);

        assertThat(saga.getTentativas()).isEqualTo(2);
        assertThat(saga.getPrazoEm()).isEqualTo(proximo);
    }

    @Test
    void definirPrazoSoMudaOPrazo() {
        SagaInstancia saga = SagaInstancia.iniciar(UUID.randomUUID(), PRAZO, RELOGIO);

        saga.definirPrazo(AGORA.plusSeconds(999), RELOGIO);

        assertThat(saga.getPrazoEm()).isEqualTo(AGORA.plusSeconds(999));
        assertThat(saga.getPassoAtual()).isEqualTo(PassoSaga.ABRIR_DIAGNOSTICO);
    }

    @Test
    void statusTerminaisEncerramASaga() {
        SagaInstancia saga = SagaInstancia.iniciar(UUID.randomUUID(), PRAZO, RELOGIO);

        saga.mudarStatus(SagaStatus.COMPENSANDO, RELOGIO);
        assertThat(saga.estaEncerrada()).isFalse();

        for (SagaStatus terminal : new SagaStatus[]{SagaStatus.COMPENSADA, SagaStatus.CONCLUIDA, SagaStatus.FALHOU}) {
            saga.mudarStatus(terminal, RELOGIO);
            assertThat(saga.estaEncerrada()).as(terminal.name()).isTrue();
        }
    }

    @Test
    void reconstruirSemContextoUsaContextoVazio() {
        SagaInstancia saga = SagaInstancia.reconstruir(UUID.randomUUID(), UUID.randomUUID(), PassoSaga.FIM,
                SagaStatus.CONCLUIDA, null, 1, null, 7L, AGORA, AGORA);

        assertThat(saga.getContexto()).isNotNull();
        assertThat(saga.getVersao()).isEqualTo(7L);
        assertThat(saga.getTentativas()).isEqualTo(1);
    }

    @Test
    void passoNovoGeraId() {
        SagaPasso passo = SagaPasso.novo(UUID.randomUUID(), PassoSaga.ABRIR_DIAGNOSTICO, "AbrirDiagnostico",
                DirecaoPasso.ENVIADO, UUID.randomUUID(), null, AGORA);

        assertThat(passo.id()).isNotNull();
        assertThat(passo.direcao()).isEqualTo(DirecaoPasso.ENVIADO);
    }

    private static Clock relogioEm(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }
}
