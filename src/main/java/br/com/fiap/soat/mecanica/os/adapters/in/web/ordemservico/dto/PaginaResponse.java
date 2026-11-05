package br.com.fiap.soat.mecanica.os.adapters.in.web.ordemservico.dto;

import java.util.List;

public record PaginaResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
