-- Após 000, 001 e 002. Histórico imutável de configurações concluídas.
-- Concluir configuração não inicia coleta operacional nem comanda a placa.
BEGIN;
CREATE TABLE IF NOT EXISTS configuracao.linha_configuracao (
    dispositivo_id VARCHAR(100) NOT NULL REFERENCES configuracao.linha_rascunho(dispositivo_id),
    revisao BIGINT NOT NULL CHECK (revisao > 0),
    documento TEXT NOT NULL,
    concluido_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dispositivo_id, revisao)
);
COMMIT;
