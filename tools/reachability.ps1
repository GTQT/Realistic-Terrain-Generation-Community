param(
    [string]$Target = 'main\java\rtg\api\world\terrain\TerrainBase.java'
)

# Script lives in <repo>\tools\, so derive the repo root without any non-ASCII literal
# (Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI and would mangle a hardcoded CJK path).
$repoRoot = Split-Path $PSScriptRoot -Parent
$Root = Join-Path $repoRoot 'src'

$targetPath = Join-Path $Root $Target
$lines = [System.IO.File]::ReadAllLines($targetPath, [System.Text.Encoding]::UTF8)

$blocks = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -notmatch '^\s*(public|private|protected)?\s*static\s+float\s+(\w+)\s*\(') { continue }
    $name = $Matches[2]
    $j = $i
    while ($j -lt $lines.Count -and $lines[$j] -notmatch '\{') { $j++ }
    if ($j -ge $lines.Count) { continue }
    $depth = 0; $end = $j
    for ($k = $j; $k -lt $lines.Count; $k++) {
        foreach ($ch in $lines[$k].ToCharArray()) {
            if ($ch -eq '{') { $depth++ }
            elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { break } }
        }
        if ($depth -eq 0) { $end = $k; break }
    }
    $blocks += [pscustomobject]@{ Name = $name; Start = $i; End = $end; SigLine = $j }
}

Write-Output ("parsed blocks: {0}" -f $blocks.Count)
$defined = $blocks | ForEach-Object { $_.Name } | Sort-Object -Unique

$calls = @{}
foreach ($b in $blocks) {
    $set = New-Object System.Collections.Generic.HashSet[string]
    for ($k = $b.SigLine + 1; $k -le $b.End; $k++) {
        $l = $lines[$k]
        if ($l -match '^\s*(//|\*)') { continue }
        foreach ($d in $defined) {
            if ($l -match "\b$d\s*\(") { [void]$set.Add($d) }
        }
    }
    $calls[$b.Name] = $set
}

$otherFiles = Get-ChildItem $Root -Recurse -Include *.java | Where-Object { $_.FullName -ne $targetPath }
$external = @{}
foreach ($d in $defined) { $external[$d] = 0 }
foreach ($f in $otherFiles) {
    $txt = [System.IO.File]::ReadAllText($f.FullName)
    foreach ($d in $defined) {
        $external[$d] += ([regex]::Matches($txt, "\b$d\s*\(")).Count
    }
}
$roots = @($defined | Where-Object { $external[$_] -gt 0 })

Write-Output ("roots (called from outside): {0}" -f $roots.Count)

$reach = New-Object System.Collections.Generic.HashSet[string]
$queue = New-Object System.Collections.Generic.Queue[string]
foreach ($r in $roots) { if ($reach.Add($r)) { $queue.Enqueue($r) } }
while ($queue.Count -gt 0) {
    $cur = $queue.Dequeue()
    if ($calls.ContainsKey($cur)) {
        foreach ($n in $calls[$cur]) { if ($reach.Add($n)) { $queue.Enqueue($n) } }
    }
}

Write-Output ''
Write-Output '=== UNREACHABLE (dead) ==='
$dead = @($defined | Where-Object { -not $reach.Contains($_) })
foreach ($d in $dead) {
    $inCount = @($blocks | Where-Object { $_.Name -eq $d }).Count
    $callerCount = @($calls.Values | Where-Object { $_.Contains($d) }).Count
    Write-Output ("  {0,-34} defs={1} extCalls={2} intraCallers={3}" -f $d, $inCount, $external[$d], $callerCount)
}
if ($dead.Count -eq 0) { Write-Output '  (none)' }

Write-Output ''
Write-Output '=== LEGACY HELPERS ==='
# NOTE: keep this list in sync with what actually exists. Stale names print a row with
# empty extCalls, which reads like "0 callers" and has been misread once already.
foreach ($n in 'blendedHillHeight', 'cellDistance', 'groundNoise', 'getTerrainBase',
               'oceanAt', 'toWorldBlocks', 'terrainDunes') {
    $state = 'DEAD'
    if ($reach.Contains($n)) { $state = 'LIVE' }
    if (-not $defined.Contains($n)) { $state = 'NOT-A-static-float-METHOD' }
    $callers = @()
    foreach ($k in $calls.Keys) { if ($calls[$k].Contains($n)) { $callers += $k } }
    Write-Output ("  {0,-22} {1,-26} extCalls={2}  callers: {3}" -f $n, $state, $external[$n], ($callers -join ', '))
}
