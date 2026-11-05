package br.com.fiap.soat.mecanica.os.adapters.out.persistence;

import br.com.fiap.soat.mecanica.os.domain.enums.StatusRecursoEnum;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Soft delete dos cadastros (decisao fixa #10): so o catalogo de servicos herda. */
@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
public abstract class PrincipalEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    protected StatusRecursoEnum status = StatusRecursoEnum.ATIVO;
}
