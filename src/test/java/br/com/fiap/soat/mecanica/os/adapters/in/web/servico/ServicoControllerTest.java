package br.com.fiap.soat.mecanica.os.adapters.in.web.servico;

import br.com.fiap.soat.mecanica.os.application.servico.usecase.AlterarServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.AtivarServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.BuscarServicoPorIdUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.CadastrarServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.InativarServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.ListarServicosUseCase;
import br.com.fiap.soat.mecanica.os.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.os.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.os.domain.servico.Servico;
import br.com.fiap.soat.mecanica.os.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.os.support.WebMvcSeguro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcSeguro
@WebMvcTest(value = ServicoController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
class ServicoControllerTest {

    private static final String CORPO = "{\"nome\":\"Alinhamento\",\"descricao\":\"Geometria\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CadastrarServicoUseCase cadastrarUseCase;
    @MockitoBean
    private AlterarServicoUseCase alterarUseCase;
    @MockitoBean
    private AtivarServicoUseCase ativarUseCase;
    @MockitoBean
    private InativarServicoUseCase inativarUseCase;
    @MockitoBean
    private BuscarServicoPorIdUseCase buscarPorIdUseCase;
    @MockitoBean
    private ListarServicosUseCase listarUseCase;

    private final UUID id = UUID.randomUUID();

    private static String mecanico() {
        return "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "MECANICO");
    }

    private Servico servico(StatusRecursoEnum status) {
        return Servico.reconstruir(id, status, "Alinhamento", "Geometria");
    }

    @Test
    @DisplayName("POST /servicos com MECANICO cadastra e devolve o serviço com status")
    void cadastra() throws Exception {
        when(cadastrarUseCase.executar("Alinhamento", "Geometria")).thenReturn(servico(StatusRecursoEnum.ATIVO));

        mockMvc.perform(post("/servicos").header("Authorization", mecanico())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.nome").value("Alinhamento"));
    }

    @Test
    @DisplayName("POST /servicos sem nome responde 400 e não chama o caso de uso")
    void cadastraSemNome() throws Exception {
        mockMvc.perform(post("/servicos").header("Authorization", mecanico())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cadastrarUseCase);
    }

    @Test
    @DisplayName("POST /servicos com nome repetido responde 422")
    void cadastraNomeRepetido() throws Exception {
        when(cadastrarUseCase.executar(any(), any()))
                .thenThrow(new RegraNegocioException("Serviço com esse nome já existe"));

        mockMvc.perform(post("/servicos").header("Authorization", mecanico())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("PUT /servicos/{id} altera")
    void altera() throws Exception {
        when(alterarUseCase.executar(eq(id), eq("Alinhamento"), eq("Geometria")))
                .thenReturn(servico(StatusRecursoEnum.ATIVO));

        mockMvc.perform(put("/servicos/{id}", id).header("Authorization", mecanico())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Geometria"));
    }

    @Test
    @DisplayName("PATCH ativar e inativar devolvem o serviço com o novo status")
    void ativaEInativa() throws Exception {
        when(ativarUseCase.executar(id)).thenReturn(servico(StatusRecursoEnum.ATIVO));
        when(inativarUseCase.executar(id)).thenReturn(servico(StatusRecursoEnum.INATIVO));

        mockMvc.perform(patch("/servicos/{id}/ativar", id).header("Authorization", mecanico()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATIVO"));
        mockMvc.perform(patch("/servicos/{id}/inativar", id).header("Authorization", mecanico()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INATIVO"));
    }

    @Test
    @DisplayName("GET /servicos/{id}: 200 com MECANICO, 404 quando não existe")
    void buscaPorId() throws Exception {
        when(buscarPorIdUseCase.executar(id)).thenReturn(servico(StatusRecursoEnum.ATIVO));
        UUID inexistente = UUID.randomUUID();
        when(buscarPorIdUseCase.executar(inexistente))
                .thenThrow(new RecursoNaoEncontradoException("Serviço não encontrado"));

        mockMvc.perform(get("/servicos/{id}", id).header("Authorization", mecanico()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Alinhamento"));
        mockMvc.perform(get("/servicos/{id}", inexistente).header("Authorization", mecanico()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /servicos lista os ativos; incluirInativos=true repassa o filtro")
    void lista() throws Exception {
        when(listarUseCase.executar(false)).thenReturn(List.of(servico(StatusRecursoEnum.ATIVO)));
        when(listarUseCase.executar(true))
                .thenReturn(List.of(servico(StatusRecursoEnum.ATIVO), servico(StatusRecursoEnum.INATIVO)));

        mockMvc.perform(get("/servicos").header("Authorization", mecanico()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/servicos").param("incluirInativos", "true").header("Authorization", mecanico()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Sem token 401; ATENDENTE 403 (o catálogo é do MECANICO)")
    void seguranca() throws Exception {
        mockMvc.perform(get("/servicos/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/servicos/{id}", id)
                        .header("Authorization", "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "ATENDENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(buscarPorIdUseCase);
    }
}
