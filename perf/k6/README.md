# Testes de carga (k6)

T1.11.2, SAD §15.7/RPerf-03. Teste de carga **leve**, não uma suíte contínua
de alta escala — roda manualmente antes de cada release maior, não faz
parte do pipeline de build (`mvn test`/`mvn verify`).

## O que valida

`leitura-nucleo.js` mede o tempo de resposta de `GET /v1/dashboard` e
`GET /v1/lancamentos` sob carga típica de uso pessoal/familiar (10 usuários
virtuais simultâneos por 30s) e falha se o p95 de qualquer um dos dois
ultrapassar 400ms (RPerf-03), ou se mais de 1% das requisições falharem.

## Como rodar

Requer a API rodando (padrão `http://localhost:8080`) contra um banco onde
criar um usuário de teste é seguro — **nunca rodar contra produção com
dados reais**.

Com o [k6](https://k6.io/docs/get-started/installation/) instalado:

```bash
k6 run perf/k6/leitura-nucleo.js
```

Sem instalar, via Docker:

```bash
docker run --rm --network=host -v "$(pwd)/perf/k6:/scripts" grafana/k6 run /scripts/leitura-nucleo.js
```

Contra outro ambiente (ex.: homologação, SAD §16.1):

```bash
k6 run -e BASE_URL=https://homologacao.example.com perf/k6/leitura-nucleo.js
```

## Lendo o resultado

O k6 imprime um resumo ao final com `http_req_duration` por tag
(`endpoint:dashboard`, `endpoint:lancamentos`) e se cada *threshold*
passou (✓) ou falhou (✗). Um `✗` no p95 de qualquer endpoint indica
regressão de performance em relação à meta RPerf-03 e vale investigar
antes do release (índices faltando, N+1, etc.) — o SAD já antecipa que a
primeira estratégia a avaliar, se isso acontecer, é cache em memória do
processo (Caffeine) para os agregados do dashboard, antes de um cache
distribuído (§10).
