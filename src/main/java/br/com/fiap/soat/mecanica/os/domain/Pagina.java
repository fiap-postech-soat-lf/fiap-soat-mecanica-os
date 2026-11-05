package br.com.fiap.soat.mecanica.os.domain;

import java.util.List;

public record Pagina<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
