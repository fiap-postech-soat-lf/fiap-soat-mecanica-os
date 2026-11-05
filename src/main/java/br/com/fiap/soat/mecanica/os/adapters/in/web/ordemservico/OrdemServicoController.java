package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico;

import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.HistoricoSituacaoResponse;
import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.OrdemServicoResponse;
import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.MotivoRequest;
import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.PaginaResponse;
import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.SituacaoPublicaResponse;
import br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto.AbrirOrdemServicoRequest;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.AbrirOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.BuscarHistoricoOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.BuscarOrdemServicoPorIdUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.CancelarOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ConsultarSituacaoPorPlacaUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ListarMinhasOrdensServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.ListarOrdensServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.ordemservico.usecase.RevisarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.os.domain.Pagina;
import br.com.fiap.soat.mecanica.os.domain.Paginacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Abertura, consultas e as duas ações que compensam a Saga (cancelar e revisar o orçamento). */
@RestController
@RequestMapping("/ordem-servicos")
@RequiredArgsConstructor
@Validated
public class OrdemServicoController {

    private static final String ATENDENTE_OU_MECANICO = "hasAnyRole('ATENDENTE', 'MECANICO')";

    private final AbrirOrdemServicoUseCase abrirUseCase;
    private final ListarOrdensServicoUseCase listarUseCase;
    private final BuscarOrdemServicoPorIdUseCase buscarPorIdUseCase;
    private final BuscarHistoricoOrdemServicoUseCase buscarHistoricoUseCase;
    private final ListarMinhasOrdensServicoUseCase listarMinhasUseCase;
    private final ConsultarSituacaoPorPlacaUseCase consultarSituacaoPorPlacaUseCase;
    private final CancelarOrdemServicoUseCase cancelarUseCase;
    private final RevisarOrcamentoUseCase revisarOrcamentoUseCase;

    @PostMapping
    @PreAuthorize(ATENDENTE_OU_MECANICO)
    @Operation(summary = "Abrir uma ordem de serviço (consulta o veículo no Identity e inicia a Saga)")
    public ResponseEntity<OrdemServicoResponse> abrir(@Valid @RequestBody AbrirOrdemServicoRequest request) {
        OrdemServico os = abrirUseCase.executar(request.veiculoId(), request.observacao());
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(os.getId()).toUri();
        return ResponseEntity.created(local).body(OrdemServicoResponseMapper.toResponse(os));
    }

    @GetMapping
    @PreAuthorize(ATENDENTE_OU_MECANICO)
    @Operation(summary = "Listar as ordens de serviço em andamento, da mais recente para a mais antiga")
    public ResponseEntity<PaginaResponse<OrdemServicoResponse>> listar(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pagina<OrdemServico> resultado = listarUseCase.executar(new Paginacao(page, size));
        return ResponseEntity.ok(new PaginaResponse<>(
                resultado.content().stream().map(OrdemServicoResponseMapper::toResponse).toList(),
                resultado.page(), resultado.size(), resultado.totalElements(), resultado.totalPages(),
                resultado.first(), resultado.last()));
    }

    @GetMapping("/minhas")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Listar as ordens de serviço do cliente autenticado")
    public ResponseEntity<List<OrdemServicoResponse>> minhas() {
        return ResponseEntity.ok(listarMinhasUseCase.executar().stream()
                .map(OrdemServicoResponseMapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize(ATENDENTE_OU_MECANICO)
    @Operation(summary = "Buscar uma ordem de serviço por ID, com o andamento da Saga")
    public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(OrdemServicoResponseMapper.toResponse(buscarPorIdUseCase.executar(id)));
    }

    @GetMapping("/{id}/historico")
    @PreAuthorize(ATENDENTE_OU_MECANICO)
    @Operation(summary = "Histórico de situações da ordem de serviço, em ordem cronológica")
    public ResponseEntity<List<HistoricoSituacaoResponse>> historico(@PathVariable UUID id) {
        return ResponseEntity.ok(buscarHistoricoUseCase.executar(id).stream()
                .map(OrdemServicoResponseMapper::toResponse).toList());
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize(ATENDENTE_OU_MECANICO)
    @Operation(summary = "Cancelar a ordem de serviço (até o orçamento ser aprovado); a Saga desfaz o que já foi feito")
    public ResponseEntity<OrdemServicoResponse> cancelar(@PathVariable UUID id,
                                                         @Valid @RequestBody(required = false) MotivoRequest request) {
        return ResponseEntity.ok(OrdemServicoResponseMapper.toResponse(
                cancelarUseCase.executar(id, request == null ? null : request.motivo())));
    }

    @PatchMapping("/{id}/revisar-orcamento")
    @PreAuthorize(ATENDENTE_OU_MECANICO)
    @Operation(summary = "Devolver a ordem de serviço ao diagnóstico para revisar o orçamento (antes da aprovação)")
    public ResponseEntity<OrdemServicoResponse> revisarOrcamento(
            @PathVariable UUID id, @Valid @RequestBody(required = false) MotivoRequest request) {
        return ResponseEntity.ok(OrdemServicoResponseMapper.toResponse(
                revisarOrcamentoUseCase.executar(id, request == null ? null : request.motivo())));
    }

    /** Rota publica (SecurityConfig): so situacao e datas. */
    @GetMapping("/veiculo/placa/{placa}")
    @Operation(summary = "Consultar a situação das ordens de serviço pela placa (público)")
    public ResponseEntity<List<SituacaoPublicaResponse>> situacaoPorPlaca(@PathVariable String placa) {
        return ResponseEntity.ok(consultarSituacaoPorPlacaUseCase.executar(placa).stream()
                .map(OrdemServicoResponseMapper::toSituacaoPublica).toList());
    }
}
