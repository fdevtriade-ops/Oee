# Configurador local da linha e dos canais

A página é servida pelo próprio Spring Boot, na raiz da aplicação. Com TelemetriaApplication em execução, abra http://127.0.0.1:8080/ no navegador da máquina da API. Se SERVER_PORT foi alterado, utilize essa porta. Não abra index.html diretamente pelo explorador de arquivos.

## Atualizar e abrir

1. Leve as alterações para a máquina que executa a API, usando commit/push no computador de desenvolvimento e pull no projeto de destino.
2. Confira se as migrações 001 e 002 já foram aplicadas ao PostgreSQL. Não há script SQL novo nesta entrega.
3. Recompile e reinicie TelemetriaApplication com as mesmas variáveis POSTGRES_URL, POSTGRES_USER e POSTGRES_PASSWORD utilizadas anteriormente.
4. Abra a raiz da aplicação no navegador. Se já estava aberta com uma versão anterior, atualize a página (Ctrl+F5 no Windows).

Os arquivos de interface estão em backend/src/main/resources/static e são incluídos no JAR pelo Maven. Não é necessário servidor frontend separado, Node.js, npm ou acesso externo para carregar a página. O JAR antigo da raiz não recebe as alterações automaticamente; para executar empacotado, utilize o JAR atualizado em backend/target após mvn package.

## Utilizar

A lista reúne dispositivos com leituras recebidas nesta execução da API, rascunhos de linha persistidos e máquinas do cadastro anterior. A busca filtra por ID ou nome da linha. A presença de uma amostra não é uma afirmação de comunicação em tempo real; cadência e tolerância permanecem pendentes.

Selecione o dispositivo. O formulário de linha consulta o rascunho e permite cadastrar ou editar nome, produto fixo e velocidade ideal em produtos por minuto. Salve a linha antes de configurar os canais. Um dispositivo sem rascunho não recebe nome, produto ou função automaticamente.

Selecione DI, contador ou AI na lista de sinais. Marque Usar este canal, informe os campos e salve. As funções, polaridades, fatores, bordas e escalas correspondem às regras já existentes na API de rascunhos. Para deixar um canal sem uso, desmarque a opção e salve: função e parâmetros são removidos desse rascunho. As DOs ficam disponíveis apenas para consulta da leitura bruta.

Cada formulário utiliza a versão retornada pelo servidor. Em caso de conflito, a tela mantém os campos preenchidos e exibe a mensagem. Use Recarregar cadastro salvo para consultar a versão mais recente; o descarte de alterações exige confirmação. A interface não força sobrescrita nem tenta salvar novamente automaticamente após conflito ou perda da resposta HTTP.

As últimas leituras do dispositivo selecionado são consultadas a cada cinco segundos enquanto a página está visível. Essa atualização não sobrescreve os formulários em edição. O botão de atualização na lista consulta os dispositivos e leituras, sem descartar os campos preenchidos. Recarregar cadastro salvo também consulta os rascunhos do dispositivo selecionado e confirma o descarte se necessário. O horário recebidoEm é apresentado no fuso do navegador; o timestamp de envio da placa continua como texto original, sem presumir seu fuso.

## Conferência

- Selecione TRD_TESTE: os rascunhos já salvos pelo Postman devem aparecer.
- Edite e salve uma linha ou canal de teste; consulte pelo Postman para comparar.
- Reabra a página: os dados são consultados novamente do PostgreSQL.
- Mantenha um campo em edição enquanto chegam leituras: o campo deve permanecer preenchido.
- Se outro cliente alterar o mesmo cadastro, uma gravação com a versão antiga deve mostrar conflito sem sobrescrever.

A verificação automatizada usa H2 isolado, sem acessar o PostgreSQL real. A interface também foi testada no navegador com criação e edição de linha, DI, AI e contador, conflito de versão, confirmação de descarte e apresentação em telas desktop e móvel. Nenhum dado simulado é incluído no frontend de produção.

O cadastro continua em RASCUNHO. Não há ativação operacional, cálculo de OEE/PPM, comandos à placa ou escrita no InfluxDB. A interface usa as novas rotas de rascunho; o cadastro legado de canais permanece independente, conforme documentado na etapa anterior.

## Prévia da configuração salva

O bloco de prévia interpreta a última amostra de DI, AI e contador, identificando a versão salva aplicada. Há aviso para alterações ainda não salvas. A interpretação é somente consultiva, sem ativação de produção. Veja `PREVIA-SINAIS.md` e `previa-sinais.http` para os estados, fórmulas e exemplos.

## Laboratório de contadores

O link Simulador de contadores no topo abre `/simulador.html`. Ele confere a matemática de quantidade e PPM usando somente o cenário informado, sem aplicar mudanças à linha. Regras e exemplos em `SIMULADOR.md`.
