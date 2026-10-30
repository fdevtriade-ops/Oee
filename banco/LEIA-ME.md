# Cadastro inicial da linha

Para um banco novo, execute primeiro `000_configuracao_inicial.sql`, depois `001_linha_rascunho.sql` e `002_canal_rascunho.sql`. O script 000 cria o schema, as três tabelas legadas e o catálogo inicial; não cria máquinas/canais de exemplo e não altera as configurações existentes. Reexecutá-lo não corrige uma estrutura incompatível. Consulte [instalação e atualização](../INSTALACAO-ATUALIZACAO.md) para permissões e conferência.

Esta entrega adiciona descoberta dos dispositivos recebidos e um rascunho persistente de nome da linha, produto fixo e velocidade ideal em produtos por minuto. Não ativa produção, não envia comandos à placa e não escreve no InfluxDB. Os canais recebidos são apresentados com seus valores brutos; a configuração de canais está disponível após a migração 002 (ver CANAIS-RASCUNHO.md). O configurador web já está disponível na raiz da API.

## Aplicar pelo IntelliJ

1. Na janela Database, conecte ao PostgreSQL e selecione o banco `OEE` utilizado pela API. Se a sua edição não oferecer Database, execute o script por outro cliente PostgreSQL disponível.
2. Abra `banco/001_linha_rascunho.sql`, associe a conexão e execute o arquivo inteiro. Ele cria apenas `configuracao.linha_rascunho`, em transação; não modifica `maquina`, `canal` ou `funcao_di` e não apaga dados.
3. Confirme a nova tabela com `SELECT * FROM configuracao.linha_rascunho;`. A tabela estará vazia antes do primeiro cadastro.
4. O usuário de conexão da API precisa de SELECT, INSERT e UPDATE na nova tabela e USAGE no schema. Executar com um usuário diferente do utilizado pela API pode exigir concessão dessas permissões pelo administrador.
5. Reinicie `TelemetriaApplication` com as mesmas variáveis de conexão já utilizadas. O Node-RED permanece igual.
6. Aguarde uma leitura da placa e execute individualmente as requisições de `cadastro-linha.http`.

O SQL não é executado automaticamente pela aplicação (`spring.sql.init.mode=never`). Antes de aplicar a migração, as rotas de rascunho retornam 503 com a orientação do arquivo SQL; as rotas anteriores e a descoberta continuam disponíveis. `IF NOT EXISTS` permite repetir o script sobre uma tabela criada por ele, mas não corrige uma tabela homônima de estrutura diferente.

## Conferência do primeiro cadastro

- `GET /api/dispositivos/TRD2001`: deve mostrar 16 canais, o timestamp da placa e recebidoEm. Contadores -1 aparecem como DESLIGADO_OU_DESCONFIGURADO. Isso não significa que a API tentou configurá-los.
- `PUT /api/dispositivos/TRD2001/linha-rascunho`: envie nomeLinha, produto, velocidadeIdealPpm e versao 0. Receberá 200, um linhaId estável, situacao RASCUNHO e versao 1.
- `GET /api/dispositivos/TRD2001/linha-rascunho`: confira os valores salvos.
- Reinicie a API e consulte novamente esse rascunho: ele permanece no PostgreSQL. A lista `/api/dispositivos` contém somente placas observadas desde o início da execução e será repovoada quando o Node-RED enviar novas leituras. `/api/linhas/rascunhos` lista os rascunhos persistidos independentemente da coleta.
- Confira `GET /api/equipamentos/TRD2001/configuracoes`: o cadastro anterior dos canais não é alterado pelo rascunho nem pelas leituras.

Para editar um rascunho, consulte sua versão e envie os quatro campos no PUT, usando essa versão. O servidor incrementa a versão; uma atualização concorrente ou versão antiga retorna 409 e exige nova consulta. Isso é controle de edição, não histórico de revisões: versões antigas ainda não são armazenadas.

nomeLinha e produto exigem texto não vazio de até 150 caracteres. velocidadeIdealPpm exige número finito positivo, informado pelo usuário; não é PPM medido. O dispositivo deve ter uma leitura válida recebida ou já estar em configuracao.maquina para criar o rascunho. A identidade da linha não muda quando seu nome é editado. Nesta etapa há um rascunho por dispositivo; a associação operacional de múltiplas placas a uma linha permanece pendente, sem ativação automática.

## Validação realizada

Testes HTTP com H2 isolado cobrem descoberta, canais, persistência, recuperação por nova instância sem leituras em memória, isolamento, validações, concorrência e funcionamento sem a migração. O DDL usado nos testes é idêntico ao script fornecido. A aplicação do script e as permissões no PostgreSQL real precisam ser verificadas no ambiente do projeto.
