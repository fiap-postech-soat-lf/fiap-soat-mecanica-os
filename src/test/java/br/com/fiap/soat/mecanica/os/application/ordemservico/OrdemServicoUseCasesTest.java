package br.com.fiap.soat.mecanica.os.application.ordemservico;

import br.com.fiap.soat.mecanica.os.application.ordemservico.dto.OrdemServicoDetalhe;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.BuscarHistoricoOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.BuscarOrdemServicoPorIdUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ConsultarSituacaoPorPlacaUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ListarMinhasOrdensServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ListarOrdensServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.os.domain.Pagina;
import br.com.fiap.soat.mecanica.os.domain.Paginacao;
import br.com.fiap.soat.mecanica.os.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.os.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaInstancia;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.CLIENTE_ID;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.PLACA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.osEm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdemServicoUseCasesTest {

    @Mock
    private OrdemServicoRepository ordemServicoRepository;
    @Mock
    private SagaRepository sagaRepository;
    @Mock
    private UsuarioAutenticadoPort usuarioAutenticado;

    @Test
    @DisplayName("Listar: repassa a paginação ao repositório")
    void lista() {
        Paginacao paginacao = new Paginacao(1, 5);
        Pagina<OrdemServico> pagina = new Pagina<>(List.of(osEm(SituacaoOrdemServico.RECEBIDA)), 1, 5, 6, 2, false, true);
        when(ordemServicoRepository.listar(paginacao)).thenReturn(pagina);

        assertThat(new ListarOrdensServicoUseCase(ordemServicoRepository).executar(paginacao)).isSameAs(pagina);
    }

    @Test
    @DisplayName("Buscar por id: junta a OS com a Saga")
    void buscaComSaga() {
        OrdemServico os = osEm(SituacaoOrdemServico.EM_DIAGNOSTICO);
        SagaInstancia saga = SagaInstancia.iniciar(os.getId(), Duration.ofMinutes(2), RELOGIO);
        when(ordemServicoRepository.buscarPorId(os.getId())).thenReturn(Optional.of(os));
        when(sagaRepository.buscarPorOsId(os.getId())).thenReturn(Optional.of(saga));

        OrdemServicoDetalhe detalhe = new BuscarOrdemServicoPorIdUseCase(ordemServicoRepository, sagaRepository)
                .executar(os.getId());

        assertThat(detalhe.ordemServico()).isSameAs(os);
        assertThat(detalhe.saga()).isSameAs(saga);
    }

    @Test
    @DisplayName("Buscar por id: OS sem Saga volta com saga nula")
    void buscaSemSaga() {
        OrdemServico os = osEm(SituacaoOrdemServico.RECEBIDA);
        when(ordemServicoRepository.buscarPorId(os.getId())).thenReturn(Optional.of(os));
        when(sagaRepository.buscarPorOsId(os.getId())).thenReturn(Optional.empty());

        assertThat(new BuscarOrdemServicoPorIdUseCase(ordemServicoRepository, sagaRepository)
                .executar(os.getId()).saga()).isNull();
    }

    @Test
    @DisplayName("Buscar por id: 404 quando a OS não existe")
    void buscaNaoEncontrada() {
        UUID id = UUID.randomUUID();
        when(ordemServicoRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new BuscarOrdemServicoPorIdUseCase(ordemServicoRepository, sagaRepository).executar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(sagaRepository, never()).buscarPorOsId(id);
    }

    @Test
    @DisplayName("Histórico: devolve as mudanças da OS")
    void historico() {
        OrdemServico os = osEm(SituacaoOrdemServico.EM_DIAGNOSTICO);
        List<HistoricoSituacao> linhas = List.of(new HistoricoSituacao(UUID.randomUUID(), os.getId(), null,
                SituacaoOrdemServico.RECEBIDA, null, AGORA));
        when(ordemServicoRepository.buscarPorId(os.getId())).thenReturn(Optional.of(os));
        when(ordemServicoRepository.buscarHistorico(os.getId())).thenReturn(linhas);

        assertThat(new BuscarHistoricoOrdemServicoUseCase(ordemServicoRepository).executar(os.getId()))
                .isEqualTo(linhas);
    }

    @Test
    @DisplayName("Histórico: 404 quando a OS não existe")
    void historicoNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(ordemServicoRepository.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new BuscarHistoricoOrdemServicoUseCase(ordemServicoRepository).executar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(ordemServicoRepository, never()).buscarHistorico(id);
    }

    @Test
    @DisplayName("Minhas: usa o sub do JWT como clienteId")
    void minhas() {
        List<OrdemServico> ordens = List.of(osEm(SituacaoOrdemServico.RECEBIDA));
        when(usuarioAutenticado.sub()).thenReturn(CLIENTE_ID);
        when(ordemServicoRepository.buscarPorClienteId(CLIENTE_ID)).thenReturn(ordens);

        assertThat(new ListarMinhasOrdensServicoUseCase(ordemServicoRepository, usuarioAutenticado).executar())
                .isEqualTo(ordens);
    }

    @Test
    @DisplayName("Situação por placa: normaliza a placa antes de buscar")
    void situacaoPorPlaca() {
        List<OrdemServico> ordens = List.of(osEm(SituacaoOrdemServico.EM_EXECUCAO));
        when(ordemServicoRepository.buscarPorPlaca(PLACA)).thenReturn(ordens);

        assertThat(new ConsultarSituacaoPorPlacaUseCase(ordemServicoRepository).executar("abc-1d23"))
                .isEqualTo(ordens);
    }

    @Test
    @DisplayName("Situação por placa: placa inválida é recusada sem consultar o banco")
    void situacaoPorPlacaInvalida() {
        assertThatThrownBy(() -> new ConsultarSituacaoPorPlacaUseCase(ordemServicoRepository).executar("XYZ"))
                .isInstanceOf(RegraNegocioException.class);
        verify(ordemServicoRepository, never()).buscarPorPlaca("XYZ");
    }
}
