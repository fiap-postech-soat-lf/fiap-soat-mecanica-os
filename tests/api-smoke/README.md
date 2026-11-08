# E2E da Saga (`api-smoke.mjs`)

Prova, pelo API Gateway e com os 4 serviços reais, o fluxo completo da Saga e duas falhas. **Escreve dados**:
cada rodada cria cliente PF, veículo e uma peça próprios (`runId = Date.now()`) e abre 3 OS. Pode rodar quantas
vezes for preciso, sem limpeza e sem gastar o estoque do seed.

Node 22, `fetch` nativo, sem dependências. Sai com 1 se algum check falhar.

## O que cobre

| Checks | Cenário |
|---|---|
| E01–E02 | borda: `actuator/health` público dos 4 serviços; 401 sem token e 403 com token malformado |
| E03–E07 | logins do seed, cliente, veículo, login por CPF e peça P (`valorUnitario` 25.00, `qtdEstoque` 3) |
| E08–E20 | **caminho feliz** até `AGUARDANDO_PAGAMENTO`: reserva (130.00), baixa real, pagamento `PENDENTE` com link, histórico, consultas do cliente e pública |
| E21–E23 | **peça sem estoque**: `ReservaPecasRecusada`, OS e ordem de volta a `EM_DIAGNOSTICO`, sem orçamento, e cancelamento |
| E24–E25 | **orçamento recusado**: OS `CANCELADA`, Saga `COMPENSADA`, estoque devolvido |
| E26 | perfis: 403 para mecânico e cliente, 422 na nova cobrança com o vigente `PENDENTE` |

**Limite (F7-b):** o E2E **não paga**. Com credenciais de teste o Mercado Pago não notifica o webhook sozinho;
APRO, OTHE e nova cobrança ficam com o `scripts/demo-saga-billing.sh` do `-k8s`.

## Variáveis

| Variável | Padrão | Uso |
|---|---|---|
| `API_BASE_URL` | — (obrigatória) | URL do API Gateway (SSM `/mecanica/auth/api-url`) |
| `IDENTITY_URL`, `OS_URL`, `BILLING_URL`, `EXECUCAO_URL` | `API_BASE_URL` | rodada local contra os 4 serviços no perfil `local` (8081 a 8084), sem API Gateway |
| `E2E_SEM_BORDA` | `false` | `true` pula E01–E02 |
| `E2E_EXIGIR_COBRANCA` | `true` | `false` aceita a OS parar em `FINALIZADA` (Mercado Pago fora), com aviso |
| `E2E_TIMEOUT` | `60` | segundos de polling por transição da Saga |

## Como roda

- **No CD**: o job `e2e` do `cd.yml` (`needs: cd`) chama o `e2e.yml` depois do deploy. O porteiro lê o SSM
  `/mecanica/auth/api-url`: ausente (EKS desligado ou `-auth` ainda não implantado) → job verde e **pulado**;
  credencial do Academy expirada → falha. Depois do E2E, confere que as 5 DLQs estão vazias.
- **Na subida**: o OS sobe antes do `-auth`, então o `scripts/subir-ambiente.sh` do `-k8s` dispara o `e2e.yml`
  no fim (`--sem-e2e` pula).
- **À mão**: `gh workflow run e2e.yml -R fiap-postech-soat-lf/fiap-soat-mecanica-os`, ou da máquina:

```bash
API_BASE_URL=$(aws ssm get-parameter --name /mecanica/auth/api-url --query Parameter.Value --output text) \
  node tests/api-smoke/api-smoke.mjs
```

## Se falhar

- **E13** (sem orçamento): `ReservarPecas` no OS e `orcamento_falhou` / `consumidor_iniciado` no Billing.
- **E16** (para em `FINALIZADA`): `mercadopago.erros` e `mercadopago_token_ausente` no Billing, ou token de
  teste expirado. Para não travar a janela: `E2E_EXIGIR_COBRANCA=false`.
