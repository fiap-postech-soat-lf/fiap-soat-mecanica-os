package br.com.fiap.soat.mecanica.os.application.ordemservico.usecase;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.os.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.os.application.ordemservico.port.Veiculo;
import br.com.fiap.soat.mecanica.os.application.ordemservico.port.VeiculoPort;
import br.com.fiap.soat.mecanica.os.application.saga.SagaMetrics;
import br.com.fiap.soat.mecanica.os.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.os.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.MudancaSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ComandoEnviado;
import br.com.fiap.soat.mecanica.os.domain.saga.DirecaoPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abre a OS e começa a Saga no passo 2 da A5. O passo 1 (validar o veículo no Identity) é síncrono e vem
 * antes de qualquer escrita: se falhar, nada é gravado.
 */
@Service
@RequiredArgsConstructor
public class AbrirOrdemServicoUseCase {

    static final String COMANDO_ABRIR_DIAGNOSTICO = "AbrirDiagnostico";
    static final String EVENTO_SITUACAO_ALTERADA = "SituacaoOrdemServicoAlterada";
    static final String DESTINO_EXECUCAO_COMANDOS = "execucao-comandos";
    static final String DESTINO_OS_EVENTOS = "os-eventos";
    private static final Duration PRAZO_COMANDO = Duration.ofMinutes(2);

    private final VeiculoPort veiculoPort;
    private final OrdemServicoRepository ordemServicoRepository;
    private final SagaRepository sagaRepository;
    private final OutboxPort outbox;
    private final MensagemFactory mensagemFactory;
    private final UsuarioAutenticadoPort usuarioAutenticado;
    private final SagaMetrics metricas;
    private final Clock clock;

    @Transactional
    public OrdemServico executar(UUID veiculoId, String observacao) {
        Veiculo veiculo = veiculoPort.consultar(veiculoId);

        OrdemServico os = ordemServicoRepository.salvar(new OrdemServico(observacao, veiculo.id(),
                veiculo.clienteId(), veiculo.placa(), usuarioAutenticado.sub(), clock));
        MudancaSituacao abertura = os.mudancaDeAbertura();
        ordemServicoRepository.registrarHistorico(os.getId(), abertura);

        SagaInstancia saga = SagaInstancia.iniciar(os.getId(), PRAZO_COMANDO, clock);
        var comando = mensagemFactory.criar(COMANDO_ABRIR_DIAGNOSTICO, 1, saga.getId(), os.getId(),
                Map.of("osId", os.getId(), "veiculoId", os.getVeiculoId(), "placa", os.getPlaca()));
        saga.getContexto().setUltimoComando(
                new ComandoEnviado(
                        COMANDO_ABRIR_DIAGNOSTICO, comando.messageId(), comando.payload()));
        sagaRepository.salvar(saga);
        sagaRepository.registrarPasso(SagaPasso.novo(saga.getId(), PassoSaga.ABRIR_DIAGNOSTICO,
                COMANDO_ABRIR_DIAGNOSTICO, DirecaoPasso.ENVIADO, comando.messageId(), Map.of(),
                clock.instant()));
        outbox.registrar(DESTINO_EXECUCAO_COMANDOS, comando);

        var evento = mensagemFactory.criar(EVENTO_SITUACAO_ALTERADA, 1, saga.getId(), os.getId(),
                payloadSituacao(os, abertura));
        outbox.registrar(DESTINO_OS_EVENTOS, evento);

        metricas.iniciada(PassoSaga.ABRIR_DIAGNOSTICO);
        BusinessEventLogger.ordemServicoCriada(os.getId(), os.getVeiculoId(), os.getUsuarioAberturaId());
        BusinessEventLogger.situacaoOrdemServicoAlterada(os.getId(), null, os.getSituacao());
        BusinessEventLogger.sagaPasso(saga.getId(), os.getId(), PassoSaga.ABRIR_DIAGNOSTICO,
                COMANDO_ABRIR_DIAGNOSTICO, DirecaoPasso.ENVIADO.name());
        return os;
    }

    private static Map<String, Object> payloadSituacao(OrdemServico os, MudancaSituacao mudanca) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("osId", os.getId());
        payload.put("clienteId", os.getClienteId());
        payload.put("placa", os.getPlaca());
        payload.put("de", null);
        payload.put("para", mudanca.para().name());
        payload.put("ocorridaEm", mudanca.em());
        return payload;
    }
}
