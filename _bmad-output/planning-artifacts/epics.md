---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: ['_bmad-output/planning-artifacts/prds/prd-digital_wallet-2026-09-19/prd.md', '_bmad-output/planning-artifacts/architecture/architecture-digital_wallet-2026-09-19/ARCHITECTURE-SPINE.md']
---

# Sistema de Carteira Digital Distribuída - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for Sistema de Carteira Digital Distribuída, decomposing the requirements from the PRD and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

Numeração alinhada à ordem dos épicos (ver Epic List).

**Epic 1 — Onboarding e Acesso**
FR-1: Rotear requisições para os serviços internos como ponto único de entrada.
FR-2: Autenticar requisições (token) antes de rotear.
FR-3: Cadastrar usuário e criar conta associada com saldo inicial zero.
FR-4: Consultar saldo atual de uma conta.

**Epic 2 — Transferências**
FR-5: Expor endpoint idempotente para débito/crédito reservado (chamado pela saga de transferência), rejeitando duplicidade via chave de idempotência.
FR-6: Persistir todo movimento como evento imutável no ledger (event sourcing) — saldo é sempre derivado, nunca sobrescrito. Publicação via Outbox pattern (evento gravado na mesma transação local; publicador assíncrono lê e publica no Kafka, sem dual-write).
FR-7: Iniciar transferência entre duas contas (origem, destino, valor), disparando a saga Reserve→Confirm→Compensate.
FR-8: Expor extrato/histórico de movimentações de uma conta, reconstruído a partir dos eventos.
FR-9: Compensar (reverter) uma transferência quando qualquer etapa da saga falhar, registrando o motivo.

**Epic 3 — Pagamento**
FR-10: Iniciar recarga de saldo via gateway sandbox (cartão simulado).
FR-11: Receber e validar webhook de confirmação do gateway, creditando a conta via evento assíncrono.

**Epic 4 — Notificações**
FR-12: Notificar (assíncrono, via evento) o usuário quando receber uma transferência.
FR-13: Notificar o usuário quando um pagamento/recarga for confirmado.

**Transversal**
FR-14: Toda operação de escrita entre serviços é idempotente (chave de idempotência obrigatória em requests que alteram estado) — inclui reenvio de notificação e chamada a provedor externo de entrega.
FR-15: Toda chamada entre serviços aplica circuit breaker (Resilience4j), com fallback definido — inclui chamada a provedor externo de notificação, quando síncrona.

### NonFunctional Requirements

NFR-1: Consistência via Outbox pattern (sem dual-write) entre banco local e evento publicado; concorrência em escrita de saldo protegida por controle otimista, com retry limitado.
NFR-2: Observabilidade — 100% das transferências geram trace distribuído completo (OpenTelemetry); métricas via Micrometer/Prometheus; logs centralizados via Loki.
NFR-3: Resiliência — indisponibilidade momentânea de serviço downstream nunca gera inconsistência de saldo (resulta em retry, saga pendente ou compensação, nunca estado indefinido).
NFR-4: Persistência database-per-service (PostgreSQL); sem acesso direto a banco de outro serviço.
NFR-5: Portabilidade local — todo o sistema sobe via Docker Compose; Kubernetes local (Minikube/Kind) como demonstração adicional.
NFR-6: Critério de sucesso mensurável de concorrência — teste de corrida com N transferências simultâneas contra saldo insuficiente para todas resulta em exatamente 1 vencedora, saldo nunca negativo nem duplicado, 0 inconsistências em N execuções.
NFR-7: Contra-métrica — nenhuma otimização de consistência pode introduzir deadlock ou timeout sistemático sob carga normal.

### Additional Requirements

- **Sem starter template** — arquitetura não especifica um starter/scaffold pronto; Épico 0 cria a estrutura de monorepo multi-módulo Maven do zero (AD Structural Seed). Monorepo = um único repositório Git com 5 módulos independentes; cada serviço continua com processo, banco e imagem Docker próprios — só o versionamento do código é compartilhado.
- Paradigma Hexagonal (Ports & Adapters) por serviço: `domain/`, `application/`, `infrastructure/` — dependência sempre para dentro (AD-7).
- Saga orquestrada como máquina de estados própria no Transaction Service, com tabelas `saga_instance` (lock otimista por `version`) e `saga_step`; correlação via `sagaId` obrigatório em comandos/replies (AD-1).
- Outbox pattern via polling publisher (scheduler por serviço lê tabela `outbox`, publica no Kafka, marca `PUBLISHED`; purge após 7 dias) (AD-2).
- Inbox pattern para idempotência: tabela `inbox` chaveada por `eventId`, purge após 7 dias (AD-3).
- Ledger event-sourced no Transaction Service: tabela `events` append-only com `UNIQUE(account_id, version)`; Transaction Service é dono único do saldo. Account Service mantém apenas uma projeção de leitura (`account_balance_cache`) atualizada via evento `BalanceChanged` (AD-4).
- Database-per-service: `account_db`, `transaction_db`, `payment_db`, `notification_db` (Postgres) (AD-5).
- Circuit breaker (Resilience4j) em toda chamada síncrona externa ao serviço, com fallback fail-fast (AD-6).
- API Gateway é o único ponto de entrada público; JWT validado uma vez no Gateway, serviços internos confiam no header repassado (AD-8).
- Envelope de evento comum `{eventId, eventType, version, occurredAt, aggregateId, payload}`; política de versionamento (aditivo não incrementa version; breaking incrementa e vai para dead-letter se não suportado) (AD-9).
- Nomenclatura de tópicos Kafka: `{domínio}.{commands|events|saga-replies}` — replies de saga em tópico `.saga-replies` dedicado.
- Stack: Java 21, Spring Boot 3.4.x, Spring Cloud 2024.0.x, Kafka 3.8.x, PostgreSQL 16, Redis 7.4, Resilience4j 2.2.x, stack de observabilidade LGTM (Prometheus, Grafana, Loki, Tempo) via OpenTelemetry/Micrometer.
- Deploy: Docker Compose (ambiente local único); manifests Kubernetes (Minikube/Kind) como demonstração adicional; CI via GitHub Actions.
- Fora de escopo (não gerar stories para): frontend, multi-moeda, KYC real, detecção de fraude, multi-tenancy, dinheiro real, Avro/Schema Registry, Debezium/CDC, staging/produção.

### UX Design Requirements

Não aplicável — projeto backend-only, sem frontend (confirmado no PRD e na Arquitetura).

### FR Coverage Map

```
Epic 0 - Fundação e Infraestrutura Base (sem FR próprio; scaffold + CI que habilitam todos os épicos abaixo)
FR1:  Epic 1 - Roteamento via API Gateway
FR2:  Epic 1 - Autenticação
FR3:  Epic 1 - Cadastro de usuário/conta
FR4:  Epic 1 - Consulta de saldo
FR5:  Epic 2 - Endpoint idempotente de débito/crédito reservado (saga)
FR6:  Epic 2 - Ledger event-sourced (Outbox pattern)
FR7:  Epic 2 - Iniciar transferência / disparar saga
FR8:  Epic 2 - Extrato/histórico
FR9:  Epic 2 - Compensação de saga
FR10: Epic 3 - Recarga via gateway sandbox
FR11: Epic 3 - Webhook de confirmação
FR12: Epic 4 - Notificação de transferência recebida
FR13: Epic 4 - Notificação de pagamento confirmado
FR14: Transversal - Idempotência (AC em Epics 1, 2, 3, 4)
FR15: Transversal - Circuit breaker (AC em Epics 1, 2, 3, 4)
```

## Epic List

### Epic 0: Fundação e Infraestrutura Base
Esqueleto do monorepo multi-serviço sobe via Docker Compose, com CI básico validando cada mudança — habilita todos os épicos seguintes. Não entrega valor de usuário final por si só (é a exceção deliberada à regra de "valor de usuário", justificada por não haver starter template).
**FRs covered:** nenhum (requisitos técnicos da Arquitetura)

### Epic 1: Onboarding e Acesso à Carteira
Usuário se cadastra, autentica via API Gateway e consulta o próprio saldo.
**FRs covered:** FR1, FR2, FR3, FR4

### Epic 2: Transferências entre Contas com Consistência Garantida
Usuário transfere saldo entre contas com garantia — mesmo sob concorrência ou falha parcial — de que nunca duplica nem perde saldo (saga Reserve→Confirm→Compensate, ledger event-sourced via Outbox, extrato).
**FRs covered:** FR5, FR6, FR7, FR8, FR9

### Epic 3: Recarga de Saldo via Gateway de Pagamento
Usuário recarrega saldo via cartão simulado (sandbox); confirmação assíncrona via webhook credita a conta de forma consistente com o ledger.
**FRs covered:** FR10, FR11

### Epic 4: Notificações de Atividade
Usuário é notificado automaticamente ao receber transferência ou ter pagamento confirmado. Como envolve reenvio (redelivery) e possível chamada a provedor externo de entrega, também aplica idempotência e circuit breaker.
**FRs covered:** FR12, FR13, FR14, FR15 (parcial)

### Epic 5: Observabilidade e Prova de Resiliência
Quem avalia o projeto consegue ver, via dashboards e testes automatizados, que o sistema é consistente sob concorrência (teste de corrida), rastreável ponta a ponta (trace distribuído) e resiliente a falha parcial (compensação visível). Materializa os critérios de sucesso do PRD (NFR-6, NFR-7) e observabilidade (NFR-2, NFR-3).
**NFRs covered:** NFR2, NFR3, NFR6, NFR7

## Epic 0: Fundação e Infraestrutura Base

Antes de qualquer funcionalidade, o esqueleto de todos os serviços roda localmente e o pipeline de CI valida cada mudança — evita que "funciona na minha máquina" vire um problema no meio do desenvolvimento. Não entrega valor de usuário final: é a exceção deliberada à regra de organização por valor, justificada porque a Arquitetura não define um starter template pronto.

### Story 0.1: Esqueleto do Monorepo e Ambiente Local via Docker Compose

As a mantenedor do projeto,
I want ter o monorepo multi-módulo com todos os serviços placeholder subindo via Docker Compose,
So that o ambiente de desenvolvimento completo esteja pronto antes de implementar qualquer regra de negócio.

**Acceptance Criteria:**

**Given** a estrutura de diretórios definida na Arquitetura (Structural Seed)
**When** o repositório é inicializado
**Then** existem os 5 módulos Maven (`account-service`, `transaction-service`, `payment-service`, `notification-service`, `api-gateway`), um repositório Git único (monorepo), cada módulo com um endpoint de health-check mínimo

**Given** o `docker-compose.yml` do projeto
**When** executo `docker compose up`
**Then** todos os serviços, Postgres (`account_db`, `transaction_db`, `payment_db`, `notification_db`), Kafka, Redis e a stack LGTM (Prometheus, Grafana, Loki, Tempo) sobem sem erro e os health-checks respondem OK

**Given** um dos serviços falha ao subir
**When** executo `docker compose up`
**Then** o log deixa claro qual serviço falhou e por quê (sem falha silenciosa)

### Story 0.2: Pipeline de CI Básico

As a mantenedor do projeto,
I want que toda alteração no repositório seja validada automaticamente,
So that eu detecte quebras de build/teste antes de mesclar código.

**Acceptance Criteria:**

**Given** um push ou pull request no repositório
**When** o GitHub Actions dispara
**Then** cada módulo Maven é compilado e os testes automatizados existentes são executados

**Given** um módulo falha na build ou nos testes
**When** o pipeline roda
**Then** o commit/PR é marcado como falho

**Given** todos os módulos passam
**When** o pipeline conclui
**Then** o status é reportado como sucesso no PR/commit

## Epic 1: Onboarding e Acesso à Carteira

Usuário se cadastra, autentica via API Gateway e consulta o próprio saldo.

### Story 1.1: Ponto Único de Entrada via API Gateway

As a integrador (cliente da API),
I want acessar todos os serviços através de um único ponto de entrada (API Gateway),
So that eu não preciso conhecer os endereços internos de cada microsserviço.

**Acceptance Criteria:**

**Given** o ambiente sobe via Docker Compose (Epic 0)
**When** faço uma requisição para o Gateway em uma rota registrada (ex: `/accounts`)
**Then** ela é roteada para o serviço correto e a resposta é retornada ao cliente

**Given** uma rota não registrada
**When** faço a requisição
**Then** recebo 404 estruturado `{code, message, traceId}`

**Given** o serviço interno de destino está fora do ar
**When** faço a requisição
**Then** o Gateway aplica circuit breaker (Resilience4j, AD-6/FR15) e retorna erro estruturado de fallback, sem travar a requisição indefinidamente

### Story 1.2: Autenticação via JWT no Gateway

As a integrador,
I want autenticar minhas requisições com um token JWT,
So that apenas chamadas autenticadas acessem os serviços internos.

**Acceptance Criteria:**

**Given** um token JWT válido
**When** faço uma requisição ao Gateway
**Then** a requisição é roteada normalmente e a identidade do usuário é repassada via header para o serviço interno (AD-8)

**Given** um token ausente ou inválido
**When** faço a requisição
**Then** recebo 401 estruturado sem que a requisição chegue a nenhum serviço interno

**Given** um token expirado
**When** faço a requisição
**Then** recebo 401 com código específico de expiração

### Story 1.3: Cadastro de Usuário e Conta

As a novo usuário,
I want me cadastrar e ter uma conta criada automaticamente com saldo zero,
So that eu possa começar a usar a carteira.

**Acceptance Criteria:**

**Given** dados válidos de cadastro (nome, email, senha)
**When** envio a requisição de cadastro via Gateway
**Then** uma conta é criada no Account Service com saldo inicial 0 e um id único (UUID) é retornado

**Given** um email já cadastrado
**When** tento cadastrar novamente
**Then** recebo erro 409 estruturado, e nenhuma conta duplicada é criada

**Given** uma requisição de cadastro repetida com a mesma chave de idempotência
**When** reenviada (retry de rede)
**Then** a mesma conta é retornada sem criar duplicata (FR14, Inbox)

### Story 1.4: Consulta de Saldo

As a usuário autenticado,
I want consultar meu saldo atual,
So that eu saiba quanto tenho disponível na carteira.

**Acceptance Criteria:**

**Given** uma conta existente com saldo X
**When** consulto `GET /accounts/{id}/balance` autenticado
**Then** recebo o saldo atual X, servido pela projeção local `account_balance_cache` (AD-4)

**Given** tento consultar o saldo de uma conta que não é minha
**When** faço a requisição
**Then** recebo 403 estruturado

**Given** a conta não existe
**When** consulto
**Then** recebo 404 estruturado

## Epic 2: Transferências entre Contas com Consistência Garantida

O coração do projeto: usuário transfere saldo entre contas, com garantia — mesmo sob concorrência ou falha parcial — de que nunca duplica nem perde saldo.

### Story 2.1: Endpoint Idempotente de Reserva de Saldo

As a orquestrador de transferências (Transaction Service),
I want reservar/debitar saldo de uma conta de forma idempotente,
So that reprocessar a mesma operação (retry) nunca duplique o débito.

**Acceptance Criteria:**

**Given** saldo suficiente na conta de origem
**When** o Transaction Service chama o endpoint de reserva com uma chave de idempotência nova
**Then** o saldo é reservado e a operação retorna sucesso

**Given** saldo insuficiente
**When** a reserva é solicitada
**Then** a operação falha com erro estruturado, sem reservar nada

**Given** a mesma chave de idempotência é reenviada
**When** o endpoint é chamado novamente
**Then** o resultado da primeira chamada é retornado sem executar nova reserva (Inbox, AD-3)

**Given** N chamadas concorrentes de débito contra uma conta cujo saldo só cobre 1
**When** todas competem simultaneamente
**Then** exatamente uma é bem-sucedida e o saldo final nunca fica negativo (base do teste de corrida da Story 5.3)

### Story 2.2: Ledger Event-Sourced do Saldo

As a mantenedor do sistema,
I want que toda movimentação de saldo seja registrada como evento imutável,
So that o saldo nunca seja sobrescrito e sempre seja auditável.

**Acceptance Criteria:**

**Given** uma movimentação de débito ou crédito é solicitada para uma conta
**When** o evento correspondente é gravado
**Then** ele entra na tabela `events` (append-only) com `UNIQUE(account_id, version)`, sem nenhum `UPDATE` direto de saldo em código

**Given** duas escritas concorrentes de evento para a mesma conta na mesma versão
**When** ambas tentam inserir
**Then** apenas uma é aceita e a outra recebe conflito de concorrência para retry

**Given** os eventos de uma conta
**When** o saldo é recalculado a partir deles
**Then** o resultado bate exatamente com a soma dos eventos (AD-4)

### Story 2.3: Iniciar Transferência entre Contas

As a usuário autenticado,
I want transferir um valor da minha conta para outra conta,
So that eu possa enviar dinheiro para outro usuário.

**Acceptance Criteria:**

**Given** saldo suficiente na conta de origem
**When** solicito uma transferência para uma conta de destino válida
**Then** a saga Reserve→Confirm→Compensate é iniciada (`saga_instance` criado com status `STARTED`), usando o endpoint de reserva (Story 2.1) e o ledger (Story 2.2), e a API retorna o id da transferência com status pendente

**Given** a conta de destino não existe
**When** solicito a transferência
**Then** recebo erro estruturado antes de qualquer reserva de saldo

**Given** a saga é concluída com sucesso (Reserve+Confirm)
**When** consulto o status da transferência
**Then** vejo status `CONFIRMED` e o saldo já refletido em ambas as contas (via eventos gravados na Story 2.2)

### Story 2.4: Extrato de Movimentações

As a usuário autenticado,
I want consultar o extrato de movimentações da minha conta,
So that eu veja o histórico de todas as transferências e recargas.

**Acceptance Criteria:**

**Given** uma conta com múltiplos eventos de movimentação
**When** consulto o extrato
**Then** recebo a lista de movimentações em ordem cronológica, reconstruída a partir da tabela `events`

**Given** a conta não possui movimentações
**When** consulto o extrato
**Then** recebo uma lista vazia (não erro)

**Given** tento consultar o extrato de uma conta que não é minha
**When** faço a requisição
**Then** recebo 403

### Story 2.5: Compensação de Transferência

As a mantenedor do sistema,
I want que uma transferência com falha em qualquer etapa seja automaticamente revertida,
So that o saldo da conta de origem nunca fique bloqueado ou incorreto após uma falha.

**Acceptance Criteria:**

**Given** uma saga em `RESERVED`
**When** o step Confirm falha (ex: conta de destino indisponível)
**Then** a saga transiciona para `COMPENSATING`, o débito reservado é revertido, e o status final é `COMPENSATED` com motivo registrado

**Given** uma falha injetada num participante durante a saga
**When** a compensação ocorre
**Then** o trace distribuído mostra a saga completa incluindo o step de compensação (liga com Story 5.1)

**Given** uma tentativa de aplicar Confirm e Compensate simultaneamente na mesma saga
**When** ambas competem
**Then** o lock otimista de `saga_instance.version` garante que só uma transição vence (AD-1)

## Epic 3: Recarga de Saldo via Gateway de Pagamento

Usuário recarrega saldo via cartão simulado (sandbox); confirmação assíncrona via webhook credita a conta de forma consistente com o ledger do Epic 2.

### Story 3.1: Iniciar Recarga de Saldo

As a usuário autenticado,
I want iniciar uma recarga de saldo via cartão simulado,
So that eu adicione dinheiro fictício à minha carteira.

**Acceptance Criteria:**

**Given** dados válidos de cartão simulado e um valor de recarga
**When** solicito a recarga
**Then** o Payment Service cria uma intenção de pagamento no gateway sandbox (Stripe/Mercado Pago) e retorna um id de pagamento com status `PENDING`

**Given** um valor de recarga inválido (≤ 0)
**When** solicito
**Then** recebo erro estruturado sem chamar o gateway sandbox

**Given** o gateway sandbox está indisponível
**When** solicito a recarga
**Then** o circuit breaker intercepta e retorna erro estruturado de fallback (FR15)

### Story 3.2: Confirmação de Pagamento via Webhook

As a mantenedor do sistema,
I want receber e validar o webhook do gateway sandbox,
So that o saldo do usuário seja creditado automaticamente quando o pagamento for confirmado.

**Acceptance Criteria:**

**Given** um webhook de confirmação de pagamento válido (assinatura verificada)
**When** recebido
**Then** um evento de crédito é publicado via Outbox para o Transaction Service, que grava o evento no ledger (AD-4) e o saldo é atualizado

**Given** um webhook com assinatura inválida
**When** recebido
**Then** é rejeitado com 401 e nenhum crédito é aplicado

**Given** o mesmo webhook é entregue duas vezes pelo gateway (redelivery)
**When** processado
**Then** o Inbox garante que o crédito é aplicado uma única vez (FR14)

## Epic 4: Notificações de Atividade

Usuário é notificado automaticamente ao receber transferência ou ter pagamento confirmado. Como o fluxo envolve redelivery de evento e possível chamada a um provedor externo de entrega, idempotência (FR14) e circuit breaker (FR15) também se aplicam aqui.

### Story 4.1: Notificação de Transferência Recebida

As a usuário,
I want ser notificado quando receber uma transferência,
So that eu saiba que meu saldo mudou sem precisar consultar ativamente.

**Acceptance Criteria:**

**Given** uma transferência confirmada (saga `CONFIRMED`)
**When** o evento de crédito é publicado
**Then** o Notification Service consome o evento e registra/envia uma notificação para o destinatário

**Given** o mesmo evento é entregue duas vezes (redelivery do Kafka)
**When** consumido
**Then** apenas uma notificação é gerada — chave `eventId` no Inbox garante a deduplicação (FR14, AD-3)

**Given** o envio da notificação depende de um provedor externo síncrono (ex: email/push)
**When** esse provedor está indisponível ou lento
**Then** a chamada passa por circuit breaker com fallback (ex: mensagem reenfileirada para retry, nunca uma tentativa travada indefinidamente) (FR15, AD-6)

**Given** o Notification Service está temporariamente fora do ar
**When** o evento é publicado
**Then** ele permanece na fila (Kafka) e é processado assim que o serviço volta

### Story 4.2: Notificação de Pagamento Confirmado

As a usuário,
I want ser notificado quando minha recarga for confirmada,
So that eu saiba que meu saldo foi atualizado.

**Acceptance Criteria:**

**Given** uma recarga confirmada via webhook (Story 3.2)
**When** o evento de crédito de pagamento é publicado
**Then** o Notification Service gera uma notificação para o usuário

**Given** o mesmo evento é entregue duas vezes
**When** consumido
**Then** apenas uma notificação é gerada — chave `eventId` no Inbox garante a deduplicação (FR14, AD-3)

**Given** o envio da notificação depende de um provedor externo síncrono
**When** esse provedor está indisponível ou lento
**Then** a chamada passa por circuit breaker com fallback, sem travar o consumidor Kafka (FR15, AD-6)

## Epic 5: Observabilidade e Prova de Resiliência

Quem avalia o projeto consegue ver, via dashboards e testes automatizados, que o sistema é consistente sob concorrência, rastreável ponta a ponta e resiliente a falha parcial.

### Story 5.1: Tracing Distribuído Ponta a Ponta

As a avaliador do projeto,
I want ver o trace completo de uma transferência atravessando todos os serviços envolvidos,
So that eu possa verificar que a saga é depurável e observável.

**Acceptance Criteria:**

**Given** uma transferência é executada
**When** consulto o Tempo/Jaeger pelo trace id
**Then** vejo todos os spans dos serviços envolvidos (Gateway, Transaction, Account, Kafka) em ordem, incluindo o step de compensação quando aplicável

**Given** uma requisição falha em algum serviço
**When** consulto o trace
**Then** o span com erro está claramente marcado

### Story 5.2: Dashboards de Métricas de Negócio e Técnicas

As a avaliador do projeto,
I want ver dashboards no Grafana com métricas de negócio e técnicas,
So that eu possa verificar a saúde do sistema em tempo real.

**Acceptance Criteria:**

**Given** o sistema em uso
**When** abro o dashboard Grafana
**Then** vejo latência p50/p95/p99, taxa de erro, lag de consumidor Kafka, e saldo total do sistema como métrica de negócio, todos populados por tráfego real

**Given** nenhum tráfego recente
**When** abro o dashboard
**Then** os painéis mostram "sem dados" de forma clara, não erro

### Story 5.3: Teste Automatizado de Concorrência

As a mantenedor do sistema,
I want um teste automatizado que dispare N transferências concorrentes contra uma conta com saldo insuficiente para todas,
So that eu comprove que o sistema nunca duplica nem perde saldo sob concorrência.

**Acceptance Criteria:**

**Given** uma conta com saldo que cobre exatamente 1 transferência
**When** N transferências concorrentes são disparadas contra ela
**Then** exatamente uma é bem-sucedida, o saldo final nunca é negativo, e 0 inconsistências ocorrem em execuções repetidas do teste (NFR6)

**Given** o teste de concorrência em execução
**When** medido o tempo de resposta
**Then** nenhuma transação trava indefinidamente — sem deadlock/timeout sistemático (NFR7)

### Story 5.4: Cenário de Falha Injetada com Compensação Visível

As a avaliador do projeto,
I want ver um cenário de falha injetada (serviço indisponível durante uma saga) sendo compensado corretamente,
So that eu confirme a resiliência do sistema na prática.

**Acceptance Criteria:**

**Given** um serviço participante é derrubado propositalmente durante uma saga em andamento
**When** a saga tenta o step seguinte
**Then** ela transiciona para `COMPENSATING` e completa a compensação assim que possível

**Given** o cenário de falha é executado
**When** consulto Grafana/Tempo
**Then** a falha e a recuperação aparecem claramente na observabilidade (liga com Story 5.1/5.2)
