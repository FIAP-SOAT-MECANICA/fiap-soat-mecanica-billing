# Banco proprio do Billing (1 RDS por servico). A rede vem do repositorio -k8s: sem vpc_id o banco nao e criado,
# o que permite aplicar a mensageria e o ECR antes de a rede existir.
locals {
  criar_rds = var.vpc_id != ""
}

resource "aws_db_subnet_group" "billing" {
  count = local.criar_rds ? 1 : 0

  name       = "${var.project}-billing-db-subnet-group"
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "billing_db" {
  count = local.criar_rds ? 1 : 0

  name   = "${var.project}-billing-db-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port       = var.db_port
    to_port         = var.db_port
    protocol        = "tcp"
    security_groups = [var.allowed_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "random_password" "billing_db" {
  count = local.criar_rds ? 1 : 0

  length  = 24
  special = false
}

resource "aws_db_instance" "billing" {
  count = local.criar_rds ? 1 : 0

  identifier             = "${var.project}-billing-db-prod"
  db_name                = var.db_name
  engine                 = "postgres"
  engine_version         = var.db_engine_version
  instance_class         = var.db_instance_class
  allocated_storage      = var.db_allocated_storage
  username               = var.db_username
  password               = random_password.billing_db[0].result
  port                   = var.db_port
  db_subnet_group_name   = aws_db_subnet_group.billing[0].name
  vpc_security_group_ids = [aws_security_group.billing_db[0].id]
  publicly_accessible    = false
  storage_encrypted      = true
  # AWS Academy e recriada a cada sessao: sem snapshot final, sem protecao e sem standby, para caber no laboratorio.
  skip_final_snapshot = true
  deletion_protection = false
  multi_az            = false
  apply_immediately   = true
}

# O CD le este segredo para montar a conexao do pod; a senha nunca passa por variavel de repositorio.
resource "aws_secretsmanager_secret" "billing_db" {
  count = local.criar_rds ? 1 : 0

  name                    = "${var.project}-billing-db-credentials"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "billing_db" {
  count = local.criar_rds ? 1 : 0

  secret_id = aws_secretsmanager_secret.billing_db[0].id
  secret_string = jsonencode({
    username = var.db_username
    password = random_password.billing_db[0].result
    dbname   = var.db_name
    port     = var.db_port
    host     = aws_db_instance.billing[0].address
  })
}
