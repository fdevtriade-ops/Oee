-- Instalação inicial PostgreSQL. Execute no banco da API antes de 001 e 002.
-- Não cria máquinas/canais fictícios e não sobrescreve o catálogo existente.
-- IF NOT EXISTS não corrige tabelas preexistentes com estrutura incompatível.
BEGIN;

CREATE SCHEMA IF NOT EXISTS configuracao;

CREATE TABLE IF NOT EXISTS configuracao.maquina (
    id VARCHAR(100) PRIMARY KEY CHECK (trim(id) <> ''),
    nome VARCHAR(150) NOT NULL CHECK (trim(nome) <> '')
);

-- Catálogo de funções escolhidas pelo usuário para as entradas digitais.
CREATE TABLE IF NOT EXISTS configuracao.funcao_di (
    codigo VARCHAR(40) PRIMARY KEY,
    descricao VARCHAR(100) NOT NULL
);

INSERT INTO configuracao.funcao_di (codigo, descricao) VALUES
    ('MAQUINA_RODANDO', 'Máquina Rodando'),
    ('MAQUINA_PARADA', 'Máquina Parada'),
    ('MAQUINA_OCIOSA', 'Máquina Ociosa'),
    ('FALHA', 'Falha'),
    ('PRODUTOS_BONS', 'Produtos Bons'),
    ('PRODUTOS_REJEITADOS', 'Produtos Rejeitados'),
    ('PRODUTOS_RETRABALHADOS', 'Produtos Retrabalhados'),
    ('VELOCIDADE', 'Velocidade')
ON CONFLICT (codigo) DO NOTHING;

CREATE TABLE IF NOT EXISTS configuracao.canal (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    maquina_id VARCHAR(100) NOT NULL REFERENCES configuracao.maquina(id),
    tipo VARCHAR(2) NOT NULL CHECK (tipo IN ('DI', 'DO', 'AI')),
    numero INTEGER NOT NULL CHECK (numero > 0),

    -- Código no JSON = tipo + numero: DI1, DO4, AI2, DI16 etc.
    nome VARCHAR(150),
    funcao_di_codigo VARCHAR(40) REFERENCES configuracao.funcao_di(codigo),
    habilitado BOOLEAN NOT NULL DEFAULT FALSE,

    -- Para DI/DO, 0 ou 1 significa "ativo"; o outro valor significa "inativo".
    -- NULL representa canal ainda não configurado. AI nunca tem nível digital.
    nivel_ativo SMALLINT CHECK (nivel_ativo IN (0, 1)),

    -- Metadados da faixa elétrica. Não são a leitura medida.
    tensao_min REAL,
    tensao_max REAL,

    UNIQUE (maquina_id, tipo, numero),
    CHECK (nome IS NULL OR trim(nome) <> ''),
    CHECK (tipo = 'DI' OR funcao_di_codigo IS NULL),
    CHECK (tipo <> 'AI' OR nivel_ativo IS NULL),
    CHECK (NOT habilitado OR tipo = 'AI' OR nivel_ativo IS NOT NULL),
    CHECK (NOT habilitado OR tipo <> 'DI' OR funcao_di_codigo IS NOT NULL),
    CHECK (
        (tensao_min IS NULL AND tensao_max IS NULL)
        OR (
            tipo = 'AI'
            AND tensao_min IS NOT NULL AND tensao_max IS NOT NULL
            AND tensao_min > '-Infinity'::REAL
            AND tensao_max < 'Infinity'::REAL
            AND tensao_min < tensao_max
        )
    )
);

COMMIT;

