-- SOMENTE banco descartável: este teste insere dados fictícios.
\set ON_ERROR_STOP on
\ir ../000_configuracao_inicial.sql
\ir ../001_linha_rascunho.sql
\ir ../002_canal_rascunho.sql
\ir ../003_configuracao_concluida.sql
\ir ../004_motor_operacional.sql
\ir ../005_placa_local.sql

INSERT INTO configuracao.maquina VALUES ('TESTE_INSTALACAO', 'Preservar máquina');
INSERT INTO configuracao.canal (maquina_id,tipo,numero,nome,funcao_di_codigo,nivel_ativo,habilitado)
VALUES ('TESTE_INSTALACAO','DI',1,'Preservar canal','FALHA',1,true);
UPDATE configuracao.funcao_di SET descricao='Descrição personalizada' WHERE codigo='FALHA';
INSERT INTO configuracao.linha_rascunho
(linha_id,dispositivo_id,nome_linha,produto,velocidade_ideal_ppm,versao)
VALUES ('12345678-1234-1234-1234-123456789abc','TESTE_INSTALACAO','Linha teste','Produto teste',120,1);
INSERT INTO configuracao.canal_rascunho (dispositivo_id,codigo,configuracao,versao)
VALUES ('TESTE_INSTALACAO','DI1','{}',1);
INSERT INTO configuracao.linha_configuracao (dispositivo_id,revisao,documento)
VALUES ('TESTE_INSTALACAO',1,'{"preservar":true}');
INSERT INTO configuracao.operacao_execucao (execucao_id,dispositivo_id,revisao,configuracao,politica)
VALUES ('aaaaaaaa-1234-1234-1234-123456789abc','TESTE_INSTALACAO',1,'{}','{}');
INSERT INTO configuracao.operacao_estado (dispositivo_id,execucao_id,ativo,estado)
VALUES ('TESTE_INSTALACAO','aaaaaaaa-1234-1234-1234-123456789abc',true,'{"referencia":"preservar"}');
INSERT INTO simulacao.placa_local VALUES ('LOCAL_SQL','{"preservar":true}');

-- Repetir instalação não pode sobrescrever nem apagar os registros.
\ir ../000_configuracao_inicial.sql
\ir ../001_linha_rascunho.sql
\ir ../002_canal_rascunho.sql
\ir ../003_configuracao_concluida.sql
\ir ../004_motor_operacional.sql
\ir ../005_placa_local.sql

DO $$
BEGIN
    IF (SELECT count(*) FROM configuracao.operacao_estado WHERE estado='{"referencia":"preservar"}') <> 1
       OR (SELECT count(*) FROM simulacao.placa_local WHERE dispositivo_id='LOCAL_SQL') <> 1 THEN
        RAISE EXCEPTION 'Estado operacional ou placa local não preservados';
    END IF;
    IF (SELECT count(*) FROM configuracao.linha_configuracao WHERE documento='{"preservar":true}') <> 1 THEN
        RAISE EXCEPTION 'Revisão concluída não preservada';
    END IF;
    IF (SELECT count(*) FROM configuracao.funcao_di) <> 8 THEN
        RAISE EXCEPTION 'Catálogo incorreto';
    END IF;
    IF (SELECT descricao FROM configuracao.funcao_di WHERE codigo='FALHA') <> 'Descrição personalizada' THEN
        RAISE EXCEPTION 'Catálogo existente sobrescrito';
    END IF;
    IF (SELECT nome FROM configuracao.maquina WHERE id='TESTE_INSTALACAO') <> 'Preservar máquina'
       OR (SELECT count(*) FROM configuracao.maquina) <> 1 THEN
        RAISE EXCEPTION 'Máquinas não preservadas ou exemplos criados';
    END IF;
    IF (SELECT count(*) FROM configuracao.canal WHERE nome='Preservar canal' AND habilitado) <> 1 THEN
        RAISE EXCEPTION 'Canal existente não preservado';
    END IF;
    IF (SELECT versao FROM configuracao.linha_rascunho WHERE dispositivo_id='TESTE_INSTALACAO') <> 1
       OR (SELECT count(*) FROM configuracao.canal_rascunho) <> 1 THEN
        RAISE EXCEPTION 'Rascunhos não preservados';
    END IF;
END $$;
SELECT 'Instalação e repetição validadas; cadastros preservados.' AS resultado;
