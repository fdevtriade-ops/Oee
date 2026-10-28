-- Executar conectado ao banco OEE. Não altera as tabelas existentes.
-- Rascunho por dispositivo; não define a associação operacional de várias placas por linha.
BEGIN;
CREATE TABLE IF NOT EXISTS configuracao.linha_rascunho (
    linha_id UUID NOT NULL UNIQUE,
    dispositivo_id VARCHAR(100) PRIMARY KEY CHECK (trim(dispositivo_id) <> ''),
    nome_linha VARCHAR(150) NOT NULL CHECK (trim(nome_linha) <> ''),
    produto VARCHAR(150) NOT NULL CHECK (trim(produto) <> ''),
    velocidade_ideal_ppm DOUBLE PRECISION NOT NULL
        CHECK (velocidade_ideal_ppm > 0 AND velocidade_ideal_ppm < 'Infinity'::DOUBLE PRECISION),
    versao BIGINT NOT NULL CHECK (versao > 0),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMIT;
