package br.com.fiap.soat.mecanica.os.adapters.out.persistence.saga;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.DirecaoPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.osNova;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaRepositoryIT extends IntegracaoTest {

    @Autowired
    private SagaRepository sagaRepository;
    @Autowired
    private OrdemServicoRepository ordemServicoRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager entityManager;

    private SagaInstancia novaSaga(UUID osId) {
        return SagaInstancia.iniciar(osId, Duration.ofMinutes(2), RELOGIO);
    }

    @Test
    @Transactional
    void salvaERecuperaComOContextoEmJsonb() {
        OrdemServico os = ordemServicoRepository.salvar(osNova());
        SagaInstancia saga = novaSaga(os.getId());
        UUID servicoId = UUID.randomUUID();
        UUID pecaId = UUID.randomUUID();
        ContextoSaga contexto = saga.getContexto();
        contexto.setItens(List.of(new ContextoSaga.ItemDiagnostico(servicoId, "Troca de óleo",
                new BigDecimal("80.00"), List.of(new ContextoSaga.PecaItem(pecaId, "Filtro", 2,
                new BigDecimal("35.50"))))));
        contexto.setPecasReservadas(List.of(new ContextoSaga.PecaReservada(pecaId, 2)));
        contexto.setReservaAtiva(true);
        contexto.setLiberacaoPendente(true);
        UUID orcamentoId = UUID.randomUUID();
        contexto.setOrcamentoId(orcamentoId);
        UUID messageId = UUID.randomUUID();
        contexto.setUltimoComando(new ContextoSaga.ComandoEnviado("ReservarPecas", messageId));

        SagaInstancia salva = sagaRepository.salvar(saga);
        entityManager.clear(); // a leitura abaixo vem do banco, nao do contexto de persistencia

        assertThat(salva.getVersao()).isZero();
        SagaInstancia lida = sagaRepository.buscarPorOsId(os.getId()).orElseThrow();
        assertThat(lida.getId()).isEqualTo(saga.getId());
        assertThat(lida.getPassoAtual()).isEqualTo(PassoSaga.ABRIR_DIAGNOSTICO);
        assertThat(lida.getStatus()).isEqualTo(SagaStatus.INICIADA);
        assertThat(lida.getPrazoEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(2)));
        assertThat(lida.getTentativas()).isZero();
        ContextoSaga lido = lida.getContexto();
        assertThat(lido.getItens()).hasSize(1);
        assertThat(lido.getItens().get(0).servicoId()).isEqualTo(servicoId);
        assertThat(lido.getItens().get(0).precoMaoDeObra()).isEqualByComparingTo("80.00");
        assertThat(lido.getItens().get(0).pecas().get(0).valorUnitario()).isEqualByComparingTo("35.50");
        assertThat(lido.getItens().get(0).pecas().get(0).quantidade()).isEqualTo(2);
        assertThat(lido.getPecasReservadas()).containsExactly(new ContextoSaga.PecaReservada(pecaId, 2));
        assertThat(lido.isReservaAtiva()).isTrue();
        assertThat(lido.isLiberacaoPendente()).isTrue();
        assertThat(lido.getOrcamentoId()).isEqualTo(orcamentoId);
        assertThat(lido.getUltimoComando()).isEqualTo(new ContextoSaga.ComandoEnviado("ReservarPecas", messageId));
        assertThat(lido.getDiagnosticoEmEspera()).isNull();
    }

    @Test
    @Transactional
    void cadaGravacaoIncrementaAVersao() {
        OrdemServico os = ordemServicoRepository.salvar(osNova());
        SagaInstancia saga = sagaRepository.salvar(novaSaga(os.getId()));
        assertThat(saga.getVersao()).isZero();

        saga.avancarPara(PassoSaga.AGUARDAR_DIAGNOSTICO, null, RELOGIO);
        SagaInstancia segunda = sagaRepository.salvar(saga);
        assertThat(segunda.getVersao()).isEqualTo(1);

        segunda.mudarStatus(SagaStatus.COMPENSANDO, RELOGIO);
        assertThat(sagaRepository.salvar(segunda).getVersao()).isEqualTo(2);
        entityManager.clear();
        assertThat(sagaRepository.buscarPorOsId(os.getId())).get()
                .extracting(SagaInstancia::getPassoAtual, SagaInstancia::getStatus, SagaInstancia::getPrazoEm)
                .containsExactly(PassoSaga.AGUARDAR_DIAGNOSTICO, SagaStatus.COMPENSANDO, null);
    }

    /** Regressao: sem equals no ContextoSaga o Hibernate via a coluna JSONB como suja e gravava a toa. */
    @Test
    @Transactional
    void lerEDarFlushSemAlterarNaoIncrementaAVersao() {
        OrdemServico os = ordemServicoRepository.salvar(osNova());
        SagaInstancia saga = novaSaga(os.getId());
        saga.getContexto().setItens(List.of(new ContextoSaga.ItemDiagnostico(UUID.randomUUID(), "Servico",
                BigDecimal.TEN, List.of())));
        sagaRepository.salvar(saga);
        entityManager.clear();

        sagaRepository.buscarPorOsId(os.getId());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT versao FROM saga_instancia WHERE os_id = ?", Long.class, os.getId()))
                .isZero();
    }

    @Test
    @Transactional
    void buscarPorOsIdSemSagaDevolveVazio() {
        assertThat(sagaRepository.buscarPorOsId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @Transactional
    void registraOsPassosEDevolveEmOrdemCronologica() {
        OrdemServico os = ordemServicoRepository.salvar(osNova());
        SagaInstancia saga = sagaRepository.salvar(novaSaga(os.getId()));
        UUID comando = UUID.randomUUID();
        sagaRepository.registrarPasso(SagaPasso.novo(saga.getId(), PassoSaga.ABRIR_DIAGNOSTICO, "DiagnosticoAberto",
                DirecaoPasso.RECEBIDO, UUID.randomUUID(), null, AGORA.plusSeconds(5)));
        sagaRepository.registrarPasso(SagaPasso.novo(saga.getId(), PassoSaga.ABRIR_DIAGNOSTICO, "AbrirDiagnostico",
                DirecaoPasso.ENVIADO, comando, Map.of("tentativa", 1, "destino", "execucao-comandos"), AGORA));
        entityManager.flush();
        entityManager.clear();

        List<SagaPasso> passos = sagaRepository.buscarPassos(saga.getId());

        assertThat(passos).extracting(SagaPasso::tipoMensagem, SagaPasso::direcao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AbrirDiagnostico", DirecaoPasso.ENVIADO),
                        org.assertj.core.groups.Tuple.tuple("DiagnosticoAberto", DirecaoPasso.RECEBIDO));
        assertThat(passos.get(0).messageId()).isEqualTo(comando);
        assertThat(passos.get(0).detalhe()).containsEntry("tentativa", 1).containsEntry("destino", "execucao-comandos");
        assertThat(passos.get(1).detalhe()).isNull();
        assertThat(sagaRepository.buscarPassos(UUID.randomUUID())).isEmpty();
    }

    /** Duas respostas concorrentes na mesma Saga: a segunda a gravar perde (@Version) e a transacao desfaz tudo. */
    @Test
    void gravacaoComVersaoDesatualizadaLancaConflito() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID osId = tx.execute(s -> {
            OrdemServico os = ordemServicoRepository.salvar(osNova());
            sagaRepository.salvar(novaSaga(os.getId()));
            return os.getId();
        });
        try {
            SagaInstancia copiaA = tx.execute(s -> sagaRepository.buscarPorOsId(osId).orElseThrow());
            SagaInstancia copiaB = tx.execute(s -> sagaRepository.buscarPorOsId(osId).orElseThrow());

            copiaA.avancarPara(PassoSaga.AGUARDAR_DIAGNOSTICO, null, RELOGIO);
            tx.executeWithoutResult(s -> sagaRepository.salvar(copiaA));

            copiaB.mudarStatus(SagaStatus.FALHOU, RELOGIO);
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> sagaRepository.salvar(copiaB)))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);

            SagaInstancia final_ = tx.execute(s -> sagaRepository.buscarPorOsId(osId).orElseThrow());
            assertThat(final_.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
            assertThat(final_.getStatus()).isEqualTo(SagaStatus.INICIADA);
            assertThat(final_.getVersao()).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM saga_passo WHERE saga_id IN (SELECT id FROM saga_instancia WHERE os_id = ?)", osId);
            jdbc.update("DELETE FROM saga_instancia WHERE os_id = ?", osId);
            jdbc.update("DELETE FROM ordem_servicos WHERE id = ?", osId);
        }
    }
}
