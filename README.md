# fiap-soat-mecanica-os

Microsserviço **os** da **Fase 4** da Oficina Mecânica do Braia (Tech Challenge FIAP SOAT). É o dono da ordem de serviço (OS), do catálogo de serviços e do **orquestrador da Saga** do atendimento.

> Rascunho da F4 (T10). Os trechos marcados com *TODO* dependem da janela do CP2 (prints e links) e são fechados na F9.

## Responsabilidade

- **Ordem de serviço:** abertura, consulta, histórico de situações, cancelamento e revisão do orçamento. A OS tem 8 situações: `RECEBIDA`, `EM_DIAGNOSTICO`, `AGUARDANDO_APROVACAO`, `EM_EXECUCAO`, `FINALIZADA`, `AGUARDANDO_PAGAMENTO`, `ENTREGUE` e `CANCELADA`.
- **Catálogo de serviços** (`servicos`): cadastro, alteração, ativação e inativação.
- **Orquestrador da Saga:** comanda o Execução e o Billing por mensagens e reage às respostas (15 tipos). Guarda o estado em `saga_instancia` e a trilha de auditoria em `saga_passo`.
- **Dados do serviço:** banco `os_db` (PostgreSQL), só dele (ADR-F4-05). Veículo e cliente são do Identity: a OS guarda apenas um snapshot (`clienteId` e `placa`) tirado na abertura.

## Arquitetura

- Spring Boot 3.5, Java 21 (virtual threads), arquitetura hexagonal: `domain/`, `application/`, `adapters/in|out/`, `config/`.
- Autenticação: JWT RS256 emitido pelo Identity, validado aqui também (defesa em profundidade). Públicos: `/actuator/health/**`, Swagger, `/error` e a consulta de situação por placa.
- Mensageria: AWS SDK v2 direto (SNS e SQS), com outbox transacional e consumidor idempotente (ADR-F4-03 e ADR-F4-04).
- Observabilidade: logs JSON com `correlationId`, métricas OTLP e agente OpenTelemetry injetado no EKS (Datadog).

```
            REST (JWT)                         SQS (comandos)
 Atendente ───────────► ┌────────────────────┐ ─────────────► Execução (fila mecanica-execucao-comandos)
                        │ OS Service         │ ─────────────► Billing  (fila mecanica-billing-comandos)
 Identity ◄──────────── │  OrdemServico      │
  GET /veiculos/{id}    │  SagaOrquestrador  │ ◄───────────── mecanica-os-saga-respostas
  (retry, JWT repassado)│  outbox / @Version │      ▲ assinaturas raw dos tópicos
                        └─────────┬──────────┘      │ mecanica-execucao-eventos, mecanica-billing-eventos
                                  │ SNS mecanica-os-eventos
                                  ▼
                        Identity (notifica o cliente)
```

## Por que Saga orquestrada

O atendimento atravessa três serviços, cada um com o seu banco, e não existe transação distribuída. Cada falha no meio do caminho precisa de um tratamento explícito: peça sem estoque, orçamento que falha, recusa, expiração, desistência e pagamento recusado. A escolha (ADR-F4-02):

- **Orquestração, e não coreografia.** O OS é o único lugar que sabe "em que passo esta OS está" (`GET /ordem-servicos/{id}` devolve `sagaStatus` e `passoAtual`). Na coreografia o fluxo ficaria espalhado em três serviços, com as compensações dependendo de cada participante conhecer o fluxo inteiro. Os participantes ficam simples: recebem um comando, respondem com um evento e não conhecem o fluxo.
- **O orquestrador mora no OS**, que já é dono do agregado cuja situação a Saga muda. Um serviço "Saga" separado seria mais um repositório e um pod, com a situação da OS fora do agregado dela.
- **Pivô em `IniciarExecucao`.** Antes dele, tudo é desfeito (compensações). Depois (a reserva virou baixa de estoque, o serviço está sendo feito), só há **recuperação para frente**: retry com backoff e, esgotado, a Saga fica `FALHOU` com alerta e ação manual.
- **Estado persistido.** A Saga é uma máquina de estados no próprio Postgres do OS, com `@Version` (otimismo), `prazo_em` para timeouts e o contexto em JSONB.

### Passos e compensações

| Resposta recebida (ou ação) | Efeito na OS | Mensagens enviadas | Próximo passo |
|---|---|---|---|
| `DiagnosticoAberto` | `EM_DIAGNOSTICO` | `SituacaoOrdemServicoAlterada` | aguarda o diagnóstico |
| `DiagnosticoConcluido` | — | `ReservarPecas` (ou direto `GerarOrcamento`, se nenhum item tem peça) | reserva / orçamento |
| `PecasReservadas` | — | `GerarOrcamento` | gera orçamento |
| `ReservaPecasRecusada` | permanece `EM_DIAGNOSTICO`, com o motivo "peças sem estoque" | `SituacaoOrdemServicoAlterada` | volta ao diagnóstico (nada a desfazer: a reserva é atômica) |
| `OrcamentoGerado` | `AGUARDANDO_APROVACAO` | `SituacaoOrdemServicoAlterada` | aguarda aprovação |
| `OrcamentoFalhou` | permanece `EM_DIAGNOSTICO` | `LiberarReservaPecas` | volta ao diagnóstico |
| `OrcamentoAprovado` | — | `IniciarExecucao` | **pivô** |
| `OrcamentoRecusado` / `OrcamentoExpirado` | **`CANCELADA` na hora** | `LiberarReservaPecas`, `OrdemServicoCancelada` | Saga `COMPENSANDO` → `COMPENSADA` |
| `ExecucaoIniciada` | `EM_EXECUCAO` | `SituacaoOrdemServicoAlterada` | aguarda a execução |
| `ExecucaoFinalizada` | `FINALIZADA` | `SolicitarPagamento`, `SituacaoOrdemServicoAlterada` | cobrança |
| `CobrancaCriada` | `AGUARDANDO_PAGAMENTO` (só na primeira) | `SituacaoOrdemServicoAlterada` | aguarda o pagamento |
| `PagamentoRecusado` | permanece `AGUARDANDO_PAGAMENTO` | `SituacaoOrdemServicoAlterada` | o Billing gera uma nova cobrança |
| `PagamentoConfirmado` | `ENTREGUE` | `SituacaoOrdemServicoAlterada` | Saga `CONCLUIDA` |
| `PATCH /cancelar` | `CANCELADA` | `LiberarReservaPecas` / `CancelarOrcamento` (conforme o que existe), `OrdemServicoCancelada` | `COMPENSANDO` → `COMPENSADA` |
| `PATCH /revisar-orcamento` | `EM_DIAGNOSTICO` | `LiberarReservaPecas`, `CancelarOrcamento`, `SituacaoOrdemServicoAlterada` | novo diagnóstico |

Regras importantes (detalhes na ADR-F4-02):

- A situação da OS muda **na hora** da decisão; a Saga fica `COMPENSANDO` até chegar a `ReservaPecasLiberada`. No cancelamento sai **só** `OrdemServicoCancelada`, para o cliente ser notificado uma vez.
- Uma resposta fora do passo é descartada (log `resposta_fora_do_passo` e linha `DESCARTADA` em `saga_passo`). Exceção: uma resposta tardia que criou um recurso numa Saga já cancelada dispara a compensação.
- Depois de `OrcamentoAprovado`, `cancelar` e `revisar-orcamento` respondem 422.
- Uma nova reserva depois de uma revisão só é enviada quando chegar a `ReservaPecasLiberada` da anterior (as filas não garantem ordem).

### Estados da OS

```
RECEBIDA → EM_DIAGNOSTICO ⇄ AGUARDANDO_APROVACAO → EM_EXECUCAO → FINALIZADA → AGUARDANDO_PAGAMENTO → ENTREGUE
   │              │                  │
   └──────────────┴──────────────────┴──► CANCELADA   (cancelar / recusa / expiração / falha ao abrir o diagnóstico)
```

### Timeouts e retries

Um `SagaTimeoutScheduler` roda a cada 30 s e trata as instâncias com `prazo_em` vencido (`FOR UPDATE SKIP LOCKED`, porque o OS roda com 2 réplicas).

| Passo | Prazo | Esgotadas as 3 tentativas |
|---|---|---|
| `ABRIR_DIAGNOSTICO` | 2 min, reenvia | OS `CANCELADA`, Saga `FALHOU` |
| `RESERVAR_PECAS` | 2 min, reenvia | libera a reserva por garantia; volta ao diagnóstico |
| `GERAR_ORCAMENTO` | 2 min, reenvia | mesmo efeito de `OrcamentoFalhou` |
| `AGUARDAR_APROVACAO` | `expiraEm` do orçamento + 1 min | mesmo efeito de `OrcamentoExpirado` (o TTL é do Billing) |
| `INICIAR_EXECUCAO`, `SOLICITAR_PAGAMENTO` | 2 min, com backoff (2, 4, 8) | Saga `FALHOU` (alerta); OS inalterada |
| Esperas humanas (diagnóstico, execução, pagamento) | sem prazo | — |

O reenvio usa um **envelope novo** (`messageId` novo, mesmo payload). Por isso Execução e Billing precisam ser idempotentes pela chave de negócio (`osId` + tipo de comando) e reemitir a resposta (ADR-F4-04).

## API

Swagger: `/os/swagger-ui.html` (via Traefik) ou `/swagger-ui.html` (local). Tabela completa em `docs/contratos/rotas.md` do `-k8s`.

| Rota | Perfil |
|---|---|
| `POST /ordem-servicos` (`{veiculoId, observacao}` → 201 `RECEBIDA`) | ATENDENTE ou MECANICO |
| `GET /ordem-servicos`, `GET /ordem-servicos/{id}`, `GET /ordem-servicos/{id}/historico` | ATENDENTE ou MECANICO |
| `PATCH /ordem-servicos/{id}/cancelar`, `PATCH /ordem-servicos/{id}/revisar-orcamento` | ATENDENTE ou MECANICO |
| `GET /ordem-servicos/minhas` | CLIENTE |
| `GET /ordem-servicos/veiculo/placa/{placa}` | público (só situação e datas) |
| `POST /servicos`, `PUT /servicos/{id}`, `PATCH /servicos/{id}/ativar`, `PATCH /servicos/{id}/inativar` | MECANICO |
| `GET /servicos/{id}`, `GET /servicos` | MECANICO |

Erros próprios: 422 `VEICULO_INVALIDO`, 503 `IDENTITY_INDISPONIVEL`, 502 `INTEGRACAO_IDENTITY`, 409 `CONFLITO_CONCORRENTE` e 422 para transição inválida.

### Chamada ao Identity

Ao abrir uma OS, o `IdentityClient` consulta `GET /veiculos/{id}` no Identity **antes de gravar qualquer coisa**; se falhar, a Saga nem começa. O `RepasseAutorizacaoInterceptor` repassa o JWT de quem chamou (não há token de serviço) e está ligado só a esse `RestClient`. Timeout de 2 s e retry de 3 tentativas (200 ms × 2), só em I/O, timeout e 5xx, sem circuit breaker. Detalhes na ADR-F4-09.

## Mensagens

Os contratos do OS (schemas e exemplos) ficam em `docs/contratos/`; os dos outros serviços são consumidos de `src/test/resources/contratos-consumidos/`, cópia dos exemplos dos produtores (`-execucao` e `-billing`), usada pelo `ContratosConsumidosTest`. A cópia só muda quando o produtor muda o contrato (regra de congelamento em `convencoes.md`).

| Direção | Tipos | Canal |
|---|---|---|
| Publica (comandos) | `AbrirDiagnostico`, `ReservarPecas`, `LiberarReservaPecas`, `IniciarExecucao` | fila `mecanica-execucao-comandos` |
| Publica (comandos) | `GerarOrcamento`, `CancelarOrcamento`, `SolicitarPagamento` | fila `mecanica-billing-comandos` |
| Publica (eventos) | `SituacaoOrdemServicoAlterada`, `OrdemServicoCancelada` | tópico `mecanica-os-eventos` (o Identity assina e notifica o cliente) |
| Consome (do Execução) | `DiagnosticoAberto`, `DiagnosticoConcluido`, `PecasReservadas`, `ReservaPecasRecusada`, `ReservaPecasLiberada`, `ExecucaoIniciada`, `ExecucaoFinalizada` | fila `mecanica-os-saga-respostas` |
| Consome (do Billing) | `OrcamentoGerado`, `OrcamentoFalhou`, `OrcamentoAprovado`, `OrcamentoRecusado`, `OrcamentoExpirado`, `CobrancaCriada`, `PagamentoConfirmado`, `PagamentoRecusado` | fila `mecanica-os-saga-respostas` |

Como o esqueleto funciona:

- **Publicar:** dentro da transação do caso de uso, `outbox.registrar("<destino>", mensagemFactory.criar("<Tipo>", 1, payload))`. O `OutboxPublisher` publica a cada 1 s (lotes de 50, `FOR UPDATE SKIP LOCKED`). O `MensagemFactory` preenche `messageId`, `correlationId`, `causationId` e `traceparent`; a Saga acrescenta `sagaId` e `osId`.
- **Consumir:** um `MensagemHandler` por tipo (15 em `adapters/in/messaging/saga/`). O `SqsConsumidor` faz long polling, e o `ProcessadorMensagemSql` garante a idempotência pelo `messageId` na mesma transação do handler. Exceção no handler devolve a mensagem para a fila (DLQ depois de 5 recebimentos). Mensagem de uma `osId` sem Saga é concluída com o log `saga_inexistente`.
- **Configurar:** `app.messaging.destinos.<nome>` (ARN do tópico ou URL da fila), `app.messaging.consumidor.filas` e `app.messaging.dlqs` (gauge `dlq.depth{fila}`), com os valores vindos do ConfigMap (ver `infra/README.md`).

## Métricas e logs

Contadores `saga.iniciada`, `saga.concluida`, `saga.compensada` e `saga.falhou` (tag `passo`); timer `os.tempo_por_situacao` (tag `situacao`); gauge `dlq.depth`. Logs de negócio: `order_service_created`, `order_service_status_changed`, `order_service_cancelled`, `saga_passo`, `saga_falhou`, `saga_finalizada`, `resposta_fora_do_passo`.

## Como rodar localmente

```bash
docker compose -f ../fiap-soat-mecanica-k8s/local/docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl localhost:8082/actuator/health
```

O Identity precisa estar em `:8081` (`app.identity.base-url`). Para abrir uma OS: `POST /auth/login` no Identity como atendente e `POST localhost:8082/ordem-servicos` com o token e um `veiculoId` do seed. A mensagem aparece na fila `mecanica-execucao-comandos` do LocalStack (`awslocal sqs receive-message --visibility-timeout 0`). Token de dev: `../fiap-soat-mecanica-k8s/local/jwt/gerar-token-dev.sh CLIENTE`.

## Testes e qualidade

```bash
./mvnw verify   # unitários + integração (Testcontainers/LocalStack/WireMock) + BDD, JaCoCo ≥ 80% de linhas
```

- **Unitários:** domínio (tabela de transições com `Clock` fixo), casos de uso e `SagaOrquestrador` com Mockito, uma linha da tabela de transições por teste.
- **Integração** (um único contexto Spring, `IntegracaoTest`, com a topologia real `mecanica-*` no LocalStack e um WireMock no lugar do Identity): migrations, matriz de perfis por rota, abertura da OS com os envelopes validados contra os schemas, timeouts com dois schedulers em paralelo.
- **`SagaOrquestradorIT`:** RECEBIDA → ENTREGUE com respostas simuladas, idempotência (mesmo `messageId` duas vezes → um efeito), descarte fora do passo, conflito de versão e timeout do `AbrirDiagnostico`.
- **BDD (Cucumber, em português):** `src/test/resources/features/atendimento_os.feature`, executado por `AtendimentoOsBddIT` (no mesmo contexto Spring). Cenários: caminho feliz até a entrega; peça sem estoque; orçamento recusado e orçamento expirado (esquema do cenário), que compensam a reserva e cancelam a OS; e pagamento recusado com nova cobrança. `ParticipantesSimulados` faz o papel do Execução e do Billing.

O CI (`ci / build`) roda os testes e a análise no SonarCloud (`fiap-postech-soat-lf_fiap-soat-mecanica-os`) com espera do Quality Gate. *TODO (F9):* print do relatório do Cucumber (`target/cucumber-report.html`) e do Sonar.

## Infraestrutura e deploy

O `infra/` (Terraform) cria o ECR, o RDS `os_db` (módulo `-db@v1`), o tópico `mecanica-os-eventos`, a fila `mecanica-os-saga-respostas` com DLQ e as 3 assinaturas raw (`execucao-eventos` e `billing-eventos` → `os-saga-respostas`; `os-eventos` → `identity-notificacoes`, por exceção da ADR-F4-03). Lê do SSM os parâmetros do Identity, do Execução e do Billing. Ver `infra/README.md`.

Contrato com o CD: `infra/outputs.tf` expõe `ecr_repository_url`, `app_config` (9 chaves, vira o ConfigMap `mecanica-os-config`) e `app_secrets` (usuário e senha do banco, vira o Secret). O CD (`servico-cd.yml@v1`) roda após o CI verde na `main`: `terraform apply` do `infra/` → imagem no ECR → ConfigMap/Secret → `kubectl apply` de `k8s/` (2 réplicas no mínimo) → rollout e smoke do health. Com o EKS desligado, termina verde sem deploy.

**Ordem de provisionamento (B4):** `identity → execucao → billing → os → -auth`. O OS vem depois dos três porque lê o SSM deles e precisa da chave pública do JWT do Identity.

## Decisões

ADRs transversais em `fiap-soat-mecanica-k8s/docs/arquitetura-fase4/adr/`, em especial ADR-F4-02 (Saga), 03 (SNS + SQS), 04 (outbox), 09 (REST síncrono) e 12 (notificações). ADRs próprias do serviço em `docs/adr/`.
