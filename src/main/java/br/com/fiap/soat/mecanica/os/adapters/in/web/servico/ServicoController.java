package br.com.fiap.soat.mecanica.os.adapters.in.web.servico;

import br.com.fiap.soat.mecanica.os.adapters.in.web.servico.dto.ServicoRequest;
import br.com.fiap.soat.mecanica.os.adapters.in.web.servico.dto.ServicoResponse;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.AlterarServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.AtivarServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.BuscarServicoPorIdUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.CadastrarServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.InativarServicoUseCase;
import br.com.fiap.soat.mecanica.os.application.servico.usecase.ListarServicosUseCase;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Catalogo de servicos: so o MECANICO (o Execucao repassa o JWT dele em GET /servicos/{id}, F5). */
@RestController
@RequestMapping("/servicos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MECANICO')")
public class ServicoController {

    private final CadastrarServicoUseCase cadastrarUseCase;
    private final AlterarServicoUseCase alterarUseCase;
    private final AtivarServicoUseCase ativarUseCase;
    private final InativarServicoUseCase inativarUseCase;
    private final BuscarServicoPorIdUseCase buscarPorIdUseCase;
    private final ListarServicosUseCase listarUseCase;

    @PostMapping
    @Operation(summary = "Cadastrar um serviço")
    public ResponseEntity<ServicoResponse> cadastrar(@Valid @RequestBody ServicoRequest request) {
        return ResponseEntity.ok(ServicoResponseMapper.toResponse(
                cadastrarUseCase.executar(request.nome(), request.descricao())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Alterar um serviço")
    public ResponseEntity<ServicoResponse> alterar(@PathVariable UUID id, @Valid @RequestBody ServicoRequest request) {
        return ResponseEntity.ok(ServicoResponseMapper.toResponse(
                alterarUseCase.executar(id, request.nome(), request.descricao())));
    }

    @PatchMapping("/{id}/ativar")
    @Operation(summary = "Ativar um serviço")
    public ResponseEntity<ServicoResponse> ativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ServicoResponseMapper.toResponse(ativarUseCase.executar(id)));
    }

    @PatchMapping("/{id}/inativar")
    @Operation(summary = "Inativar um serviço")
    public ResponseEntity<ServicoResponse> inativar(@PathVariable UUID id) {
        return ResponseEntity.ok(ServicoResponseMapper.toResponse(inativarUseCase.executar(id)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar um serviço por ID")
    public ResponseEntity<ServicoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(ServicoResponseMapper.toResponse(buscarPorIdUseCase.executar(id)));
    }

    @GetMapping
    @Operation(summary = "Listar os serviços ativos (incluirInativos=true traz todos)")
    public ResponseEntity<List<ServicoResponse>> listar(
            @RequestParam(defaultValue = "false") boolean incluirInativos) {
        return ResponseEntity.ok(listarUseCase.executar(incluirInativos).stream()
                .map(ServicoResponseMapper::toResponse).toList());
    }
}
