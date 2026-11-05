package br.com.fiap.soat.mecanica.os.adapters.out.persistence.ordemservico;

import br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ordem_servicos")
@Getter
@Setter
@NoArgsConstructor
public class OrdemServicoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false)
    private SituacaoOrdemServico situacao;

    @Column(name = "data_recebida", nullable = false)
    private Instant dataRecebida;

    @Column(name = "data_diagnostico")
    private Instant dataDiagnostico;

    @Column(name = "data_aguardando_aprovacao")
    private Instant dataAguardandoAprovacao;

    @Column(name = "data_execucao")
    private Instant dataExecucao;

    @Column(name = "data_finalizada")
    private Instant dataFinalizada;

    @Column(name = "data_aguardando_pagamento")
    private Instant dataAguardandoPagamento;

    @Column(name = "data_entregue")
    private Instant dataEntregue;

    @Column(name = "data_cancelada")
    private Instant dataCancelada;

    @Column(name = "motivo_cancelamento")
    private String motivoCancelamento;

    @Column(name = "pago")
    private Boolean pago;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "observacao")
    private String observacao;

    @Column(name = "veiculo_id", nullable = false)
    private UUID veiculoId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "placa", nullable = false)
    private String placa;

    @Column(name = "usuario_abertura_id", nullable = false)
    private UUID usuarioAberturaId;
}
