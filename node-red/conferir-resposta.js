const status = Number(msg.statusCode);
if (!Number.isInteger(status) || status < 200 || status >= 300) {
    node.error(`Falha no envio ao backend: ${msg.statusCode || "sem resposta HTTP"}`, msg);
    return null;
}

msg.payload = {
    recebido: true,
    id: msg.equipamentoId,
    statusCode: status
};
return msg;
