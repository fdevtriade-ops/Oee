# Integração MK2

O ramo OEE deve sair do MQTT `My_Home_1/BM/JSON` em paralelo ao ramo de temperatura/umidade já existente. Mantenha conectados `Parse & Validate`, `To Line Protocol` e `Influx Write API`, além do tópico de porta.

## Atualizar uma instalação existente

1. Exporte uma cópia local do fluxo atual para recuperação; ela pode conter credenciais, portanto não a envie ao Git.
2. No ramo OEE existente, substitua somente o código de `Preparar JSON para API` pelo arquivo `preparar-json.js`. O arquivo JSON de importação é uma alternativa para criar esse ramo; não precisa importá-lo se o ramo já existe.
3. Configure `BACKEND_URL` como a URL completa de `/api/leituras`, acessível a partir da máquina/container do Node-RED. `OEE_DEVICE_ID` escolhe o piloto e tem padrão `TRD2001`. `TRD_TESTE` também é aceito para teste manual.
4. Configure a saída do MQTT como texto ou buffer. Isso preserva os inteiros grandes da placa; um objeto convertido pelo JavaScript pode já ter arredondado os contadores.
5. No `Parse & Validate` do ramo ambiental, após converter/validar o objeto `p` e antes de produzir os dados do InfluxDB, mantenha o filtro:

```javascript
if (
    msg.topic === "My_Home_1/BM/JSON" &&
    Object.keys(p).some(chave => /^contadorDI[1-4]$/.test(chave))
) {
    return null;
}
```

6. Faça Deploy. Uma mensagem MK2 válida deve receber 204; mensagens ambientais continuam no ramo existente. O ramo OEE ignora mensagens sem contadores e outros equipamentos.

## Importar um ramo novo

Importe `fluxo-node-red.json` no fluxo atual e conecte o MQTT ao nó de preparação. Não duplique um ramo OEE já instalado. O arquivo contém apenas o ramo OEE e um botão manual para `TRD_TESTE`; não inclui broker, fluxo ambiental ou credenciais. O botão não executa automaticamente nem usa o ID da placa real. A data é fixa, destinada somente à conferência de recebimento.

A função encaminha o JSON completo, incluindo contadores, timestamp e campos extras. A API valida os valores, produz `recebidoEm` e retorna 400 se a leitura for inválida. Esta integração não calcula produção, não envia comandos à placa e não implementa recuperação/reenvio.

## Conferir sem Node-RED

Com Node.js instalado, na raiz do projeto:

```sh
node --test node-red/preparar-json.test.cjs
```

Node.js é necessário apenas para esses testes e para o próprio Node-RED; o backend e sua interface continuam sem depender dele.
