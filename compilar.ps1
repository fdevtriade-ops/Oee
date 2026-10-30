$ErrorActionPreference='Stop'
if(-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven não encontrado. Configure-o no PATH ou compile pelo IntelliJ.' }
& mvn -f (Join-Path $PSScriptRoot 'backend\pom.xml') package
if($LASTEXITCODE -ne 0) { throw 'Compilação/testes falharam. O JAR da raiz não foi substituído.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'backend\target\telemetria-api-1.1.0.jar') -Destination (Join-Path $PSScriptRoot 'telemetria-oee.jar')
Write-Output 'Testes concluídos e telemetria-oee.jar atualizado.'
