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

## O que continua pendente

O recebimento anterior em `POST /api/leituras` e a consulta em `GET /api/leituras/{id}` continuam disponíveis. As leituras continuam temporariamente em memória e ainda exigem os 12 canais originais. A configuração salva no PostgreSQL não é sobrescrita pela chegada de uma leitura.

A próxima etapa é implementar a gravação das leituras no InfluxDB e a apresentação dos estados usando as configurações salvas. Contagem por pulsos, cálculo de velocidade, aceitação de canais adicionais na telemetria e a tela de configuração ainda não estão implementados.

## Testes

Build concluído e 54 testes aprovados: 35 do recebimento de leituras e 19 das configurações, incluindo persistência SQL, isolamento por máquina, inversão do nível ativo, AIs vazias e rejeição de dados inválidos.

Execute `mvn test` dentro da pasta `backend`. Os testes usam HTTP real e um banco H2 temporário em modo PostgreSQL, com uma adaptação do schema fornecido (`btrim` foi substituído por `trim`). As propriedades de teste estão em `src/test/resources` e não são incluídas no JAR de produção. Os testes não usam as credenciais do ambiente nem acessam seu banco OEE.

O acesso ao PostgreSQL real precisa ser confirmado no seu ambiente usando os passos acima. A API ainda não possui autenticação; os endpoints estão preparados para desenvolvimento em ambiente controlado.

## Referência

[Spring Boot: conexão com bancos SQL e Spring JDBC](https://docs.spring.io/spring-boot/reference/data/sql.html)
