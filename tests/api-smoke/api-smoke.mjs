#!/usr/bin/env node
// E2E da Saga pelo API Gateway (F7-b/c/e). ESCREVE DADOS: cada rodada cria cliente, veiculo e peca
// proprios (runId) e abre 3 OS. Nao paga: o Mercado Pago com credenciais de teste nao notifica o webhook
// sozinho, entao o E2E vai ate AGUARDANDO_PAGAMENTO com o pagamento PENDENTE e o link (F7-b).
// Variaveis: API_BASE_URL (obrigatoria), IDENTITY_URL/OS_URL/BILLING_URL/EXECUCAO_URL (rodada local),
// E2E_SEM_BORDA, E2E_EXIGIR_COBRANCA, E2E_TIMEOUT. Detalhes em tests/api-smoke/README.md.
// Saida: 0 se todos os checks passarem, 1 se algum falhar. Node 22, fetch nativo, sem dependencias.

const API = (process.env.API_BASE_URL ?? '').replace(/\/+$/, '');
const URLS = {
  identity: (process.env.IDENTITY_URL ?? API).replace(/\/+$/, ''),
  os: (process.env.OS_URL ?? API).replace(/\/+$/, ''),
  billing: (process.env.BILLING_URL ?? API).replace(/\/+$/, ''),
  execucao: (process.env.EXECUCAO_URL ?? API).replace(/\/+$/, ''),
};
const SEM_BORDA = process.env.E2E_SEM_BORDA === 'true';
const EXIGIR_COBRANCA = process.env.E2E_EXIGIR_COBRANCA !== 'false';
const TIMEOUT_S = Number(process.env.E2E_TIMEOUT ?? 60);

if (!API && Object.values(URLS).some((u) => !u)) {
  console.error('API_BASE_URL e obrigatoria (ou as quatro *_URL da rodada local).');
  process.exit(2);
}

// Dados de teste ja versionados nos seeds do Identity, do OS e do Execucao.
const SENHA_SEED = 'Senha@123';
const EMAIL_ATENDENTE = 'atendente@mecanica.test';
const EMAIL_MECANICO = 'mecanico@mecanica.test';
const EMAIL_ALMOXARIFE = 'almoxarife@mecanica.test';
const SERVICO_ID_SEED = '77777777-7777-7777-7777-777777777777';
const PRECO_MAO_DE_OBRA = 80.0;
const VALOR_PECA = 25.0;
const ESTOQUE_PECA = 3;

const runId = String(Date.now());
const resultados = [];
const avisos = [];
const osIds = {};
const t0 = Date.now();

// ---------------------------------------------------------------- infraestrutura

function pass(id, nome, detalhe = '') {
  resultados.push({ id, ok: true });
  console.log(`PASS  ${id.padEnd(4)} ${nome}${detalhe ? ` (${detalhe})` : ''}`);
}

function fail(id, nome, detalhe) {
  resultados.push({ id, ok: false });
  console.log(`FAIL  ${id.padEnd(4)} ${nome}: ${detalhe}`);
}

function aviso(id, texto) {
  avisos.push(`${id}: ${texto}`);
  console.log(`WARN  ${id.padEnd(4)} ${texto}`);
}

function inicio(corpo) {
  const texto = typeof corpo === 'string' ? corpo : JSON.stringify(corpo);
  return (texto ?? '').slice(0, 300);
}

/** Uma falha de pre-condicao que impede os checks seguintes do cenario. */
class Abortar extends Error {}

/**
 * req(servico, metodo, caminho, { token, corpo }): { status, corpo, texto }.
 * O caminho e o da rota do servico; pela borda ganha o prefixo do servico onde o API Gateway o exige.
 */
async function req(servico, metodo, caminho, { token, corpo, cabecalhos } = {}) {
  const headers = { Accept: 'application/json', ...(cabecalhos ?? {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (corpo !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(corpo);
  }
  try {
    const r = await fetch(`${URLS[servico]}${caminho}`, {
      method: metodo,
      headers,
      body,
      signal: AbortSignal.timeout(20_000),
    });
    const texto = await r.text();
    let json;
    try {
      json = texto ? JSON.parse(texto) : undefined;
    } catch {
      json = undefined;
    }
    return { status: r.status, corpo: json, texto };
  } catch (e) {
    return { status: 0, corpo: undefined, texto: `erro de rede: ${e.message}` };
  }
}

const dormir = (ms) => new Promise((r) => setTimeout(r, ms));

/** Repete `consulta` (async, devolve valor truthy quando a condicao vale) de 2 em 2 s ate E2E_TIMEOUT. */
async function esperar(consulta) {
  const limite = Date.now() + TIMEOUT_S * 1000;
  for (;;) {
    const v = await consulta();
    if (v) return v;
    if (Date.now() >= limite) return null;
    await dormir(2000);
  }
}

/** Confere uma condicao e registra PASS/FAIL. Devolve true se passou. */
function conferir(id, nome, cond, detalheFalha, detalheOk = '') {
  if (cond) {
    pass(id, nome, detalheOk);
    return true;
  }
  fail(id, nome, detalheFalha());
  return false;
}

/** Exige que a precondicao valha; senao aborta o cenario (os checks seguintes dependem dela). */
function exigir(cond, motivo) {
  if (!cond) throw new Abortar(motivo);
}

// ---------------------------------------------------------------- dados da rodada (3.2)

function cpfValido(runIdStr) {
  let base = runIdStr.slice(-9).padStart(9, '1').split('').map(Number);
  if (base.every((d) => d === base[0])) base[8] = (base[8] + 1) % 10;
  const dv = (digitos) => {
    const peso = digitos.length + 1;
    const soma = digitos.reduce((acc, d, i) => acc + d * (peso - i), 0);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };
  const d1 = dv(base);
  const d2 = dv([...base, d1]);
  return [...base, d1, d2].join('');
}

function placaMercosul(runIdStr) {
  const n = Number(runIdStr.slice(-6));
  const d = n % 10;
  const letra = String.fromCharCode(65 + (Math.floor(n / 10) % 26));
  const dd = String(Math.floor(n / 260) % 100).padStart(2, '0');
  return `TST${d}${letra}${dd}`;
}

const CPF = cpfValido(runId);
const PLACA = placaMercosul(runId);
const EMAIL_CLIENTE = `e2e.${runId}@mecanica.test`;
const NOME_CLIENTE = `Cliente E2E ${runId}`;

// ---------------------------------------------------------------- estado compartilhado

const tk = {}; // tokens: atendente, mecanico, almoxarife, cliente
let veiculoId;
let clienteId;
let pecaId;

const situacaoOs = async (osId) => {
  const r = await req('os', 'GET', `/ordem-servicos/${osId}`, { token: tk.atendente });
  return r.status === 200 ? r.corpo : null;
};

/** Texto "situacao/sagaStatus" atual, para as mensagens de falha de polling. */
async function estadoOs(osId) {
  const o = await situacaoOs(osId);
  return o ? `situacao ${o.situacao}, sagaStatus ${o.sagaStatus ?? '-'}, passo ${o.passoAtual ?? '-'}` : 'OS ilegivel';
}

const esperarOs = (osId, situacao, sagaStatus) =>
  esperar(async () => {
    const o = await situacaoOs(osId);
    return o && o.situacao === situacao && (!sagaStatus || o.sagaStatus === sagaStatus) ? o : null;
  });

const pecaAtual = async () => {
  const r = await req('execucao', 'GET', `/pecas/${pecaId}`, { token: tk.almoxarife });
  return r.status === 200 ? r.corpo : null;
};

const esperarPeca = (campo, valor) =>
  esperar(async () => {
    const p = await pecaAtual();
    return p && p[campo] === valor ? p : null;
  });

const statusExecucao = async (osId) => {
  const r = await req('execucao', 'GET', `/execucoes/${osId}`, { token: tk.mecanico });
  return r.status === 200 ? r.corpo?.status : null;
};

const esperarExecucao = (osId, status) => esperar(async () => ((await statusExecucao(osId)) === status ? true : null));

/** Abre uma OS e a leva ate EM_DIAGNOSTICO. */
async function abrirOs(id, observacao) {
  const r = await req('os', 'POST', '/ordem-servicos', {
    token: tk.atendente,
    corpo: { veiculoId, observacao },
  });
  const ok = conferir(id, `POST /ordem-servicos -> 201 RECEBIDA (${observacao})`,
    r.status === 201 && r.corpo?.situacao === 'RECEBIDA', () => `HTTP ${r.status}: ${inicio(r.texto)}`);
  exigir(ok, 'nao abriu a OS');
  return r.corpo.id;
}

async function aguardarDiagnostico(id, osId) {
  const o = await esperarOs(osId, 'EM_DIAGNOSTICO');
  const execucao = await esperarExecucao(osId, 'EM_DIAGNOSTICO');
  return conferir(id, 'OS EM_DIAGNOSTICO e ordem criada no Execucao', !!o && !!execucao,
    () => `OS ${o ? 'ok' : 'nao chegou a EM_DIAGNOSTICO'}; ordem ${execucao ? 'ok' : 'ausente no Execucao'}`);
}

/** Prestacao do servico do seed + n pecas P; devolve o id da prestacao. */
async function montarDiagnostico(id, osId, qtdPecas) {
  const p = await req('execucao', 'POST', `/execucoes/${osId}/prestacoes`, {
    token: tk.mecanico,
    corpo: { servicoId: SERVICO_ID_SEED, precoMaoDeObra: PRECO_MAO_DE_OBRA },
  });
  const okP = conferir(`${id}a`, 'POST /prestacoes -> 201 com nomeServico do catalogo',
    p.status === 201 && !!p.corpo?.nomeServico, () => `HTTP ${p.status}: ${inicio(p.texto)}`,
    p.corpo?.nomeServico);
  exigir(okP, 'sem prestacao');
  const prestacaoId = p.corpo.id;
  const a = await req('execucao', 'POST', `/execucoes/${osId}/prestacoes/${prestacaoId}/pecas`, {
    token: tk.almoxarife,
    corpo: { pecaId, quantidade: qtdPecas },
  });
  const okA = conferir(`${id}b`, `POST /prestacoes/{id}/pecas -> 201 (${qtdPecas} x P)`, a.status === 201,
    () => `HTTP ${a.status}: ${inicio(a.texto)}`);
  exigir(okA, 'sem peca alocada');
  return prestacaoId;
}

async function concluirDiagnostico(id, osId) {
  const c = await req('execucao', 'PATCH', `/execucoes/${osId}/diagnostico/concluir`, { token: tk.mecanico });
  const ok = conferir(id, 'PATCH /diagnostico/concluir -> 200', c.status === 200,
    () => `HTTP ${c.status}: ${inicio(c.texto)}`);
  exigir(ok, 'diagnostico nao concluido');
}

async function orcamentoDaOs(osId, token = tk.cliente) {
  const r = await req('billing', 'GET', `/orcamentos/por-os/${osId}`, { token });
  return r;
}

const esperarOrcamento = (osId, status) =>
  esperar(async () => {
    const r = await orcamentoDaOs(osId);
    return r.status === 200 && r.corpo?.status === status ? r.corpo : null;
  });

const esperarPagamento = (osId, status) =>
  esperar(async () => {
    const r = await req('billing', 'GET', `/pagamentos/por-os/${osId}`, { token: tk.cliente });
    return r.status === 200 && r.corpo?.vigente?.status === status ? r.corpo : null;
  });

async function login(email) {
  const r = await req('identity', 'POST', '/auth/login', { corpo: { email, senha: SENHA_SEED } });
  return r.status === 200 ? r.corpo?.accessToken : null;
}

// ---------------------------------------------------------------- preparacao (E01-E07)

async function preparacao() {
  if (!SEM_BORDA) {
    const falhas = [];
    for (const svc of ['identity', 'os', 'execucao', 'billing']) {
      const r = await req(svc, 'GET', `/${svc}/actuator/health`);
      if (!(r.status === 200 && r.texto.includes('"UP"'))) falhas.push(`${svc}: HTTP ${r.status} ${inicio(r.texto)}`);
    }
    conferir('E01', 'GET /{svc}/actuator/health dos 4 servicos sem token -> UP', falhas.length === 0,
      () => falhas.join(' | '));

    const sem = await req('os', 'GET', '/ordem-servicos');
    const mal = await req('os', 'GET', '/ordem-servicos', { token: 'isto.nao.e-um-jwt' });
    conferir('E02', 'sem token -> 401 e token malformado -> 403 do API Gateway',
      sem.status === 401 && mal.status === 403, () => `sem token ${sem.status}, malformado ${mal.status}`);
  }

  tk.atendente = await login(EMAIL_ATENDENTE);
  tk.mecanico = await login(EMAIL_MECANICO);
  tk.almoxarife = await login(EMAIL_ALMOXARIFE);
  const ok3 = conferir('E03', 'POST /auth/login do atendente, do mecanico e do almoxarife -> accessToken',
    !!tk.atendente && !!tk.mecanico && !!tk.almoxarife,
    () => `atendente ${!!tk.atendente}, mecanico ${!!tk.mecanico}, almoxarife ${!!tk.almoxarife}`);
  exigir(ok3, 'sem logins do seed');

  const c = await req('identity', 'POST', '/clientes', {
    token: tk.atendente,
    corpo: { nome: NOME_CLIENTE, cpf: CPF, email: EMAIL_CLIENTE, telefone: '11999990000' },
  });
  const ok4 = conferir('E04', `POST /clientes -> 201 (cpf ${CPF})`, c.status === 201 && !!c.corpo?.id,
    () => `HTTP ${c.status}: ${inicio(c.texto)}`);
  exigir(ok4, 'sem cliente');
  clienteId = c.corpo.id;

  const v = await req('identity', 'POST', '/veiculos', {
    token: tk.atendente,
    corpo: { placa: PLACA, marca: 'E2E', modelo: 'Smoke', ano: '2020', quantidadeEixos: 2, clienteId },
  });
  const ok5 = conferir('E05', `POST /veiculos -> 201 (placa ${PLACA})`, v.status === 201 && !!v.corpo?.id,
    () => `HTTP ${v.status}: ${inicio(v.texto)}`);
  exigir(ok5, 'sem veiculo');
  veiculoId = v.corpo.id;

  const l = await req('identity', 'POST', '/auth/cpf', { corpo: { cpf: CPF } });
  tk.cliente = l.corpo?.accessToken;
  const me = tk.cliente ? await req('identity', 'GET', '/clientes/me', { token: tk.cliente }) : null;
  const ok6 = conferir('E06', 'POST /auth/cpf com o CPF novo e GET /clientes/me -> o cliente novo',
    l.status === 200 && !!tk.cliente && me?.status === 200 && me.corpo?.id === clienteId,
    () => `login HTTP ${l.status}; me HTTP ${me?.status}: ${inicio(me?.texto ?? l.texto)}`);
  exigir(ok6, 'sem token do cliente');

  const p = await req('execucao', 'POST', '/pecas', {
    token: tk.almoxarife,
    corpo: { nome: `Peça E2E ${runId}`, marca: 'E2E', valorUnitario: VALOR_PECA, qtdEstoque: ESTOQUE_PECA },
  });
  const ok7 = conferir('E07', 'POST /pecas -> 201 com qtdDisponivel 3',
    p.status === 201 && p.corpo?.qtdDisponivel === ESTOQUE_PECA, () => `HTTP ${p.status}: ${inicio(p.texto)}`);
  exigir(ok7, 'sem peca');
  pecaId = p.corpo.id;
}

// ---------------------------------------------------------------- cenario 1 (E08-E20)

const SEQUENCIA_FELIZ = ['EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'EM_EXECUCAO', 'FINALIZADA', 'AGUARDANDO_PAGAMENTO'];

async function cenario1() {
  const osId = await abrirOs('E08', 'E2E caminho feliz');
  osIds.feliz = osId;

  const okDiag = await aguardarDiagnostico('E09', osId);
  if (!okDiag) {
    console.log(`      ${await estadoOs(osId)}`);
    throw new Abortar('OS nao chegou a EM_DIAGNOSTICO');
  }

  // E10, E11 (a, b)
  const prestacaoId = await montarDiagnostico('E10', osId, 2);
  // E12
  await concluirDiagnostico('E12', osId);

  // E13
  const aprov = await esperarOs(osId, 'AGUARDANDO_APROVACAO');
  const peca = await esperarPeca('qtdDisponivel', 1);
  const orc = await esperarOrcamento(osId, 'GERADO');
  const ok13 = conferir('E13', 'OS AGUARDANDO_APROVACAO, qtdDisponivel 1 (reserva) e orcamento GERADO de 130.00',
    !!aprov && !!peca && !!orc && Number(orc.valorTotal) === 130.0,
    () => `OS ${aprov ? 'ok' : 'nao chegou'}; qtdDisponivel ${peca?.qtdDisponivel}; orcamento ${orc ? orc.valorTotal : 'ausente'}`);
  if (!ok13) console.log(`      ${await estadoOs(osId)}`);
  exigir(ok13, 'sem orcamento');

  // E14
  const ap = await req('billing', 'PATCH', `/orcamentos/${orc.id}/aprovar`, { token: tk.cliente });
  const ok14 = conferir('E14', 'PATCH /orcamentos/{id}/aprovar (cliente) -> 200 APROVADO',
    ap.status === 200 && ap.corpo?.status === 'APROVADO', () => `HTTP ${ap.status}: ${inicio(ap.texto)}`);
  exigir(ok14, 'orcamento nao aprovado');

  // E15
  const exec = await esperarOs(osId, 'EM_EXECUCAO');
  const baixa = await esperarPeca('qtdEstoque', 1);
  const ok15 = conferir('E15', 'OS EM_EXECUCAO e qtdEstoque 1 (baixa real)', !!exec && !!baixa,
    () => `OS ${exec ? 'ok' : 'nao chegou'}; qtdEstoque ${baixa?.qtdEstoque ?? '(nao chegou a 1)'}`);
  if (!ok15) console.log(`      ${await estadoOs(osId)}`);
  exigir(ok15, 'sem execucao');

  // E16
  const fin = await req('execucao', 'PATCH', `/execucoes/${osId}/prestacoes/${prestacaoId}/finalizar`, {
    token: tk.mecanico,
  });
  exigir(conferir('E16a', 'PATCH /prestacoes/{id}/finalizar -> 200', fin.status === 200,
    () => `HTTP ${fin.status}: ${inicio(fin.texto)}`), 'prestacao nao finalizada');
  const aguardando = await esperarOs(osId, 'AGUARDANDO_PAGAMENTO');
  const cobrancaOk = !!aguardando;
  if (aguardando) {
    pass('E16', 'OS AGUARDANDO_PAGAMENTO (cobranca criada no Mercado Pago)');
  } else if (!EXIGIR_COBRANCA && (await esperarOs(osId, 'FINALIZADA'))) {
    aviso('E16', 'OS parou em FINALIZADA: cobranca nao criada (E2E_EXIGIR_COBRANCA=false); E17 e E18 pulados');
    pass('E16', 'OS FINALIZADA (cobranca dispensada)');
  } else {
    fail('E16', 'OS AGUARDANDO_PAGAMENTO', await estadoOs(osId)
      + ' (confira mercadopago.erros e mercadopago_token_ausente no Billing)');
  }

  if (cobrancaOk) {
    // E17
    const pg = await esperarPagamento(osId, 'PENDENTE');
    conferir('E17', 'GET /pagamentos/por-os (cliente) -> PENDENTE com link https://',
      !!pg && String(pg.vigente.link ?? '').startsWith('https://'),
      () => `vigente: ${inicio(pg?.vigente)}`);

    // E18
    const h = await req('os', 'GET', `/ordem-servicos/${osId}/historico`, { token: tk.atendente });
    const fluxo = Array.isArray(h.corpo)
      ? h.corpo.map((t, i) => (i === 0 ? [t.de ?? t.para, t.para] : [t.para])).flat().filter((s, i, a) => i === 0 || s !== a[i - 1])
      : [];
    const esperada = ['RECEBIDA', ...SEQUENCIA_FELIZ];
    conferir('E18', 'historico da OS: RECEBIDA -> ... -> AGUARDANDO_PAGAMENTO, nessa ordem',
      h.status === 200 && JSON.stringify(fluxo) === JSON.stringify(esperada),
      () => `HTTP ${h.status}; obtido ${fluxo.join(' -> ') || inicio(h.texto)}`);
  }

  // E19
  const minhas = await req('os', 'GET', '/ordem-servicos/minhas', { token: tk.cliente });
  const publica = await req('os', 'GET', `/ordem-servicos/veiculo/placa/${PLACA}`);
  const vazou = [NOME_CLIENTE, CPF, EMAIL_CLIENTE].filter((s) => publica.texto.includes(s));
  conferir('E19', 'GET /ordem-servicos/minhas contem a OS; consulta publica por placa sem dados pessoais',
    minhas.status === 200 && Array.isArray(minhas.corpo) && minhas.corpo.some((o) => o.id === osId)
      && publica.status === 200 && Array.isArray(publica.corpo) && publica.corpo.length >= 1 && vazou.length === 0,
    () => `minhas HTTP ${minhas.status}; placa HTTP ${publica.status}, vazou: ${vazou.join(',') || 'nada'}`);

  // E20
  const tm = await req('execucao', 'GET', '/execucoes/tempo-medio', { token: tk.mecanico });
  const lista = Array.isArray(tm.corpo) ? tm.corpo : tm.corpo ? [tm.corpo] : [];
  const item = lista.find((t) => t.servicoId === SERVICO_ID_SEED);
  conferir('E20', 'GET /execucoes/tempo-medio -> o servico do seed com amostras >= 1',
    tm.status === 200 && !!item && item.amostras >= 1, () => `HTTP ${tm.status}: ${inicio(tm.texto)}`,
    item ? `${item.amostras} amostra(s)` : '');
}

// ---------------------------------------------------------------- cenario 2 (E21-E23)

async function cenario2() {
  const os2 = await abrirOs('E21', 'E2E peca sem estoque');
  osIds.semEstoque = os2;
  const okD = await aguardarDiagnostico('E21d', os2);
  exigir(okD, 'OS 2 nao chegou a EM_DIAGNOSTICO');
  await montarDiagnostico('E21p', os2, 2); // disponivel 1 < 2
  const c = await req('execucao', 'PATCH', `/execucoes/${os2}/diagnostico/concluir`, { token: tk.mecanico });
  conferir('E21', 'PATCH /diagnostico/concluir -> 200 (reserva de 2 com 1 disponivel)', c.status === 200,
    () => `HTTP ${c.status}: ${inicio(c.texto)}`);

  // O concluir leva a ordem a DIAGNOSTICO_CONCLUIDO; a ReservaPecasRecusada a devolve a EM_DIAGNOSTICO.
  // O 200 do concluir so volta com a ordem ja em DIAGNOSTICO_CONCLUIDO, entao esperar EM_DIAGNOSTICO e seguro.
  const voltou = await esperarExecucao(os2, 'EM_DIAGNOSTICO');
  await dormir(3000); // o OS processa a recusa junto com o Execucao; evita ler antes do rollback
  const o = await situacaoOs(os2);
  const orc = await orcamentoDaOs(os2);
  const peca = await pecaAtual();
  conferir('E22', 'ordem e OS de volta a EM_DIAGNOSTICO, sem orcamento (404) e qtdDisponivel 1',
    !!voltou && o?.situacao === 'EM_DIAGNOSTICO' && orc.status === 404 && peca?.qtdDisponivel === 1,
    () => `ordem ${voltou ? 'EM_DIAGNOSTICO' : 'nao voltou'}; OS ${o?.situacao}/${o?.sagaStatus}; orcamento HTTP ${orc.status}; qtdDisponivel ${peca?.qtdDisponivel}`);

  const cn = await req('os', 'PATCH', `/ordem-servicos/${os2}/cancelar`, {
    token: tk.atendente,
    corpo: { motivo: 'E2E: peca sem estoque' },
  });
  const osCan = await esperarOs(os2, 'CANCELADA');
  const exCan = await esperarExecucao(os2, 'CANCELADA');
  conferir('E23', 'PATCH /cancelar -> OS CANCELADA e ordem do Execucao CANCELADA',
    cn.status === 200 && !!osCan && !!exCan,
    () => `cancelar HTTP ${cn.status}; OS ${osCan ? 'CANCELADA' : 'nao cancelou'}; ordem ${exCan ? 'CANCELADA' : 'nao cancelou'}: ${inicio(cn.texto)}`);
}

// ---------------------------------------------------------------- cenario 3 (E24-E25)

async function cenario3() {
  const os3 = await abrirOs('E24', 'E2E orcamento recusado');
  osIds.recusada = os3;
  const okD = await aguardarDiagnostico('E24d', os3);
  exigir(okD, 'OS 3 nao chegou a EM_DIAGNOSTICO');
  await montarDiagnostico('E24p', os3, 1);
  await concluirDiagnostico('E24c', os3);
  const aprov = await esperarOs(os3, 'AGUARDANDO_APROVACAO');
  const peca = await esperarPeca('qtdDisponivel', 0);
  const orc = await esperarOrcamento(os3, 'GERADO');
  const ok24 = conferir('E24', 'OS 3 AGUARDANDO_APROVACAO, qtdDisponivel 0 e orcamento GERADO de 105.00',
    !!aprov && !!peca && !!orc && Number(orc.valorTotal) === 105.0,
    () => `OS ${aprov ? 'ok' : 'nao chegou'}; qtdDisponivel ${peca?.qtdDisponivel ?? '(nao chegou a 0)'}; orcamento ${orc ? orc.valorTotal : 'ausente'}`);
  exigir(ok24, 'sem orcamento da OS 3');

  const rc = await req('billing', 'PATCH', `/orcamentos/${orc.id}/recusar`, {
    token: tk.cliente,
    corpo: { motivo: 'E2E: orcamento recusado' },
  });
  const can = await esperarOs(os3, 'CANCELADA', 'COMPENSADA');
  const dev = await esperarPeca('qtdDisponivel', 1);
  conferir('E25', 'PATCH /orcamentos/{id}/recusar -> RECUSADO; OS CANCELADA, Saga COMPENSADA e estoque devolvido',
    rc.status === 200 && rc.corpo?.status === 'RECUSADO' && !!can && !!dev,
    () => `recusar HTTP ${rc.status}: ${inicio(rc.texto)}; OS ${can ? 'CANCELADA/COMPENSADA' : 'nao compensou'}; qtdDisponivel ${dev ? 1 : 'nao voltou a 1'}`);
  if (!can) console.log(`      ${await estadoOs(os3)}`);
}

// ---------------------------------------------------------------- perfis (E26)

async function perfis() {
  const osId = osIds.feliz;
  const mec = await req('billing', 'GET', `/orcamentos/por-os/${osId}`, { token: tk.mecanico });
  const cli = await req('billing', 'POST', `/pagamentos/${osId}/nova-cobranca`, { token: tk.cliente });
  const ate = await req('billing', 'POST', `/pagamentos/${osId}/nova-cobranca`, { token: tk.atendente });
  const esperado422 = EXIGIR_COBRANCA ? 422 : ate.status; // sem cobranca criada, nao ha vigente PENDENTE
  conferir('E26', 'mecanico em /orcamentos -> 403; cliente em nova-cobranca -> 403; atendente com vigente PENDENTE -> 422',
    mec.status === 403 && cli.status === 403 && ate.status === esperado422,
    () => `mecanico ${mec.status}, cliente ${cli.status}, atendente ${ate.status} (esperado 403, 403, 422): ${inicio(ate.texto)}`);
}

// ---------------------------------------------------------------- execucao

async function main() {
  console.log(`E2E da Saga | runId ${runId} | ${API || '(servicos diretos)'}${SEM_BORDA ? ' | sem borda' : ''}`);
  console.log(`cliente ${CPF} | placa ${PLACA} | timeout de polling ${TIMEOUT_S}s\n`);

  const etapas = [
    ['preparacao', preparacao, true],
    ['cenario 1', cenario1, false],
    ['cenario 2', cenario2, false],
    ['cenario 3', cenario3, false],
    ['perfis', perfis, false],
  ];
  for (const [nome, fn, essencial] of etapas) {
    try {
      await fn();
    } catch (e) {
      if (!(e instanceof Abortar)) {
        fail('ERRO', nome, e.stack ?? String(e));
      } else {
        console.log(`ABORT ${nome}: ${e.message}`);
        fail('ABRT', nome, e.message);
      }
      if (essencial) break;
    }
  }

  const falhas = resultados.filter((r) => !r.ok);
  const total = resultados.length;
  console.log(`\nrunId: ${runId}`);
  console.log(`OS feliz: ${osIds.feliz ?? '-'} | sem estoque: ${osIds.semEstoque ?? '-'} | recusada: ${osIds.recusada ?? '-'}`);
  for (const a of avisos) console.log(`aviso: ${a}`);
  console.log(`${total - falhas.length} de ${total} checks PASS, ${falhas.length} FAIL em ${Math.round((Date.now() - t0) / 1000)}s`);
  process.exit(falhas.length === 0 ? 0 : 1);
}

main();
