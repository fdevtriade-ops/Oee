# Conferir e concluir a configuração da linha

A conclusão guarda uma revisão persistente dos cadastros. A etapa seguinte é a ativação explícita do motor em `/operacao.html`, com regras informadas pelo operador; consulte `MOTOR-OPERACIONAL.md`. Concluir sozinho não inicia o motor.

## Instalar

1. No banco PostgreSQL da API, após as migrações anteriores, execute `banco/003_configuracao_concluida.sql`.
2. O usuário da API precisa de SELECT e INSERT em `configuracao.linha_configuracao`, além das permissões anteriores. UPDATE/DELETE não são usados pelo código para esse histórico.
3. Compile e reinicie a API. Abra `/` e clique em **Conferir e concluir configuração**, ou acesse `/conclusao.html`.
4. Escolha a linha e as três fontes: estado, total produzido e qualidade. Nenhuma fonte é escolhida pelo número do canal.
5. Clique em **Conferir configuração**; com a conferência aprovada, clique em **Concluir configuração**.

Na fonte de estado, esta entrega suporta DI com função `MAQUINA_RODANDO` e polaridade já salva. A ausência de sinal de parada/falha não é usada para presumir que a máquina está rodando. Total usa `TOTAL_PRODUZIDO`; qualidade pode usar `PRODUTOS_BONS` ou `PRODUTOS_REJEITADOS`. As fontes precisam estar habilitadas, e os dois contadores devem ter a mesma unidade de produto. O fator e a borda ficam preservados na revisão.

Esta conferência trata da configuração salva, não da disponibilidade física da placa ou da atualidade das leituras. Contador desconfigurado, recuperação após energia, confirmação de comando e aptidão operacional serão tratados pelo fluxo de habilitação quando seu contrato estiver definido.

## Revisões e edições

A conclusão guarda a identificação da linha, os canais, as fontes escolhidas e as versões conferidas. Edições posteriores continuam no rascunho e não modificam a revisão anterior. A consulta passa a informar `RASCUNHO_ALTERADO`; para concluir outra revisão, confira novamente.

A conclusão usa o mesmo bloqueio de linha dos cadastros de canal e verifica a versão da linha, de todos os canais salvos e da revisão anterior. Se outro usuário editar ou concluir durante a conferência, retorna 409 em vez de aceitar silenciosamente dados diferentes.

Estados desta etapa: `CONFIGURACAO_CONCLUIDA` e `RASCUNHO_ALTERADO`. `processamentoHabilitado` informa se essa revisão está sendo usada por uma execução ativa do motor. A conclusão em si não envia MQTT nem escreve no InfluxDB.

## API

`POST /api/dispositivos/{id}/configuracao/conferir`:

```json
{
  "fontes": {
    "estado": "DI3",
    "total": "contadorDI1",
    "qualidade": "contadorDI4"
  }
}
```

A resposta contém `podeConcluir`, `pendencias`, `fontes`, `versaoLinha`, `versoesCanais` e `revisaoAtual`. Canais incorretos retornam pendências na conferência; não são ativados automaticamente.

`POST /api/dispositivos/{id}/configuracao/concluir`: envie somente `fontes`, `versaoLinha`, `versoesCanais` e `revisaoAtual` da resposta conferida. Não envie `podeConcluir` ou `pendencias` no corpo da conclusão.

`GET /api/dispositivos/{id}/configuracao/concluida`: consulta a revisão mais recente, sua situação e o documento preservado. Sem conclusão retorna 404; sem a migração 003, as novas operações retornam 503 com a orientação do SQL.

## Próximas definições necessárias para os seis itens pedidos

- MQTT: tópico, payload de habilitação/borda, ack, valor inicial e sinal/ID de reinício.
- Tempo: cadência contratual, fuso da placa, limites e política de atraso, lacunas e retorno da comunicação.
- Calendário/paradas: turnos, efeito das paradas programadas e classificação/limiar de microparadas.
- Indicadores: confirmar as fórmulas propostas no MK2 e sua aplicação aos intervalos válidos e às mudanças de velocidade.

Não se atribuiu automaticamente America/Sao_Paulo ao horário da placa nem os turnos propostos como calendário real. O documento MK2 original não foi fornecido; esta implementação se baseia no resumo de continuidade e nos cadastros existentes.
