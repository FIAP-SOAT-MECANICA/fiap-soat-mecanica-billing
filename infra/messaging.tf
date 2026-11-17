# Eventos do Billing para o OS: o OS cria a propria fila e a assinatura deste topico no state dele.
resource "aws_sns_topic" "eventos" {
  name = "${var.project}-billing-eventos"
}

resource "aws_sqs_queue" "comandos_dlq" {
  name                      = "${var.project}-billing-comandos-dlq"
  message_retention_seconds = 1209600
}

# Comandos do OS para o Billing: GerarOrcamento, CancelarOrcamento, SolicitarPagamento.
resource "aws_sqs_queue" "comandos" {
  name                       = "${var.project}-billing-comandos"
  visibility_timeout_seconds = var.visibility_timeout_seconds
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.comandos_dlq.arn
    maxReceiveCount     = var.max_receive_count
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "comandos_dlq" {
  queue_url = aws_sqs_queue.comandos_dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.comandos.arn]
  })
}

# Como no Execucao, quem fala com o Billing descobre os recursos pelo SSM.
resource "aws_ssm_parameter" "command_queue_url" {
  name  = "/${var.project}/billing/command-queue-url"
  type  = "String"
  value = aws_sqs_queue.comandos.url
}

resource "aws_ssm_parameter" "event_topic_arn" {
  name  = "/${var.project}/billing/event-topic-arn"
  type  = "String"
  value = aws_sns_topic.eventos.arn
}
