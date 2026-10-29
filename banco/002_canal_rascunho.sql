-- Executar no mesmo banco OEE da API, após 001_linha_rascunho.sql.
-- Configurações novas ficam isoladas da tabela configuracao.canal existente.
BEGIN;
CREATE TABLE IF NOT EXISTS configuracao.canal_rascunho (
    dispositivo_id VARCHAR(100) NOT NULL REFERENCES configuracao.linha_rascunho(dispositivo_id),
    codigo VARCHAR(20) NOT NULL CHECK (codigo IN (
        'DI1','DI2','DI3','DI4','contadorDI1','contadorDI2','contadorDI3','contadorDI4',
        'AI1','AI2','AI3','AI4')),
    configuracao TEXT NOT NULL,
    versao BIGINT NOT NULL CHECK (versao > 0),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dispositivo_id, codigo)
);
COMMIT;
