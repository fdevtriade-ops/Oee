# Conectar a API ao banco OEE

Esta é a versão 1.1.0 da API, preparada para o PostgreSQL que você já criou:

- Banco padrão: `OEE`.
- Schema: `configuracao`.
- Tabelas: `maquina`, `funcao_di` e `canal`.
- API: porta 8080.

A API usa Spring Boot 4.1.1 com Spring JDBC e o driver PostgreSQL. As consultas usam parâmetros. Não executa scripts de criação, exclusão ou alteração de tabelas ao iniciar. A inicialização verifica a conexão e a existência das colunas usadas pela aplicação. As configurações só são modificadas ao chamar o endpoint PUT.

## 1. Informar a conexão

Configure estas variáveis de ambiente na execução da aplicação (por exemplo, na configuração Run/Debug da sua IDE):

```text
POSTGRES_URL=jdbc:postgresql://localhost:5432/OEE
POSTGRES_USER=seu_usuario_do_postgres
POSTGRES_PASSWORD=sua_senha_do_postgres
```

Substitua os valores de exemplo localmente. Se o PostgreSQL estiver em outra máquina ou container, ajuste o host e a porta de `POSTGRES_URL`. O nome do banco na URL deve corresponder exatamente ao nome exibido no PostgreSQL. O usuário precisa de acesso ao banco e ao schema, leitura nas três tabelas e atualização na tabela `configuracao.canal`.

Não é necessário criar o banco novamente ou executar `schema.sql`/`exemplo.sql` outra vez. A opção `spring.sql.init.mode=never` está configurada.

## 2. Iniciar

Com Java 17 ou superior e as variáveis configuradas, execute na pasta deste pacote:

```sh
java -jar telemetria-oee.jar
```

Ou abra a pasta `backend` na IDE e execute `TelemetriaApplication`. Com Maven 3.6.3 ou superior, também pode executar dentro de `backend`:

```sh
mvn spring-boot:run
```

Não inicie simultaneamente a versão anterior da API na mesma porta. Para usar outra porta, configure `SERVER_PORT` e ajuste a URL do Node-RED.

Quando a conexão e as tabelas forem verificadas, o log mostrará:

```text
Conexão e tabelas do schema configuracao verificadas.
```

Uma falha de conexão ou schema impede a inicialização completa. Confira endereço, usuário, senha e permissões com base na mensagem de erro. O pacote não contém suas credenciais.

## 3. Consultar o banco pela API

Abra no navegador ou use um cliente HTTP:

```text
GET http://localhost:8080/api/equipamentos
GET http://localhost:8080/api/funcoes-di
GET http://localhost:8080/api/equipamentos/TRD2001/configuracoes
```

O primeiro endpoint lista as máquinas, o segundo lista as oito funções disponíveis e o terceiro retorna o nome da máquina e todas as configurações de seus canais. Os valores são consultados no PostgreSQL a cada requisição.

Os canais são apresentados em DI, DO, AI e em ordem numérica. Os campos sem configuração aparecem como `null`. Os limites das AIs continuam sendo metadados; não são leituras de tensão.

## 4. Salvar a configuração de uma DI

```http
PUT http://localhost:8080/api/equipamentos/TRD2001/canais/DI1
Content-Type: application/json

{
  "nome": "Máquina Parada",
  "funcao": "MAQUINA_PARADA",
  "nivelAtivo": 1,
  "habilitado": true
}
```

Para inverter o sinal, envie a mesma requisição com `"nivelAtivo": 0`. O endpoint retorna HTTP 200 e o canal salvo, após a transação no banco. Consultar novamente o endpoint de configurações mostrará a alteração.

O PUT substitui os quatro campos editáveis: `nome`, `funcao`, `nivelAtivo` e `habilitado`. Envie todos eles. O ID, o tipo, o número do canal e os limites de tensão não são alterados por esse endpoint.

Para retirar a configuração e desabilitar um canal:

```json
{
  "nome": null,
  "funcao": null,
  "nivelAtivo": null,
  "habilitado": false
}
```

Regras:

- DI habilitada exige função cadastrada e nível ativo 0 ou 1.
- DO habilitada exige nível ativo; a função do catálogo de DIs deve ser `null`.
- AI deve ter `funcao` e `nivelAtivo` nulos. Pode permanecer desabilitada e sem nome.
- Máquina/canal inexistente retorna 404. Configuração inválida retorna 400. A configuração anterior é preservada quando a validação falha.
- Novos canais, como DI5, podem ser configurados por este endpoint depois de cadastrados na tabela `canal`. Esta versão não adiciona endpoints para criar máquinas ou canais.

## Recebimento de leituras MK2

`POST /api/leituras` exige o objeto completo mostrado em `exemplo.json`:

- `id`: texto não vazio.
- `DI1` a `DI4` e `DO1` a `DO4`: números 0 ou 1.
- `contadorDI1` a `contadorDI4`: números inteiros de 0 a 9223372036854775807, ou -1 para contador desligado/desconfigurado. O valor -1 é preservado e não é produção negativa. Contadores fracionários ou fora desse limite são rejeitados, sem truncamento.
- `AI1` a `AI4`: números finitos entre 0 e 10 V, inclusive; -0,0 equivale a zero.
- `timestamp`: texto obrigatório no formato `dd/MM/yyyy HH:mm:ss`, com data e hora válidas. É o horário de envio da placa, preservado sem presumir fuso.

A API responde HTTP 204 sem corpo quando aceita a leitura. Campos ausentes ou inválidos retornam HTTP 400 e não substituem a última leitura válida. Campos extras são ignorados.

`GET /api/leituras/{id}` devolve os 18 campos do contrato MK2 mais `recebidoEm`, gerado pelo servidor no início do processamento, como instante UTC em ISO 8601 (por exemplo, `2026-10-28T13:21:13Z`). Um `recebidoEm` enviado pelo cliente não é utilizado. Equipamento sem leitura retorna HTTP 404.

O fuso da placa, a sincronização dos relógios e a política para atraso ainda precisam ser definidos. Não há cálculo de atraso nem descarte por antiguidade nesta etapa; a consulta continua representando a última leitura aceita por ordem de processamento.

### Ajuste manual no Node-RED

Os arquivos da pasta `node-red` não foram alterados. O preparador antigo remove contadores e timestamp; após esta atualização da API, isso causará HTTP 400. Ajuste manualmente o nó para encaminhar os 18 campos do exemplo, preservando seus tipos numéricos e o texto de timestamp. O endpoint e a resposta de sucesso 204 permanecem iguais. O arquivo `requisicoes.http` permite testar diretamente pelo IntelliJ antes de ajustar o fluxo.

### Executar no IntelliJ

Configure `POSTGRES_URL`, `POSTGRES_USER` e `POSTGRES_PASSWORD` nas variáveis de ambiente da configuração Run/Debug. A URL possui o padrão local documentado; usuário e senha não possuem valor padrão. Reinicie `TelemetriaApplication` após recompilar. A inicialização verifica o schema existente e não cria ou altera tabelas.

## O que continua pendente

As leituras continuam temporariamente em memória, mantendo somente a última por equipamento. Reiniciar a API apaga essas leituras. As configurações permanecem no PostgreSQL e não são sobrescritas pela chegada de dados.

Não estão implementados nesta etapa: histórico persistente de leituras, cadastro operacional automático, ativação da linha, comandos de habilitação RISING/FALLING, recuperação de acumulados, cálculo operacional de PPM/OEE, processamento temporal operacional e InfluxDB. A interface de rascunhos e a prévia consultiva estão disponíveis, conforme as seções seguintes. Aceitar -1 não habilita nem reconfigura a placa automaticamente.

O JAR `telemetria-oee.jar` na raiz é uma distribuição anterior. Para gerar uma distribuição atualizada, execute `mvn clean package` em `backend` e utilize `backend/target/telemetria-api-1.1.0.jar`. Não execute o JAR antigo esperando o novo contrato.

## Testes

Execute `mvn test` dentro de `backend`. Os testes de leitura verificam o contrato MK2, o JSON real com todos os contadores -1, campos obrigatórios, contagens grandes, limites analógicos, datas inválidas, horário de recebimento do servidor e preservação da leitura anterior após rejeição. Os testes de configuração também devem continuar passando.

Os testes usam HTTP real em porta aleatória e banco H2 temporário em modo PostgreSQL. Não usam as credenciais do ambiente nem acessam seu banco OEE. O acesso ao PostgreSQL real precisa ser confirmado no seu ambiente. A API ainda não possui autenticação.

## Referência

[Spring Boot: conexão com bancos SQL e Spring JDBC](https://docs.spring.io/spring-boot/reference/data/sql.html)

## Primeira etapa do configurador

Foram adicionados `GET /api/dispositivos`, `GET /api/dispositivos/{id}`,
`GET /api/linhas/rascunhos` e GET/PUT `/api/dispositivos/{id}/linha-rascunho`.
Os dispositivos são descobertos pelas leituras válidas recebidas nesta execução;
os rascunhos de linha, produto e velocidade ideal ficam persistidos no PostgreSQL.
A configuração atual de canais e o contrato de telemetria foram preservados.

Antes de salvar, aplique `banco/001_linha_rascunho.sql` no banco OEE pelo IntelliJ.
Siga `banco/LEIA-ME.md` e use `cadastro-linha.http` para testar individualmente cada etapa.
Não há migração automática nem ativação operacional nesta entrega.

## Configuração dos canais em rascunho

Adicionados GET da lista e GET/PUT por canal em `/api/dispositivos/{id}/canais-rascunho`.
Aplique `banco/002_canal_rascunho.sql` após o script 001 e use `canais-rascunho.http`.
As funções e parâmetros de DI, contador e medida analógica estão documentados em
`banco/CANAIS-RASCUNHO.md`. Cada canal tem uma versão própria, independente da linha.
A borda RISING/FALLING é apenas cadastrada; não há comando enviado à placa.

## Configurador web local

Com a API atualizada em execução, abra `http://127.0.0.1:8080/`.
A página permite selecionar dispositivos, consultar suas últimas leituras e editar
os rascunhos da linha e dos canais. A interface é incluída no JAR e utiliza as mesmas
rotas já verificadas no Postman. Não exige instalação de frontend separado nem nova migração.
Consulte `CONFIGURADOR.md` para instalação, comportamento de versões e conferência.

## Prévia dos sinais

GET `/api/dispositivos/{id}/previa` interpreta a última amostra com o rascunho salvo:
estados digitais por polaridade, medida analógica por escala e valor da amostra do
contador por fator. A tela apresenta o resultado e sua situação, incluindo ausências
e contador -1. Não requer migração nova. Consulte `PREVIA-SINAIS.md`.

## Simulador de contadores

Abra `/simulador.html` ou use POST `/api/simulacoes/contadores` para testar quantidade
e PPM entre leituras controladas. Exemplos de sessão, contador -1 e horários inválidos
estão disponíveis na tela. A simulação não altera telemetria nem cadastros e não é
ativação operacional. Consulte `SIMULADOR.md`, `simulacao-exemplo.json` e
`simulacao-contadores.http`. Não exige nova migração SQL.
