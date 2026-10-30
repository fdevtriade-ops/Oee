> Registro histórico de uma etapa anterior. Para o estado atual, consulte ENTREGA-03.md e COMECE-AQUI.md.

# Etapa 2 — conclusão da configuração

Foi implementada a parte de conferência/conclusão de cadastro do item 1 solicitado. A ativação do processamento ainda depende do contrato da placa e das regras temporais necessárias aos itens 2 a 6.

## Entregue

- Página `/conclusao.html`, acessível pelo configurador.
- Escolha explícita das fontes de estado, total e qualidade, com conferência de habilitação, função e unidade.
- Rotas POST `/api/dispositivos/{id}/configuracao/conferir` e `/concluir`, e GET `/concluida`.
- Migração `banco/003_configuracao_concluida.sql` para revisões persistentes dos cadastros concluídos.
- Preservação das revisões anteriores ao editar o rascunho e indicação de `RASCUNHO_ALTERADO`.
- Controle das versões de linha, canais e revisão para proteger contra conclusão de um cadastro alterado após a conferência.
- Conclusão concorrente serializada por linha; a segunda requisição com revisão antiga recebe 409.

## Validado

- 177 testes do backend aprovados, com oito novos testes de conclusão incluindo HTTP, recursos da interface, versões, unidades, fontes, histórico e concorrência.
- JavaScript da nova página conferido sintaticamente; página e script servidos com HTTP 200 nos testes. Não foi feita inspeção visual no navegador nesta etapa.
- Migrações 000–003 executadas e repetidas em PostgreSQL 15.12 temporário, preservando cadastros e revisão existente. Servidor temporário encerrado ao final.
- Compilação e JAR concluídos. Não foi aplicado SQL ao banco de produção nem atualizado o Node-RED real.

## Continuar a implementação

Os seis itens solicitados continuam no escopo. Para finalizar a ativação e implementar comandos, produção/PPM, retomada, turnos/paradas e indicadores, ainda é necessário obter os tópicos/payloads/ack da placa e as decisões de tempo, lacunas, sessões, turnos e microparadas.

O resumo recebido classifica esses pontos como pendentes e algumas fórmulas como propostas. Esta entrega não os converte em regras de produção. A revisão concluída retorna `processamentoHabilitado: false`; a coleta e o simulador mantêm o comportamento anterior.

Instalação e exemplos da nova função: `CONCLUSAO-CONFIGURACAO.md`. As correções de instalação/Node-RED da etapa anterior também estão incluídas. O pacote tem os fontes; aplique as mudanças ao clone real antes de commit/push. Nada foi enviado ao GitHub ou à outra máquina.

