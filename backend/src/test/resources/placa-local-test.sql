-- Simulador local: não representa nem envia o protocolo MQTT da placa física.
BEGIN;
CREATE SCHEMA IF NOT EXISTS simulacao;
CREATE TABLE IF NOT EXISTS simulacao.placa_local (
    dispositivo_id VARCHAR(100) PRIMARY KEY,
    estado TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS simulacao.comando_local (
    comando_id UUID PRIMARY KEY,
    dispositivo_id VARCHAR(100) NOT NULL REFERENCES simulacao.placa_local(dispositivo_id),
    acao VARCHAR(40) NOT NULL,
    confirmacao TEXT NOT NULL,
    confirmado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMIT;
