# Contrato infra -> CD (secao 3.6 do plano da F1): o servico-cd.yml le estes tres outputs.

output "ecr_repository_url" {
  description = "Repositorio da imagem do servico."
  value       = aws_ecr_repository.this.repository_url
}

output "app_config" {
  description = "Vira o ConfigMap mecanica-os-config."
  value = {
    SPRING_DATASOURCE_URL = module.db.jdbc_url
    AWS_REGION            = var.aws_region
    # --from-env-file nao aceita valor multilinha; o JwtTokenValidator aceita PEM com \n escapado.
    JWT_PUBLIC_KEY = replace(data.aws_ssm_parameter.jwt_public_key.insecure_value, "\n", "\\n")

    # DNS do cluster, sem passar pelo API Gateway (F4-b, tabela 3.1 da F4).
    APP_IDENTITY_BASE_URL = "http://mecanica-identity.mecanica.svc.cluster.local:8080"

    APP_MESSAGING_CONSUMIDOR_FILAS = aws_sqs_queue.saga_respostas.url
    APP_MESSAGING_DLQS             = aws_sqs_queue.saga_respostas_dlq.url

    MESSAGING_DESTINO_EXECUCAO_COMANDOS = data.aws_ssm_parameter.execucao_comandos_queue_url.insecure_value
    MESSAGING_DESTINO_BILLING_COMANDOS  = data.aws_ssm_parameter.billing_comandos_queue_url.insecure_value
    MESSAGING_DESTINO_OS_EVENTOS        = aws_sns_topic.os_eventos.arn
  }
}

output "app_secrets" {
  description = "Vira o Secret mecanica-os-secret."
  sensitive   = true
  value = {
    SPRING_DATASOURCE_USERNAME = module.db.username
    SPRING_DATASOURCE_PASSWORD = module.db.password
  }
}
