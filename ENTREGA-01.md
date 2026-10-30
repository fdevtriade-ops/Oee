> Registro histórico de uma etapa anterior. Para o estado atual, consulte ENTREGA-03.md e COMECE-AQUI.md.

# Primeira etapa de implementação — 30/10/2026

## O que mudou

- Corrigido o transporte MK2 em `node-red/preparar-json.js`: encaminha o JSON inteiro com quatro contadores e timestamp, filtra o piloto configurável e aceita `TRD_TESTE` para conferência.
- Texto/buffer MQTT é preservado para não arredondar contadores de 64 bits. Objetos com contadores numericamente inseguros são bloqueados com diagnóstico.
- O ramo constrói headers próprios, sem herdar autorização/cookies do InfluxDB; a mensagem original permanece intacta.
- Atualizado `node-red/fluxo-node-red.json` com a mesma função e um botão manual de JSON completo para `TRD_TESTE`, sem disparo automático.
- Incluído `banco/000_configuracao_inicial.sql`: schema, três tabelas legadas e oito funções iniciais. Não cria máquinas/canais fictícios. Catálogo existente é preservado por `ON CONFLICT DO NOTHING`.
- Incluído `.gitignore` na raiz para IDE, builds, logs e credenciais locais.
- Corrigido o comando de execução em `LEIA-ME.md` para usar o JAR de `backend/target`.
- Incluídos os guias `INSTALACAO-ATUALIZACAO.md` e `node-red/LEIA-ME.md`; atualizado `banco/LEIA-ME.md`.

## Validação desta entrega

- Backend: 169 testes aprovados; zero falhas/erros. Compilação e empacotamento concluídos.
- Integração Node-RED: sete testes aprovados, incluindo precisão Long, isolamento de headers, tópico compartilhado, amostra manual e consistência do fluxo exportado.
- PostgreSQL 15.12 temporário: execução de 000, 001 e 002, inserção de cadastros e repetição dos scripts com preservação dos registros e da descrição personalizada do catálogo. Servidor temporário encerrado ao final.
- O banco, a placa e os fluxos reais do usuário não foram alterados; o recebimento físico e o Deploy no Node-RED permanecem para a instalação.
- O pacote é baseado no ZIP fornecido. Não contém histórico Git e não foi enviado ao GitHub. Deve ser comparado e aplicado ao clone real antes de commit/push.

Para repetir os testes de transporte: `node --test node-red/preparar-json.test.cjs`. O teste `banco/testes/instalacao.sql` deve ser executado por psql **somente em um banco vazio descartável**, pois insere dados fictícios. Os testes Java continuam em `backend/src/test`.

## O que ainda falta implementar

| Área | Trabalho restante | Definição necessária |
| --- | --- | --- |
| Configuração operacional | Conferência completa, ativação e vigência de revisões | Fontes obrigatórias, duplicidade de funções e associação de várias placas |
| Placa | Habilitar contadores/bordas e recuperar após energia | Tópico, payload, ack, valor inicial e sessão |
| Processamento real | Deltas, PPM, relógio, atraso, lacunas e ordenação | Cadência, fuso, tolerâncias e política de retomada |
| Continuidade | Persistir referências/acumulados sem duplicação | Sessões e transação entre cálculo/escrita |
| Calendário | Planejamento e consolidação por minuto/turno | Turnos, paradas programadas e vigência da velocidade |
| Paradas | Classificação, quantidade e duração de microparadas | Limite, igualdade, evento aberto e atravessamento de períodos |
| Indicadores | Bases válidas, coerência, disponibilidade, performance, qualidade e OEE | Aprovação das fórmulas e regras ainda propostas no MK2 |
| InfluxDB/Grafana | Escrita após ativação, correções e painel por linha | Versão/modelo/retenção, acesso e validação de latência |
| Acesso | Autenticação/autorização da API | Contrato de acesso do ambiente |

A primeira etapa corrige a instalação e a integração existentes. Os cadastros permanecem rascunhos; a prévia continua consultiva e o simulador continua isolado. Nenhuma regra de produção pendente foi deduzida das regras do simulador.

O documento MK2 original não foi disponibilizado nesta implementação. A análise se baseou no contexto de continuidade recebido e no código do ZIP. Para a próxima entrega operacional, obter o contrato da placa e as decisões temporais antes de conectar cálculo à coleta real.

