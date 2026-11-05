# infra/ do serviço os

Terraform do OS Service, aplicado pelo `servico-cd.yml@v1` antes do build da imagem (F4, T07).

## Recursos

| Arquivo | Recursos |
|---|---|
| `data.tf` | Lê do SSM a VPC, as subnets e o SG dos nodes (`/mecanica/k8s/*`) e os parâmetros do Identity, do Execução e do Billing (tabela abaixo). Leitura direta: se faltar algum, o `plan` falha com `ParameterNotFound` |
| `ecr.tf` | Repositório `mecanica-os` (`MUTABLE`, `force_delete`, scan on push) e lifecycle das 10 imagens mais recentes |
| `rds.tf` | Módulo `fiap-soat-mecanica-db//modules/rds-postgres?ref=v1`: RDS, secret e SG `mecanica-os-db`, banco `os_db` |
| `sns.tf` | Tópico `mecanica-os-eventos`, SSM `/mecanica/os/os-eventos-topic-arn` e a assinatura `os-eventos → identity-notificacoes` (raw), que é do OS por exceção (F4-a, ADR-F4-03) |
| `sqs.tf` | Fila `mecanica-os-saga-respostas` (visibility 30 s, retenção 4 dias) + DLQ, redrive após 5 recebimentos; policy permitindo `sns:SendMessage` dos tópicos do Execução e do Billing; as duas assinaturas raw |

- A policy da fila de notificações (que permite o `os-eventos` publicar nela) fica no `infra/` do Identity, que monta o ARN do tópico a partir do nome.
- Ordem B4: `identity → execucao → billing → os → -auth`. O OS é o último dos serviços porque lê o SSM dos três anteriores.
- Tudo é destrutível sem sobra: ECR com `force_delete`, RDS sem snapshot final (regras 3.3 do plano da F2).

## Parâmetros SSM

| Parâmetro | Operação | Dono |
|---|---|---|
| `/mecanica/k8s/{vpc-id, subnet-ids, node-security-group-id}` | lê | k8s |
| `/mecanica/identity/jwt-public-key` | lê (`JWT_PUBLIC_KEY`) | identity |
| `/mecanica/identity/notificacoes-queue-arn` | lê (endpoint da assinatura) | identity |
| `/mecanica/execucao/execucao-eventos-topic-arn` | lê (assinatura e policy) | execucao |
| `/mecanica/execucao/execucao-comandos-queue-url` | lê (`MESSAGING_DESTINO_EXECUCAO_COMANDOS`) | execucao |
| `/mecanica/billing/billing-eventos-topic-arn` | lê (assinatura e policy) | billing |
| `/mecanica/billing/billing-comandos-queue-url` | lê (`MESSAGING_DESTINO_BILLING_COMANDOS`) | billing |
| `/mecanica/os/os-eventos-topic-arn` | **publica** | os |

## Comandos

```bash
terraform -chdir=infra init -backend=false
terraform -chdir=infra validate
terraform -chdir=infra test   # provider aws mockado, sem credencial
```

## Contrato com o CD (`servico-cd.yml@v1`)

O `infra/outputs.tf` precisa expor:

| Output | Tipo | Uso no CD |
|---|---|---|
| `ecr_repository_url` | `string` | Repositório da imagem (`mecanica-os`) |
| `app_config` | `map(string)` | Vira o ConfigMap `mecanica-os-config` |
| `app_secrets` | `map(string)`, `sensitive = true` | Vira o Secret `mecanica-os-secret` |

- Backend S3 configurado pelo CD: bucket `mecanica-tfstate-<accountId>`, key `fiap-soat-mecanica-os/terraform.tfstate`. O `backend.tf` fica só com `backend "s3" {}`.
- Provider AWS `~> 6.0`, `required_version = ">= 1.10.0, < 2.0.0"`.
- O CD passa `TF_VAR_mp_access_token` e `TF_VAR_mp_webhook_secret`. Variáveis não declaradas são ignoradas.
- Nomes de filas, tópicos, RDS e SSM seguem a tabela 3.4 do plano da F1.

Valores atuais do `app_config` (9 chaves) e do `app_secrets` (2 chaves):

| Output | Chave | Valor |
|---|---|---|
| `app_config` | `SPRING_DATASOURCE_URL` | `jdbc_url` do módulo (`os_db`, `sslmode=require`) |
| `app_config` | `AWS_REGION` | `var.aws_region` |
| `app_config` | `JWT_PUBLIC_KEY` | PEM do SSM do Identity com `\n` escapado (`--from-env-file` não aceita valor multilinha) |
| `app_config` | `APP_IDENTITY_BASE_URL` | `http://mecanica-identity.mecanica.svc.cluster.local:8080` (DNS do cluster) |
| `app_config` | `APP_MESSAGING_CONSUMIDOR_FILAS`, `APP_MESSAGING_DLQS` | URL da `os-saga-respostas` e da sua DLQ |
| `app_config` | `MESSAGING_DESTINO_EXECUCAO_COMANDOS`, `MESSAGING_DESTINO_BILLING_COMANDOS` | URLs das filas de comando, lidas do SSM dos donos |
| `app_config` | `MESSAGING_DESTINO_OS_EVENTOS` | ARN do tópico `mecanica-os-eventos` |
| `app_secrets` | `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Usuário e senha master do RDS |

### Chaves de mensageria no `app_config`

As chaves do `app_config` viram variáveis de ambiente do pod (`envFrom`).

| Chave | Valor | Propriedade |
|---|---|---|
| `APP_MESSAGING_CONSUMIDOR_FILAS` | URLs das filas consumidas, separadas por vírgula | `app.messaging.consumidor.filas` |
| `APP_MESSAGING_DLQS` | URLs das DLQs dessas filas | `app.messaging.dlqs` (gauge `dlq.depth`) |
| `MESSAGING_DESTINO_<NOME>` | ARN do tópico ou URL da fila | declarada no `application.properties` do serviço como `app.messaging.destinos.<nome>=${MESSAGING_DESTINO_<NOME>:}` |

Os destinos usam variável explícita porque a ligação relaxada do Spring não preserva o `-` das chaves de mapa vindas de variáveis de ambiente (`execucao-comandos` viraria `execucao.comandos`). A role do pod precisa de `sns:Publish` nos tópicos e de `sqs:SendMessage`, `ReceiveMessage`, `DeleteMessage` e `GetQueueAttributes` nas filas.
