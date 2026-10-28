// Cole no nó Function "Preparar JSON para API".
// Conecte diretamente ao MQTT in do tópico My_Home_1/BM/JSON.
const BACKEND_URL = env.get("BACKEND_URL") || "http://127.0.0.1:8080/api/leituras";

if (msg.topic !== "My_Home_1/BM/JSON") {
    return null;
}

let dados = msg.payload;
if (Buffer.isBuffer(dados)) {
    dados = dados.toString("utf8");
}

if (typeof dados === "string") {
    try {
        dados = JSON.parse(dados);
    } catch (erro) {
        node.error("O payload não contém JSON válido", msg);
        return null;
    }
}

if (!dados || typeof dados !== "object" || Array.isArray(dados)) {
    node.error("O payload deve ser um objeto JSON", msg);
    return null;
}

if (typeof dados.id !== "string" || dados.id.trim() === "") {
    node.error("O JSON deve conter o id do equipamento", msg);
    return null;
}

const digitais = ["DI1", "DI2", "DI3", "DI4", "DO1", "DO2", "DO3", "DO4"];
const analogicos = ["AI1", "AI2", "AI3", "AI4"];
const payload = { id: dados.id };

for (const canal of digitais) {
    if (dados[canal] !== 0 && dados[canal] !== 1) {
        node.error(`${canal} deve ser um número 0 ou 1`, msg);
        return null;
    }
    payload[canal] = dados[canal];
}

for (const canal of analogicos) {
    if (typeof dados[canal] !== "number" || !Number.isFinite(dados[canal])) {
        node.error(`${canal} deve ser um número finito`, msg);
        return null;
    }
    payload[canal] = dados[canal];
}

// Somente payload será o corpo HTTP. Os demais campos controlam o nó HTTP Request.
// Esta rota MQTT cria headers próprios e não herda configurações da rota Influx.
return {
    _msgid: msg._msgid,
    equipamentoId: payload.id,
    method: "POST",
    url: BACKEND_URL,
    headers: { "Content-Type": "application/json", "Accept": "application/json" },
    payload: JSON.stringify(payload),
    requestTimeout: 10000,
    followRedirects: false
};
