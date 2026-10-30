# OEE Tríade — pacote local completo

## Executar neste PC

1. Instale/use Java 17 ou superior e PostgreSQL. Para alterar/compilar os fontes, use também Maven.
2. No banco `OEE` usado pela API, execute os arquivos da pasta `banco` na ordem **000, 001, 002, 003, 004 e 005**. Em instalação já existente, aplique os que faltam. Não execute os arquivos de `banco/testes` em produção.
3. Configure `POSTGRES_URL`, `POSTGRES_USER` e `POSTGRES_PASSWORD` na execução Java do IntelliJ ou no ambiente local. A aplicação não lê `.env` automaticamente.
4. Execute `TelemetriaApplication` pelo IntelliJ, ou rode `iniciar.ps1` no PowerShell. O script pergunta a conexão localmente se as variáveis estiverem ausentes; não salva as credenciais em arquivo.
5. Abra **http://localhost:8080/**. O configurador mantém o padrão visual do projeto recebido.
6. Abra **http://localhost:8080/operacao.html** para operação e indicadores.

Este ZIP inclui `telemetria-oee.jar` recompilado com esta entrega. Para recompilar e atualizar esse arquivo da raiz, execute `compilar.ps1`. A saída Maven também fica em `backend/target/telemetria-api-1.1.0.jar`.

## Testar tudo sem placa física

1. Em `/operacao.html`, clique em **Preparar demonstração local**. Isso cria uma linha separada com id `LOCAL_DEMO_...`, produto de teste, velocidade ideal de 120 produtos/min, DI3 como estado, contadorDI1 como total e contadorDI4 como boas. Os demais cadastros não são alterados.
2. Confira as regras exibidas e clique em **Ativar processamento**. Os valores iniciais são exemplos para teste, não calendário aprovado da fábrica.
3. Clique em **Habilitar contadores da revisão**. A placa de software confirma as bordas RISING/FALLING e estabelece a referência.
4. Com 5 segundos, 10 pulsos totais, 10 boas e máquina rodando, clique em **Enviar próxima leitura**. Sem atravessar fronteira de minuto, o resultado será 10 produtos/5 s/120 PPM, disponibilidade/performance/qualidade/OEE de 100%.
5. Clique em **Simular queda de energia**, depois **Ligar placa**. A perda de configuração deixa os contadores em -1 e o motor exige recuperação.
6. Clique em **Reconfigurar e retomar** e envie outra leitura. Os contadores físicos simulados reiniciam; os produtos já confirmados permanecem no acumulado lógico.
7. Para testar paradas, use zero pulsos e desmarque máquina rodando. O estado da amostra anterior define o intervalo seguinte. Reenvie amostras paradas e depois retorne a rodando. Eventos curtos ficam pendentes até fechar; eventos maiores que o limiar afetam disponibilidade.

O relógio da placa de software avança a cada passo. Configure o limite de adiantamento e atraso compatível com o cenário simulado. A consulta indica amostras ignoradas e intervalos excluídos. Não são inventados deltas para intervalos que cruzam minuto/turno.

## O que funciona nesta entrega

- Conferência, conclusão e ativação local de revisão da linha.
- Recebimento contínuo MK2 pelo mesmo `POST /api/leituras`.
- Deltas, produção, PPM, boas/rejeitadas e indicadores calculados no backend.
- Referências, sessões, paradas pendentes e bases persistidas em PostgreSQL com transação e bloqueio por dispositivo.
- Turnos configuráveis, paradas programadas, microparadas e consolidação por minuto/turno.
- Placa local com configuração, confirmação de comando e recuperação simulada após energia.

**Placa física:** o usuário informou o tópico de telemetria `My_Home_1/BM/JSON`, mas não dispõe do payload/ack de comando. Por isso este pacote não publica comandos MQTT inventados. A coleta real pode alimentar o motor após configuração; a habilitação automática da placa física permanece dependente desse contrato. InfluxDB/Grafana não fazem parte desta entrega local.

Detalhes das contas e das políticas suportadas: `MOTOR-OPERACIONAL.md`. Instalação e atualização de fontes: `INSTALACAO-ATUALIZACAO.md`. Nenhum push ao GitHub ou atualização de outra máquina foi executado.
