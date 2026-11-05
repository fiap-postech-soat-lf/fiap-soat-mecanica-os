package br.com.fiap.soat.mecanica.os.domain.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.Pagina;
import br.com.fiap.soat.mecanica.os.domain.Paginacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrdemServicoRepository {

    OrdemServico salvar(OrdemServico ordemServico);

    Optional<OrdemServico> buscarPorId(UUID id);

    /** Da mais recente para a mais antiga. */
    List<OrdemServico> buscarPorPlaca(String placa);

    /** Da mais recente para a mais antiga. */
    List<OrdemServico> buscarPorClienteId(UUID clienteId);

    /** Ordens em andamento (sem ENTREGUE e CANCELADA), da mais recente para a mais antiga. */
    Pagina<OrdemServico> listar(Paginacao paginacao);

    void registrarHistorico(UUID osId, MudancaSituacao mudanca);

    /** Em ordem cronologica. */
    List<HistoricoSituacao> buscarHistorico(UUID osId);
}
