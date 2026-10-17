# Unifinancas — Backend

API REST do **Unifinancas**, sistema de gestão financeira pessoal e multiambiente para pessoas físicas e
famílias (não é voltado a empresas). Este repositório contém **apenas o backend**.

> **[PREENCHER]** Disciplina / Desafio · Equipe · Integrantes · Orientador

## Visão geral

- Monólito modular em camadas (`domain` → `application` → `infrastructure` → `presentation`), com a regra de
  dependência verificada automaticamente por ArchUnit.
- Multi-tenant lógico: cada dado pertence a um **Ambiente Financeiro** e todo repositório recebe
  `ambienteFinanceiroId` explicitamente (isolamento entre tenants).
- API versionada (`/v1`), autenticação JWT (RS256) com refresh token rotativo.
- Processamento assíncrono por tabela de jobs (`job_execucao`) com worker interno — sem broker de mensagens.

## Stack

| Item | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.3.4 (Web, Data JPA, Security, Validation, AOP, Mail) |
| Banco | PostgreSQL 18 (migrações com Flyway, IDs UUID v7) |
| Segurança | JWT (jjwt), bcrypt |
| Storage de documentos | Amazon S3 (MinIO no ambiente local) |
| Integrações | Resend/SMTP (e-mail), Firebase Cloud Messaging (push), Gemini/OpenAI (OCR com IA, opcional) |
| Documentos | Apache PDFBox, Tesseract (subprocesso), Apache POI |
| Resiliência / logs | Resilience4j, Logback JSON (logstash encoder) |
| Testes | JUnit 5, Mockito, Spring Security Test, Testcontainers, ArchUnit |

## Pré-requisitos

- JDK 21
- Docker (PostgreSQL e MinIO locais; também usado pelos testes de integração via Testcontainers)
- Opcional: Tesseract (idioma `por`) para o OCR local de imagens; [k6](https://k6.io) para os testes de carga

## Como rodar localmente

```bash
# 1. variáveis do Docker Compose
cp .env.example .env          # Windows: Copy-Item .env.example .env

# 2. PostgreSQL + MinIO (cria o bucket de documentos)
docker compose up -d

# 3. API em http://localhost:8080 (profile "local" aponta o storage para o MinIO)
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
# PowerShell: $env:SPRING_PROFILES_ACTIVE = "local"; ./mvnw spring-boot:run
```

Em desenvolvimento, se `JWT_CHAVE_PRIVADA`/`JWT_CHAVE_PUBLICA` não forem definidas, a aplicação gera um
par RSA efêmero a cada start (os tokens deixam de valer ao reiniciar). **Não use isso em produção.**

### Variáveis de ambiente principais

Todas têm default para desenvolvimento em `src/main/resources/application.yml`.

| Variável | Para quê |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL |
| `JWT_CHAVE_PRIVADA`, `JWT_CHAVE_PUBLICA`, `JWT_KEY_ID` | Chaves de assinatura do JWT (PEM) |
| `CORS_ORIGENS_PERMITIDAS` | Origens do(s) cliente(s) web autorizadas |
| `APP_URL_BASE` | URL base do cliente web (link de redefinição de senha) |
| `MAIL_*`, `MAIL_REMETENTE`, `RESEND_API_KEY` | E-mail transacional |
| `FCM_CREDENCIAIS_PATH` | Credenciais do Firebase (push) |
| `STORAGE_S3_*` | Bucket/credenciais do storage de documentos |
| `GEMINI_API_KEY` / `OPENAI_API_KEY`, `OCR_IA_PROVEDOR` | OCR com IA (opcional; desligado se vazio) |

## Testes

```bash
./mvnw -B verify    # unitários + ArchUnit + integração (Testcontainers; exige Docker ligado)
./mvnw test         # só unitários e ArchUnit
```

O ArchUnit (`src/test/java/.../architecture/ArchitectureTest.java`) falha o build se `domain` importar
`infrastructure`/`presentation` ou se um repositório for declarado sem `ambienteFinanceiroId`.
A integração contínua está em `.github/workflows/ci.yml` (build, testes e revisão de dependências em PR).

Teste de carga leve (RPerf-03, p95 < 400 ms): ver [`perf/k6/README.md`](perf/k6/README.md).

## Estrutura

```text
src/main/java/br/com/unifinancas/
 ├── domain/          # entidades, regras de negócio, eventos, interfaces de repositório
 ├── application/     # casos de uso e portas para a infraestrutura
 ├── infrastructure/  # JPA, segurança, storage, OCR, notificações, jobs
 ├── presentation/    # controllers REST, DTOs, tratamento de erros
 └── shared/          # configuração e utilitários sem regra de negócio
src/main/resources/db/migration/   # migrações Flyway (V1 … V37)
perf/k6/                           # teste de carga
```

## API

Todas as rotas ficam sob `/v1`. Exceto `/v1/auth/*`, exigem `Authorization: Bearer <token>` e o header
`X-Ambiente-Financeiro-Id` (tenant ativo). Grupos de recursos:

`auth` · `ambientes` (+ membros e convites) · `contas-bancarias` · `movimentacoes` · `lancamentos` ·
`lancamentos-recorrentes` · `categorias` · `contatos` · `orcamentos-metas` · `notificacoes` · `dispositivos` ·
`dashboard` · `relatorios` · `cartoes` (+ faturas) · `compras` · `vendas` · `documentos` · `importacoes` ·
`usuarios/me`.

Os endpoints estão nos controllers em `src/main/java/br/com/unifinancas/presentation/rest/`. Não há
Swagger/OpenAPI publicado neste repositório.

## Documentação

O Levantamento Técnico de Necessidades do backend (diagnóstico, necessidades e requisitos) é entregue como
documento separado e referencia este repositório.

## Licença

**[PREENCHER]** — o repositório ainda não define uma licença.
