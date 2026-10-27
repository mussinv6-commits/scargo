$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$hta  = Join-Path $root '런처\S그룹_런처.hta'
$ico  = Join-Path $root '런처\sgroup.ico'
$desk = [Environment]::GetFolderPath('Desktop')
$ws = New-Object -ComObject WScript.Shell
foreach ($old in @('S그룹 실행.lnk','S그룹 종료.lnk')) {
  $p = Join-Path $desk $old
  if (Test-Path $p) { Remove-Item $p -Force }
}
$lnk = $ws.CreateShortcut((Join-Path $desk 'S그룹.lnk'))
$lnk.TargetPath = Join-Path $env:SystemRoot 'System32\mshta.exe'
$lnk.Arguments = '"' + $hta + '"'
$lnk.WorkingDirectory = Join-Path $root '런처'
$lnk.IconLocation = $ico
$lnk.Description = 'S Group 통합 실행'
$lnk.Save()
Write-Host '바탕화면에 [S그룹] 바로가기를 만들었습니다.'
