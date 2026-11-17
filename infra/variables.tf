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
