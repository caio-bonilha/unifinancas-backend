// T1.11.2, SAD Sec15.7/RPerf-03: teste de carga leve nos endpoints de
// leitura do nucleo (GET /v1/dashboard, GET /v1/lancamentos), validando a
// meta p95 < 400ms sob carga tipica de uso pessoal/familiar - NAO e uma
// suite continua de alta escala (Sec15.7), roda manualmente antes de cada
// release maior, nao faz parte do pipeline de build (mvn test/verify).
//
// Uso (requer k6 - https://k6.io, ou `docker run --network=host -v
// "$(pwd)/perf/k6:/scripts" grafana/k6 run /scripts/leitura-nucleo.js`):
//
//   k6 run perf/k6/leitura-nucleo.js
//   k6 run -e BASE_URL=https://homologacao.example.com perf/k6/leitura-nucleo.js
//
// Pre-requisito: a API estar de pe (BASE_URL, default http://localhost:8080)
// contra um banco onde criar um usuario de teste e permitido (nunca rodar
// contra producao com dados reais).

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// RNF/RPerf-03: "carga tipica de uso pessoal/familiar" - poucos usuarios
// simultaneos navegando o dashboard/lista de lancamentos, nao milhares.
export const options = {
  scenarios: {
    leitura_nucleo: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
    },
  },
  thresholds: {
    // RPerf-03: p95 < 400ms para cada endpoint de leitura do nucleo, medido separadamente via tag.
    'http_req_duration{endpoint:dashboard}': ['p(95)<400'],
    'http_req_duration{endpoint:lancamentos}': ['p(95)<400'],
    http_req_failed: ['rate<0.01'],
  },
};

const HEADERS_JSON = { 'Content-Type': 'application/json' };

/**
 * Roda uma vez, antes das VUs comecarem: cria um usuario de teste e um
 * volume modesto de Lancamentos (RF-06), para o dashboard/lista nao serem
 * medidos contra uma conta vazia - carga tipica tem historico real, ainda
 * que pequeno (uso pessoal/familiar, nao milhares de linhas).
 */
export function setup() {
  const email = `k6-loadtest-${Date.now()}@example.com`;
  const cadastroRes = http.post(
    `${BASE_URL}/v1/auth/cadastro`,
    JSON.stringify({ nome: 'K6 Load Test', email, senha: 'senha12345' }),
    { headers: HEADERS_JSON },
  );
  if (cadastroRes.status !== 201) {
    throw new Error(`Falha ao criar usuario de teste (status ${cadastroRes.status}): ${cadastroRes.body}`);
  }
  const cadastro = cadastroRes.json();
  const headers = {
    ...HEADERS_JSON,
    Authorization: `Bearer ${cadastro.accessToken}`,
    'X-Ambiente-Financeiro-Id': cadastro.ambienteFinanceiroId,
  };

  for (let i = 0; i < 30; i++) {
    http.post(
      `${BASE_URL}/v1/lancamentos`,
      JSON.stringify({
        tipo: i % 2 === 0 ? 'DESPESA' : 'RECEITA',
        valor: (10 + i).toFixed(2),
        dataEmissao: '2026-08-01',
        dataVencimento: '2026-08-10',
        descricao: `Lancamento de carga ${i}`,
      }),
      { headers },
    );
  }

  return { authorization: headers.Authorization, ambienteFinanceiroId: cadastro.ambienteFinanceiroId };
}

/** Corpo executado repetidamente por cada VU durante `duration` - simula um usuario navegando dashboard + lista. */
export default function (dados) {
  const headers = {
    Authorization: dados.authorization,
    'X-Ambiente-Financeiro-Id': dados.ambienteFinanceiroId,
  };

  const dashboardRes = http.get(`${BASE_URL}/v1/dashboard`, { headers, tags: { endpoint: 'dashboard' } });
  check(dashboardRes, { 'dashboard retorna 200': (r) => r.status === 200 });

  const lancamentosRes = http.get(`${BASE_URL}/v1/lancamentos?limit=20`, { headers, tags: { endpoint: 'lancamentos' } });
  check(lancamentosRes, { 'lancamentos retorna 200': (r) => r.status === 200 });

  sleep(1);
}
