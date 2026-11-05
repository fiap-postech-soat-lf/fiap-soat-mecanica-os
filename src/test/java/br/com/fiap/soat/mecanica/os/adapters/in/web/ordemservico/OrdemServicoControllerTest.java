package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico;

import br.com.fiap.soat.mecanica.os.adapters.in.web.security.UsuarioAutenticadoAdapter;
import br.com.fiap.soat.mecanica.os.application.ordemservico.dto.OrdemServicoDetalhe;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.BuscarHistoricoOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.BuscarOrdemServicoPorIdUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ConsultarSituacaoPorPlacaUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ListarMinhasOrdensServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ListarOrdensServicoUseCase;
import br.com.fiap.soat.mecanica.os.domain.Pagina;
import br.com.fiap.soat.mecanica.os.domain.Paginacao;
import br.com.fiap.soat.mecanica.os.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.os.support.WebMvcSeguro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.CLIENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.PLACA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.osEm;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcSeguro
@WebMvcTest(value = OrdemServicoController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@org.springframework.context.annotation.Import(UsuarioAutenticadoAdapter.class)
class OrdemServicoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ListarOrdensServicoUseCase listarUseCase;
    @MockitoBean
    private BuscarOrdemServicoPorIdUseCase buscarPorIdUseCase;
    @MockitoBean
    private BuscarHistoricoOrdemServicoUseCase historicoUseCase;
    @MockitoBean
    private ListarMinhasOrdensServicoUseCase minhasUseCase;
    @MockitoBean
    private ConsultarSituacaoPorPlacaUseCase placaUseCase;

    private static String funcionario(String cargo) {
        return "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), cargo);
    }

    @Test
    @DisplayName("GET /ordem-servicos pagina e usa o default 0/20")
    void lista() throws Exception {
        OrdemServico os = osEm(SituacaoOrdemServico.EM_DIAGNOSTICO);
        when(listarUseCase.executar(new Paginacao(0, 20)))
                .thenReturn(new Pagina<>(List.of(os), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/ordem-servicos").header("Authorization", funcionario("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(os.getId().toString()))
                .andExpect(jsonPath("$.content[0].situacao").value("EM_DIAGNOSTICO"))
                .andExpect(jsonPath("$.content[0].clienteId").value(CLIENTE_ID.toString()))
                .andExpect(jsonPath("$.content[0].placa").value(PLACA))
                .andExpect(jsonPath("$.content[0].sagaId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("GET /ordem-servicos com size acima de 100 responde 400")
    void listaTamanhoInvalido() throws Exception {
        mockMvc.perform(get("/ordem-servicos").param("size", "101").header("Authorization", funcionario("MECANICO")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listarUseCase);
    }

    @Test
    @DisplayName("GET /ordem-servicos/{id} traz clienteId, placa e o andamento da Saga")
    void buscaPorId() throws Exception {
        OrdemServico os = osEm(SituacaoOrdemServico.AGUARDANDO_APROVACAO);
        SagaInstancia saga = SagaInstancia.iniciar(os.getId(), Duration.ofMinutes(2), RELOGIO);
        UUID orcamentoId = UUID.randomUUID();
        saga.getContexto().setOrcamentoId(orcamentoId);
        when(buscarPorIdUseCase.executar(os.getId())).thenReturn(new OrdemServicoDetalhe(os, saga));

        mockMvc.perform(get("/ordem-servicos/{id}", os.getId()).header("Authorization", funcionario("MECANICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("AGUARDANDO_APROVACAO"))
                .andExpect(jsonPath("$.clienteId").value(CLIENTE_ID.toString()))
                .andExpect(jsonPath("$.placa").value(PLACA))
                .andExpect(jsonPath("$.valorTotal").value(0))
                .andExpect(jsonPath("$.sagaId").value(saga.getId().toString()))
                .andExpect(jsonPath("$.sagaStatus").value("INICIADA"))
                .andExpect(jsonPath("$.passoAtual").value("ABRIR_DIAGNOSTICO"))
                .andExpect(jsonPath("$.orcamentoId").value(orcamentoId.toString()));
    }

    @Test
    @DisplayName("GET /ordem-servicos/{id} sem Saga omite os campos da Saga")
    void buscaPorIdSemSaga() throws Exception {
        OrdemServico os = osEm(SituacaoOrdemServico.RECEBIDA);
        when(buscarPorIdUseCase.executar(os.getId())).thenReturn(new OrdemServicoDetalhe(os, null));

        mockMvc.perform(get("/ordem-servicos/{id}", os.getId()).header("Authorization", funcionario("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sagaId").doesNotExist())
                .andExpect(jsonPath("$.orcamentoId").doesNotExist());
    }

    @Test
    @DisplayName("GET /ordem-servicos/{id} inexistente responde 404")
    void buscaPorIdNaoEncontrada() throws Exception {
        UUID id = UUID.randomUUID();
        when(buscarPorIdUseCase.executar(id)).thenThrow(new RecursoNaoEncontradoException("Ordem de serviço não encontrada"));

        mockMvc.perform(get("/ordem-servicos/{id}", id).header("Authorization", funcionario("ATENDENTE")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /ordem-servicos/{id}/historico devolve as mudanças em ordem")
    void historico() throws Exception {
        UUID id = UUID.randomUUID();
        when(historicoUseCase.executar(id)).thenReturn(List.of(
                new HistoricoSituacao(UUID.randomUUID(), id, null, SituacaoOrdemServico.RECEBIDA, null, AGORA),
                new HistoricoSituacao(UUID.randomUUID(), id, SituacaoOrdemServico.RECEBIDA,
                        SituacaoOrdemServico.CANCELADA, "desistência", AGORA.plusSeconds(5))));

        mockMvc.perform(get("/ordem-servicos/{id}/historico", id).header("Authorization", funcionario("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].de").doesNotExist())
                .andExpect(jsonPath("$[0].para").value("RECEBIDA"))
                .andExpect(jsonPath("$[1].de").value("RECEBIDA"))
                .andExpect(jsonPath("$[1].motivo").value("desistência"));
    }

    @Test
    @DisplayName("GET /ordem-servicos/minhas é do CLIENTE; funcionário recebe 403")
    void minhas() throws Exception {
        when(minhasUseCase.executar()).thenReturn(List.of(osEm(SituacaoOrdemServico.EM_EXECUCAO)));

        mockMvc.perform(get("/ordem-servicos/minhas")
                        .header("Authorization", "Bearer " + JwtTestTokens.cliente(CLIENTE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].situacao").value("EM_EXECUCAO"));
        mockMvc.perform(get("/ordem-servicos/minhas").header("Authorization", funcionario("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET por placa é público e devolve só a situação e as datas")
    void situacaoPorPlaca() throws Exception {
        when(placaUseCase.executar(PLACA)).thenReturn(List.of(osEm(SituacaoOrdemServico.EM_EXECUCAO)));

        mockMvc.perform(get("/ordem-servicos/veiculo/placa/{placa}", PLACA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].situacao").value("EM_EXECUCAO"))
                .andExpect(jsonPath("$[0].dataRecebida").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].clienteId").doesNotExist())
                .andExpect(jsonPath("$[0].veiculoId").doesNotExist())
                .andExpect(jsonPath("$[0].usuarioAberturaId").doesNotExist())
                .andExpect(jsonPath("$[0].valorTotal").doesNotExist())
                .andExpect(jsonPath("$[0].observacao").doesNotExist())
                .andExpect(jsonPath("$[0].placa").doesNotExist());
    }

    @Test
    @DisplayName("GET por placa inválida responde 422")
    void situacaoPorPlacaInvalida() throws Exception {
        when(placaUseCase.executar("XYZ")).thenThrow(new RegraNegocioException("Placa inválida"));

        mockMvc.perform(get("/ordem-servicos/veiculo/placa/{placa}", "XYZ"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Rotas autenticadas sem token respondem 401; CLIENTE nas rotas de funcionário, 403")
    void seguranca() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/ordem-servicos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/ordem-servicos/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/ordem-servicos/minhas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/ordem-servicos/{id}/historico", id)
                        .header("Authorization", "Bearer " + JwtTestTokens.cliente(CLIENTE_ID)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(listarUseCase, buscarPorIdUseCase, historicoUseCase);
    }
}
