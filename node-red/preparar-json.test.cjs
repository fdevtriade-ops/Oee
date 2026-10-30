const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const codigo = fs.readFileSync(path.join(__dirname, 'preparar-json.js'), 'utf8');
const executar = new Function('msg', 'env', 'node', 'Buffer', codigo);
const exemplo = JSON.parse(fs.readFileSync(path.join(__dirname, '../exemplo.json'), 'utf8'));
function enviar(payload, extras = {}, variaveis = {}) {
    const erros = [];
    const msg = { topic: 'My_Home_1/BM/JSON', payload, ...extras };
    const resultado = executar(msg, { get: chave => variaveis[chave] },
        { error: texto => erros.push(texto) }, Buffer);
    return { resultado, erros, msg };
}

test('preserva texto completo e contadores até Long.MAX_VALUE sem arredondamento', () => {
    const texto = JSON.stringify({ ...exemplo, extra: 'preservado' })
        .replace('"contadorDI1":-1', '"contadorDI1":9223372036854775807');
    for (const payload of [texto, Buffer.from(texto)]) {
        const { resultado, erros } = enviar(payload);
        assert.deepEqual(erros, []);
        assert.equal(resultado.payload, texto);
        assert.match(resultado.payload, /9223372036854775807/);
        assert.match(resultado.payload, /timestamp/);
    }
});

test('não herda autorização, cookies ou opções HTTP nem modifica a mensagem original', () => {
    const payload = { ...exemplo };
    const extras = { headers: { Authorization: 'segredo-de-teste', Cookie: 'teste' },
        url: 'http://outro', method: 'DELETE', auth: 'teste', _msgid: '123' };
    const { resultado, msg } = enviar(payload, extras,
        { BACKEND_URL: 'http://servidor:8080/api/leituras' });
    assert.equal(resultado.url, 'http://servidor:8080/api/leituras');
    assert.equal(resultado.method, 'POST');
    assert.equal(resultado._msgid, '123');
    assert.deepEqual(resultado.headers, { 'Content-Type': 'application/json', Accept: 'application/json' });
    assert.equal(resultado.auth, undefined);
    assert.deepEqual(msg.headers, extras.headers);
    assert.deepEqual(JSON.parse(resultado.payload), exemplo);
    assert.equal(msg.payload, payload);
});

test('ignora mensagens ambientais, outro tópico e outros equipamentos', () => {
    for (const [payload, extras] of [
        [{ id: 'TRD2001', temperatura: 25 }, {}],
        [exemplo, { topic: 'My_Home_1/BM/Porta' }],
        [{ ...exemplo, id: 'OUTRO' }, {}]
    ]) {
        const { resultado, erros } = enviar(payload, extras);
        assert.equal(resultado, null);
        assert.deepEqual(erros, []);
    }
});

test('aceita piloto configurado e TRD_TESTE para teste separado da placa', () => {
    for (const id of ['PILOTO_2', 'TRD_TESTE']) {
        assert.equal(enviar({ ...exemplo, id }, {}, { OEE_DEVICE_ID: 'PILOTO_2' })
            .resultado.equipamentoId, id);
    }
    assert.equal(enviar(exemplo, {}, { OEE_DEVICE_ID: 'PILOTO_2' }).resultado, null);
});

test('rejeita JSON quebrado ou incompleto com diagnóstico', () => {
    const semTimestamp = { ...exemplo };
    delete semTimestamp.timestamp;
    const semContador = { ...exemplo };
    delete semContador.contadorDI4;
    for (const payload of ['{quebrado', 'null', '[]', semTimestamp, semContador]) {
        const { resultado, erros } = enviar(payload);
        assert.equal(resultado, null);
        assert.equal(erros.length, 1);
    }
});

test('bloqueia contadores grandes já convertidos em objeto', () => {
    const { resultado, erros } = enviar({ ...exemplo, contadorDI1: 9007199254740992 });
    assert.equal(resultado, null);
    assert.match(erros[0], /precisão/);
});

test('fluxo importável contém a mesma função e amostra MK2 exclusiva de teste', () => {
    const fluxo = JSON.parse(fs.readFileSync(path.join(__dirname, 'fluxo-node-red.json'), 'utf8'));
    assert.equal(fluxo.find(n => n.name === 'Preparar JSON para API').func, codigo);
    const inject = fluxo.find(n => n.type === 'inject');
    assert.equal(inject.once, false);
    assert.equal(inject.repeat, '');
    assert.equal(inject.payloadType, 'str');
    const { resultado } = enviar(inject.payload);
    assert.equal(resultado.equipamentoId, 'TRD_TESTE');
    assert.equal(JSON.parse(resultado.payload).contadorDI4, -1);
    const ids = new Set(fluxo.map(n => n.id));
    for (const no of fluxo) for (const saida of no.wires || []) {
        for (const destino of saida) assert.ok(ids.has(destino));
    }
});
