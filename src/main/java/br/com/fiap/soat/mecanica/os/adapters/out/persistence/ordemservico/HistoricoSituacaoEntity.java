package br.com.fiap.soat.mecanica.os.adapters.out.persistence.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historico_situacao_os")
@Getter
@Setter
@NoArgsConstructor
public class HistoricoSituacaoEntity {

    @Id
    private UUID id;

    @Column(name = "os_id", nullable = false)
    private UUID osId;

    @Enumerated(EnumType.STRING)
    @Column(name = "de")
    private SituacaoOrdemServico de;

    @Enumerated(EnumType.STRING)
    @Column(name = "para", nullable = false)
    private SituacaoOrdemServico para;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "ocorrida_em", nullable = false)
    private Instant ocorridaEm;
}
