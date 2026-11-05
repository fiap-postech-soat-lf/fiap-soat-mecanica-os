package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import br.com.fiap.soat.mecanica.os.support.CenarioSaga;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_APROVACAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_DIAGNOSTICO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_EXECUCAO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RevisarOrcamentoUseCaseTest {

    private static final UUID ORCAMENTO = UUID.randomUUID();

    @Test
    @DisplayName("AGUARDAR_APROVACAO com reserva: volta ao diagnóstico, cancela o orçamento e libera a reserva (REVISAO)")
    void comReserva() {
        var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO);
        c.saga.getContexto().setReservaAtiva(true);
        c.saga.getContexto().setOrcamentoId(ORCAMENTO);

        var detalhe = new RevisarOrcamentoUseCase(c.orquestrador).executar(c.osId(), "preço alto");

        assertThat(detalhe.ordemServico().getSituacao()).isEqualTo(EM_DIAGNOSTICO);
        assertThat(c.tipos()).containsExactlyInAnyOrder("SituacaoOrdemServicoAlterada", "CancelarOrcamento",
                "LiberarReservaPecas");
        assertThat(c.primeira("LiberarReservaPecas").envelope().payload().path("motivo").asText())
                .isEqualTo("REVISAO");
        assertThat(c.primeira("SituacaoOrdemServicoAlterada").envelope().payload().path("motivo").asText())
                .isEqualTo("preço alto");
        assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
        assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.INICIADA);
        assertThat(c.saga.getContexto().isLiberacaoPendente()).isTrue();
        assertThat(c.saga.getContexto().getOrcamentoId()).isNull();
        assertThat(c.saga.getPrazoEm()).isEqualTo(AGORA.plusSeconds(120));
    }

    @Test
    @DisplayName("sem reserva (diagnóstico sem peças): não envia a liberação nem fica com pendência")
    void semReserva() {
        var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO);
        c.saga.getContexto().setOrcamentoId(ORCAMENTO);

        new RevisarOrcamentoUseCase(c.orquestrador).executar(c.osId(), null);

        assertThat(c.tipos()).containsExactlyInAnyOrder("SituacaoOrdemServicoAlterada", "CancelarOrcamento");
        assertThat(c.saga.getContexto().isLiberacaoPendente()).isFalse();
        assertThat(c.saga.getPrazoEm()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PassoSaga.class, names = {"ABRIR_DIAGNOSTICO", "AGUARDAR_DIAGNOSTICO", "RESERVAR_PECAS",
            "GERAR_ORCAMENTO", "INICIAR_EXECUCAO", "AGUARDAR_EXECUCAO", "SOLICITAR_PAGAMENTO", "AGUARDAR_PAGAMENTO",
            "FIM"})
    @DisplayName("fora de AGUARDAR_APROVACAO (inclusive depois do pivô) responde 422")
    void passoProibido(PassoSaga passo) {
        var c = CenarioSaga.em(EM_EXECUCAO, passo);

        assertThatThrownBy(() -> new RevisarOrcamentoUseCase(c.orquestrador).executar(c.osId(), null))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(c.enviadas).isEmpty();
    }
}
