package br.com.fiap.soat.mecanica.os.application.servico;

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
import br.com.fiap.soat.mecanica.os.domain.servico.ServicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicoUseCasesTest {

    @Mock
    private ServicoRepository repository;

    private final UUID id = UUID.randomUUID();

    private Servico servico(String nome, StatusRecursoEnum status) {
        return Servico.reconstruir(id, status, nome, "desc");
    }

    @BeforeEach
    void setUp() {
        // salvar devolve o que recebeu, como o adapter
        org.mockito.Mockito.lenient().when(repository.salvar(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Cadastrar: salva o serviço novo")
    void cadastra() {
        when(repository.buscarPorNome("Alinhamento")).thenReturn(Optional.empty());

        Servico salvo = new CadastrarServicoUseCase(repository).executar("Alinhamento", "d");

        assertThat(salvo.getNome()).isEqualTo("Alinhamento");
        verify(repository).salvar(any());
    }

    @Test
    @DisplayName("Cadastrar: recusa nome repetido")
    void cadastraRecusaNomeRepetido() {
        when(repository.buscarPorNome("Alinhamento")).thenReturn(Optional.of(servico("Alinhamento", StatusRecursoEnum.ATIVO)));

        assertThatThrownBy(() -> new CadastrarServicoUseCase(repository).executar("Alinhamento", "d"))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Serviço com esse nome já existe");
        verify(repository, never()).salvar(any());
    }

    @Test
    @DisplayName("Alterar: atualiza nome e descrição")
    void altera() {
        when(repository.buscarPorNome("Novo")).thenReturn(Optional.empty());
        when(repository.buscarPorId(id)).thenReturn(Optional.of(servico("Antigo", StatusRecursoEnum.ATIVO)));

        Servico alterado = new AlterarServicoUseCase(repository).executar(id, "Novo", "nova");

        assertThat(alterado.getNome()).isEqualTo("Novo");
        assertThat(alterado.getDescricao()).isEqualTo("nova");
    }

    @Test
    @DisplayName("Alterar: aceita manter o próprio nome")
    void alteraMantendoONome() {
        when(repository.buscarPorNome("Mesmo")).thenReturn(Optional.of(servico("Mesmo", StatusRecursoEnum.ATIVO)));
        when(repository.buscarPorId(id)).thenReturn(Optional.of(servico("Mesmo", StatusRecursoEnum.ATIVO)));

        assertThat(new AlterarServicoUseCase(repository).executar(id, "Mesmo", "x").getDescricao()).isEqualTo("x");
    }

    @Test
    @DisplayName("Alterar: recusa o nome de outro serviço")
    void alteraRecusaNomeDeOutro() {
        Servico outro = Servico.reconstruir(UUID.randomUUID(), StatusRecursoEnum.ATIVO, "Outro", null);
        when(repository.buscarPorNome("Outro")).thenReturn(Optional.of(outro));

        assertThatThrownBy(() -> new AlterarServicoUseCase(repository).executar(id, "Outro", "x"))
                .isInstanceOf(RegraNegocioException.class);
        verify(repository, never()).salvar(any());
    }

    @Test
    @DisplayName("Alterar: 404 quando o serviço não existe")
    void alteraNaoEncontrado() {
        when(repository.buscarPorNome("Novo")).thenReturn(Optional.empty());
        when(repository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AlterarServicoUseCase(repository).executar(id, "Novo", "x"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Ativar e inativar trocam o status")
    void ativaEInativa() {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(servico("S", StatusRecursoEnum.INATIVO)));
        assertThat(new AtivarServicoUseCase(repository).executar(id).isAtivo()).isTrue();

        when(repository.buscarPorId(id)).thenReturn(Optional.of(servico("S", StatusRecursoEnum.ATIVO)));
        assertThat(new InativarServicoUseCase(repository).executar(id).isInativo()).isTrue();
    }

    @Test
    @DisplayName("Ativar e inativar: 404 quando o serviço não existe")
    void ativaEInativaNaoEncontrado() {
        when(repository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AtivarServicoUseCase(repository).executar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> new InativarServicoUseCase(repository).executar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Buscar por id: devolve o serviço, mesmo inativo, ou 404")
    void buscaPorId() {
        when(repository.buscarPorId(id)).thenReturn(Optional.of(servico("S", StatusRecursoEnum.INATIVO)));
        assertThat(new BuscarServicoPorIdUseCase(repository).executar(id).getNome()).isEqualTo("S");

        when(repository.buscarPorId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new BuscarServicoPorIdUseCase(repository).executar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar: só os ativos, a menos que peça os inativos")
    void lista() {
        Servico ativo = servico("A", StatusRecursoEnum.ATIVO);
        Servico inativo = servico("B", StatusRecursoEnum.INATIVO);
        when(repository.buscarTodos()).thenReturn(List.of(ativo, inativo));

        assertThat(new ListarServicosUseCase(repository).executar(false)).containsExactly(ativo);
        assertThat(new ListarServicosUseCase(repository).executar(true)).containsExactly(ativo, inativo);
    }
}
