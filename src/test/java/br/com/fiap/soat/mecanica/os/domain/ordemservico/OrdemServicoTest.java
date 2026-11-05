package br.com.fiap.soat.mecanica.os.domain.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_APROVACAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_PAGAMENTO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.CANCELADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_DIAGNOSTICO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_EXECUCAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.ENTREGUE;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.FINALIZADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.RECEBIDA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.ATENDENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.CLIENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.PLACA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.VEICULO_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.osEm;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.osNova;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdemServicoTest {

    /** Uma linha da tabela de transicoes da F4 (secao T01): situacoes de origem, destino e a chamada. */
    record Transicao(String nome, Set<SituacaoOrdemServico> de, SituacaoOrdemServico para,
                     BiFunction<OrdemServico, Clock, MudancaSituacao> acao) {
        @Override
        public String toString() {
            return nome;
        }
    }

    static final Set<SituacaoOrdemServico> TODAS = EnumSet.allOf(SituacaoOrdemServico.class);

    static Stream<Transicao> transicoes() {
        return Stream.of(
                new Transicao("iniciarDiagnostico", Set.of(RECEBIDA), EM_DIAGNOSTICO,
                        (os, c) -> os.iniciarDiagnostico(c)),
                new Transicao("registrarProblemaNoDiagnostico", Set.of(EM_DIAGNOSTICO), EM_DIAGNOSTICO,
                        (os, c) -> os.registrarProblemaNoDiagnostico("peças sem estoque", c)),
                new Transicao("enviarParaAprovacao", Set.of(EM_DIAGNOSTICO), AGUARDANDO_APROVACAO,
                        (os, c) -> os.enviarParaAprovacao(new BigDecimal("350.00"), c)),
                new Transicao("voltarParaDiagnostico", Set.of(AGUARDANDO_APROVACAO), EM_DIAGNOSTICO,
                        (os, c) -> os.voltarParaDiagnostico("cliente pediu revisão", c)),
                new Transicao("iniciarExecucao", Set.of(AGUARDANDO_APROVACAO), EM_EXECUCAO,
                        (os, c) -> os.iniciarExecucao(c)),
                new Transicao("finalizar", Set.of(EM_EXECUCAO), FINALIZADA, (os, c) -> os.finalizar(c)),
                new Transicao("aguardarPagamento", Set.of(FINALIZADA), AGUARDANDO_PAGAMENTO,
                        (os, c) -> os.aguardarPagamento(c)),
                new Transicao("registrarPagamentoRecusado", Set.of(AGUARDANDO_PAGAMENTO), AGUARDANDO_PAGAMENTO,
                        (os, c) -> os.registrarPagamentoRecusado("cartão recusado", c)),
                new Transicao("entregar", Set.of(AGUARDANDO_PAGAMENTO), ENTREGUE, (os, c) -> os.entregar(c)),
                new Transicao("cancelar", Set.of(RECEBIDA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO), CANCELADA,
                        (os, c) -> os.cancelar("desistência", c)));
    }

    /** Cada transicao contra cada uma das 8 situacoes: aceita so as de origem e recusa as demais. */
    static Stream<Arguments> transicoesPorSituacao() {
        return transicoes().flatMap(t -> TODAS.stream().map(s -> Arguments.of(t, s)));
    }

    @ParameterizedTest(name = "{0} a partir de {1}")
    @MethodSource("transicoesPorSituacao")
    @DisplayName("Cada transição só vale nas situações de origem")
    void transicaoSoValeNaOrigem(Transicao transicao, SituacaoOrdemServico origem) {
        OrdemServico os = osEm(origem);

        if (transicao.de().contains(origem)) {
            MudancaSituacao mudanca = transicao.acao().apply(os, RELOGIO);

            assertThat(mudanca.de()).isEqualTo(origem);
            assertThat(mudanca.para()).isEqualTo(transicao.para());
            assertThat(mudanca.em()).isEqualTo(AGORA);
            assertThat(os.getSituacao()).isEqualTo(transicao.para());
        } else {
            assertThatThrownBy(() -> transicao.acao().apply(os, RELOGIO))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining(origem.name());
            assertThat(os.getSituacao()).isEqualTo(origem);
        }
    }

    @Test
    @DisplayName("Deve abrir RECEBIDA, com valor zero e o snapshot do Identity")
    void deveAbrirRecebida() {
        OrdemServico os = osNova();

        assertThat(os.getId()).isNull();
        assertThat(os.getSituacao()).isEqualTo(RECEBIDA);
        assertThat(os.getValorTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(os.getDataRecebida()).isEqualTo(AGORA);
        assertThat(os.getVeiculoId()).isEqualTo(VEICULO_ID);
        assertThat(os.getClienteId()).isEqualTo(CLIENTE_ID);
        assertThat(os.getPlaca()).isEqualTo(PLACA);
        assertThat(os.getUsuarioAberturaId()).isEqualTo(ATENDENTE_ID);
        assertThat(os.getPago()).isNull();
    }

    @Test
    @DisplayName("A mudança de abertura tem de nulo e para RECEBIDA")
    void mudancaDeAbertura() {
        assertThat(osNova().mudancaDeAbertura())
                .isEqualTo(new MudancaSituacao(null, RECEBIDA, null, AGORA));
    }

    @Test
    @DisplayName("Deve recusar a abertura sem veículo, cliente, placa ou usuário")
    void deveRecusarAberturaIncompleta() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new OrdemServico("o", null, id, PLACA, id, RELOGIO))
                .isInstanceOf(RegraNegocioException.class).hasMessage("O veículo é obrigatório");
        assertThatThrownBy(() -> new OrdemServico("o", id, null, PLACA, id, RELOGIO))
                .isInstanceOf(RegraNegocioException.class).hasMessage("O cliente é obrigatório");
        assertThatThrownBy(() -> new OrdemServico("o", id, id, " ", id, RELOGIO))
                .isInstanceOf(RegraNegocioException.class).hasMessage("A placa é obrigatória");
        assertThatThrownBy(() -> new OrdemServico("o", id, id, null, id, RELOGIO))
                .isInstanceOf(RegraNegocioException.class).hasMessage("A placa é obrigatória");
        assertThatThrownBy(() -> new OrdemServico("o", id, id, PLACA, null, RELOGIO))
                .isInstanceOf(RegraNegocioException.class).hasMessage("O usuário de abertura é obrigatório");
    }

    @Test
    @DisplayName("Deve registrar as datas e o motivo em cada etapa do caminho feliz")
    void deveRegistrarDatasNoCaminhoFeliz() {
        OrdemServico os = osNova();
        Instant t1 = AGORA.plusSeconds(60);
        Instant t2 = AGORA.plusSeconds(120);
        Instant t3 = AGORA.plusSeconds(180);
        Instant t4 = AGORA.plusSeconds(240);
        Instant t5 = AGORA.plusSeconds(300);
        Instant t6 = AGORA.plusSeconds(360);

        os.iniciarDiagnostico(relogioEm(t1));
        os.enviarParaAprovacao(new BigDecimal("350.00"), relogioEm(t2));
        os.iniciarExecucao(relogioEm(t3));
        os.finalizar(relogioEm(t4));
        os.aguardarPagamento(relogioEm(t5));
        assertThat(os.getPago()).isFalse();
        os.entregar(relogioEm(t6));

        assertThat(os.getDataDiagnostico()).isEqualTo(t1);
        assertThat(os.getDataAguardandoAprovacao()).isEqualTo(t2);
        assertThat(os.getDataExecucao()).isEqualTo(t3);
        assertThat(os.getDataFinalizada()).isEqualTo(t4);
        assertThat(os.getDataAguardandoPagamento()).isEqualTo(t5);
        assertThat(os.getDataEntregue()).isEqualTo(t6);
        assertThat(os.getValorTotal()).isEqualByComparingTo("350.00");
        assertThat(os.getPago()).isTrue();
        assertThat(os.isCancelada()).isFalse();
    }

    @Test
    @DisplayName("Deve guardar a data e o motivo do cancelamento")
    void deveGuardarCancelamento() {
        OrdemServico os = osEm(AGUARDANDO_APROVACAO);

        MudancaSituacao mudanca = os.cancelar("orçamento recusado", RELOGIO);

        assertThat(mudanca.motivo()).isEqualTo("orçamento recusado");
        assertThat(os.getMotivoCancelamento()).isEqualTo("orçamento recusado");
        assertThat(os.getDataCancelada()).isEqualTo(AGORA);
        assertThat(os.isCancelada()).isTrue();
    }

    @Test
    @DisplayName("Problema no diagnóstico e pagamento recusado ficam no histórico com o motivo")
    void deveCarregarMotivoNasMudancasQueNaoMudamDeSituacao() {
        assertThat(osEm(EM_DIAGNOSTICO).registrarProblemaNoDiagnostico("peças sem estoque", RELOGIO).motivo())
                .isEqualTo("peças sem estoque");
        assertThat(osEm(AGUARDANDO_PAGAMENTO).registrarPagamentoRecusado("saldo insuficiente", RELOGIO).motivo())
                .isEqualTo("saldo insuficiente");
    }

    @Test
    @DisplayName("Deve recusar valor total nulo ou negativo ao enviar para aprovação")
    void deveRecusarValorInvalido() {
        assertThatThrownBy(() -> osEm(EM_DIAGNOSTICO).enviarParaAprovacao(null, RELOGIO))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Valor total inválido");
        OrdemServico os = osEm(EM_DIAGNOSTICO);
        assertThatThrownBy(() -> os.enviarParaAprovacao(new BigDecimal("-1"), RELOGIO))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Valor total inválido");
        assertThat(os.getSituacao()).isEqualTo(EM_DIAGNOSTICO);
    }

    @Test
    @DisplayName("Só RECEBIDA, EM_DIAGNOSTICO e AGUARDANDO_APROVACAO permitem cancelamento")
    void permiteCancelamento() {
        assertThat(TODAS.stream().filter(SituacaoOrdemServico::permiteCancelamento))
                .containsExactlyInAnyOrder(RECEBIDA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO);
    }

    @Test
    @DisplayName("Os 8 valores do enum são os do schema, e têm descrição legível")
    void valoresDoEnum() {
        assertThat(TODAS).extracting(Enum::name).containsExactly("RECEBIDA", "EM_DIAGNOSTICO",
                "AGUARDANDO_APROVACAO", "EM_EXECUCAO", "FINALIZADA", "AGUARDANDO_PAGAMENTO", "ENTREGUE",
                "CANCELADA");
        assertThat(AGUARDANDO_PAGAMENTO.descricao()).isEqualTo("Aguardando pagamento");
    }

    private static Clock relogioEm(Instant instante) {
        return Clock.fixed(instante, java.time.ZoneOffset.UTC);
    }
}
