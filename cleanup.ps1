$base=Split-Path -Parent $MyInvocation.MyCommand.Path
$trash=Join-Path (Join-Path $base ([regex]::Unescape('_\uc548\uc4f0\ub294\ud30c\uc77c'))) 'cleanup_20261024'
New-Item -ItemType Directory -Force -Path $trash | Out-Null
Get-ChildItem -LiteralPath $base -File -Filter '*20261024*.sql' | ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination (Join-Path $trash $_.Name) -Force; Write-Host ('moved: '+$_.Name) }
Get-ChildItem -LiteralPath $base -File | Where-Object { $_.Name -in @('check_accounts.bat') } | ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination (Join-Path $trash $_.Name) -Force; Write-Host ('moved: '+$_.Name) }
Write-Host 'Done.'
