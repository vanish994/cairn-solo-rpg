# Cairn Guardian Server

Backend mínimo que mantém a chave do Gemini fora do APK.

## Configuração

Defina a variável de ambiente:

GEMINI_API_KEY=<sua chave>

A chave nunca deve ser colocada no código, no APK ou em arquivos versionados.

## Executar localmente

Na raiz do projeto:

    gradle :guardian-server:installDist
    GEMINI_API_KEY="..." gradle :guardian-server:run

Health check:

    GET /health

Endpoint do Guardião:

    POST /guardian

O APK deve apontar para a URL HTTPS publicada desse servidor usando a propriedade Gradle
guardianApiUrl em local.properties:

    guardianApiUrl=https://SEU-SERVIDOR/guardian

O servidor usa a Gemini Interactions API com saída JSON estruturada. O Guardião pode narrar e
pedir uma resolução mecânica, mas nunca altera o estado do Rule Engine.
