-- Após 003. Estado e intervalos transacionais do motor local.
BEGIN;
CREATE TABLE IF NOT EXISTS configuracao.operacao_execucao (
    execucao_id UUID PRIMARY KEY,
    dispositivo_id VARCHAR(100) NOT NULL REFERENCES configuracao.linha_rascunho(dispositivo_id),
    revisao BIGINT NOT NULL,
    configuracao TEXT NOT NULL,
    politica TEXT NOT NULL,
    iniciado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    encerrado_em TIMESTAMP WITH TIME ZONE,
    FOREIGN KEY (dispositivo_id,revisao) REFERENCES configuracao.linha_configuracao(dispositivo_id,revisao)
);
CREATE TABLE IF NOT EXISTS configuracao.operacao_estado (
    dispositivo_id VARCHAR(100) PRIMARY KEY REFERENCES configuracao.linha_rascunho(dispositivo_id),
    execucao_id UUID NOT NULL REFERENCES configuracao.operacao_execucao(execucao_id),
    ativo BOOLEAN NOT NULL,
    estado TEXT NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS configuracao.operacao_intervalo (
    execucao_id UUID NOT NULL REFERENCES configuracao.operacao_execucao(execucao_id),
    sessao_id UUID NOT NULL,
    inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    fim TIMESTAMP WITH TIME ZONE NOT NULL CHECK (fim > inicio),
    minuto TIMESTAMP WITH TIME ZONE NOT NULL,
    turno VARCHAR(100) NOT NULL,
    turno_inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    situacao VARCHAR(60) NOT NULL,
    observado_segundos NUMERIC NOT NULL,
    planejado_segundos NUMERIC NOT NULL,
    rodando_segundos NUMERIC NOT NULL,
    quantidade NUMERIC NOT NULL,
    boas NUMERIC NOT NULL,
    capacidade_ideal NUMERIC NOT NULL,
    microparada_segundos NUMERIC NOT NULL,
    parada_segundos NUMERIC NOT NULL,
    microparadas INTEGER NOT NULL,
    paradas INTEGER NOT NULL,
    PRIMARY KEY (execucao_id,sessao_id,inicio),
    CHECK (planejado_segundos >= 0 AND rodando_segundos >= 0 AND rodando_segundos <= planejado_segundos),
    CHECK (quantidade >= 0 AND boas >= 0 AND boas <= quantidade)
);
CREATE INDEX IF NOT EXISTS operacao_intervalo_periodo ON configuracao.operacao_intervalo(execucao_id,minuto);
CREATE TABLE IF NOT EXISTS configuracao.operacao_programada (
    id UUID PRIMARY KEY,
    execucao_id UUID NOT NULL REFERENCES configuracao.operacao_execucao(execucao_id),
    inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    fim TIMESTAMP WITH TIME ZONE NOT NULL CHECK (fim > inicio),
    motivo VARCHAR(200) NOT NULL
);
COMMIT;
