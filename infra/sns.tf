# Topico de eventos do OS (SituacaoOrdemServicoAlterada e OrdemServicoCancelada).
resource "aws_sns_topic" "os_eventos" {
  name = "mecanica-os-eventos"
}

# Lido pelos scripts do -k8s e pela F8. O Identity nao le mais (F4-a).
resource "aws_ssm_parameter" "os_eventos_topic_arn" {
  name           = "/mecanica/os/os-eventos-topic-arn"
  type           = "String"
  insecure_value = aws_sns_topic.os_eventos.arn
}

# F4-a: excecao a regra "o consumidor e dono da assinatura" (ADR-F4-03). O OS assina em nome
# do Identity para desfazer o ciclo da chave publica do JWT. A policy da fila fica no Identity.
resource "aws_sns_topic_subscription" "os_eventos_para_notificacoes" {
  topic_arn            = aws_sns_topic.os_eventos.arn
  protocol             = "sqs"
  endpoint             = data.aws_ssm_parameter.notificacoes_queue_arn.insecure_value
  raw_message_delivery = true
}

# F5-b: segunda excecao a regra "o consumidor e dono da assinatura" (ADR-F4-03), pelo mesmo motivo da
# F4-a: o topico os-eventos nasce depois do Execucao na ordem B4. Sem este consumidor, uma OS cancelada
# antes da reserva ficaria para sempre na fila do mecanico (o OS so manda LiberarReservaPecas quando
# houve ReservarPecas). O filtro e por MessageAttributes (escopo padrao): o SnsSqsPublicador ja manda
# o atributo type. A policy da fila fica no Execucao, que monta o ARN deste topico pelo nome.
resource "aws_sns_topic_subscription" "os_eventos_para_execucao" {
  topic_arn            = aws_sns_topic.os_eventos.arn
  protocol             = "sqs"
  endpoint             = data.aws_ssm_parameter.execucao_os_eventos_queue_arn.insecure_value
  raw_message_delivery = true

  filter_policy = jsonencode({
    type = ["OrdemServicoCancelada"]
  })
}
