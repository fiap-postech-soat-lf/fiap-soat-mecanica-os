package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.os.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.os.application.ordemservico.exception.VeiculoInvalidoException;
import br.com.fiap.soat.mecanica.os.application.ordemservico.port.Veiculo;
import br.com.fiap.soat.mecanica.os.application.ordemservico.port.VeiculoPort;
import br.com.fiap.soat.mecanica.os.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.MudancaSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.DirecaoPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.ATENDENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.CLIENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.PLACA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.VEICULO_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbrirOrdemServicoUseCaseTest {

    @Mock
    private VeiculoPort veiculoPort;
    @Mock
    private OrdemServicoRepository ordemServicoRepository;
    @Mock
    private SagaRepository sagaRepository;
    @Mock
    private OutboxPort outbox;
    @Mock
    private UsuarioAutenticadoPort usuarioAutenticado;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private AbrirOrdemServicoUseCase useCase;
    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        useCase = new AbrirOrdemServicoUseCase(veiculoPort, ordemServicoRepository, sagaRepository, outbox,
                new MensagemFactory(new ObjectMapper().findAndRegisterModules()), usuarioAutenticado, new br.com.fiap.soat.mecanica.os.application.saga.SagaMetrics(meterRegistry),
                new br.com.fiap.soat.mecanica.os.config.SagaTimeoutProperties(Duration.ofMinutes(2), 3,
                        Duration.ofMinutes(1), Duration.ofSeconds(30)),
                RELOGIO);
        when(usuarioAutenticado.sub()).thenReturn(ATENDENTE_ID);
        when(veiculoPort.consultar(VEICULO_ID)).thenReturn(new Veiculo(VEICULO_ID, PLACA, CLIENTE_ID, true));
        when(ordemServicoRepository.salvar(any())).thenAnswer(inv -> {
            OrdemServico nova = inv.getArgument(0);
            return OrdemServico.reconstruir(osId, nova.getSituacao(), nova.getDataRecebida(), null, null, null,
                    null, null, null, null, null, null, nova.getValorTotal(), nova.getObservacao(),
                    nova.getVeiculoId(), nova.getClienteId(), nova.getPlaca(), nova.getUsuarioAberturaId());
        });
    }

    @Test
    void abreAOsGravaHistoricoESagaERegistraOsDoisEventosNaOutbox() {
        OrdemServico os = useCase.executar(VEICULO_ID, "Barulho");

        assertThat(os.getSituacao()).isEqualTo(SituacaoOrdemServico.RECEBIDA);
        assertThat(os.getClienteId()).isEqualTo(CLIENTE_ID);
        assertThat(os.getPlaca()).isEqualTo(PLACA);
        assertThat(os.getUsuarioAberturaId()).isEqualTo(ATENDENTE_ID);

        ArgumentCaptor<MudancaSituacao> historico = ArgumentCaptor.forClass(MudancaSituacao.class);
        InOrder ordem = inOrder(veiculoPort, ordemServicoRepository, sagaRepository, outbox);
        ordem.verify(veiculoPort).consultar(VEICULO_ID);
        ordem.verify(ordemServicoRepository).salvar(any());
        ordem.verify(ordemServicoRepository).registrarHistorico(eq(osId), historico.capture());
        ArgumentCaptor<SagaInstancia> saga = ArgumentCaptor.forClass(SagaInstancia.class);
        ordem.verify(sagaRepository).salvar(saga.capture());
        ArgumentCaptor<SagaPasso> passo = ArgumentCaptor.forClass(SagaPasso.class);
        ordem.verify(sagaRepository).registrarPasso(passo.capture());
        ArgumentCaptor<MensagemEnvelope> comando = ArgumentCaptor.forClass(MensagemEnvelope.class);
        ordem.verify(outbox).registrar(eq("execucao-comandos"), comando.capture());
        ArgumentCaptor<MensagemEnvelope> evento = ArgumentCaptor.forClass(MensagemEnvelope.class);
        ordem.verify(outbox).registrar(eq("os-eventos"), evento.capture());

        assertThat(historico.getValue().de()).isNull();
        assertThat(historico.getValue().para()).isEqualTo(SituacaoOrdemServico.RECEBIDA);

        SagaInstancia criada = saga.getValue();
        assertThat(criada.getStatus()).isEqualTo(SagaStatus.INICIADA);
        assertThat(criada.getPassoAtual()).isEqualTo(PassoSaga.ABRIR_DIAGNOSTICO);
        assertThat(criada.getPrazoEm()).isEqualTo(RELOGIO.instant().plus(Duration.ofMinutes(2)));
        assertThat(criada.getContexto().getUltimoComando().messageId()).isEqualTo(comando.getValue().messageId());
        assertThat(passo.getValue().direcao()).isEqualTo(DirecaoPasso.ENVIADO);

        MensagemEnvelope abrir = comando.getValue();
        assertThat(abrir.type()).isEqualTo("AbrirDiagnostico");
        assertThat(abrir.sagaId()).isEqualTo(criada.getId());
        assertThat(abrir.osId()).isEqualTo(osId);
        assertThat(abrir.payload().path("placa").asText()).isEqualTo(PLACA);
        assertThat(abrir.payload().path("veiculoId").asText()).isEqualTo(VEICULO_ID.toString());

        MensagemEnvelope situacao = evento.getValue();
        assertThat(situacao.type()).isEqualTo("SituacaoOrdemServicoAlterada");
        assertThat(situacao.payload().has("de")).isTrue();
        assertThat(situacao.payload().get("de").isNull()).isTrue();
        assertThat(situacao.payload().path("para").asText()).isEqualTo("RECEBIDA");
        assertThat(situacao.payload().path("clienteId").asText()).isEqualTo(CLIENTE_ID.toString());

        assertThat(meterRegistry.counter("saga.iniciada", "passo", "ABRIR_DIAGNOSTICO").count()).isEqualTo(1.0);
    }

    @Test
    void veiculoInvalidoNaoGravaNada() {
        when(veiculoPort.consultar(VEICULO_ID)).thenThrow(new VeiculoInvalidoException("inativo"));

        assertThatThrownBy(() -> useCase.executar(VEICULO_ID, "x")).isInstanceOf(VeiculoInvalidoException.class);

        verifyNoInteractions(ordemServicoRepository, sagaRepository, outbox);
    }
}
