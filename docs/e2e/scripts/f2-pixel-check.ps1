# f2-pixel-check.ps1 —— 客观核验角标截图的色值（不依赖肉眼读图）
# 红色 --fs-danger #dc2626=(220,38,38)；橙色 --fs-warning #d97706=(217,119,6)
param([string]$Dir = 'D:\xcode\wt\docs\e2e\evidence-2026-09-29-f2-expiry')
Add-Type -AssemblyName System.Drawing

$files = @(
  'f2-step-06-a5-badge-near-red.png',
  'f2-step-07-a6-badge-far-normal.png',
  'f2-step-08-a7-badge-expired-red.png',
  'f2-step-09-a8-badge-none.png',
  'f2-step-11-a10-detail-near-red.png',
  'f2-step-12-a11-detail-far-normal.png',
  'f2-step-19-b6-badge-far-red-30.png'
)

function Count-Color([string]$path, [string]$mode) {
  $bmp = [System.Drawing.Bitmap]::FromFile($path)
  $hits = 0
  for ($y = 0; $y -lt $bmp.Height; $y += 2) {
    for ($x = 0; $x -lt $bmp.Width; $x += 2) {
      $c = $bmp.GetPixel($x, $y)
      if ($mode -eq 'red') {
        if (($c.R -gt 170) -and ($c.G -lt 90) -and ($c.B -lt 90)) { $hits++ }
      } else {
        if (($c.R -ge 190) -and ($c.R -le 235) -and ($c.G -ge 95) -and ($c.G -le 150) -and ($c.B -le 90)) { $hits++ }
      }
    }
  }
  $bmp.Dispose()
  return $hits
}

Write-Output "file`tredPx`torangePx"
foreach ($f in $files) {
  $p = Join-Path $Dir $f
  if (-not (Test-Path $p)) { Write-Output "$f`tMISSING"; continue }
  $r = Count-Color $p 'red'
  $o = Count-Color $p 'orange'
  Write-Output "$f`t$r`t$o"
}
