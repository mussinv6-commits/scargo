# S그룹 바로가기 만들기 - 바탕화면에 아이콘 있는 바로가기 2개를 만든다
$root = $PSScriptRoot
$desk = [Environment]::GetFolderPath('Desktop')
$sh = New-Object -ComObject WScript.Shell
$items = @(
  @{ Name = 'S그룹 실행'; Bat = 'S그룹_전체실행.bat'; Ico = 'sgroup_run.ico';  Desc = 'S Cargo + S-Bot 전체 실행' },
  @{ Name = 'S그룹 종료'; Bat = '전체_종료.bat';       Ico = 'sgroup_stop.ico'; Desc = 'S Cargo + S-Bot 전체 종료' }
)
foreach ($i in $items) {
  $bat = Join-Path $root $i.Bat
  $ico = Join-Path $root $i.Ico
  if (-not (Test-Path $bat)) { Write-Host ('[없음] ' + $i.Bat); continue }
  $lnk = $sh.CreateShortcut((Join-Path $desk ($i.Name + '.lnk')))
  $lnk.TargetPath = $bat
  $lnk.WorkingDirectory = $root
  $lnk.Description = $i.Desc
  if (Test-Path $ico) { $lnk.IconLocation = $ico + ',0' }
  $lnk.Save()
  Write-Host ('[완료] 바탕화면 ' + $i.Name)
}
