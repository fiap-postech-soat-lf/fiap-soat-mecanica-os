package br.com.fiap.soat.mecanica.os.adapters.out.persistence.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.Pagina;
import br.com.fiap.soat.mecanica.os.domain.Paginacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.ATENDENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.CLIENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.PLACA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.VEICULO_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.osNova;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class OrdemServicoRepositoryIT extends IntegracaoTest {

    @Autowired
    private OrdemServicoRepository repository;

    private static Clock em(long segundos) {
        return Clock.fixed(AGORA.plusSeconds(segundos), ZoneOffset.UTC);
    }

    private static OrdemServico osAbertaEm(long segundos, UUID clienteId, String placa) {
        return new OrdemServico("obs", UUID.randomUUID(), clienteId, placa, ATENDENTE_ID, em(segundos));
    }

    @Test
    void salvaERecuperaTodosOsCampos() {
        OrdemServico salva = repository.salvar(osNova());

        assertThat(salva.getId()).isNotNull();
        OrdemServico lida = repository.buscarPorId(salva.getId()).orElseThrow();
        assertThat(lida.getSituacao()).isEqualTo(SituacaoOrdemServico.RECEBIDA);
        assertThat(lida.getDataRecebida()).isEqualTo(AGORA);
        assertThat(lida.getValorTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lida.getObservacao()).isEqualTo("Barulho no motor");
        assertThat(lida.getVeiculoId()).isEqualTo(VEICULO_ID);
        assertThat(lida.getClienteId()).isEqualTo(CLIENTE_ID);
        assertThat(lida.getPlaca()).isEqualTo(PLACA);
        assertThat(lida.getUsuarioAberturaId()).isEqualTo(ATENDENTE_ID);
        assertThat(lida.getPago()).isNull();
    }

    @Test
    void atualizaAsTransicoesDoCaminhoFeliz() {
        OrdemServico os = repository.salvar(osNova());
        os.iniciarDiagnostico(em(10));
        os.enviarParaAprovacao(new BigDecimal("350.50"), em(20));
        os.iniciarExecucao(em(30));
        os.finalizar(em(40));
        os.aguardarPagamento(em(50));
        os.entregar(em(60));

        repository.salvar(os);

        OrdemServico lida = repository.buscarPorId(os.getId()).orElseThrow();
        assertThat(lida.getSituacao()).isEqualTo(SituacaoOrdemServico.ENTREGUE);
        assertThat(lida.getValorTotal()).isEqualByComparingTo("350.50");
        assertThat(lida.getPago()).isTrue();
        assertThat(lida.getDataDiagnostico()).isEqualTo(AGORA.plusSeconds(10));
        assertThat(lida.getDataAguardandoAprovacao()).isEqualTo(AGORA.plusSeconds(20));
        assertThat(lida.getDataExecucao()).isEqualTo(AGORA.plusSeconds(30));
        assertThat(lida.getDataFinalizada()).isEqualTo(AGORA.plusSeconds(40));
        assertThat(lida.getDataAguardandoPagamento()).isEqualTo(AGORA.plusSeconds(50));
        assertThat(lida.getDataEntregue()).isEqualTo(AGORA.plusSeconds(60));
    }

    @Test
    void persisteOCancelamentoComMotivo() {
        OrdemServico os = repository.salvar(osNova());
        os.cancelar("orçamento recusado", em(5));

        repository.salvar(os);

        OrdemServico lida = repository.buscarPorId(os.getId()).orElseThrow();
        assertThat(lida.isCancelada()).isTrue();
        assertThat(lida.getMotivoCancelamento()).isEqualTo("orçamento recusado");
        assertThat(lida.getDataCancelada()).isEqualTo(AGORA.plusSeconds(5));
    }

    @Test
    void buscaPorPlacaEPorClienteDaMaisRecenteParaAMaisAntiga() {
        UUID outroCliente = UUID.randomUUID();
        OrdemServico antiga = repository.salvar(osAbertaEm(0, CLIENTE_ID, "AAA1A11"));
        OrdemServico recente = repository.salvar(osAbertaEm(100, CLIENTE_ID, "AAA1A11"));
        OrdemServico deOutro = repository.salvar(osAbertaEm(50, outroCliente, "BBB2B22"));

        assertThat(repository.buscarPorPlaca("AAA1A11")).extracting(OrdemServico::getId)
                .containsExactly(recente.getId(), antiga.getId());
        assertThat(repository.buscarPorPlaca("ZZZ9Z99")).isEmpty();
        assertThat(repository.buscarPorClienteId(CLIENTE_ID)).extracting(OrdemServico::getId)
                .containsExactly(recente.getId(), antiga.getId());
        assertThat(repository.buscarPorClienteId(outroCliente)).extracting(OrdemServico::getId)
                .containsExactly(deOutro.getId());
    }

    @Test
    void listaSoAsEmAndamentoPaginadasDaMaisRecenteParaAMaisAntiga() {
        OrdemServico a = repository.salvar(osAbertaEm(0, CLIENTE_ID, "AAA1A11"));
        OrdemServico b = repository.salvar(osAbertaEm(10, CLIENTE_ID, "AAA1A11"));
        OrdemServico c = repository.salvar(osAbertaEm(20, CLIENTE_ID, "AAA1A11"));
        OrdemServico entregue = osAbertaEm(30, CLIENTE_ID, "AAA1A11");
        entregue = repository.salvar(entregueDe(entregue));
        OrdemServico cancelada = osAbertaEm(40, CLIENTE_ID, "AAA1A11");
        cancelada.cancelar("x", em(41));
        repository.salvar(cancelada);

        Pagina<OrdemServico> primeira = repository.listar(new Paginacao(0, 2));
        Pagina<OrdemServico> segunda = repository.listar(new Paginacao(1, 2));

        assertThat(primeira.content()).extracting(OrdemServico::getId).containsExactly(c.getId(), b.getId());
        assertThat(primeira.totalElements()).isEqualTo(3);
        assertThat(primeira.totalPages()).isEqualTo(2);
        assertThat(primeira.first()).isTrue();
        assertThat(primeira.last()).isFalse();
        assertThat(segunda.content()).extracting(OrdemServico::getId).containsExactly(a.getId());
        assertThat(segunda.last()).isTrue();
        assertThat(entregue.getSituacao()).isEqualTo(SituacaoOrdemServico.ENTREGUE);
    }

    @Test
    void historicoVoltaEmOrdemCronologicaComOMotivo() {
        OrdemServico os = repository.salvar(osNova());
        repository.registrarHistorico(os.getId(), os.mudancaDeAbertura());
        repository.registrarHistorico(os.getId(), os.iniciarDiagnostico(em(10)));
        repository.registrarHistorico(os.getId(), os.registrarProblemaNoDiagnostico("peças sem estoque", em(20)));

        assertThat(repository.buscarHistorico(os.getId()))
                .extracting(HistoricoSituacao::de, HistoricoSituacao::para, HistoricoSituacao::motivo,
                        HistoricoSituacao::ocorridaEm)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(null, SituacaoOrdemServico.RECEBIDA, null, AGORA),
                        org.assertj.core.groups.Tuple.tuple(SituacaoOrdemServico.RECEBIDA,
                                SituacaoOrdemServico.EM_DIAGNOSTICO, null, AGORA.plusSeconds(10)),
                        org.assertj.core.groups.Tuple.tuple(SituacaoOrdemServico.EM_DIAGNOSTICO,
                                SituacaoOrdemServico.EM_DIAGNOSTICO, "peças sem estoque", AGORA.plusSeconds(20)));
        assertThat(repository.buscarHistorico(UUID.randomUUID())).isEmpty();
    }

    private static OrdemServico entregueDe(OrdemServico os) {
        os.iniciarDiagnostico(em(31));
        os.enviarParaAprovacao(BigDecimal.TEN, em(32));
        os.iniciarExecucao(em(33));
        os.finalizar(em(34));
        os.aguardarPagamento(em(35));
        os.entregar(em(36));
        return os;
    }
}
