# Simulador de quantidade e PPM

O simulador calcula diferenças de contadores e velocidade em uma sequência fornecida pelo usuário. É independente da coleta real e das configurações salvas. Não escreve no banco, não modifica as últimas leituras, não habilita contadores e não calcula OEE ou produção do turno. Não requer nova migração SQL.

## Usar pela tela

Atualize o projeto da máquina da API, recompile e reinicie TelemetriaApplication. Abra http://127.0.0.1:8080/simulador.html ou clique em Simulador de contadores no topo do configurador. Se a porta da API foi alterada, use essa porta.

O exemplo inicial já está preenchido. Clique em Executar simulação. Resultado esperado: quantidade 10, tempo 5 segundos e PPM médio 120. A primeira amostra aparece como Referência inicial; a segunda representa o intervalo calculado.

Escolha outro exemplo e clique em Carregar exemplo antes de executar. Cenários disponíveis:

- 10 peças em 5 segundos: 1000 para 1010, fator 1, resulta em 10 e 120 PPM.
- Intervalos com durações diferentes: 10 produtos em 5 segundos e 15 em 10 segundos; total 25, tempo 15 e PPM médio 100. Não é a média simples de 120 e 90.
- Contador -1 e nova sessão: interrupção da sessão A, retomada declarada na B; total dos intervalos válidos 15, tempo 10 e PPM médio 90, resultado parcial.
- Horário repetido e contador decrescente: identifica e descarta os intervalos afetados; nova sessão permite retomar. O resultado mantém a indicação de parcial.

Contador e fator são selecionados no próprio simulador; não são buscados do rascunho. Se escolher contadorDI2, as leituras da sequência também precisam incluir contadorDI2. A borda física não é aplicada outra vez: os valores de entrada já são contadores acumulados.

No campo Amostras em JSON, cole apenas a lista de amostras, não o objeto completo da requisição HTTP. Alterar o cenário marca o resultado como anterior até executar de novo. Ao carregar um exemplo sobre uma edição, a tela pede confirmação. As amostras e resultados não são persistidos; copie o texto se quiser guardar um cenário.

## Usar pelo Postman ou IntelliJ

POST http://127.0.0.1:8080/api/simulacoes/contadores, Body raw / JSON, Content-Type application/json. Copie o objeto completo de simulacao-exemplo.json ou execute simulacao-contadores.http. O retorno é 200 com o resumo e as linhas detalhadas. Erros no contrato, fator inválido ou mistura de dispositivos retornam 400.

O corpo contém exatamente canal, fator e amostras. canal aceita contadorDI1 a contadorDI4, fator exige número positivo finito e amostras exige de 2 a 500 itens. Cada item contém exatamente sessao e leitura. sessao é texto não vazio, de até 100 caracteres. leitura exige id não vazio de até 100 caracteres e usa timestamp e o contador selecionado. Datas ou contagens inválidas são apresentadas na tabela como amostras não calculáveis.

Você pode colocar o JSON completo da placa dentro de leitura sem modificar seu formato. Os outros canais não participam do cálculo nem são validados pelo simulador. O contrato de POST /api/leituras continua sendo o JSON MK2 completo. O campo sessao pertence ao cenário do simulador; não foi acrescentado ao contrato da placa.

## Regras exclusivas deste ambiente de simulação

1. A sequência não é ordenada automaticamente. Todos os itens pertencem ao mesmo dispositivo. Cada sessão tem uma referência de relógio simulada; os horários dd/MM/yyyy HH:mm:ss não são convertidos para UTC.
2. A primeira amostra válida é uma referência. Na mesma sessão, quantidade = diferença de contagem × fator; PPM = quantidade × 60 / segundos. Zero de produção em intervalo válido produz quantidade e PPM iguais a zero.
3. Não há cálculo entre sessões diferentes. Use identificadores diferentes (A, B etc.) para declarar sessões de teste independentes. O horário pode mudar entre sessões sem criar um intervalo entre elas.
4. Contador -1 bloqueia o restante daquela sessão. Contador abaixo de uma referência válida anterior também bloqueia a sessão, inclusive após amostra de horário inválido. Para retomar a simulação, declare um identificador de sessão novo. Não há produção negativa nem reconstrução de contagens perdidas.
5. Horário repetido ou anterior ao maior horário já visto na sessão é descartado. A próxima amostra cronológica válida apenas estabelece nova referência; a posterior poderá formar novo intervalo. Amostra com contador ou timestamp inválido também rompe a referência. Nenhum intervalo atravessa uma amostra inválida.
6. No resumo, somam-se somente quantidades e segundos de intervalos válidos. PPM médio é recalculado por essas bases. Intervalos excluídos geram resultado parcial. Sem nenhum intervalo válido, quantidade e PPM ficam null/indisponíveis; não são inventados como zero.

Essas regras conservadoras tornam os cenários verificáveis. Não são decisões de recuperação ou descarte para a coleta operacional do MK2. A política de atraso, o fuso, a sincronização do relógio, o contrato da placa e as regras operacionais de reinício continuam pendentes.

## Resposta e precisão

A resposta identifica modo SIMULACAO, dispositivo, contador e fator. resumo contém intervalosPossiveis, intervalosValidos, segundosCalculados, quantidadeCalculada, ppmMedio e suas representações textuais. situações possíveis do resumo são CALCULADO, PARCIAL e SEM_INTERVALOS_VALIDOS.

passos identifica cada amostra, sessão, horário e contador, além do intervalo, variação, quantidade, PPM, situação e explicação. Campos não calculáveis são null. As representações quantidadeTexto e ppmTexto preservam decimais; os contadores são retornados como texto. A tela envia as amostras pelo texto original, evitando truncar inteiros grandes ao convertê-los para números JavaScript.

## Validação

Testes unitários e HTTP cobrem as contas, média por tempo, fator fracionário, produção zero, ausência de intervalos, virada de dia, sessões, -1, redução, repetição e inversão de horário, limites numéricos, JSON inválido e ausência de efeitos na telemetria e nos cadastros. O navegador foi verificado com a API e banco H2 temporários, sem acessar PostgreSQL de produção.
