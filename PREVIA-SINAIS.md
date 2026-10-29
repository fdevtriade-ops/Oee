# Prévia de interpretação dos sinais

A prévia interpreta a última amostra com as configurações de canal salvas no PostgreSQL. Continua em RASCUNHO e não acumula tempo nem produção. Não precisa de nova migração SQL. Atualize o código na máquina da API, recompile, reinicie e recarregue a página com Ctrl+F5.

## Consulta e tela

GET `/api/dispositivos/{id}/previa` retorna o dispositivo, situação RASCUNHO, timestamp e recebidoEm da amostra utilizada e 16 canais. Requer rascunho de linha existente; sem ele retorna 404. Se faltarem as tabelas de rascunho, retorna 503 com orientação sobre os scripts 001/002. Sem nova amostra após reiniciar a API, a rota continua respondendo com os cadastros persistidos e valores interpretados indisponíveis.

A tela apresenta o bloco Prévia da configuração salva para o canal selecionado. Ele é atualizado junto das consultas periódicas e após salvar a configuração. Alterações apenas preenchidas no formulário não mudam a interpretação; há aviso explícito quando existem campos não salvos. A prévia identifica a versão aplicada de cada canal. Se outro cliente mudar o cadastro, a tela informa a diferença para a versão do editor e orienta recarregar.

## Interpretações

- DI: compara o nível recebido com nivelAtivo. MAQUINA_RODANDO produz Rodando/Parada; MAQUINA_PARADA produz Parada/Sem sinal de parada; MAQUINA_OCIOSA produz Ociosa/Sem sinal de ociosidade; FALHA produz Falha ativa/Sem falha. A ausência de falha, parada ou ociosidade não é convertida em uma afirmação de máquina rodando.
- AI: conversão linear `valorMin + (leitura - tensaoMin) × (valorMax - valorMin) / (tensaoMax - tensaoMin)`. Faixa inclusiva nos extremos; suporta saída crescente ou decrescente. Leitura fora da faixa configurada fica indisponível; não extrapola nem força o valor para o limite.
- Contador: exibe apenas a leitura acumulada da amostra multiplicada pelo fator. Não é produção do turno, delta, PPM nem acumulado lógico entre reinícios. -1 fica indisponível e não entra em uma multiplicação de produção. A borda RISING/FALLING não é reaplicada ao contador, que já vem acumulado da placa.
- DO: somente leitura bruta, sem função operacional atribuída.

## Contrato de cada canal

codigo, tipo, nome, funcao e versaoConfiguracao identificam a configuração usada. valorBruto preserva a leitura. valorBrutoTexto e valorFormatado oferecem representações textuais sem perda de inteiros grandes no navegador. valorInterpretado contém booleano para DI ou número decimal para AI/contador; permanece null quando indisponível. ativo só se aplica à DI. situacao e detalhe explicam o resultado.

Situações: VALIDO, SEM_CONFIGURACAO, DESABILITADO, SEM_LEITURA, CONTADOR_DESCONFIGURADO, FORA_DA_FAIXA, LEITURA_INVALIDA, CONFIGURACAO_INVALIDA ou SOMENTE_LEITURA. Campos não calculáveis não viram zero. Um cadastro inválido de um canal não impede a prévia dos demais.

VALIDO indica compatibilidade da amostra com o mapeamento, não garantia de que ela é recente. A cadência, a tolerância de comunicação e a política de atraso continuam pendentes. O timestamp da placa permanece como texto sem atribuição de fuso. A consulta usa o cadastro atual em rascunho e não representa reprocessamento histórico da leitura.

## Teste com TRD_TESTE

1. Envie o JSON de teste completo para POST /api/leituras com DI3 1, AI1 3.5 e contadorDI1 100.
2. Mantenha DI3 como MAQUINA_RODANDO, nivelAtivo 1; AI1 como MEDIDA_ANALOGICA, 0–10 V correspondendo a 0–100 bar; contadorDI1 como TOTAL_PRODUZIDO, fator 1, unidade pecas.
3. Consulte a rota com `previa-sinais.http` ou abra a tela. Esperado: DI3 Rodando; AI1 35 bar; contadorDI1 100 pecas, identificado como valor da amostra.
4. Edite a escala na tela sem salvar: a prévia deve manter o valor da configuração salva e mostrar aviso. Ao salvar, deve recalcular com a nova escala e versão.
5. Envie contador -1: se o canal está habilitado/configurado, a situação será CONTADOR_DESCONFIGURADO e valorInterpretado null.
6. Após reiniciar sem reenviar telemetria, os canais habilitados/configurados devem ficar SEM_LEITURA, com valorInterpretado null. Nova leitura retoma a prévia.

Os testes automatizados usam H2 isolado e cobrem polaridade, funções digitais, conversão, limites, escala decrescente, precisão dos contadores, ausência de amostra, configuração inválida e consulta HTTP sem alterar leituras nem cadastros. O navegador foi verificado com dados simulados em uma API temporária.

OEE, PPM, sessões de contadores, recuperação de acumulados, coerência temporal dos sinais, classificação de paradas e microparadas, ativação e InfluxDB seguem para etapas posteriores. Nenhum comando é enviado à placa por esta consulta.
