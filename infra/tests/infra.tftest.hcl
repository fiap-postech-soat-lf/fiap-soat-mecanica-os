# Testes do infra/ com o provider aws mockado: nao precisa de credencial AWS nem do EKS.
# O mock nao pega palavra reservada de DBName, entao o nome do banco e conferido por valor.

mock_provider "aws" {
  # O mock gera ARN aleatorio, que o provider recusa nas assinaturas.
  mock_resource "aws_sns_topic" {
    defaults = {
      arn = "arn:aws:sns:us-east-1:123456789012:mecanica-os-eventos"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.vpc_id
    values = {
      insecure_value = "vpc-0123456789abcdef0"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.subnet_ids
    values = {
      insecure_value = "subnet-0aaaaaaaaaaaaaaaa,subnet-0bbbbbbbbbbbbbbbb"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.node_security_group_id
    values = {
      insecure_value = "sg-0123456789abcdef0"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.jwt_public_key
    values = {
      insecure_value = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n-----END PUBLIC KEY-----\n"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.notificacoes_queue_arn
    values = {
      insecure_value = "arn:aws:sqs:us-east-1:123456789012:mecanica-identity-notificacoes"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.execucao_eventos_topic_arn
    values = {
      insecure_value = "arn:aws:sns:us-east-1:123456789012:mecanica-execucao-eventos"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.execucao_comandos_queue_url
    values = {
      insecure_value = "https://sqs.us-east-1.amazonaws.com/123456789012/mecanica-execucao-comandos"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.execucao_os_eventos_queue_arn
    values = {
      insecure_value = "arn:aws:sqs:us-east-1:123456789012:mecanica-execucao-os-eventos"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.billing_eventos_topic_arn
    values = {
      insecure_value = "arn:aws:sns:us-east-1:123456789012:mecanica-billing-eventos"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.billing_comandos_queue_url
    values = {
      insecure_value = "https://sqs.us-east-1.amazonaws.com/123456789012/mecanica-billing-comandos"
    }
  }
}

# O servico-cd.yml transforma as chaves em ConfigMap e Secret com --from-env-file.
run "contrato_infra_cd" {
  command = apply

  assert {
    condition = toset(keys(output.app_config)) == toset([
      "SPRING_DATASOURCE_URL", "AWS_REGION", "JWT_PUBLIC_KEY", "APP_IDENTITY_BASE_URL",
      "APP_MESSAGING_CONSUMIDOR_FILAS", "APP_MESSAGING_DLQS",
      "MESSAGING_DESTINO_EXECUCAO_COMANDOS", "MESSAGING_DESTINO_BILLING_COMANDOS", "MESSAGING_DESTINO_OS_EVENTOS",
    ])
    error_message = "app_config deve ter as 9 chaves da tabela 8 (T07)."
  }

  assert {
    condition     = toset(keys(output.app_secrets)) == toset(["SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"])
    error_message = "app_secrets deve ter so usuario e senha do banco."
  }

  assert {
    condition     = !contains(keys(output.app_config), "SPRING_DATASOURCE_PASSWORD") && !contains(keys(output.app_config), "JWT_PRIVATE_KEY")
    error_message = "Nenhuma senha ou chave privada no app_config."
  }

  assert {
    condition     = output.ecr_repository_url == aws_ecr_repository.this.repository_url && aws_ecr_repository.this.name == "mecanica-os"
    error_message = "ecr_repository_url deve ser o repositorio mecanica-os."
  }

  assert {
    condition = (
      output.app_config.MESSAGING_DESTINO_EXECUCAO_COMANDOS == "https://sqs.us-east-1.amazonaws.com/123456789012/mecanica-execucao-comandos"
      && output.app_config.MESSAGING_DESTINO_BILLING_COMANDOS == "https://sqs.us-east-1.amazonaws.com/123456789012/mecanica-billing-comandos"
      && output.app_config.MESSAGING_DESTINO_OS_EVENTOS == aws_sns_topic.os_eventos.arn
    )
    error_message = "Os destinos devem vir do SSM dos donos e do proprio topico."
  }

  assert {
    condition     = output.app_config.APP_IDENTITY_BASE_URL == "http://mecanica-identity.mecanica.svc.cluster.local:8080"
    error_message = "O Identity e chamado pelo DNS do cluster, sem passar pelo API Gateway."
  }
}

run "nome_do_banco" {
  command = apply

  assert {
    condition     = module.db.db_name == "os_db"
    error_message = "db_name deve ser os_db."
  }

  assert {
    condition     = startswith(output.app_config.SPRING_DATASOURCE_URL, "jdbc:postgresql://") && endswith(output.app_config.SPRING_DATASOURCE_URL, "/os_db?sslmode=require")
    error_message = "SPRING_DATASOURCE_URL deve apontar para o banco os_db com sslmode=require."
  }
}

run "fila_com_dlq" {
  command = apply

  assert {
    condition     = aws_sqs_queue.saga_respostas.name == "mecanica-os-saga-respostas" && aws_sqs_queue.saga_respostas_dlq.name == "mecanica-os-saga-respostas-dlq"
    error_message = "Nomes das filas conforme a tabela 3.4 da F1."
  }

  assert {
    condition = (
      jsondecode(aws_sqs_queue.saga_respostas.redrive_policy).maxReceiveCount == 5
      && jsondecode(aws_sqs_queue.saga_respostas.redrive_policy).deadLetterTargetArn == aws_sqs_queue.saga_respostas_dlq.arn
    )
    error_message = "Redrive para a DLQ com maxReceiveCount = 5."
  }

  assert {
    condition     = output.app_config.APP_MESSAGING_CONSUMIDOR_FILAS == aws_sqs_queue.saga_respostas.url && output.app_config.APP_MESSAGING_DLQS == aws_sqs_queue.saga_respostas_dlq.url
    error_message = "app_config deve apontar para a fila de respostas e para a DLQ."
  }
}

run "assinaturas_raw" {
  command = apply

  assert {
    condition = (
      length(aws_sns_topic_subscription.respostas) == 2
      && alltrue([for s in aws_sns_topic_subscription.respostas : s.protocol == "sqs" && s.raw_message_delivery])
      && alltrue([for s in aws_sns_topic_subscription.respostas : s.endpoint == aws_sqs_queue.saga_respostas.arn])
    )
    error_message = "execucao-eventos e billing-eventos assinam a os-saga-respostas, ambas raw."
  }

  assert {
    condition = (
      aws_sns_topic_subscription.respostas["execucao"].topic_arn == "arn:aws:sns:us-east-1:123456789012:mecanica-execucao-eventos"
      && aws_sns_topic_subscription.respostas["billing"].topic_arn == "arn:aws:sns:us-east-1:123456789012:mecanica-billing-eventos"
    )
    error_message = "Cada assinatura aponta para o topico do seu dono."
  }

  assert {
    condition = (
      aws_sns_topic_subscription.os_eventos_para_notificacoes.raw_message_delivery
      && aws_sns_topic_subscription.os_eventos_para_notificacoes.protocol == "sqs"
      && aws_sns_topic_subscription.os_eventos_para_notificacoes.topic_arn == aws_sns_topic.os_eventos.arn
      && aws_sns_topic_subscription.os_eventos_para_notificacoes.endpoint == "arn:aws:sqs:us-east-1:123456789012:mecanica-identity-notificacoes"
    )
    error_message = "os-eventos -> identity-notificacoes raw, com o ARN da fila lido do SSM (F4-a)."
  }

  assert {
    condition = (
      aws_sns_topic_subscription.os_eventos_para_execucao.raw_message_delivery
      && aws_sns_topic_subscription.os_eventos_para_execucao.protocol == "sqs"
      && aws_sns_topic_subscription.os_eventos_para_execucao.topic_arn == aws_sns_topic.os_eventos.arn
      && aws_sns_topic_subscription.os_eventos_para_execucao.endpoint == "arn:aws:sqs:us-east-1:123456789012:mecanica-execucao-os-eventos"
    )
    error_message = "os-eventos -> execucao-os-eventos raw, com o ARN da fila lido do SSM (F5-b)."
  }

  assert {
    condition = (
      length(aws_sns_topic_subscription.respostas) + length([
        aws_sns_topic_subscription.os_eventos_para_notificacoes,
        aws_sns_topic_subscription.os_eventos_para_execucao,
      ]) == 4
    )
    error_message = "O infra/ do OS cria 4 assinaturas: 2 de respostas e 2 do os-eventos."
  }
}

run "filtro_so_cancelada" {
  command = apply

  assert {
    condition     = jsondecode(aws_sns_topic_subscription.os_eventos_para_execucao.filter_policy) == { type = ["OrdemServicoCancelada"] }
    error_message = "A assinatura do Execucao so recebe OrdemServicoCancelada (type)."
  }

  assert {
    condition     = aws_sns_topic_subscription.os_eventos_para_notificacoes.filter_policy == null || aws_sns_topic_subscription.os_eventos_para_notificacoes.filter_policy == ""
    error_message = "A assinatura do Identity continua sem filtro."
  }
}

run "policy_da_fila_de_respostas" {
  command = apply

  assert {
    condition = toset(jsondecode(aws_sqs_queue_policy.saga_respostas.policy).Statement[0].Condition.ArnEquals["aws:SourceArn"]) == toset([
      "arn:aws:sns:us-east-1:123456789012:mecanica-execucao-eventos",
      "arn:aws:sns:us-east-1:123456789012:mecanica-billing-eventos",
    ])
    error_message = "A policy deve permitir exatamente os topicos do Execucao e do Billing."
  }

  assert {
    condition     = jsondecode(aws_sqs_queue_policy.saga_respostas.policy).Statement[0].Action == "sqs:SendMessage"
    error_message = "A policy so permite sqs:SendMessage."
  }
}

run "topico_publicado_no_ssm" {
  command = apply

  assert {
    condition     = aws_sns_topic.os_eventos.name == "mecanica-os-eventos"
    error_message = "Topico mecanica-os-eventos."
  }

  assert {
    condition     = aws_ssm_parameter.os_eventos_topic_arn.name == "/mecanica/os/os-eventos-topic-arn" && aws_ssm_parameter.os_eventos_topic_arn.insecure_value == aws_sns_topic.os_eventos.arn
    error_message = "O ARN do topico deve estar em /mecanica/os/os-eventos-topic-arn."
  }
}

# --from-env-file nao aceita valor multilinha.
run "chave_publica_escapada" {
  command = apply

  assert {
    condition     = !strcontains(output.app_config.JWT_PUBLIC_KEY, "\n")
    error_message = "JWT_PUBLIC_KEY nao pode ter quebra de linha real."
  }

  assert {
    condition     = startswith(output.app_config.JWT_PUBLIC_KEY, "-----BEGIN PUBLIC KEY-----\\n")
    error_message = "JWT_PUBLIC_KEY deve comecar por -----BEGIN PUBLIC KEY-----\\n (escapado)."
  }

  assert {
    condition     = replace(output.app_config.JWT_PUBLIC_KEY, "\\n", "\n") == data.aws_ssm_parameter.jwt_public_key.insecure_value
    error_message = "A chave do ConfigMap deve ser a do SSM do Identity."
  }
}
