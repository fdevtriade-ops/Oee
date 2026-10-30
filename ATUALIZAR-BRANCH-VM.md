# Continuar o projeto na branch da VM

Este pacote é a continuação da estrutura de `Oee-main (4).zip`, não outro projeto. Mantém `backend/pom.xml`, os pacotes Java existentes, `backend/src/main/resources/static`, os scripts em `banco/` e os exemplos HTTP/JSON. Não contém pasta `.git` nem configurações da máquina de desenvolvimento.

## Aplicar na branch escolhida

1. Na VM, abra o clone real de `fdevtriade-ops/Oee`. Confira a branch e as alterações locais com `git status`. Troque/crie a branch em que deseja testar antes de copiar os arquivos.
2. Extraia o ZIP em uma pasta separada. Compare com o clone e copie os arquivos do projeto para essa branch. Preserve `.git`, a conexão de banco do ambiente, configurações locais e quaisquer alterações próprias que ainda não estejam salvas. A pasta `.idea` não é necessária para a entrega.
3. Confira `git diff` antes de gravar o commit. O arquivo `ARQUIVOS-ALTERADOS.md` lista as adições/alterações em relação ao ZIP de origem.
4. No mesmo PostgreSQL usado pela API da VM, aplique as migrações pendentes. Se 001/002 já existem, as novas etapas são 003, 004 e 005. Em banco vazio, comece pela 000. As migrações não são executadas automaticamente pelo Java.
5. Compile com `mvn -f backend/pom.xml package` e inicie `TelemetriaApplication` no IntelliJ, ou use o JAR atualizado fornecido na raiz. O JAR Maven fica em `backend/target/telemetria-api-1.1.0.jar`. No Windows, `compilar.ps1` atualiza também o JAR da raiz.
6. Reinicie a instância correta da API com as variáveis de conexão da VM. Não use as credenciais de teste de outro ambiente. Mantenha apenas uma instância por porta.
7. Atualize o navegador com Ctrl+F5. Abra `/`, `/conclusao.html` e `/operacao.html`.
8. Execute a demonstração local de `COMECE-AQUI.md`, usando os IDs `LOCAL_DEMO_...`. Ela mantém os cadastros de produção separados.

Se acessar a API de fora da VM, use o endereço e a porta da VM. `localhost` sempre se refere à máquina/container que faz a requisição. O Node-RED também precisa usar o endereço da API acessível a partir dele.

## Node-RED existente

Mantenha os ramos de temperatura, umidade e porta. Se o ramo OEE já foi corrigido manualmente, compare sua função com `node-red/preparar-json.js`; não importe uma segunda cópia ou sobrescreva o fluxo completo. Veja `node-red/LEIA-ME.md`.

## Escopo local

A linha concluída pode ser ativada para processar as leituras reais recebidas, com regras informadas explicitamente. Os comandos e a recuperação automáticos da placa de software são locais. O envio de comandos à placa física depende do tópico de comando, payload e ack ainda não disponibilizados. Nada foi enviado ao GitHub ou aplicado à VM pelo agente.

PostgreSQL, Java e, para compilar, Maven são requisitos do ambiente. Eles não são instaladores incluídos no ZIP. A interface não exige npm.
