---
title: PRD - Sistema de Carteira Digital Distribuída
status: final
created: 2026-09-19
updated: 2026-09-19
---

# PRD — Sistema de Carteira Digital Distribuída

## 1. Visão

Projeto de portfólio técnico: uma carteira digital simulada, construída como microsserviços em Java/Spring Boot, cujo valor está em demonstrar domínio de arquitetura de sistemas distribuídos — consistência financeira sob concorrência, padrões de mensageria/transação, e observabilidade de ponta a ponta. Não há frontend: a interface do produto é a API (Swagger/OpenAPI), coleções Postman/Insomnia e os dashboards de observabilidade. Dinheiro é fictício (ledger próprio); entrada de saldo é simulada via gateway sandbox (Stripe ou Mercado Pago), sem movimentação real de dinheiro em nenhuma etapa.

## 2. Objetivo e Critérios de Sucesso

`[ASSUMPTION]` Como o objetivo é portfólio técnico, sucesso é demonstrável e verificável, não uma métrica de negócio real:

- **Correção sob concorrência**: um teste de corrida (N transferências simultâneas contra uma conta cujo saldo só cobre 1) resulta em exatamente uma vencedora, saldo final nunca negativo e nunca duplicado, 0 inconsistências em N execuções.
- **Saga observável e depurável**: uma transferência entre contas gera um trace distribuído completo (Tempo/Jaeger) atravessando os serviços envolvidos, visível do início ao fim (incluindo compensação, se disparada).
- **Dashboards de negócio+técnico no Grafana**: latência p50/p95/p99, taxa de erro, lag de consumidor Kafka, e saldo total do sistema como métrica de negócio, todos populados por uso real do sistema (não mockado).
- **Resiliência demonstrada**: pelo menos um cenário de falha injetada (ex: serviço fora do ar durante uma saga) com compensação executada corretamente e visível na observabilidade.

**Contra-métrica**: nenhuma otimização de consistência pode introduzir deadlock ou timeout sistemático nas transferências — se o teste de concorrência passar às custas de travar o sistema sob carga normal, não conta como sucesso.

## 3. Escopo do MVP

Serviços (todos entram no MVP, não há fase 2 para esses cinco — `[ASSUMPTION]`: mantidos como serviços separados desde o início, incluindo Account e Transaction, porque fundir os dois removeria a principal demonstração de saga entre serviços, que é o ponto central do projeto):

- **Account Service** — cadastro de usuário, conta, saldo atual (fonte da verdade de saldo corrente).
- **Transaction Service** — transferências entre contas; ledger de movimentações via event sourcing (nunca sobrescreve saldo, sempre deriva de eventos).
- **Payment Service** — integração com gateway sandbox (recarga de saldo via cartão simulado, webhooks).
- **Notification Service** — notificações assíncronas (transferência recebida, pagamento confirmado).
- **API Gateway** — ponto único de entrada, roteamento, autenticação.

**Fluxo de referência da saga de transferência**: Reserve → Confirm → Compensate, orquestrado (não coreografado) — orquestração escolhida deliberadamente porque facilita depuração/observabilidade, que é objetivo do projeto (coreografia tende a gerar "event storms" difíceis de rastrear).

### Fora de escopo (explícito)

`[ASSUMPTION]` Confirmar com o usuário, mas assumindo fora de escopo por padrão em projeto de portfólio backend-only:
- Frontend (web ou mobile) de qualquer tipo.
- Multi-moeda / câmbio.
- KYC real ou verificação de identidade além de cadastro simples.
- Detecção de fraude.
- Multi-tenancy / white-label.
- Dinheiro real em qualquer etapa (mesmo com gateway real, sempre em modo sandbox).

## 4. Requisitos Funcionais

Numerados na ordem em que os épicos de implementação os entregam (ver `epics.md`).

**Epic 1 — Onboarding e Acesso (API Gateway + Account Service)**
- FR-1: Rotear requisições para os serviços internos como ponto único de entrada.
- FR-2: Autenticar requisições (token) antes de rotear.
- FR-3: Cadastrar usuário e criar conta associada com saldo inicial zero.
- FR-4: Consultar saldo atual de uma conta.

**Epic 2 — Transferências (Transaction Service)**
- FR-5: Expor endpoint idempotente para débito/crédito reservado (chamado pela saga de transferência), rejeitando duplicidade via chave de idempotência.
- FR-6: Persistir todo movimento como evento imutável no ledger (event sourcing) — saldo é sempre derivado, nunca sobrescrito. A publicação desses eventos segue o **Outbox pattern**: o evento é gravado na mesma transação local da escrita do movimento; um publicador assíncrono lê os eventos pendentes e os publica no Kafka, eliminando dual-write.
- FR-7: Iniciar transferência entre duas contas (origem, destino, valor), disparando a saga Reserve→Confirm→Compensate.
- FR-8: Expor extrato/histórico de movimentações de uma conta, reconstruído a partir dos eventos.
- FR-9: Compensar (reverter) uma transferência quando qualquer etapa da saga falhar, registrando o motivo.

**Epic 3 — Pagamento (Payment Service)**
- FR-10: Iniciar recarga de saldo via gateway sandbox (cartão simulado).
- FR-11: Receber e validar webhook de confirmação do gateway, creditando a conta via evento assíncrono.

**Epic 4 — Notificações (Notification Service)**
- FR-12: Notificar (assíncrono, via evento) o usuário quando receber uma transferência.
- FR-13: Notificar o usuário quando um pagamento/recarga for confirmado.

**Transversal (todos os serviços)**
- FR-14: Toda operação de escrita entre serviços é idempotente (chave de idempotência obrigatória em requests que alteram estado) — inclui reenvio de notificação e qualquer chamada a provedor externo de entrega.
- FR-15: Toda chamada entre serviços (e a qualquer provedor externo, ex: gateway de pagamento sandbox ou canal de notificação) aplica circuit breaker (Resilience4j), com fallback definido.

## 5. Requisitos Não-Funcionais

- **Consistência**: garantia via Outbox pattern (sem dual-write) entre banco local e evento publicado; concorrência em escrita de saldo protegida por controle otimista (`UNIQUE(aggregate_id, version)` ou equivalente) com retry limitado.
- **Observabilidade**: 100% das transações de transferência devem gerar trace distribuído completo (OpenTelemetry); métricas expostas via Micrometer/Prometheus; logs centralizados via Loki.
- **Resiliência**: nenhuma indisponibilidade momentânea de um serviço downstream pode causar inconsistência de saldo — deve resultar em retry, saga pendente, ou compensação, nunca em estado indefinido.
- **Persistência**: database-per-service (PostgreSQL); sem acesso direto a banco de outro serviço.
- **Portabilidade local**: todo o sistema sobe via Docker Compose; Kubernetes local (Minikube/Kind) como demonstração adicional, não requisito de produção.

## 6. Riscos e Trade-offs Conhecidos

- Saga **não** oferece atomicidade all-or-nothing real — é consistência eventual orquestrada. Isso é aceito conscientemente como parte do que o projeto quer demonstrar (o trade-off em si é conteúdo de portfólio).
- Outbox sem limpeza cresce indefinidamente — precisa de rotina de purge/TTL definida na Architecture.
- Sagas orquestradas concentram lógica no orquestrador — aceito em troca de depurabilidade (prioridade do projeto sobre desacoplamento máximo).

## 7. Próximos Passos

1. `bmad-architecture` — decisões de arquitetura (comunicação entre serviços, orquestrador de saga, esquema de eventos, estrutura do outbox, estratégia de deploy).
2. `bmad-create-epics-and-stories` — quebra em épicos e stories a partir deste PRD + arquitetura.
