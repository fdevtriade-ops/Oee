INSERT INTO configuracao.maquina (id,nome) VALUES ('TESTE_A','Máquina A'), ('TESTE_B','Máquina B');
INSERT INTO configuracao.canal (maquina_id,tipo,numero,tensao_min,tensao_max)
SELECT maquinas.id, tipos.tipo, numeros.numero,
       CASE WHEN tipos.tipo='AI' THEN -0.5 END,
       CASE WHEN tipos.tipo='AI' THEN 10.0 END
FROM configuracao.maquina maquinas
CROSS JOIN (VALUES ('DI'),('DO'),('AI')) AS tipos(tipo)
CROSS JOIN (VALUES (1),(2),(3),(4)) AS numeros(numero);
