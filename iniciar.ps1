param([int]$Porta=8080)
$ErrorActionPreference='Stop'
if(-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Instale Java 17 ou superior antes de iniciar.' }
$jarOee=Join-Path $PSScriptRoot 'telemetria-oee.jar'
if(-not (Test-Path -LiteralPath $jarOee)) { throw 'O JAR não foi encontrado. Execute compilar.ps1.' }
if(-not $env:POSTGRES_URL) {
    $conexaoOee=Read-Host 'URL JDBC (Enter para jdbc:postgresql://localhost:5432/OEE)'
    $env:POSTGRES_URL=if($conexaoOee) {$conexaoOee} else {'jdbc:postgresql://localhost:5432/OEE'}
}
if(-not $env:POSTGRES_USER) { $env:POSTGRES_USER=Read-Host 'Usuário do PostgreSQL' }
if(-not $env:POSTGRES_PASSWORD) {
    $senhaOee=Read-Host 'Senha do PostgreSQL (não será exibida nem salva)' -AsSecureString
    $env:POSTGRES_PASSWORD=[System.Net.NetworkCredential]::new('',$senhaOee).Password
}
Write-Output "Iniciando OEE. Abra http://localhost:$Porta/ . Encerre com Ctrl+C."
& java -jar $jarOee "--server.port=$Porta"
exit $LASTEXITCODE
