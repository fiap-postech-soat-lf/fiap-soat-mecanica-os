package br.com.fiap.soat.mecanica.os.support;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.os.application.saga.ComandosSaga;
import br.com.fiap.soat.mecanica.os.application.saga.EventosOrdemServico;
import br.com.fiap.soat.mecanica.os.application.saga.SagaOrquestrador;
import br.com.fiap.soat.mecanica.os.config.SagaTimeoutProperties;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.MudancaSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.DirecaoPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.osEm;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Orquestrador real sobre repositórios simulados (Mockito) e uma outbox que só anota o que recebeu. Um cenário
 * tem uma OS e uma Saga, na situação e no passo que o teste escolher.
 */
public final class CenarioSaga {

    public record Enviada(String destino, MensagemEnvelope envelope) {
        public String tipo() {
            return envelope.type();
        }
    }

    public final SagaOrquestrador orquestrador;
    public final SagaRepository sagaRepository = mock(SagaRepository.class);
    public final OrdemServicoRepository osRepository = mock(OrdemServicoRepository.class);
    public final List<Enviada> enviadas = new ArrayList<>();
    public final List<SagaPasso> passos = new ArrayList<>();
    public final List<MudancaSituacao> historico = new ArrayList<>();
    public OrdemServico os;
    public SagaInstancia saga;

    public CenarioSaga() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        MensagemFactory fabrica = new MensagemFactory(mapper);
        orquestrador = new SagaOrquestrador(sagaRepository, osRepository,
                (destino, envelope) -> enviadas.add(new Enviada(destino, envelope)),
                new ComandosSaga(fabrica), new EventosOrdemServico(fabrica),
                new SagaTimeoutProperties(Duration.ofMinutes(2), 3, Duration.ofMinutes(1), Duration.ofSeconds(30)),
                RELOGIO);
        when(sagaRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(osRepository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doAnswer(inv -> {
            passos.add(inv.getArgument(0));
            return null;
        }).when(sagaRepository).registrarPasso(any());
        org.mockito.Mockito.doAnswer(inv -> {
            historico.add(inv.getArgument(1));
            return null;
        }).when(osRepository).registrarHistorico(any(), any());
    }

    public static CenarioSaga em(SituacaoOrdemServico situacao, PassoSaga passo) {
        CenarioSaga cenario = new CenarioSaga();
        cenario.os = osEm(situacao);
        cenario.saga = SagaInstancia.iniciar(cenario.os.getId(), Duration.ofMinutes(2), RELOGIO);
        cenario.saga.avancarPara(passo, null, RELOGIO);
        when(cenario.osRepository.buscarPorId(cenario.os.getId())).thenReturn(Optional.of(cenario.os));
        when(cenario.sagaRepository.buscarPorOsId(cenario.os.getId())).thenReturn(Optional.of(cenario.saga));
        return cenario;
    }

    public UUID osId() {
        return os.getId();
    }

    /** Tipos publicados, na ordem. */
    public List<String> tipos() {
        return enviadas.stream().map(Enviada::tipo).toList();
    }

    public Enviada primeira(String tipo) {
        return enviadas.stream().filter(e -> e.tipo().equals(tipo)).findFirst()
                .orElseThrow(() -> new AssertionError("Não foi publicado " + tipo + "; publicados: " + tipos()));
    }

    public List<DirecaoPasso> direcoes() {
        return passos.stream().map(SagaPasso::direcao).toList();
    }
}
