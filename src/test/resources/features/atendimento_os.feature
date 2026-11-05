# language: pt
Funcionalidade: Atendimento da ordem de serviço (Saga orquestrada)

  Contexto:
    Dado que o Identity conhece o veículo "44444444-4444-4444-4444-444444444444" do cliente "33333333-3333-3333-3333-333333333333" com a placa "ABC1D23"
    E que um atendente está autenticado

  Cenário: Caminho feliz até a entrega
    Quando o atendente abre uma OS para o veículo
    Então o Execução recebe o comando "AbrirDiagnostico"
    Quando o Execução responde "DiagnosticoAberto"
    E o Execução responde "DiagnosticoConcluido" com 1 serviço e 1 peça
    Então o Execução recebe o comando "ReservarPecas"
    Quando o Execução responde "PecasReservadas"
    Então o Billing recebe o comando "GerarOrcamento" com o valor total do diagnóstico
    Quando o Billing responde "OrcamentoGerado"
    E o Billing responde "OrcamentoAprovado"
    Então o Execução recebe o comando "IniciarExecucao"
    Quando o Execução responde "ExecucaoIniciada"
    E o Execução responde "ExecucaoFinalizada"
    Então o Billing recebe o comando "SolicitarPagamento"
    Quando o Billing responde "CobrancaCriada"
    E o Billing responde "PagamentoConfirmado"
    Então a OS fica "ENTREGUE" e a Saga "CONCLUIDA"
    E o cliente foi notificado das situações "RECEBIDA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO, EM_EXECUCAO, FINALIZADA, AGUARDANDO_PAGAMENTO, ENTREGUE"

  Cenário: Peça sem estoque
    Dado uma OS em diagnóstico com 1 peça
    Quando o Execução responde "ReservaPecasRecusada"
    Então a OS fica "EM_DIAGNOSTICO" com o motivo "peças sem estoque"
    E o Billing não recebe nenhum comando
    E o cliente foi notificado do motivo

  Esquema do Cenário: Orçamento <evento> compensa a reserva e cancela a OS
    Dado uma OS aguardando aprovação com peças reservadas
    Quando o Billing responde "<evento>"
    Então a OS fica "CANCELADA" e a Saga "COMPENSANDO"
    E o Execução recebe o comando "LiberarReservaPecas" com o motivo "<motivo>"
    Quando o Execução responde "ReservaPecasLiberada"
    Então a Saga fica "COMPENSADA"
    E o cliente recebeu "OrdemServicoCancelada"

    Exemplos:
      | evento            | motivo             |
      | OrcamentoRecusado | ORCAMENTO_RECUSADO |
      | OrcamentoExpirado | ORCAMENTO_EXPIRADO |

  Cenário: Pagamento recusado gera nova cobrança
    Dado uma OS aguardando pagamento
    Quando o Billing responde "PagamentoRecusado"
    Então a OS continua "AGUARDANDO_PAGAMENTO"
    Quando o Billing responde "CobrancaCriada" com uma nova cobrança
    E o Billing responde "PagamentoConfirmado"
    Então a OS fica "ENTREGUE" e a Saga "CONCLUIDA"
