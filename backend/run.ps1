param([switch]$Test)
$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $configPath) {
    foreach ($line in Get-Content -LiteralPath $configPath) {
        if ($line -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD|JWT_SECRET|FRONTEND_URL|CLINIC_ZONE|DEMO_DATA|SMTP_HOST|SMTP_PORT|SMTP_AUTH|SMTP_STARTTLS|SMTP_USERNAME|SMTP_PASSWORD|MAIL_FROM|PORT)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
        }
    }
}
Push-Location $PSScriptRoot
try {
    if ($Test) { & ./mvnw.cmd test } else { & ./mvnw.cmd spring-boot:run }
    exit $LASTEXITCODE
} finally { Pop-Location }
