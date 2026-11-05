package br.com.fiap.soat.mecanica.os;

import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O Flyway aplica V1-V6, o catalogo do seed existe e os CHECKs do schema valem. */
class MigracoesIT extends IntegracaoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAplicaDoV1AoV6() {
        Integer aplicadas = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2','3','4','5','6')",
                Integer.class);
        assertThat(aplicadas).isEqualTo(6);
    }

    @Test
    void seedTemSoOCatalogoDeServicos() {
        assertThat(jdbc.queryForList("SELECT id::text FROM servicos ORDER BY id", String.class))
                .containsExactly("77777777-7777-7777-7777-777777777777", "77777777-7777-7777-7777-777777777778");
        assertThat(jdbc.queryForObject("SELECT nome FROM servicos WHERE id = '77777777-7777-7777-7777-777777777777'",
                String.class)).isEqualTo("Troca de oleo");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordem_servicos", Integer.class)).isZero();
    }

    @Test
    void situacaoForaDasOitoEhRecusada() {
        assertThatThrownBy(() -> inserirOs("EM_REPARO"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ordem_servicos_situacao");
    }

    @Test
    void ordemServicoNaoTemFkParaVeiculoNemUsuario() {
        UUID id = UUID.randomUUID();
        try {
            // veiculo, cliente e usuario moram no Identity: ids arbitrarios sao aceitos
            inserirOs(id, "RECEBIDA");
            assertThat(jdbc.queryForObject("SELECT situacao FROM ordem_servicos WHERE id = ?", String.class, id))
                    .isEqualTo("RECEBIDA");
        } finally {
            jdbc.update("DELETE FROM ordem_servicos WHERE id = ?", id);
        }
    }

    @Test
    void ordemServicoNaoTemColunaStatus() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'ordem_servicos' AND column_name = 'status'""", Integer.class)).isZero();
    }

    @Test
    void sagaTemUmaInstanciaPorOsEStatusValido() {
        UUID osId = UUID.randomUUID();
        try {
            inserirOs(osId, "RECEBIDA");
            inserirSaga(osId, "INICIADA");

            assertThatThrownBy(() -> inserirSaga(osId, "INICIADA"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> inserirSaga(UUID.randomUUID(), "INICIADA"))
                    .as("saga sem OS viola a FK").isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM saga_instancia WHERE os_id = ?", osId);
            jdbc.update("DELETE FROM ordem_servicos WHERE id = ?", osId);
        }
        assertThatThrownBy(() -> inserirSaga(osId, "QUEBRADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void indiceDeTimeoutDaSagaEhParcial() {
        String definicao = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_saga_instancia_prazo'", String.class);
        assertThat(definicao).contains("prazo_em").containsIgnoringCase("WHERE (prazo_em IS NOT NULL)");
    }

    private void inserirOs(String situacao) {
        inserirOs(UUID.randomUUID(), situacao);
    }

    private void inserirOs(UUID id, String situacao) {
        jdbc.update("""
                INSERT INTO ordem_servicos (id, situacao, data_recebida, valor_total, veiculo_id, cliente_id, placa,
                                            usuario_abertura_id)
                VALUES (?, ?, now(), 0, ?, ?, 'ABC1D23', ?)""",
                id, situacao, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private void inserirSaga(UUID osId, String status) {
        jdbc.update("""
                INSERT INTO saga_instancia (id, os_id, passo_atual, status, criada_em, atualizada_em)
                VALUES (?, ?, 'ABRIR_DIAGNOSTICO', ?, now(), now())""", UUID.randomUUID(), osId, status);
    }
}
