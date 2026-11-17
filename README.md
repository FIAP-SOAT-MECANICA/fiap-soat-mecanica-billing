# Billing — Oficina Mecânica

Fundação isolada do microsserviço responsável por orçamento e cobrança. O projeto usa Java 21, Spring Boot 3.5, PostgreSQL/Flyway, JUnit e Testcontainers. O domínio calcula valores com `BigDecimal` a partir de snapshots imutáveis de serviço e peças. Uma cobrança só pode nascer de um orçamento aprovado; notificações repetidas da mesma decisão de pagamento são idempotentes, e um pagamento aprovado não pode regredir.

## Estado desta entrega

- Modelo de `Orcamento`, `ItemOrcamento`, `ItemPeca` e `Pagamento`, com transições de aprovação, recusa, expiração, cancelamento e decisão da cobrança.
- Migração `V1__billing_core.sql` para orçamentos, pagamentos, eventos de webhook, mensagens processadas e outbox. Há unicidade por `(os_id, revisao)`, `message_id`, `provider_event_id` e `referencia_externa`, além de vínculo composto entre pagamento e OS do orçamento.
- `CobrancaGateway` define a fronteira com o futuro adapter do Mercado Pago. Não há adapter, webhook, API de orçamento ou processamento de mensagens nesta fundação; nenhum pagamento é simulado em produção.
- CI executa `verify` com JaCoCo exigindo 80% de cobertura de instruções. Se `SONAR_TOKEN`, `SONAR_PROJECT_KEY` e `SONAR_ORGANIZATION` estiverem configurados no repositório, executa também análise SonarQube Cloud e aguarda o Quality Gate. Essas configurações e o gate remoto ainda precisam ser ativados e comprovados.

## Executar e verificar

Com Java 21 e Docker ativos, rode `./mvnw.cmd verify` no Windows ou `./mvnw verify` em Linux/macOS. Os testes de domínio não exigem serviços externos; o teste da migração usa PostgreSQL em Testcontainers. O relatório de cobertura fica em `target/site/jacoco/index.html`. Para iniciar a aplicação, forneça `BILLING_DB_URL`, `BILLING_DB_USER` e `BILLING_DB_PASSWORD` para um PostgreSQL acessível e rode `./mvnw.cmd spring-boot:run`. Flyway aplica a migração ao iniciar. `GET /actuator/health` verifica a aplicação. A imagem pode ser construída após `verify` com `docker build -t mecanica-billing .`.

## Integração pendente com a Saga

O [plano da Fase 4](https://gist.github.com/lenilsonf/c8fa28726f348a97c178e870665cef4f) define a Saga orquestrada no serviço OS: Execução publica `DiagnosticoConcluido` para o OS, que solicita `GerarOrcamento` ao Billing. O PDF de 11/11 propõe o Billing consumir `DiagnosticoConcluido` diretamente. A escolha do fluxo, o contrato final do comando/evento e a responsabilidade pelo disparo permanecem pendentes de decisão entre os serviços. Esta fundação não implementa nenhum dos dois consumidores e preserva o domínio independente dessa escolha.

O campo `revisao` na migração permite mais de um orçamento por OS. A atribuição da próxima revisão, o bloqueio transacional de duplicatas, a persistência dos agregados, outbox e deduplicação por `message_id` serão implementados no adapter de aplicação/persistência. As transições de domínio rejeitam aprovar ou recusar um orçamento que não esteja `GERADO`; um consumidor futuro deverá deduplicar a mensagem antes de invocar essas transições.

## Infraestrutura de mensageria

`infra/` é um root module Terraform com a mensageria do Billing, no mesmo padrão do Execução:

| Recurso | Nome | Para que serve |
| --- | --- | --- |
| Fila SQS | `mecanica-billing-comandos` | Comandos do OS: `GerarOrcamento`, `CancelarOrcamento`, `SolicitarPagamento` |
| Fila SQS (DLQ) | `mecanica-billing-comandos-dlq` | Recebe o comando que falhou 5 vezes; retenção de 14 dias |
| Tópico SNS | `mecanica-billing-eventos` | Eventos do Billing: `OrcamentoAprovado`, `OrcamentoRecusado` e os próximos |
| Parâmetro SSM | `/mecanica/billing/command-queue-url` | Onde o OS descobre a fila de comandos |
| Parâmetro SSM | `/mecanica/billing/event-topic-arn` | Onde o OS descobre o tópico, para criar a fila e a assinatura dele |

O OS é dono da assinatura ao tópico, como já é com o tópico do Execução; o Billing não cria fila de respostas. Como o OS lê os dois parâmetros com `data`, este módulo precisa do `apply` **antes** do `infra/messaging` do OS:

```
fiap-soat-mecanica-k8s → fiap-soat-mecanica-execucao, fiap-soat-mecanica-billing → fiap-soat-mecanica-os (infra/messaging)
```

O `bucket` e a `region` do state ficam fora do código, porque o nome do bucket depende da conta da AWS Academy:

```bash
cd infra
terraform init -backend-config="bucket=<bucket-de-state>" -backend-config="region=us-east-1" \
  -backend-config="key=billing/terraform.tfstate"
terraform plan
terraform apply
```

Sem credenciais da AWS, o CI valida o módulo com `terraform fmt -check`, `init -backend=false` e `validate`. Ainda não há CD: o `apply` é manual, e RDS, ECR e manifestos do Kubernetes entram em etapas seguintes.
