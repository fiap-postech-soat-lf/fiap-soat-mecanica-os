# Publicados pelo -k8s (cluster.yml, ADR-F4-14). Sem eles o servico-cd nem chega aqui:
# o porteiro pula o deploy com o EKS desligado.
data "aws_ssm_parameter" "vpc_id" {
  name = "/mecanica/k8s/vpc-id"
}

data "aws_ssm_parameter" "subnet_ids" {
  name = "/mecanica/k8s/subnet-ids"
}

data "aws_ssm_parameter" "node_security_group_id" {
  name = "/mecanica/k8s/node-security-group-id"
}

# Tabela 3.4 da F4. Leitura direta (nao condicional): na ordem B4 (F4-a) o dono sempre aplica
# antes do OS, e falhar aqui com ParameterNotFound e melhor do que subir uma Saga sem destino.
data "aws_ssm_parameter" "jwt_public_key" {
  name = "/mecanica/identity/jwt-public-key"
}

data "aws_ssm_parameter" "notificacoes_queue_arn" {
  name = "/mecanica/identity/notificacoes-queue-arn"
}

data "aws_ssm_parameter" "execucao_eventos_topic_arn" {
  name = "/mecanica/execucao/execucao-eventos-topic-arn"
}

data "aws_ssm_parameter" "execucao_comandos_queue_url" {
  name = "/mecanica/execucao/execucao-comandos-queue-url"
}

# F5-b: endpoint da assinatura filtrada os-eventos -> execucao-os-eventos. O Execucao aplica antes do OS
# na ordem B4, entao a leitura e direta: sem o parametro, o plan falha com ParameterNotFound.
data "aws_ssm_parameter" "execucao_os_eventos_queue_arn" {
  name = "/mecanica/execucao/os-eventos-queue-arn"
}

data "aws_ssm_parameter" "billing_eventos_topic_arn" {
  name = "/mecanica/billing/billing-eventos-topic-arn"
}

data "aws_ssm_parameter" "billing_comandos_queue_url" {
  name = "/mecanica/billing/billing-comandos-queue-url"
}
