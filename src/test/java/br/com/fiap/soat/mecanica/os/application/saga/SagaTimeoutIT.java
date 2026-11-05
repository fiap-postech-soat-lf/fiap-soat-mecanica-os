package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ComandoEnviado;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.osNova;
import static org.assertj.core.api.Assertions.assertThat;

/** Duas réplicas varrendo ao mesmo tempo não tratam a mesma instância duas vezes (SKIP LOCKED). */
class SagaTimeoutIT extends IntegracaoTest {

    private static final int TOTAL = 10;

    @Autowired
    private TratarTimeoutsSagaUseCase useCase;
    @Autowired
    private SagaRepository sagaRepository;
    @Autowired
    private OrdemServicoRepository ordemServicoRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> sagas = new ArrayList<>();

    @BeforeEach
    void prepararSagasVencidas() {
        // outras classes de teste deixam sagas no banco compartilhado: só as deste teste podem vencer
        jdbc.update("UPDATE saga_instancia SET prazo_em = NULL");
        sagas.clear();
        Clock passado = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int i = 0; i < TOTAL; i++) {
                OrdemServico os = ordemServicoRepository.salvar(osNova());
                SagaInstancia saga = SagaInstancia.iniciar(os.getId(), Duration.ofMinutes(2), passado);
                saga.getContexto().setUltimoComando(new ComandoEnviado("AbrirDiagnostico", UUID.randomUUID(),
                        objectMapper.valueToTree(Map.of("osId", os.getId(), "veiculoId", os.getVeiculoId(),
                                "placa", os.getPlaca()))));
                sagas.add(sagaRepository.salvar(saga).getId());
            }
        });
    }

    /** Os ITs compartilham o banco e outros contam linhas: não deixar nada para trás. */
    @AfterEach
    void limpar() {
        String[] ids = sagas.stream().map(UUID::toString).toArray(String[]::new);
        jdbc.update("DELETE FROM outbox WHERE envelope ->> 'sagaId' = ANY (?::text[])", (Object) ids);
        List<UUID> osIds = jdbc.queryForList("SELECT os_id FROM saga_instancia WHERE id = ANY (?::uuid[])",
                UUID.class, (Object) ids);
        jdbc.update("DELETE FROM saga_passo WHERE saga_id = ANY (?::uuid[])", (Object) ids);
        jdbc.update("DELETE FROM saga_instancia WHERE id = ANY (?::uuid[])", (Object) ids);
        for (UUID osId : osIds) {
            jdbc.update("DELETE FROM historico_situacao_os WHERE os_id = ?", osId);
            jdbc.update("DELETE FROM ordem_servicos WHERE id = ?", osId);
        }
    }

    @Test
    void duasReplicasNaoTratamAMesmaInstanciaDuasVezes() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> rodadas = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                rodadas.add(pool.submit(() -> {
                    largada.await();
                    int tratadas = 0;
                    for (int tentativa = 0; tentativa < 3; tentativa++) {
                        tratadas += useCase.executar(TOTAL / 2);
                    }
                    return tratadas;
                }));
            }
            largada.countDown();
            int total = 0;
            for (Future<Integer> rodada : rodadas) {
                total += rodada.get(60, TimeUnit.SECONDS);
            }
            assertThat(total).isEqualTo(TOTAL);
        } finally {
            pool.shutdownNow();
        }

        for (UUID sagaId : sagas) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_passo WHERE saga_id = ? AND direcao = 'TIMEOUT'",
                    Integer.class, sagaId)).as("TIMEOUT de %s", sagaId).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_passo WHERE saga_id = ? AND direcao = 'ENVIADO'",
                    Integer.class, sagaId)).as("reenvios de %s", sagaId).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT tentativas FROM saga_instancia WHERE id = ?", Integer.class, sagaId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT passo_atual FROM saga_instancia WHERE id = ?", String.class, sagaId))
                    .isEqualTo(PassoSaga.ABRIR_DIAGNOSTICO.name());
        }
        // o reenvio saiu pela outbox, uma vez por instância
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox WHERE tipo = 'AbrirDiagnostico'
                  AND envelope ->> 'sagaId' = ANY (?::text[])""", Integer.class,
                (Object) sagas.stream().map(UUID::toString).toArray(String[]::new))).isEqualTo(TOTAL);
    }
}
