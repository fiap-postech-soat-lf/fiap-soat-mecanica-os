module "db" {
  source = "git::https://github.com/fiap-postech-soat-lf/fiap-soat-mecanica-db.git//modules/rds-postgres?ref=v1"

  name_prefix = "mecanica-os"
  # "os" nao e confiavel como nome de banco; o sufixo _db evita palavra reservada (licao da F2).
  db_name                    = "os_db"
  vpc_id                     = data.aws_ssm_parameter.vpc_id.insecure_value
  subnet_ids                 = split(",", data.aws_ssm_parameter.subnet_ids.insecure_value)
  allowed_security_group_ids = [data.aws_ssm_parameter.node_security_group_id.insecure_value]
}
