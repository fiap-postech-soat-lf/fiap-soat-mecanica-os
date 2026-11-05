package br.com.fiap.soat.mecanica.os.domain.saga;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Instancia da Saga de uma OS (uma por OS). O {@code id} e o sagaId dos envelopes. {@code versao} e o
 * controle otimista de concorrencia (nulo ate a primeira gravacao).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaInstancia {

    private UUID id;
    private UUID osId;
    private PassoSaga passoAtual;
    private SagaStatus status;
    private ContextoSaga contexto;
    private int tentativas;
    private Instant prazoEm;
    private Long versao;
    private Instant criadaEm;
    private Instant atualizadaEm;

    /** Saga nova no passo 2 da A5: aguardando o DiagnosticoAberto, com o prazo do comando. */
    public static SagaInstancia iniciar(UUID osId, Duration prazoComando, Clock clock) {
        Instant agora = clock.instant();
        SagaInstancia saga = new SagaInstancia();
        saga.id = UUID.randomUUID();
        saga.osId = osId;
        saga.passoAtual = PassoSaga.ABRIR_DIAGNOSTICO;
        saga.status = SagaStatus.INICIADA;
        saga.contexto = new ContextoSaga();
        saga.tentativas = 0;
        saga.prazoEm = agora.plus(prazoComando);
        saga.criadaEm = agora;
        saga.atualizadaEm = agora;
        return saga;
    }

    @SuppressWarnings("java:S107") // reconstrucao a partir da persistencia: um parametro por coluna
    public static SagaInstancia reconstruir(UUID id, UUID osId, PassoSaga passoAtual, SagaStatus status,
                                            ContextoSaga contexto, int tentativas, Instant prazoEm, Long versao,
                                            Instant criadaEm, Instant atualizadaEm) {
        SagaInstancia saga = new SagaInstancia();
        saga.id = id;
        saga.osId = osId;
        saga.passoAtual = passoAtual;
        saga.status = status;
        saga.contexto = contexto == null ? new ContextoSaga() : contexto;
        saga.tentativas = tentativas;
        saga.prazoEm = prazoEm;
        saga.versao = versao;
        saga.criadaEm = criadaEm;
        saga.atualizadaEm = atualizadaEm;
        return saga;
    }

    /** Muda de passo, zera as tentativas e troca o prazo ({@code null} = sem prazo). */
    public void avancarPara(PassoSaga passo, Instant novoPrazo, Clock clock) {
        this.passoAtual = passo;
        this.tentativas = 0;
        this.prazoEm = novoPrazo;
        this.atualizadaEm = clock.instant();
    }

    public void mudarStatus(SagaStatus novoStatus, Clock clock) {
        this.status = novoStatus;
        this.atualizadaEm = clock.instant();
    }

    /** Conta um reenvio por timeout e agenda o proximo prazo. */
    public void registrarTentativa(Instant novoPrazo, Clock clock) {
        this.tentativas++;
        this.prazoEm = novoPrazo;
        this.atualizadaEm = clock.instant();
    }

    public void definirPrazo(Instant novoPrazo, Clock clock) {
        this.prazoEm = novoPrazo;
        this.atualizadaEm = clock.instant();
    }

    public boolean estaEncerrada() {
        return status == SagaStatus.CONCLUIDA || status == SagaStatus.COMPENSADA || status == SagaStatus.FALHOU;
    }
}
