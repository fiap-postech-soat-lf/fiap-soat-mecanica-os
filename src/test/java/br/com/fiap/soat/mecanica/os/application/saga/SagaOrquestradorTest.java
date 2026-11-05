package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.application.saga.dto.CobrancaCriada;
import br.com.fiap.soat.mecanica.os.application.saga.dto.DiagnosticoAberto;
import br.com.fiap.soat.mecanica.os.application.saga.dto.DiagnosticoConcluido;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ExecucaoFinalizada;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ExecucaoIniciada;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoAprovado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoExpirado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoFalhou;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoGerado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.OrcamentoRecusado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PagamentoConfirmado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PagamentoRecusado;
import br.com.fiap.soat.mecanica.os.application.saga.dto.PecasReservadas;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ReservaPecasLiberada;
import br.com.fiap.soat.mecanica.os.application.saga.dto.ReservaPecasRecusada;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.ItemDiagnostico;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.PecaItem;
import br.com.fiap.soat.mecanica.os.domain.saga.ContextoSaga.PecaReservada;
import br.com.fiap.soat.mecanica.os.domain.saga.DirecaoPasso;
import br.com.fiap.soat.mecanica.os.domain.saga.PassoSaga;
import br.com.fiap.soat.mecanica.os.domain.saga.SagaStatus;
import br.com.fiap.soat.mecanica.os.support.CenarioSaga;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_APROVACAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_PAGAMENTO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.CANCELADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_DIAGNOSTICO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.EM_EXECUCAO;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.ENTREGUE;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.FINALIZADA;
import static br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.RECEBIDA;
import static br.com.fiap.soat.mecanica.os.support.TestDataFactory.AGORA;
import static org.assertj.core.api.Assertions.assertThat;

/** Uma linha da tabela 6.2 por teste, com as variações de reserva, diagnóstico sem peças e respostas tardias. */
class SagaOrquestradorTest {

    private static final UUID PECA_A = UUID.fromString("55555555-1111-4222-8333-666666666666");
    private static final UUID PECA_B = UUID.fromString("77777777-4444-4555-8666-888888888888");
    private static final UUID ORCAMENTO = UUID.fromString("e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b");
    private static final UUID PAGAMENTO = UUID.fromString("a9b8c7d6-e5f4-4a3b-9c2d-1e0f9a8b7c6d");

    private static final ItemDiagnostico COM_PECAS = new ItemDiagnostico(UUID.randomUUID(), "Troca de óleo",
            new BigDecimal("80"), List.of(new PecaItem(PECA_A, "Óleo", 4, new BigDecimal("42.90")),
            new PecaItem(PECA_B, "Filtro", 1, new BigDecimal("35.50"))));
    private static final ItemDiagnostico SEM_PECAS = new ItemDiagnostico(UUID.randomUUID(), "Alinhamento",
            new BigDecimal("120"), List.of());

    private static UUID msg() {
        return UUID.randomUUID();
    }

    private static String texto(CenarioSaga.Enviada e, String campo) {
        return e.envelope().payload().path(campo).asText();
    }

    @Nested
    @DisplayName("respostas do Execução")
    class Execucao {

        @Test
        @DisplayName("DiagnosticoAberto: OS em diagnóstico, evento e espera sem prazo")
        void diagnosticoAberto() {
            var c = CenarioSaga.em(RECEBIDA, PassoSaga.ABRIR_DIAGNOSTICO);

            c.orquestrador.diagnosticoAberto(msg(), new DiagnosticoAberto(c.osId(), AGORA));

            assertThat(c.os.getSituacao()).isEqualTo(EM_DIAGNOSTICO);
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
            assertThat(c.saga.getPrazoEm()).isNull();
            assertThat(c.tipos()).containsExactly("SituacaoOrdemServicoAlterada");
            assertThat(c.enviadas.get(0).destino()).isEqualTo("os-eventos");
            assertThat(texto(c.enviadas.get(0), "de")).isEqualTo("RECEBIDA");
            assertThat(texto(c.enviadas.get(0), "para")).isEqualTo("EM_DIAGNOSTICO");
            assertThat(c.historico).hasSize(1);
            assertThat(c.direcoes()).containsExactly(DirecaoPasso.RECEBIDO);
        }

        @Test
        @DisplayName("DiagnosticoConcluido com peças: ReservarPecas somando a mesma peça de itens diferentes")
        void diagnosticoComPecas() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);
            var outroItem = new ItemDiagnostico(UUID.randomUUID(), "Outro", BigDecimal.TEN,
                    List.of(new PecaItem(PECA_A, "Óleo", 2, new BigDecimal("42.90"))));

            c.orquestrador.diagnosticoConcluido(msg(), new DiagnosticoConcluido(c.osId(), List.of(COM_PECAS, outroItem)));

            assertThat(c.tipos()).containsExactly("ReservarPecas");
            assertThat(c.enviadas.get(0).destino()).isEqualTo("execucao-comandos");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.RESERVAR_PECAS);
            assertThat(c.saga.getPrazoEm()).isEqualTo(AGORA.plusSeconds(120));
            assertThat(c.saga.getContexto().getPecasReservadas())
                    .containsExactly(new PecaReservada(PECA_A, 6), new PecaReservada(PECA_B, 1));
            assertThat(c.saga.getContexto().getUltimoComando().tipo()).isEqualTo("ReservarPecas");
            assertThat(c.passos).extracting(p -> p.passo()).contains(PassoSaga.RESERVAR_PECAS);
        }

        @Test
        @DisplayName("DiagnosticoConcluido sem peças pula a reserva e vai direto ao GerarOrcamento (F4-g)")
        void diagnosticoSemPecas() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);

            c.orquestrador.diagnosticoConcluido(msg(), new DiagnosticoConcluido(c.osId(), List.of(SEM_PECAS)));

            assertThat(c.tipos()).containsExactly("GerarOrcamento");
            assertThat(c.enviadas.get(0).destino()).isEqualTo("billing-comandos");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.GERAR_ORCAMENTO);
            assertThat(c.saga.getContexto().isReservaAtiva()).isFalse();
            assertThat(c.enviadas.get(0).envelope().payload().path("valorTotal").decimalValue())
                    .isEqualByComparingTo("120");
            assertThat(texto(c.enviadas.get(0), "clienteId")).isEqualTo(c.os.getClienteId().toString());
        }

        @Test
        @DisplayName("DiagnosticoConcluido com liberação pendente guarda o diagnóstico e não envia nada (F4-g)")
        void diagnosticoEmEspera() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);
            c.saga.getContexto().setLiberacaoPendente(true);

            c.orquestrador.diagnosticoConcluido(msg(), new DiagnosticoConcluido(c.osId(), List.of(COM_PECAS)));

            assertThat(c.enviadas).isEmpty();
            assertThat(c.saga.getContexto().getDiagnosticoEmEspera()).containsExactly(COM_PECAS);
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
        }

        @Test
        @DisplayName("PecasReservadas: GerarOrcamento com a soma dos itens e reserva ativa")
        void pecasReservadas() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.RESERVAR_PECAS);
            c.saga.getContexto().setItens(List.of(COM_PECAS, SEM_PECAS));

            c.orquestrador.pecasReservadas(msg(), new PecasReservadas(c.osId(),
                    List.of(new PecaReservada(PECA_A, 4), new PecaReservada(PECA_B, 1))));

            assertThat(c.tipos()).containsExactly("GerarOrcamento");
            // 80 + 4 x 42,90 + 1 x 35,50 + 120
            assertThat(c.enviadas.get(0).envelope().payload().path("valorTotal").decimalValue())
                    .isEqualByComparingTo("407.10");
            assertThat(c.saga.getContexto().isReservaAtiva()).isTrue();
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.GERAR_ORCAMENTO);
        }

        @Test
        @DisplayName("ReservaPecasRecusada: problema no diagnóstico com o motivo e volta a aguardar o diagnóstico")
        void reservaRecusada() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.RESERVAR_PECAS);

            c.orquestrador.reservaPecasRecusada(msg(), new ReservaPecasRecusada(c.osId(),
                    List.of(new ReservaPecasRecusada.Faltante(PECA_A, 4, 2))));

            assertThat(c.os.getSituacao()).isEqualTo(EM_DIAGNOSTICO);
            assertThat(c.tipos()).containsExactly("SituacaoOrdemServicoAlterada");
            assertThat(texto(c.enviadas.get(0), "motivo")).startsWith("peças sem estoque: ")
                    .contains("solicitada 4", "disponível 2");
            assertThat(c.historico.get(0).motivo()).startsWith("peças sem estoque");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
            assertThat(c.saga.getPrazoEm()).isNull();
        }

        @Test
        @DisplayName("ExecucaoIniciada: OS em execução, reserva vira baixa e espera sem prazo")
        void execucaoIniciada() {
            var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.INICIAR_EXECUCAO);
            c.saga.getContexto().setReservaAtiva(true);

            c.orquestrador.execucaoIniciada(msg(), new ExecucaoIniciada(c.osId(), AGORA));

            assertThat(c.os.getSituacao()).isEqualTo(EM_EXECUCAO);
            assertThat(c.saga.getContexto().isReservaAtiva()).isFalse();
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_EXECUCAO);
            assertThat(c.saga.getPrazoEm()).isNull();
            assertThat(c.tipos()).containsExactly("SituacaoOrdemServicoAlterada");
        }

        @Test
        @DisplayName("ExecucaoFinalizada: OS finalizada e SolicitarPagamento com orçamento e valor")
        void execucaoFinalizada() {
            var c = CenarioSaga.em(EM_EXECUCAO, PassoSaga.AGUARDAR_EXECUCAO);
            c.saga.getContexto().setOrcamentoId(ORCAMENTO);

            c.orquestrador.execucaoFinalizada(msg(), new ExecucaoFinalizada(c.osId(), AGORA, List.of()));

            assertThat(c.os.getSituacao()).isEqualTo(FINALIZADA);
            assertThat(c.tipos()).containsExactlyInAnyOrder("SituacaoOrdemServicoAlterada", "SolicitarPagamento");
            var comando = c.primeira("SolicitarPagamento");
            assertThat(comando.destino()).isEqualTo("billing-comandos");
            assertThat(texto(comando, "orcamentoId")).isEqualTo(ORCAMENTO.toString());
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.SOLICITAR_PAGAMENTO);
            assertThat(c.saga.getPrazoEm()).isEqualTo(AGORA.plusSeconds(120));
        }
    }

    @Nested
    @DisplayName("respostas do Billing")
    class Billing {

        @Test
        @DisplayName("OrcamentoGerado: aguarda aprovação com prazo de expiraEm + 1 min")
        void orcamentoGerado() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.GERAR_ORCAMENTO);
            Instant expira = AGORA.plusSeconds(86_400);

            c.orquestrador.orcamentoGerado(msg(),
                    new OrcamentoGerado(c.osId(), ORCAMENTO, new BigDecimal("407.10"), expira));

            assertThat(c.os.getSituacao()).isEqualTo(AGUARDANDO_APROVACAO);
            assertThat(c.os.getValorTotal()).isEqualByComparingTo("407.10");
            assertThat(c.saga.getContexto().getOrcamentoId()).isEqualTo(ORCAMENTO);
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_APROVACAO);
            assertThat(c.saga.getPrazoEm()).isEqualTo(expira.plusSeconds(60));
            assertThat(c.tipos()).containsExactly("SituacaoOrdemServicoAlterada");
        }

        @Test
        @DisplayName("OrcamentoFalhou com reserva ativa: libera a reserva (ORCAMENTO_FALHOU) e espera a liberação")
        void orcamentoFalhouComReserva() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.GERAR_ORCAMENTO);
            c.saga.getContexto().setReservaAtiva(true);

            c.orquestrador.orcamentoFalhou(msg(), new OrcamentoFalhou(c.osId(), "Serviço sem preço"));

            assertThat(c.tipos()).containsExactlyInAnyOrder("SituacaoOrdemServicoAlterada", "LiberarReservaPecas");
            assertThat(texto(c.primeira("LiberarReservaPecas"), "motivo")).isEqualTo("ORCAMENTO_FALHOU");
            assertThat(texto(c.primeira("SituacaoOrdemServicoAlterada"), "motivo")).isEqualTo("Serviço sem preço");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
            assertThat(c.saga.getContexto().isLiberacaoPendente()).isTrue();
            assertThat(c.saga.getContexto().isReservaAtiva()).isFalse();
            assertThat(c.saga.getPrazoEm()).isEqualTo(AGORA.plusSeconds(120));
            assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.INICIADA);
        }

        @Test
        @DisplayName("OrcamentoFalhou sem reserva (diagnóstico sem peças): só o evento, sem prazo")
        void orcamentoFalhouSemReserva() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.GERAR_ORCAMENTO);

            c.orquestrador.orcamentoFalhou(msg(), new OrcamentoFalhou(c.osId(), "Falha"));

            assertThat(c.tipos()).containsExactly("SituacaoOrdemServicoAlterada");
            assertThat(c.saga.getContexto().isLiberacaoPendente()).isFalse();
            assertThat(c.saga.getPrazoEm()).isNull();
        }

        @Test
        @DisplayName("OrcamentoAprovado: IniciarExecucao e o pivô, sem mudar a OS")
        void orcamentoAprovado() {
            var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO);

            c.orquestrador.orcamentoAprovado(msg(), new OrcamentoAprovado(c.osId(), ORCAMENTO, UUID.randomUUID()));

            assertThat(c.os.getSituacao()).isEqualTo(AGUARDANDO_APROVACAO);
            assertThat(c.tipos()).containsExactly("IniciarExecucao");
            assertThat(c.enviadas.get(0).destino()).isEqualTo("execucao-comandos");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.INICIAR_EXECUCAO);
            assertThat(c.saga.getPrazoEm()).isEqualTo(AGORA.plusSeconds(120));
        }

        @Test
        @DisplayName("OrcamentoRecusado com reserva: OS cancelada na hora, libera a reserva e a Saga compensa")
        void orcamentoRecusadoComReserva() {
            var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO);
            c.saga.getContexto().setReservaAtiva(true);

            c.orquestrador.orcamentoRecusado(msg(), new OrcamentoRecusado(c.osId(), ORCAMENTO, UUID.randomUUID()));

            assertThat(c.os.getSituacao()).isEqualTo(CANCELADA);
            // só OrdemServicoCancelada: sem SituacaoOrdemServicoAlterada para CANCELADA (F4-e)
            assertThat(c.tipos()).containsExactlyInAnyOrder("OrdemServicoCancelada", "LiberarReservaPecas");
            assertThat(texto(c.primeira("LiberarReservaPecas"), "motivo")).isEqualTo("ORCAMENTO_RECUSADO");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.FIM);
            assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSANDO);
            assertThat(c.saga.getPrazoEm()).isEqualTo(AGORA.plusSeconds(120));
        }

        @Test
        @DisplayName("OrcamentoExpirado sem reserva: OS cancelada e Saga direto em COMPENSADA")
        void orcamentoExpiradoSemReserva() {
            var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO);

            c.orquestrador.orcamentoExpirado(msg(), new OrcamentoExpirado(c.osId(), ORCAMENTO));

            assertThat(c.os.getSituacao()).isEqualTo(CANCELADA);
            assertThat(c.tipos()).containsExactly("OrdemServicoCancelada");
            assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSADA);
            assertThat(c.saga.getPrazoEm()).isNull();
            assertThat(c.os.getMotivoCancelamento()).contains("expirado");
        }

        @Test
        @DisplayName("CobrancaCriada na primeira vez: OS aguarda pagamento")
        void primeiraCobranca() {
            var c = CenarioSaga.em(FINALIZADA, PassoSaga.SOLICITAR_PAGAMENTO);

            c.orquestrador.cobrancaCriada(msg(), new CobrancaCriada(c.osId(), PAGAMENTO, "https://mp", "PENDENTE"));

            assertThat(c.os.getSituacao()).isEqualTo(AGUARDANDO_PAGAMENTO);
            assertThat(c.tipos()).containsExactly("SituacaoOrdemServicoAlterada");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_PAGAMENTO);
            assertThat(c.saga.getContexto().getPagamentoId()).isEqualTo(PAGAMENTO);
        }

        @Test
        @DisplayName("CobrancaCriada numa nova cobrança: só atualiza o pagamentoId")
        void novaCobranca() {
            var c = CenarioSaga.em(AGUARDANDO_PAGAMENTO, PassoSaga.AGUARDAR_PAGAMENTO);
            var nova = UUID.randomUUID();

            c.orquestrador.cobrancaCriada(msg(), new CobrancaCriada(c.osId(), nova, "https://mp", "PENDENTE"));

            assertThat(c.enviadas).isEmpty();
            assertThat(c.historico).isEmpty();
            assertThat(c.saga.getContexto().getPagamentoId()).isEqualTo(nova);
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_PAGAMENTO);
        }

        @Test
        @DisplayName("PagamentoRecusado: continua aguardando pagamento, com o motivo no evento")
        void pagamentoRecusado() {
            var c = CenarioSaga.em(AGUARDANDO_PAGAMENTO, PassoSaga.AGUARDAR_PAGAMENTO);

            c.orquestrador.pagamentoRecusado(msg(),
                    new PagamentoRecusado(c.osId(), PAGAMENTO, "1", "RECUSADO", "cc_rejected"));

            assertThat(c.os.getSituacao()).isEqualTo(AGUARDANDO_PAGAMENTO);
            assertThat(texto(c.enviadas.get(0), "motivo")).isEqualTo("cc_rejected");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_PAGAMENTO);
            assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.INICIADA);
        }

        @Test
        @DisplayName("PagamentoConfirmado: OS entregue e Saga concluída")
        void pagamentoConfirmado() {
            var c = CenarioSaga.em(AGUARDANDO_PAGAMENTO, PassoSaga.AGUARDAR_PAGAMENTO);

            c.orquestrador.pagamentoConfirmado(msg(), new PagamentoConfirmado(c.osId(), PAGAMENTO, "1", "APROVADO"));

            assertThat(c.os.getSituacao()).isEqualTo(ENTREGUE);
            assertThat(c.os.getPago()).isTrue();
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.FIM);
            assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.CONCLUIDA);
        }
    }

    @Nested
    @DisplayName("ReservaPecasLiberada")
    class Liberada {

        @Test
        @DisplayName("com a Saga COMPENSANDO: vira COMPENSADA e zera o prazo")
        void compensa() {
            var c = CenarioSaga.em(CANCELADA, PassoSaga.FIM);
            c.saga.getContexto().setLiberacaoPendente(true);
            c.saga.mudarStatus(SagaStatus.COMPENSANDO, br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO);
            c.saga.definirPrazo(AGORA.plusSeconds(120), br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO);

            c.orquestrador.reservaPecasLiberada(msg(), new ReservaPecasLiberada(c.osId()));

            assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSADA);
            assertThat(c.saga.getContexto().isLiberacaoPendente()).isFalse();
            assertThat(c.saga.getPrazoEm()).isNull();
            assertThat(c.enviadas).isEmpty();
        }

        @Test
        @DisplayName("com diagnóstico em espera: envia o ReservarPecas guardado (F4-g)")
        void enviaReservaEmEspera() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);
            ContextoSaga ctx = c.saga.getContexto();
            ctx.setLiberacaoPendente(true);
            ctx.setDiagnosticoEmEspera(List.of(COM_PECAS));

            c.orquestrador.reservaPecasLiberada(msg(), new ReservaPecasLiberada(c.osId()));

            assertThat(c.tipos()).containsExactly("ReservarPecas");
            assertThat(ctx.isLiberacaoPendente()).isFalse();
            assertThat(ctx.getDiagnosticoEmEspera()).isNull();
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.RESERVAR_PECAS);
            assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.INICIADA);
        }

        @Test
        @DisplayName("com diagnóstico em espera sem peças: vai direto ao GerarOrcamento")
        void enviaOrcamentoEmEspera() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);
            c.saga.getContexto().setLiberacaoPendente(true);
            c.saga.getContexto().setDiagnosticoEmEspera(List.of(SEM_PECAS));

            c.orquestrador.reservaPecasLiberada(msg(), new ReservaPecasLiberada(c.osId()));

            assertThat(c.tipos()).containsExactly("GerarOrcamento");
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.GERAR_ORCAMENTO);
        }

        @Test
        @DisplayName("sem liberação pendente: descartada")
        void semPendencia() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);

            c.orquestrador.reservaPecasLiberada(msg(), new ReservaPecasLiberada(c.osId()));

            assertThat(c.enviadas).isEmpty();
            assertThat(c.direcoes()).containsExactly(DirecaoPasso.DESCARTADA);
        }
    }

    @Nested
    @DisplayName("respostas fora de hora (F4-f)")
    class ForaDeHora {

        @Test
        @DisplayName("resposta fora do passo é descartada: sem mudar nada nem enviar")
        void descarta() {
            var c = CenarioSaga.em(EM_DIAGNOSTICO, PassoSaga.AGUARDAR_DIAGNOSTICO);

            c.orquestrador.orcamentoAprovado(msg(), new OrcamentoAprovado(c.osId(), ORCAMENTO, UUID.randomUUID()));
            c.orquestrador.pecasReservadas(msg(), new PecasReservadas(c.osId(), List.of()));

            assertThat(c.enviadas).isEmpty();
            assertThat(c.saga.getPassoAtual()).isEqualTo(PassoSaga.AGUARDAR_DIAGNOSTICO);
            assertThat(c.direcoes()).containsExactly(DirecaoPasso.DESCARTADA, DirecaoPasso.DESCARTADA);
        }

        @Test
        @DisplayName("PecasReservadas tardia numa Saga cancelada: libera a reserva")
        void reservaTardia() {
            var c = CenarioSaga.em(CANCELADA, PassoSaga.FIM);
            c.saga.mudarStatus(SagaStatus.COMPENSADA, br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO);

            c.orquestrador.pecasReservadas(msg(), new PecasReservadas(c.osId(), List.of(new PecaReservada(PECA_A, 1))));

            assertThat(c.tipos()).containsExactly("LiberarReservaPecas");
            assertThat(texto(c.enviadas.get(0), "motivo")).isEqualTo("DESISTENCIA");
            assertThat(c.saga.getStatus()).isEqualTo(SagaStatus.COMPENSANDO);
            assertThat(c.saga.getContexto().isLiberacaoPendente()).isTrue();
            assertThat(c.direcoes()).containsExactly(DirecaoPasso.DESCARTADA, DirecaoPasso.ENVIADO);
        }

        @Test
        @DisplayName("OrcamentoGerado tardio numa Saga cancelada: cancela o orçamento")
        void orcamentoTardio() {
            var c = CenarioSaga.em(CANCELADA, PassoSaga.FIM);
            c.saga.mudarStatus(SagaStatus.COMPENSADA, br.com.fiap.soat.mecanica.os.support.TestDataFactory.RELOGIO);

            c.orquestrador.orcamentoGerado(msg(), new OrcamentoGerado(c.osId(), ORCAMENTO, BigDecimal.TEN, AGORA));

            assertThat(c.tipos()).containsExactly("CancelarOrcamento");
            assertThat(c.enviadas.get(0).destino()).isEqualTo("billing-comandos");
            assertThat(texto(c.enviadas.get(0), "orcamentoId")).isEqualTo(ORCAMENTO.toString());
            assertThat(c.os.getSituacao()).isEqualTo(CANCELADA);
        }

        @Test
        @DisplayName("OS sem Saga: a mensagem é concluída sem efeito")
        void sagaInexistente() {
            var c = new CenarioSaga();
            UUID osId = UUID.randomUUID();
            org.mockito.Mockito.when(c.sagaRepository.buscarPorOsId(osId)).thenReturn(java.util.Optional.empty());

            c.orquestrador.diagnosticoAberto(msg(), new DiagnosticoAberto(osId, AGORA));
            c.orquestrador.pecasReservadas(msg(), new PecasReservadas(osId, List.of()));
            c.orquestrador.orcamentoGerado(msg(), new OrcamentoGerado(osId, ORCAMENTO, BigDecimal.ONE, AGORA));
            c.orquestrador.reservaPecasLiberada(msg(), new ReservaPecasLiberada(osId));

            assertThat(c.enviadas).isEmpty();
            assertThat(c.passos).isEmpty();
        }
    }

    @Test
    @DisplayName("métricas: grava o tempo na situação anterior, a partir do histórico, e conta a Saga concluída")
    void metricas() {
        var c = CenarioSaga.em(br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_PAGAMENTO,
                PassoSaga.AGUARDAR_PAGAMENTO);
        org.mockito.Mockito.when(c.osRepository.buscarHistorico(c.osId())).thenReturn(List.of(
                new br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao(UUID.randomUUID(), c.osId(),
                        null, br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.RECEBIDA, null,
                        AGORA.minusSeconds(7200)),
                new br.com.fiap.soat.mecanica.os.domain.ordemservico.HistoricoSituacao(UUID.randomUUID(), c.osId(),
                        br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.FINALIZADA,
                        br.com.fiap.soat.mecanica.os.domain.ordemservico.SituacaoOrdemServico.AGUARDANDO_PAGAMENTO,
                        null, AGORA.minusSeconds(600))));

        c.orquestrador.pagamentoConfirmado(msg(), new PagamentoConfirmado(c.osId(), PAGAMENTO, "1", "APROVADO"));

        var timer = c.registro.timer("os.tempo_por_situacao", "situacao", "AGUARDANDO_PAGAMENTO");
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(600.0);
        assertThat(c.registro.counter("saga.concluida", "passo", "FIM").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("métricas: conta a Saga compensada com o passo em que o cancelamento aconteceu")
    void metricaCompensada() {
        var c = CenarioSaga.em(AGUARDANDO_APROVACAO, PassoSaga.AGUARDAR_APROVACAO);

        c.orquestrador.orcamentoExpirado(msg(), new OrcamentoExpirado(c.osId(), ORCAMENTO));

        assertThat(c.registro.counter("saga.compensada", "passo", "AGUARDAR_APROVACAO").count()).isEqualTo(1.0);
    }
}
