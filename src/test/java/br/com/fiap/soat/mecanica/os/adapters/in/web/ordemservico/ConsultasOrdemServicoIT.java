package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.os.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.ATENDENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.CLIENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.PLACA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.VEICULO_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** As consultas da OS de ponta a ponta (controller, casos de uso e banco), com a Saga em JSONB. */
@Transactional
class ConsultasOrdemServicoIT extends IntegracaoTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrdemServicoRepository ordemServicoRepository;
    @Autowired
    private SagaRepository sagaRepository;

    private static String atendente() {
        return "Bearer " + JwtTestTokens.funcionario(ATENDENTE_ID, "ATENDENTE");
    }

    private OrdemServico abrir(long segundos, UUID clienteId) {
        Clock relogio = Clock.fixed(AGORA.plusSeconds(segundos), ZoneOffset.UTC);
        OrdemServico os = ordemServicoRepository.salvar(
                new OrdemServico("Barulho no motor", VEICULO_ID, clienteId, PLACA, ATENDENTE_ID, relogio));
        ordemServicoRepository.registrarHistorico(os.getId(), os.mudancaDeAbertura());
        return os;
    }

    @Test
    void buscaPorIdTrazOAndamentoDaSaga() throws Exception {
        OrdemServico os = abrir(0, CLIENTE_ID);
        SagaInstancia saga = SagaInstancia.iniciar(os.getId(), Duration.ofMinutes(2), RELOGIO);
        UUID orcamentoId = UUID.randomUUID();
        saga.getContexto().setOrcamentoId(orcamentoId);
        saga.avancarPara(PassoSaga.AGUARDAR_APROVACAO, null, RELOGIO);
        sagaRepository.salvar(saga);

        mockMvc.perform(get("/ordem-servicos/{id}", os.getId()).header("Authorization", atendente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(os.getId().toString()))
                .andExpect(jsonPath("$.situacao").value("RECEBIDA"))
                .andExpect(jsonPath("$.clienteId").value(CLIENTE_ID.toString()))
                .andExpect(jsonPath("$.placa").value(PLACA))
                .andExpect(jsonPath("$.valorTotal").value(BigDecimal.ZERO.intValue()))
                .andExpect(jsonPath("$.sagaId").value(saga.getId().toString()))
                .andExpect(jsonPath("$.sagaStatus").value(SagaStatus.INICIADA.name()))
                .andExpect(jsonPath("$.passoAtual").value("AGUARDAR_APROVACAO"))
                .andExpect(jsonPath("$.orcamentoId").value(orcamentoId.toString()))
                .andExpect(jsonPath("$.dataRecebida").value(AGORA.toString()));
    }

    @Test
    void buscaPorIdInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/ordem-servicos/{id}", UUID.randomUUID()).header("Authorization", atendente()))
                .andExpect(status().isNotFound());
    }

    @Test
    void historicoVoltaEmOrdemComAAberturaPrimeiro() throws Exception {
        OrdemServico os = abrir(0, CLIENTE_ID);
        ordemServicoRepository.registrarHistorico(os.getId(),
                os.iniciarDiagnostico(Clock.fixed(AGORA.plusSeconds(30), ZoneOffset.UTC)));

        mockMvc.perform(get("/ordem-servicos/{id}/historico", os.getId()).header("Authorization", atendente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].de").doesNotExist())
                .andExpect(jsonPath("$[0].para").value("RECEBIDA"))
                .andExpect(jsonPath("$[1].de").value("RECEBIDA"))
                .andExpect(jsonPath("$[1].para").value("EM_DIAGNOSTICO"));
    }

    @Test
    void listaPaginadaSemAsEncerradas() throws Exception {
        OrdemServico antiga = abrir(0, CLIENTE_ID);
        OrdemServico recente = abrir(60, CLIENTE_ID);
        OrdemServico cancelada = abrir(120, CLIENTE_ID);
        cancelada.cancelar("desistência", RELOGIO);
        ordemServicoRepository.salvar(cancelada);

        mockMvc.perform(get("/ordem-servicos").param("size", "10").header("Authorization", atendente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(recente.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(antiga.getId().toString()));
    }

    @Test
    void minhasDevolveSoAsDoClienteDoToken() throws Exception {
        OrdemServico minha = abrir(0, CLIENTE_ID);
        abrir(10, UUID.randomUUID());

        mockMvc.perform(get("/ordem-servicos/minhas")
                        .header("Authorization", "Bearer " + JwtTestTokens.cliente(CLIENTE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(minha.getId().toString()));
    }

    @Test
    void placaPublicaDevolveSoSituacaoEDatas() throws Exception {
        abrir(0, CLIENTE_ID);

        mockMvc.perform(get("/ordem-servicos/veiculo/placa/{placa}", "abc-1d23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].situacao").value("RECEBIDA"))
                .andExpect(jsonPath("$[0].dataRecebida").value(AGORA.toString()))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].clienteId").doesNotExist())
                .andExpect(jsonPath("$[0].valorTotal").doesNotExist())
                .andExpect(jsonPath("$[0].observacao").doesNotExist());
        mockMvc.perform(get("/ordem-servicos/veiculo/placa/{placa}", "ZZZ9Z99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
