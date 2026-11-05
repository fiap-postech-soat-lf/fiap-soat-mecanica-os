package br.com.fiap.soat.mecanica.os.adapters.in.web.security;

import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.os.support.JwtTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de perfis (tabela 3.2 da F4) das rotas existentes: para cada rota, sem token -> 401, perfil errado -> 403
 * e perfil certo passa da seguranca (2xx, 404 ou 422; nunca 401, 403 ou 5xx). Roda no contexto completo com o
 * seed e em transacao com rollback, entao as rotas de escrita nao deixam dados. As rotas de escrita da OS entram
 * na matriz quando existirem (T03 e T04).
 */
@Transactional
class PerfisIT extends IntegracaoTest {

    static final String SERVICO_SEED = "77777777-7777-7777-7777-777777777777";
    static final String CLIENTE_ID = "33333333-3333-3333-3333-333333333333";

    static final String CLIENTE = "CLIENTE";
    static final String ATENDENTE = "ATENDENTE";
    static final String MECANICO = "MECANICO";
    static final String ALMOXARIFE = "ALMOXARIFE";
    static final List<String> PERFIS = List.of(CLIENTE, ATENDENTE, MECANICO, ALMOXARIFE);

    record Rota(HttpMethod metodo, String caminho, String corpo, Set<String> permitidos) {
        @Override
        public String toString() {
            return metodo + " " + caminho + " -> " + permitidos;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    static Stream<Rota> rotas() {
        String id = UUID.randomUUID().toString();
        String novoServico = "{\"nome\":\"Alinhamento\",\"descricao\":\"Geometria\"}";
        String servicoAlterado = "{\"nome\":\"Troca de oleo sintetico\",\"descricao\":\"Novo\"}";
        Set<String> atendenteOuMecanico = Set.of(ATENDENTE, MECANICO);
        Set<String> soMecanico = Set.of(MECANICO);
        return Stream.of(
                new Rota(HttpMethod.GET, "/ordem-servicos", null, atendenteOuMecanico),
                new Rota(HttpMethod.GET, "/ordem-servicos/" + id, null, atendenteOuMecanico),
                new Rota(HttpMethod.GET, "/ordem-servicos/" + id + "/historico", null, atendenteOuMecanico),
                new Rota(HttpMethod.GET, "/ordem-servicos/minhas", null, Set.of(CLIENTE)),
                new Rota(HttpMethod.POST, "/servicos", novoServico, soMecanico),
                new Rota(HttpMethod.PUT, "/servicos/" + SERVICO_SEED, servicoAlterado, soMecanico),
                new Rota(HttpMethod.PATCH, "/servicos/" + SERVICO_SEED + "/ativar", null, soMecanico),
                new Rota(HttpMethod.PATCH, "/servicos/" + SERVICO_SEED + "/inativar", null, soMecanico),
                new Rota(HttpMethod.GET, "/servicos/" + SERVICO_SEED, null, soMecanico),
                new Rota(HttpMethod.GET, "/servicos", null, soMecanico)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rotas")
    @DisplayName("sem token responde 401")
    void semTokenResponde401(Rota rota) throws Exception {
        mockMvc.perform(requisicao(rota, null)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rotas")
    @DisplayName("perfil errado responde 403")
    void perfilErradoResponde403(Rota rota) throws Exception {
        for (String perfil : PERFIS) {
            if (!rota.permitidos().contains(perfil)) {
                mockMvc.perform(requisicao(rota, perfil)).andExpect(status().isForbidden());
            }
        }
    }

    /** Um caso por (rota, perfil permitido): cada um roda na sua transacao, entao POSTs repetidos nao colidem. */
    static Stream<Arguments> rotasEPerfisPermitidos() {
        return rotas().flatMap(rota -> rota.permitidos().stream().sorted()
                .map(perfil -> Arguments.of(Named.of(rota.metodo() + " " + rota.caminho(), rota), perfil)));
    }

    @ParameterizedTest(name = "{0} com {1}")
    @MethodSource("rotasEPerfisPermitidos")
    @DisplayName("perfil certo passa da seguranca (2xx, 404 ou 422)")
    void perfilCertoPassa(Rota rota, String perfil) throws Exception {
        int status = mockMvc.perform(requisicao(rota, perfil)).andReturn().getResponse().getStatus();
        assertThat(status).as("%s com %s", rota, perfil).satisfiesAnyOf(
                s -> assertThat(s).isBetween(200, 299),
                s -> assertThat(s).isIn(404, 422));
    }

    @Test
    @DisplayName("GET /servicos/{id} com MECANICO devolve o serviço do seed; com ATENDENTE, 403")
    void catalogoEhDoMecanico() throws Exception {
        mockMvc.perform(get("/servicos/" + SERVICO_SEED).header("Authorization", "Bearer " + token(MECANICO)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.nome").value("Troca de oleo"));
        mockMvc.perform(get("/servicos/" + SERVICO_SEED).header("Authorization", "Bearer " + token(ATENDENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET por placa é público: sem token e com qualquer perfil responde 200")
    void consultaPorPlacaEPublica() throws Exception {
        mockMvc.perform(get("/ordem-servicos/veiculo/placa/ABC1D23")).andExpect(status().isOk());
        for (String perfil : PERFIS) {
            mockMvc.perform(get("/ordem-servicos/veiculo/placa/ABC1D23")
                    .header("Authorization", "Bearer " + token(perfil))).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Placa inválida na rota pública responde 422 e a rota só é pública para GET")
    void placaInvalidaEMetodoDiferente() throws Exception {
        mockMvc.perform(get("/ordem-servicos/veiculo/placa/XYZ")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/ordem-servicos/veiculo/placa/ABC1D23")).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder requisicao(Rota rota, String perfil) {
        MockHttpServletRequestBuilder builder = request(rota.metodo(), rota.caminho());
        if (rota.corpo() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(rota.corpo());
        }
        if (perfil != null) {
            builder.header("Authorization", "Bearer " + token(perfil));
        }
        return builder;
    }

    private static String token(String perfil) {
        return CLIENTE.equals(perfil)
                ? JwtTestTokens.cliente(UUID.fromString(CLIENTE_ID))
                : JwtTestTokens.funcionario(UUID.randomUUID(), perfil);
    }
}
