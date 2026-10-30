# Instalar e atualizar o OEE Tríade

Esta versão instala coleta MK2, configurador, rascunhos persistentes, prévia, simulador isolado e motor operacional local. O motor calcula produção/OEE com regras explícitas e possui placa de software para testar comandos e recuperação. O protocolo de comando da placa física, InfluxDB e Grafana permanecem fora desta entrega. Comece por `COMECE-AQUI.md`.

## 1. Preparar o projeto no PC

Use Java 17 ou superior, Maven e PostgreSQL. A interface é servida pelo backend; não exige npm. Para subir alterações no GitHub, use um clone do repositório com histórico:

```sh
git clone https://github.com/fdevtriade-ops/Oee.git
```

Se já possui o clone, confira `git status` e sua branch antes de copiar os arquivos desta entrega. O ZIP não contém `.git`; não inicialize outro histórico para forçar o envio. Compare as alterações com a versão do clone e preserve o trabalho local que já existir.

## 2. Preparar PostgreSQL

No cliente PostgreSQL/Database do IntelliJ, conecte ao banco usado pela API. Se ainda não existe, crie o banco `OEE` com o nome correspondente à URL de conexão. Dentro dele execute:

1. `banco/000_configuracao_inicial.sql`: schema, tabelas legadas e catálogo inicial.
2. `banco/001_linha_rascunho.sql`: rascunho da linha.
3. `banco/002_canal_rascunho.sql`: rascunhos dos canais.
4. `banco/003_configuracao_concluida.sql`: revisões dos cadastros concluídos. A nova tabela precisa de SELECT/INSERT para o usuário da API. Veja `CONCLUSAO-CONFIGURACAO.md`.
5. `banco/004_motor_operacional.sql`: execuções, referências, intervalos e paradas programadas.
6. `banco/005_placa_local.sql`: schema `simulacao`, placa e confirmações de comandos locais. Garanta USAGE no schema e SELECT/INSERT/UPDATE nas novas tabelas usadas pela API.

Não use os SQLs de `backend/src/test/resources` para instalar produção. Os scripts de `banco/` não são executados automaticamente. Não removem tabelas nem cadastros. `IF NOT EXISTS` permite repetição sobre estruturas compatíveis, mas não é migração de tabelas antigas incompatíveis.

O usuário da API precisa de conexão ao banco, USAGE no schema, SELECT nas três tabelas legadas, UPDATE em `canal` e SELECT/INSERT/UPDATE nos dois rascunhos. Permissões de criação são necessárias para quem executar os scripts, não para a rotina da API. Se usar usuários diferentes, peça ao administrador para conceder esses acessos.

Confira as tabelas de configuração e operação:

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'configuracao'
ORDER BY table_name;
```

Um banco novo não terá máquinas nem canais legados. Após receber JSON válido, o dispositivo aparece na descoberta e pode ganhar rascunhos pela interface, independentemente desses cadastros antigos.

## 3. Iniciar e conferir

Configure na execução Java/Run Configuration do IntelliJ:

```text
POSTGRES_URL=jdbc:postgresql://localhost:5432/OEE
POSTGRES_USER=seu_usuario
POSTGRES_PASSWORD=sua_senha
```

Use os valores reais apenas na máquina. Não salve senhas no Git. Um `.env` não é carregado automaticamente. A configuração Database do IntelliJ é separada da configuração da aplicação.

Na raiz do projeto:

```sh
mvn -f backend/pom.xml package
java -jar backend/target/telemetria-api-1.1.0.jar
```

Ou execute `TelemetriaApplication` pelo IntelliJ. Use apenas uma instância por porta. O JAR da raiz foi atualizado neste pacote; `iniciar.ps1` o executa, e `compilar.ps1` atualiza essa cópia após compilar.

1. Aguarde a mensagem de conexão e tabelas verificadas.
2. Abra `http://localhost:8080/`.
3. No Postman, envie um JSON completo de `exemplo.json` a `POST /api/leituras`, trocando o id por `TRD_TESTE`. Body raw/JSON; resposta esperada 204.
4. Consulte `GET /api/dispositivos/TRD_TESTE`: deve listar 16 canais.
5. Cadastre uma linha e seus canais; reinicie e confira os rascunhos. Leituras em memória somem ao reiniciar até novo recebimento, como antes.
6. Abra `/simulador.html` e execute os exemplos de `SIMULADOR.md`: operação normal deve dar 10 produtos/5 segundos/120 PPM.
7. Siga `node-red/LEIA-ME.md` para validar MQTT sem interromper a aplicação ambiental.

## 4. Enviar ao Git

No clone real, revise `git status` e `git diff`. Inclua somente os arquivos desejados com `git add CAMINHO`, faça commit e push na branch escolhida. Não inclua dados do PostgreSQL, exports com tokens, credenciais, `.idea`, `target` ou logs. O `.gitignore` da raiz cobre arquivos novos; arquivos já rastreados precisam de revisão separada. Não retire arquivos do controle de versão sem conferir o diff.

## 5. Atualizar a outra máquina

1. Anote o commit em execução e guarde o JAR anterior; exporte o fluxo Node-RED local para recuperação.
2. No clone da máquina de destino, confira a branch e alterações locais. Com o diretório pronto para atualização, use `git pull --ff-only`. Se houver divergência, resolva-a antes de continuar.
3. Configure a conexão daquele ambiente e aplique somente os scripts necessários. Não recrie o banco já existente. Git transporta código, não os cadastros do PostgreSQL.
4. Compile com `mvn -f backend/pom.xml package`. Se falhar, mantenha a versão anterior em execução.
5. Pare a API pelo mecanismo que a iniciou (IntelliJ/terminal/serviço) e inicie o novo JAR pelo mesmo mecanismo, com as variáveis daquele ambiente.
6. Atualize a função OEE do Node-RED conforme o guia; evite importar um segundo ramo ou sobrescrever o fluxo ambiental.
7. Recarregue a interface com Ctrl+F5 e repita a conferência de recebimento, persistência e simulador. Confira também temperatura/umidade/porta.

Se a nova aplicação falhar, pare-a e volte ao JAR anterior com a mesma configuração. Os scripts desta entrega são aditivos; não remova tabelas para reverter o executável. Restaurar banco só se torna necessário se houver alterações de dados que precisem ser desfeitas.
