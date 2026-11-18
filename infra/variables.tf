variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "Regiao da conta AWS Academy e do cluster compartilhado."
}

variable "project" {
  type        = string
  default     = "mecanica"
  description = "Prefixo dos recursos compartilhado entre repositorios."

  validation {
    condition     = can(regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", var.project))
    error_message = "project precisa ser um nome DNS minusculo."
  }
}

variable "visibility_timeout_seconds" {
  type        = number
  default     = 60
  description = "Tempo que um comando fica invisivel enquanto o Billing o processa. Folga sobre a chamada ao Mercado Pago."
}

variable "max_receive_count" {
  type        = number
  default     = 5
  description = "Tentativas antes de o comando ir para a DLQ."
}

variable "vpc_id" {
  type        = string
  default     = ""
  description = "VPC do cluster (saida do repositorio -k8s). Vazio: o RDS do Billing nao e criado."
}

variable "subnet_ids" {
  type        = list(string)
  default     = []
  description = "Subnets do DB subnet group."
}

variable "allowed_security_group_id" {
  type        = string
  default     = ""
  description = "Security group dos nos do EKS, unico com acesso a porta do banco."
}

variable "db_name" {
  type        = string
  default     = "mecanica_billing"
  description = "Nome do banco do Billing."
}

variable "db_username" {
  type        = string
  default     = "billing"
  description = "Usuario mestre do banco do Billing."
}

variable "db_port" {
  type        = number
  default     = 5432
  description = "Porta do PostgreSQL."
}

variable "db_engine_version" {
  type        = string
  default     = "17"
  description = "Versao do PostgreSQL, a mesma do banco do OS."
}

variable "db_instance_class" {
  type        = string
  default     = "db.t3.micro"
  description = "Classe da instancia, a menor que o laboratorio permite."
}

variable "db_allocated_storage" {
  type        = number
  default     = 10
  description = "Armazenamento do banco, em GB."
}
