// Conecte em paralelo ao ramo existente de temperatura/umidade, sem removê-lo.
const BACKEND_URL = env.get("BACKEND_URL") || "http://127.0.0.1:8080/api/leituras";
const DISPOSITIVO = env.get("OEE_DEVICE_ID") || "TRD2001";
if (msg.topic !== "My_Home_1/BM/JSON") return null;

// Preserve o texto MQTT: JSON.parse + stringify pode arredondar contadores Long.
const original = Buffer.isBuffer(msg.payload) ? msg.payload.toString("utf8") : msg.payload;
let dados;
try {
    dados = typeof original === "string" ? JSON.parse(original) : original;
} catch (erro) {
    node.error("O payload não contém JSON válido", msg);
    return null;
}
if (!dados || typeof dados !== "object" || Array.isArray(dados)) {
    node.error("O payload deve ser um objeto JSON", msg);
    return null;
}
// O tópico é compartilhado. Outros equipamentos e mensagens ambientais não seguem à API.
if (dados.id !== DISPOSITIVO && dados.id !== "TRD_TESTE") return null;
const contadores = ["contadorDI1", "contadorDI2", "contadorDI3", "contadorDI4"];
if (!contadores.some(canal => Object.hasOwn(dados, canal))) return null;
if (!contadores.every(canal => Object.hasOwn(dados, canal)) || !Object.hasOwn(dados, "timestamp")) {
    node.error("O JSON MK2 deve conter os quatro contadores e timestamp", msg);
    return null;
}
// Objetos já convertidos podem ter perdido precisão; exija o texto original nesse caso.
if (typeof original !== "string" && contadores.some(canal =>
    typeof dados[canal] === "number" && !Number.isSafeInteger(dados[canal]))) {
    node.error("Contador sem precisão segura: configure o MQTT para retornar texto ou buffer", msg);
    return null;
}
// A API valida os valores e o horário. Não herde headers/opções HTTP do ramo Influx.
return {
    _msgid: msg._msgid,
    equipamentoId: dados.id,
    method: "POST",
    url: BACKEND_URL,
    headers: { "Content-Type": "application/json", "Accept": "application/json" },
    payload: typeof original === "string" ? original : JSON.stringify(dados),
    requestTimeout: 10000,
    followRedirects: false
};
