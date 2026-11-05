# Fila de respostas da Saga (tabela 3.4 da F1): recebe os eventos do Execucao e do Billing.
# Destrutivel sem sobra (regras 3.3 do plano da F2).
resource "aws_sqs_queue" "saga_respostas_dlq" {
  name                      = "mecanica-os-saga-respostas-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "saga_respostas" {
  name                       = "mecanica-os-saga-respostas"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.saga_respostas_dlq.arn
    maxReceiveCount     = 5
  })
}

locals {
  topicos_de_respostas = {
    execucao = data.aws_ssm_parameter.execucao_eventos_topic_arn.insecure_value
    billing  = data.aws_ssm_parameter.billing_eventos_topic_arn.insecure_value
  }
}

resource "aws_sqs_queue_policy" "saga_respostas" {
  queue_url = aws_sqs_queue.saga_respostas.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PermiteEventosDeExecucaoEBilling"
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.saga_respostas.arn
      Condition = { ArnEquals = { "aws:SourceArn" = values(local.topicos_de_respostas) } }
    }]
  })
}

resource "aws_sns_topic_subscription" "respostas" {
  for_each = local.topicos_de_respostas

  topic_arn            = each.value
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.saga_respostas.arn
  raw_message_delivery = true

  depends_on = [aws_sqs_queue_policy.saga_respostas]
}
