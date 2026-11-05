package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import br.com.fiap.soat.mecanica.os.support.CenarioSaga;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_APROVACAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.CANCELADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_DIAGNOSTICO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_EXECUCAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.RECEBIDA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class CancelarOrdemServicoUseCaseTest {

    private static final UUID ORCAMENTO = UUID.randomUUID();

    private static CancelarOrdemServicoUseCase useCase(CenarioSaga c) {
        return new CancelarOrdemServicoUseCase(c.orquestrador);
    }

    @Test
    @DisplayName("ABRIR_DIAGNOSTICO: nada a desfazer, Saga vai direto a COMPENSADA e só OrdemServicoCancelada sai")
    void abrirDiagnostico() {
        var c = CenarioSaga.em(RECEBIDA, PassoSaga.ABRIR_DIAGNOSTICO);

        var detalhe = useCase(c).executar(c.osId(), "cliente desistiu");

        assertThat(detalhe.ordemServico().getSituacao()).isEqualTo(CANCELADA);
        assertThat(detalhe.ordemServico().getMotivoCancelamento()).isEqualTo("cliente desistiu");
        assertThat(c.tipos()).containsExactly("OrdemServicoCancelada");
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSADA);
        assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.FIM);
        assertThat(c.historico).hasSize(1);
    }

    @Test
    @DisplayName("AGUARDAR_DIAGNOSTICO sem motivo: usa o motivo padrão e não envia comandos")
    void aguardarDiagnostico() {
        var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);

        var detalhe = useCase(c).executar(c.osId(), " ");

        assertThat(detalhe.ordemServico().getMotivoCancelamento()).isEqualTo("Cancelada a pedido do cliente");
        assertThat(c.tipos()).containsExactly("OrdemServicoCancelada");
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSADA);
    }

    @Test
    @DisplayName("RESERVAR_PECAS: o ReservarPecas já saiu, então libera (DESISTENCIA) e compensa")
    void reservarPecas() {
        var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.RESERVAR_PECAS);

        useCase(c).executar(c.osId(), null);

        assertThat(c.tipos()).containsExactlyInAnyOrder("OrdemServicoCancelada", "LiberarReservaPecas");
        assertThat(c.primeira("LiberarReservaPecas").envelope().payload().path("motivo").asText())
                .isEqualTo("DESISTENCIA");
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSANDO);
        assertThat(c.saga.getContexto().isLiberacaoPendente()).isTrue();
    }

    @Test
    @DisplayName("GERAR_ORCAMENTO com reserva ativa: libera a reserva; sem orçamento ainda, não há o que cancelar")
    void gerarOrcamento() {
        var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.GERAR_ORCAMENTO);
        c.saga.getContexto().setReservaAtiva(true);

        useCase(c).executar(c.osId(), null);

        assertThat(c.tipos()).containsExactlyInAnyOrder("OrdemServicoCancelada", "LiberarReservaPecas");
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSANDO);
    }

    @Test
    @DisplayName("AGUARDAR_APROVACAO: cancela o orçamento e libera a reserva")
    void aguardarAprovacao() {
        var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO);
        c.saga.getContexto().setReservaAtiva(true);
        c.saga.getContexto().setOrcamentoId(ORCAMENTO);

        useCase(c).executar(c.osId(), "sem orçamento");

        assertThat(c.tipos()).containsExactlyInAnyOrder("OrdemServicoCancelada", "CancelarOrcamento",
                "LiberarReservaPecas");
        var cancelar = c.primeira("CancelarOrcamento");
        assertThat(cancelar.destino()).isEqualTo("billing-comandos");
        assertThat(cancelar.envelope().payload().path("orcamentoId").asText()).isEqualTo(ORCAMENTO.toString());
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSANDO);
        assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.FIM);
    }

    @Test
    @DisplayName("AGUARDAR_APROVACAO sem reserva: só cancela o orçamento e a Saga fica COMPENSADA")
    void aguardarAprovacaoSemReserva() {
        var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO);
        c.saga.getContexto().setOrcamentoId(ORCAMENTO);

        useCase(c).executar(c.osId(), null);

        assertThat(c.tipos()).containsExactlyInAnyOrder("OrdemServicoCancelada", "CancelarOrcamento");
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSADA);
        assertThat(c.saga.getPrazoEm()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PassoSaga.class, names = {"INICIAR_EXECUCAO", "AGUARDAR_EXECUCAO", "SOLICITAR_PAGAMENTO",
            "AGUARDAR_PAGAMENTO", "FIM"})
    @DisplayName("depois da aprovação (e com a Saga encerrada) responde 422 sem tocar em nada")
    void passoProibido(PassoSaga passo) {
        var c = CenarioSaga.em(EM_EXECUCAO, passo);

        assertThatThrownBy(() -> useCase(c).executar(c.osId(), null)).isInstanceOf(RegraNegocioException.class);
        assertThat(c.enviadas).isEmpty();
        assertThat(c.os.getSituacao()).isEqualTo(EM_EXECUCAO);
    }

    @Test
    @DisplayName("OS inexistente: 404")
    void naoEncontrada() {
        var c = new CenarioSaga();
        UUID id = UUID.randomUUID();
        when(c.osRepository.buscarPorId(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> useCase(c).executar(id, null)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("OS sem Saga: 404")
    void semSaga() {
        var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);
        when(c.sagaRepository.buscarPorOsId(c.osId())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> useCase(c).executar(c.osId(), null)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
