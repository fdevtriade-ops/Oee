# Configurações de canais em rascunho

A API permite salvar a função e os parâmetros de DI, contador e AI para um dispositivo cujo rascunho de linha já existe. Cada canal possui sua própria versão. Os cadastros permanecem no PostgreSQL após reiniciar a API. Novas leituras mantêm os valores brutos e não sobrescrevem a configuração.

## Instalação

1. No projeto em que a API realmente é executada, atualize o código, compilando e reiniciando após a mudança. Se estiver usando outro computador, é necessário levar as alterações pelo Git antes de reiniciar.
2. Execute `002_canal_rascunho.sql` inteiro no mesmo banco OEE utilizado pela API, após `001_linha_rascunho.sql`. O novo script cria somente `configuracao.canal_rascunho`, com vínculo ao rascunho de linha. Não modifica as tabelas e cadastros antigos.
3. O usuário PostgreSQL da API precisa de SELECT, INSERT e UPDATE na nova tabela e SELECT/UPDATE na linha_rascunho (a edição de canais usa bloqueio da linha durante a transação). Se aplicar o script com outro usuário, o administrador pode precisar conceder essas permissões.
4. Use `canais-rascunho.http`, executando individualmente. Os exemplos usam TRD_TESTE, que precisa ter seu rascunho de linha já salvo. No Postman, PUT usa Body raw / JSON e Content-Type application/json; GET usa Body none.

Não há execução automática do SQL. A falta da tabela retorna 503 nas novas rotas, com orientação para aplicar os scripts; as rotas anteriores continuam disponíveis. Repetir IF NOT EXISTS sobre uma tabela criada pelo script preserva os dados, mas não repara uma tabela diferente de mesmo nome.

## Rotas

- GET `/api/dispositivos/{id}/canais-rascunho`: lista somente os canais que já têm configuração salva. [] significa que ainda não há canais configurados, não ausência de leitura.
- GET `/api/dispositivos/{id}/canais-rascunho/{codigo}`: consulta o cadastro de um canal; 404 se ainda não foi salvo.
- PUT na mesma rota: substitui a configuração completa do canal. Os campos obrigatórios são nome, habilitado, funcao, parametros e versao. Não envie os campos da resposta, como atualizadoEm ou situacao, no corpo do PUT.

O PUT retorna 200 com situacao RASCUNHO. versao 0 cria, retornando versao 1. Para editar, consulte e envie a versão atual daquele canal. Edições incrementam essa versão; versão antiga ou criação duplicada retorna 409. A versão da linha é independente. O controle impede sobrescrita silenciosa, mas ainda não mantém histórico das versões anteriores.

## Funções e parâmetros desta entrega

| Canal | Funções permitidas | Parâmetros |
| --- | --- | --- |
| DI1–DI4 | MAQUINA_RODANDO, MAQUINA_PARADA, MAQUINA_OCIOSA, FALHA | nivelAtivo: número 0 ou 1; o outro nível é inativo |
| contadorDI1–contadorDI4 | TOTAL_PRODUZIDO, PRODUTOS_BONS, PRODUTOS_REJEITADOS | unidade: texto; fator: número positivo finito; borda: RISING ou FALLING |
| AI1–AI4 | MEDIDA_ANALOGICA | grandeza e unidade: textos; tensaoMin/tensaoMax: extremos de entrada entre 0 e 10 V, mínimo menor que máximo; valorMin/valorMax: extremos de saída distintos e finitos |

O formulário recebe exatamente os parâmetros do tipo escolhido; campos desconhecidos ou incompletos são rejeitados com 400. Nome configurado deve conter de 1 a 150 caracteres; unidade, de 1 a 30; grandeza, de 1 a 100. Fator e números devem ser números JSON, não textos. A escala de saída pode ser crescente ou decrescente; valorMin corresponde a tensaoMin e valorMax a tensaoMax. Esta etapa armazena essa escala, sem aplicar conversão às leituras.

Para marcar um canal sem uso, envie nome null (ou um nome válido), habilitado false, funcao null, parametros {} e a versão adequada. Um canal desabilitado também pode preservar uma configuração completa válida. Não é permitido habilitar sem nome, função e parâmetros compatíveis.

Essas novas rotas não usam o catálogo legado de `/api/funcoes-di`: nesse catálogo há funções produtivas que agora pertencem aos contadores. Os endpoints antigos permanecem com o comportamento anterior e seus dados não são sincronizados automaticamente com os rascunhos novos. Não atribuímos funções automaticamente pelo número de canal. DO continua presente na telemetria; seu uso funcional não foi definido no MK2 e não integra este cadastro.

## Validação e limites

Testes HTTP e H2 isolado cobrem DI/contador/AI, edição, canal sem uso, concorrência tanto na criação quanto na edição, versões desatualizadas, faixas inválidas, campos ausentes, isolamento entre dispositivos, recuperação do banco e preservação dos cadastros e leituras existentes. O DDL de teste é idêntico ao script de instalação. A instalação no PostgreSQL real e suas permissões precisam ser conferidas no ambiente do projeto.

Salvar canais não ativa a linha. O rascunho permite canais com a mesma função; a conclusão posterior exige escolha explícita das fontes. A ativação, o cálculo de produção/PPM/OEE e os comandos da placa simulada local estão documentados em ../MOTOR-OPERACIONAL.md. AI permanece consultiva e não é fonte de estado do motor atual. O protocolo de habilitação da placa física e a escrita no InfluxDB permanecem separados.

Para conferir a persistência: salve os canais, consulte, reinicie a API e consulte a lista novamente. Nenhuma nova leitura é necessária para recuperar os cadastros. Depois envie uma leitura de teste e confirme que os cadastros continuam iguais.
