package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ComandoEnviado;
import br.com.fiap.soat.mecanica.os.domain.saga.DirecaoPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import br.com.fiap.soat.mecanica.os.support.CenarioSaga;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_APROVACAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.CANCELADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_DIAGNOSTICO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_EXECUCAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.FINALIZADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.RECEBIDA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Uma linha da tabela 6.3 por teste, com o Clock avançado além do prazo da Saga (que nasce em {@code AGORA}). */
class TratarTimeoutsSagaUseCaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID ORCAMENTO = UUID.randomUUID();

    private static Clock aposMinutos(long minutos) {
        return Clock.fixed(AGORA.plus(Duration.ofMinutes(minutos)), ZoneOffset.UTC);
    }

    /** Cenário com a Saga vencida: prazo de 2 min a partir de AGORA e o relógio 3 min depois. */
    private static CenarioSaga vencida(SituacaoOrdemServico os, PassoSaga passo, String ultimoComando) {
        CenarioSaga c = CenarioSaga.em(os, passo, aposMinutos(3));
        c.saga.definirPrazo(AGORA.plusSeconds(120), RELOGIO);
        if (ultimoComando != null) {
            c.saga.getContexto().setUltimoComando(new ComandoEnviado(ultimoComando, UUID.randomUUID(),
                    MAPPER.valueToTree(Map.of("osId", c.osId()))));
        }
        when(c.sagaRepository.travarVencidas(any(), anyInt())).thenAnswer(inv ->
                c.saga.getPrazoEm() != null && !c.saga.getPrazoEm().isAfter(inv.getArgument(0))
                        ? List.of(c.saga) : List.of());
        return c;
    }

    private static int rodar(CenarioSaga c) {
        return new TratarTimeoutsSagaUseCase(c.sagaRepository, c.orquestrador, c.clock).executar();
    }

    private static void esgotar(CenarioSaga c) {
        // dois reenvios já feitos: o terceiro timeout é o último (3 envios no total)
        c.saga.registrarTentativa(c.saga.getPrazoEm(), RELOGIO);
        c.saga.registrarTentativa(c.saga.getPrazoEm(), RELOGIO);
    }

    private static String motivo(CenarioSaga.Enviada e) {
        return e.envelope().payload().path("motivo").asText();
    }

    @Test
    @DisplayName("sem prazo vencido não faz nada")
    void naoVencida() {
        var c = CenarioSaga.em(RECEBIDA, PassoSaga.ABRIR_DIAGNOSTICO, aposMinutos(1));
        c.saga.definirPrazo(AGORA.plusSeconds(120), RELOGIO);
        when(c.sagaRepository.travarVencidas(any(), anyInt())).thenReturn(List.of());

        assertThat(rodar(c)).isZero();
        assertThat(c.enviadas).isEmpty();
        assertThat(c.passos).isEmpty();
    }

    @Test
    @DisplayName("ABRIR_DIAGNOSTICO: reenvia com messageId novo e causationId = comando anterior (F4-h)")
    void reenviaAbrirDiagnostico() {
        var c = vencida(RECEBIDA, PassoSaga.ABRIR_DIAGNOSTICO, "AbrirDiagnostico");
        UUID anterior = c.saga.getContexto().getUltimoComando().messageId();

        assertThat(rodar(c)).isEqualTo(1);

        assertThat(c.tipos()).containsExactly("AbrirDiagnostico");
        var envelope = c.enviadas.get(0).envelope();
        assertThat(c.enviadas.get(0).destino()).isEqualTo("execucao-comandos");
        assertThat(envelope.messageId()).isNotEqualTo(anterior);
        assertThat(envelope.causationId()).isEqualTo(anterior);
        assertThat(envelope.sagaId()).isEqualTo(c.saga.getId());
        assertThat(c.saga.getTentativas()).isEqualTo(1);
        assertThat(c.saga.getPrazoEm()).isEqualTo(AGORA.plusSeconds(180 + 120));
        assertThat(c.saga.getContexto().getUltimoComando().messageId()).isEqualTo(envelope.messageId());
        assertThat(c.direcoes()).containsExactly(DirecaoPasso.TIMEOUT, DirecaoPasso.ENVIADO);
    }

    @Test
    @DisplayName("ABRIR_DIAGNOSTICO esgotado: OS CANCELADA (Execução não respondeu) e Saga FALHOU")
    void esgotaAbrirDiagnostico() {
        var c = vencida(RECEBIDA, PassoSaga.ABRIR_DIAGNOSTICO, "AbrirDiagnostico");
        esgotar(c);

        rodar(c);

        assertThat(c.os.getSituacao()).isEqualTo(CANCELADA);
        assertThat(c.os.getMotivoCancelamento()).isEqualTo("Execução não respondeu");
        assertThat(c.tipos()).containsExactly("OrdemServicoCancelada");
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.FALHOU);
        assertThat(c.saga.getPrazoEm()).isNull();
        assertThat(c.registro.counter("saga.falhou", "passo", "ABRIR_DIAGNOSTICO").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("RESERVAR_PECAS: reenvia o ReservarPecas")
    void reenviaReservarPecas() {
        var c = vencida(EM_DIAGNOSTICO, PassoSaga.RESERVAR_PECAS, "ReservarPecas");

        rodar(c);

        assertThat(c.tipos()).containsExactly("ReservarPecas");
        assertThat(c.saga.getTentativas()).isEqualTo(1);
    }

    @Test
    @DisplayName("RESERVAR_PECAS esgotado: libera por garantia, a OS segue em diagnóstico com o motivo")
    void esgotaReservarPecas() {
        var c = vencida(EM_DIAGNOSTICO, PassoSaga.RESERVAR_PECAS, "ReservarPecas");
        esgotar(c);

        rodar(c);

        assertThat(c.tipos()).containsExactlyInAnyOrder("SituacaoOrdemServicoAlterada", "LiberarReservaPecas");
        assertThat(c.os.getSituacao()).isEqualTo(EM_DIAGNOSTICO);
        assertThat(motivo(c.primeira("SituacaoOrdemServicoAlterada"))).contains("reserva de peças");
        assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
        assertThat(c.saga.getContexto().isLiberacaoPendente()).isTrue();
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.INICIADA);
    }

    @Test
    @DisplayName("GERAR_ORCAMENTO: reenvia o GerarOrcamento para o Billing")
    void reenviaGerarOrcamento() {
        var c = vencida(EM_DIAGNOSTICO, PassoSaga.GERAR_ORCAMENTO, "GerarOrcamento");

        rodar(c);

        assertThat(c.tipos()).containsExactly("GerarOrcamento");
        assertThat(c.enviadas.get(0).destino()).isEqualTo("billing-comandos");
    }

    @Test
    @DisplayName("GERAR_ORCAMENTO esgotado: mesmo efeito do OrcamentoFalhou, com liberação se havia reserva")
    void esgotaGerarOrcamento() {
        var c = vencida(EM_DIAGNOSTICO, PassoSaga.GERAR_ORCAMENTO, "GerarOrcamento");
        c.saga.getContexto().setReservaAtiva(true);
        esgotar(c);

        rodar(c);

        assertThat(c.tipos()).containsExactlyInAnyOrder("SituacaoOrdemServicoAlterada", "LiberarReservaPecas");
        assertThat(motivo(c.primeira("LiberarReservaPecas"))).isEqualTo("ORCAMENTO_FALHOU");
        assertThat(c.os.getSituacao()).isEqualTo(EM_DIAGNOSTICO);
        assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
    }

    @Test
    @DisplayName("AGUARDAR_APROVACAO vencido: expira como o OrcamentoExpirado, mais CancelarOrcamento")
    void expiraAprovacao() {
        var c = vencida(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO, null);
        c.saga.getContexto().setReservaAtiva(true);
        c.saga.getContexto().setOrcamentoId(ORCAMENTO);

        rodar(c);

        assertThat(c.os.getSituacao()).isEqualTo(CANCELADA);
        assertThat(c.tipos()).containsExactlyInAnyOrder("OrdemServicoCancelada", "CancelarOrcamento",
                "LiberarReservaPecas");
        assertThat(motivo(c.primeira("LiberarReservaPecas"))).isEqualTo("ORCAMENTO_EXPIRADO");
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSANDO);
        assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.FIM);
    }

    @Test
    @DisplayName("liberação pendente: reenvia o LiberarReservaPecas com o mesmo motivo")
    void reenviaLiberacao() {
        var c = vencida(CANCELADA, PassoSaga.FIM, null);
        c.saga.mudarStatus(SagaStatus.COMPENSANDO, RELOGIO);
        c.saga.getContexto().setLiberacaoPendente(true);
        c.saga.getContexto().setUltimoComando(new ComandoEnviado("LiberarReservaPecas", UUID.randomUUID(),
                MAPPER.valueToTree(Map.of("osId", c.osId(), "motivo", "DESISTENCIA"))));

        rodar(c);

        assertThat(c.tipos()).containsExactly("LiberarReservaPecas");
        assertThat(motivo(c.enviadas.get(0))).isEqualTo("DESISTENCIA");
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSANDO);
    }

    @Test
    @DisplayName("liberação pendente esgotada: Saga FALHOU e a OS não muda")
    void esgotaLiberacao() {
        var c = vencida(CANCELADA, PassoSaga.FIM, null);
        c.saga.mudarStatus(SagaStatus.COMPENSANDO, RELOGIO);
        c.saga.getContexto().setLiberacaoPendente(true);
        c.saga.getContexto().setUltimoComando(new ComandoEnviado("LiberarReservaPecas", UUID.randomUUID(),
                MAPPER.valueToTree(Map.of("osId", c.osId(), "motivo", "DESISTENCIA"))));
        esgotar(c);

        rodar(c);

        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.FALHOU);
        assertThat(c.os.getSituacao()).isEqualTo(CANCELADA);
        assertThat(c.enviadas).isEmpty();
        assertThat(c.registro.counter("saga.falhou", "passo", "FIM").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("INICIAR_EXECUCAO depois do pivô: reenvia com backoff de 4 e 8 min e, esgotado, só falha a Saga")
    void backoffDepoisDoPivo() {
        var c = vencida(AGUARDANDO_APROVACAO, PassoSaga.INICIAR_EXECUCAO, "IniciarExecucao");

        rodar(c); // 1º timeout (2 min) -> reenvio com prazo de 4 min
        assertThat(c.saga.getPrazoEm()).isEqualTo(c.clock.instant().plus(Duration.ofMinutes(4)));
        assertThat(c.saga.getTentativas()).isEqualTo(1);

        var c2 = vencida(AGUARDANDO_APROVACAO, PassoSaga.INICIAR_EXECUCAO, "IniciarExecucao");
        c2.saga.registrarTentativa(AGORA.plusSeconds(120), RELOGIO);
        rodar(c2); // 2º timeout -> reenvio com prazo de 8 min
        assertThat(c2.saga.getPrazoEm()).isEqualTo(c2.clock.instant().plus(Duration.ofMinutes(8)));
        assertThat(c2.tipos()).containsExactly("IniciarExecucao");

        var c3 = vencida(AGUARDANDO_APROVACAO, PassoSaga.INICIAR_EXECUCAO, "IniciarExecucao");
        esgotar(c3);
        rodar(c3); // esgotado: só recuperação para frente
        assertThat(c3.saga.getStatus()).isEqualTo(SagaStatus.FALHOU);
        assertThat(c3.os.getSituacao()).isEqualTo(AGUARDANDO_APROVACAO);
        assertThat(c3.enviadas).isEmpty();
    }

    @Test
    @DisplayName("SOLICITAR_PAGAMENTO esgotado: Saga FALHOU e a OS fica FINALIZADA")
    void esgotaSolicitarPagamento() {
        var c = vencida(FINALIZADA, PassoSaga.SOLICITAR_PAGAMENTO, "SolicitarPagamento");
        esgotar(c);

        rodar(c);

        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.FALHOU);
        assertThat(c.os.getSituacao()).isEqualTo(FINALIZADA);
    }

    @Test
    @DisplayName("comando sem payload guardado não pode ser reenviado: trata como esgotado")
    void semPayload() {
        var c = vencida(EM_EXECUCAO, PassoSaga.INICIAR_EXECUCAO, null);
        c.saga.getContexto().setUltimoComando(new ComandoEnviado("IniciarExecucao", UUID.randomUUID(), null));

        rodar(c);

        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.FALHOU);
    }

    @Test
    @DisplayName("passo sem prazo próprio: só limpa o prazo")
    void passoSemPrazo() {
        var c = vencida(FINALIZADA, PassoSaga.AGUARDAR_PAGAMENTO, null);

        rodar(c);

        assertThat(c.saga.getPrazoEm()).isNull();
        assertThat(c.enviadas).isEmpty();
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.INICIADA);
    }
}
