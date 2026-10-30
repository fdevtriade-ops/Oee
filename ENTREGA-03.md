# Entrega local — continuação de Oee-main (4).zip

## Base

Arquivo original: `Oee-main (4).zip`.

SHA-256 da base: `FD24D2BFF90DDB9C31A42320C575FA2884115DC516909DB4E12E656B12D5DF4A`.

O `backend/src.zip` posteriormente fornecido foi comparado com o ZIP completo e contém os mesmos fontes. O projeto segue a estrutura e o visual existentes. Não foi criado outro histórico Git nem alterada qualquer branch da VM.

## Situação dos seis itens

| Item | Entrega |
| --- | --- |
| Ativação | Revisão concluída ativável com política explícita e configuração imutável por execução |
| Comandos/energia | Configuração, confirmação e recuperação implementadas na placa simulada local; comando da placa física pendente do contrato MQTT |
| Produção/PPM | Processamento contínuo no POST de leituras, deltas e PPM sobre intervalos válidos |
| Continuidade | Referência, sessão, pendências e bases no PostgreSQL, com avanço transacional e proteção contra duplicação na mesma sessão |
| Turnos/paradas | Calendário configurável, paradas programadas, eventos de microparada e disponibilidade, durações e quantidades |
| Indicadores | Disponibilidade, performance limitada, qualidade e OEE; consolidação por minuto/turno da execução |

Os limites, exclusões de fronteiras, tratamento de períodos sem base e condições de recuperação estão documentados em `MOTOR-OPERACIONAL.md`. Esta é uma implementação local para configuração e validação; não equivale a homologação da linha física.

## Verificado

- 200 testes Java aprovados, zero falhas/erros. Incluem integração HTTP, concorrência, precisão de contadores, rollback de escrita e retomada por nova instância.
- Sete testes Node-RED aprovados. JSON completo, contadores grandes, headers separados, tópico compartilhado e fluxo importável conferidos.
- JAR executado com PostgreSQL 15.12 temporário e todas as migrações aplicadas.
- Pelo navegador: preparação da demonstração, conclusão, ativação, comando confirmado localmente, 10 produtos em 5 s e 120 PPM; queda/religamento produziu bloqueio por -1; recuperação preservou o acumulado e a próxima leitura levou a 20 produtos.
- As páginas anteriores e o simulador isolado permanecem cobertos pela suíte original.
- As migrações são aditivas; o teste SQL de instalação/repetição inclui preservação de cadastros, estado operacional e placa local.

O banco real do usuário, o fluxo Node-RED em execução e a VM não foram alterados. Nenhum push foi realizado. `COMECE-AQUI.md` orienta a execução; `ATUALIZAR-BRANCH-VM.md` orienta a aplicação na branch escolhida.
