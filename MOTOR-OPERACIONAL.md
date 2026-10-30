# Motor operacional local

O backend mantém o padrão Java 17/Spring Boot/Spring JDBC/PostgreSQL e a interface HTML/CSS/JavaScript servida pelo próprio JAR. Não foram acrescentadas dependências de frontend. A base fornecida em `Oee-main (4).zip` foi preservada, com evolução aditiva.

## Instalação

Depois de 000–003, aplique `banco/004_motor_operacional.sql` e `banco/005_placa_local.sql` no mesmo banco da API. A primeira cria execuções, estado, intervalos e programação de paradas; a segunda cria o schema `simulacao` e as tabelas da placa de software. Não há execução automática dos SQLs.

O usuário da API precisa de USAGE nos schemas `configuracao` e `simulacao`; SELECT/INSERT/UPDATE nas novas tabelas, além das permissões anteriores. O motor usa bloqueios por linha e transações. As permissões reais precisam ser verificadas no banco do usuário; a validação desta entrega utilizou banco temporário.

Em `/conclusao.html`, selecione as fontes e conclua a configuração. Em `/operacao.html`, informe as regras, turnos e limites e ative a revisão. Se o rascunho mudou desde a conclusão, a ativação é recusada. Uma execução guarda uma cópia da configuração e da política; alterações posteriores não mudam suas contas. Encerre a execução para iniciar outra com regras diferentes.

## Processamento e continuidade

`POST /api/leituras` continua exigindo JSON MK2 completo e retornando 204 para recebimento válido. Primeiro valida o corpo; para uma linha ativa, processa o motor e confirma a transação; depois atualiza a consulta bruta em memória. Se a escrita falhar, não confirma o avanço da referência. Dispositivos não ativados continuam apenas na coleta/prévia.

As referências, os contadores exatos, a sessão lógica e os eventos pendentes ficam no PostgreSQL. Reiniciar a aplicação não zera as bases operacionais. A primeira leitura de uma execução ou sessão nova estabelece referência. Repetições e mensagens anteriores ao maior horário aceito são ignoradas sem duplicar intervalos.

Contador -1 ou redução em qualquer fonte produtiva bloqueia o cálculo até uma retomada explícita. `/nova-referencia` cria outra sessão lógica, preservando os intervalos já confirmados. O primeiro valor válido da nova sessão não é contado como produção. A API não acrescenta `sessao` ao contrato da placa.

Sem sequência/ID de inicialização no firmware, não é possível reconhecer todos os reinícios físicos ou distinguir todo reenvio entre sessões. Por exemplo, um contador reiniciado que ultrapassou a referência antiga antes da próxima mensagem pode não apresentar redução. A recuperação simulada é verificável; a recuperação automática física requer o contrato do firmware.

A última leitura bruta continua em memória, como no projeto original. O histórico novo é de intervalos/bases operacionais, não um arquivo completo de todas as mensagens brutas.

## Regras configuráveis e limites suportados

Os campos da política são todos obrigatórios. Não há fuso, turnos ou limiar de microparada de fábrica definidos automaticamente. A interface oferece um cenário inicial de teste, visível antes da ativação:

- `fuso`: zona do horário da placa. Datas inválidas ou ambíguas nesse fuso são ignoradas.
- `lacunaMaximaSegundos`: de 1 a 86400. Intervalo maior é excluído, e a amostra estabelece nova referência.
- `atrasoMaximoSegundos` e `adiantamentoMaximoSegundos`: tolerâncias entre horário da placa e recebimento do backend.
- `turnos`: nome, início e fim HH:mm; sem sobreposição. Início incluído/fim excluído. Um turno com horários iguais dura 24 horas. O turno noturno é identificado pela data do início.
- `microparadaLimiteSegundos`: de 0 a 3600, com escolha explícita de inclusão ou exclusão da igualdade ao limite.
- `estadoNoIntervalo: AMOSTRA_ANTERIOR`: o estado da amostra inicial define o intervalo até a seguinte. O instante exato de uma mudança entre amostras não é reconstruído.
- `foraDeOrdem: IGNORAR`: a amostra repetida/fora de ordem não muda a referência de produção.
- `lacunas: DESCARTAR_E_REINICIAR_REFERENCIA`.
- `fronteiras: DESCARTAR_INTERVALO`: deltas que atravessam minuto, turno ou planejamento não são rateados por uma taxa de produção presumida. A cobertura perdida é exibida.
- `programadas: EXCLUIR_DO_PLANEJADO`: períodos programados ficam fora do tempo e da produção usados nas contas. Programação retroativa sobre amostras já recebidas é recusada.
- `paradaInterrompida: DESCARTAR_PENDENTES`: perda de continuidade, contador inválido, interrupção ou encerramento descarta a parte de parada ainda aguardando classificação.

Estas são as políticas suportadas por esta implementação local. A ativação registra as escolhas; isso não representa validação física ou aprovação dos parâmetros da fábrica. Os intervalos fora do turno, com qualidade maior que produção ou produção enquanto o estado anterior é parado também são excluídos e identificados.

## Paradas e microparadas

Uma sequência parada sem produção é acumulada. Enquanto a duração não ultrapassa o limite escolhido, os intervalos permanecem pendentes. Ao fechar o evento, uma microparada inclui seu tempo em R: reduz performance, preservando disponibilidade. Se ultrapassar o limite, os intervalos são classificados como parada de disponibilidade: entram em T e não entram em R. Não há dupla penalização.

São registrados quantidade e duração observada de eventos nos intervalos válidos. Uma parada contínua pode atravessar minutos/turnos quando as amostras respeitam as fronteiras. O evento é contado uma vez no primeiro intervalo classificado; suas durações são atribuídas aos intervalos correspondentes. Não se infere tempo de silêncio após a última amostra. Eventos interrompidos não recebem uma duração física inventada.

## Indicadores

Para as bases válidas: T = segundos planejados válidos, R = segundos elegíveis para disponibilidade, N = total, G = boas e C = soma de velocidade ideal × R/60. A fonte de qualidade pode contar boas diretamente ou rejeitadas, calculando G=N−rejeitadas após aplicar os fatores das duas fontes.

- Quantidade = delta do contador total × fator.
- PPM = N × 60/T, incluindo os períodos válidos de parada no denominador. Não é uma média dos PPMs individuais.
- Disponibilidade = R/T.
- Performance bruta = N/C; performance exibida = mínimo entre 1 e a bruta.
- Qualidade = G/N.
- OEE = disponibilidade × performance exibida × qualidade.

As razões retornam entre 0 e 1. A tela apresenta percentuais. Divisor sem base resulta em null/“—”; intervalo válido de zero produção mantém quantidade/PPM/performance zero quando seus divisores existem. Com N=0, qualidade e OEE permanecem sem base. Quantidades e PPM têm representações textuais exatas para evitar perda de inteiros grandes no navegador.

Por minuto e por turno, somam-se as bases e recalculam-se as razões. Os dados são consultados diretamente dos intervalos confirmados, sem esperar um processo separado de consolidação. A consulta de grupos retorna até 500 períodos mais recentes. O resumo é da execução selecionada; execuções anteriores permanecem consultáveis pelo seu UUID. Não existe reprocessamento retroativo ou correção de mensagem já contabilizada nesta versão.

## Rotas novas

Prefixo `/api/dispositivos/{id}/operacao`:

| Método/rota | Uso |
| --- | --- |
| POST `/ativar` | `{revisao, politica}` |
| GET | Estado, referências, execução, regras, diagnóstico e pendências |
| POST `/parar` | `{execucao}`; confirma o encerramento e preserva resultados |
| POST `/nova-referencia` | `{execucao,motivo}`; retoma sem somar o valor inicial |
| POST `/programadas` | `{execucao,inicio,fim,motivo}`; horários ISO 8601 com Z |
| GET `/indicadores` | Resumo; `agrupamento=MINUTO` ou `TURNO` |

A consulta de indicadores aceita `execucao` UUID e filtros opcionais `inicio`/`fim` ISO. Inclui apenas intervalos inteiramente dentro do filtro, sem rateio das bordas. São expostos intervalos excluídos e segundos excluídos. Pendências e amostras ignoradas aparecem no estado da operação e na tela.

## Placa simulada e comandos

Prefixo `/api/simulacoes/placas/{id}`; aceita somente `TRD_TESTE` ou `LOCAL_...`:

- POST na raiz: `{fuso,rodando}` cria o dispositivo de software e publica a primeira leitura no coletor local.
- GET: estado da placa, contadores e última confirmação.
- POST `/comandos`: `{execucao,acao}` com `CONFIGURAR_PELA_REVISAO`, `DESLIGAR`, `LIGAR` ou `RECUPERAR`.
- POST `/passo`: `{execucao,segundos,rodando,pulsosTotal,pulsosQualidade}`.

Configurar copia habilitação e borda da revisão. Pulsos completos contêm uma borda RISING e uma FALLING; cada contador conta uma vez por pulso de acordo com sua configuração. Desligar perde a configuração; ligar não a restaura. Recuperar confirma a configuração simulada, zera os contadores habilitados e inicia uma nova referência, preservando acumulados. Comandos e confirmações são persistidos no schema `simulacao` e identificados como `SIMULADA_LOCAL`.

O simulador antigo `/simulador.html` permanece isolado e inalterado. A placa local nova, por outro lado, foi criada para exercitar o motor operacional e seus cadastros num dispositivo de teste separado. Nenhum comando real é publicado em MQTT: o payload/ack da placa física não foi disponibilizado. O tópico `My_Home_1/BM/JSON` informado é o de telemetria existente; o ramo ambiental deve continuar preservado.
