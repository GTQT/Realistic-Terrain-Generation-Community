param(
    [string]$Target = 'main\java\rtg\world\biome\realistic'
)

# Verifies that each biome's ACTUAL terrain class (the one initTerrain() returns)
# routes into an RWG-ported terrain function. Catches the "migration was applied to
# a dead class" failure mode -- e.g. RealisticBiomeVanillaMesaBryce.initTerrain()
# returned TerrainRTGBrycePlateau while the migrated class was TerrainRTGMesaBryce.

$repoRoot = Split-Path $PSScriptRoot -Parent
$srcRoot = Join-Path $repoRoot 'src'
$scanRoot = Join-Path $srcRoot $Target
$allJava = Get-ChildItem $srcRoot -Recurse -Include *.java | ForEach-Object { $_.FullName }
$text = @{}
foreach ($f in $allJava) { $text[$f] = [System.IO.File]::ReadAllText($f) }

# ---- 1. Every "class X extends TerrainBase" -> its simple name and declaring file ----
$decl = @{}   # simpleName -> file
foreach ($f in $allJava) {
    foreach ($m in [regex]::Matches($text[$f], 'class\s+(\w+)\s+extends\s+(?:\w+\.)*TerrainBase')) {
        $n = $m.Groups[1].Value
        if (-not $decl.ContainsKey($n)) { $decl[$n] = $f }
    }
}

function Get-Body([string]$file, [string]$simple) {
    $ls = [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)
    $start = -1
    for ($i = 0; $i -lt $ls.Count; $i++) {
        if ($ls[$i] -match "class\s+$simple\s+extends") { $start = $i; break }
    }
    if ($start -lt 0) { return $null }
    $depth = 0; $out = @()
    for ($i = $start; $i -lt $ls.Count; $i++) {
        $out += $ls[$i]
        foreach ($ch in $ls[$i].ToCharArray()) {
            if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth-- }
        }
        if ($depth -eq 0 -and $i -gt $start) { break }
    }
    return $out
}

# ---- 2. Walk each biome file ----
$files = Get-ChildItem $scanRoot -Recurse -Include 'RealisticBiome*.java'
$bad = @()
$okCount = 0
foreach ($f in $files) {
    $ls = [System.IO.File]::ReadAllLines($f.FullName, [System.Text.Encoding]::UTF8)
    $ret = $null
    for ($i = 0; $i -lt $ls.Count; $i++) {
        if ($ls[$i] -match 'TerrainBase\s+initTerrain\s*\(') {
            for ($j = $i; $j -lt [Math]::Min($i + 12, $ls.Count); $j++) {
                if ($ls[$j] -match 'return\s+new\s+([\w\.]+)\s*\(') { $ret = $Matches[1]; break }
            }
            if (-not $ret) {
                # initTerrain() may return a FIELD, e.g. RealisticBiomeMountainChain returns `terrain`.
                # Resolve the field's declared type in the same file, then look that class up.
                for ($j = $i; $j -lt [Math]::Min($i + 12, $ls.Count); $j++) {
                    if ($ls[$j] -match 'return\s+(\w+)\s*;') {
                        $fieldName = $Matches[1]
                        # Prefer the concrete class assigned to the field (`this.terrain = new X()`),
                        # because the declared type may just be the `TerrainBase` base class.
                        foreach ($l2 in $ls) {
                            if ($l2 -match "\.?$fieldName\s*=\s*new\s+([\w\.]+)\s*\(") { $ret = $Matches[1]; break }
                        }
                        if (-not $ret) {
                            foreach ($l2 in $ls) {
                                if ($l2 -match "([\w\.]+)\s+$fieldName\s*[=;]") { $ret = $Matches[1]; break }
                            }
                        }
                        break
                    }
                }
            }
            break
        }
    }
    if (-not $ret) { $bad += [pscustomobject]@{ File = $f.Name; Why = 'no initTerrain return found' }; continue }

    # resolve qualified name: A.B -> class B declared in A.java
    if ($ret -match '\.') {
        $parts = $ret -split '\.'
        $simple = $parts[-1]
        $hostSimple = $parts[-2]
        $hostFile = $null
        foreach ($cand in $allJava) { if ([System.IO.Path]::GetFileNameWithoutExtension($cand) -eq $hostSimple) { $hostFile = $cand; break } }
        if (-not $hostFile) { $hostFile = $decl[$simple] }
    } else {
        $simple = $ret
        $hostFile = $f.FullName
        if (-not ([regex]::IsMatch($text[$f.FullName], "class\s+$simple\s+extends"))) { $hostFile = $decl[$simple] }
    }
    if (-not $hostFile) { $bad += [pscustomobject]@{ File = $f.Name; Why = "class $simple not found" }; continue }

    $body = Get-Body $hostFile $simple
    if (-not $body) { $bad += [pscustomobject]@{ File = $f.Name; Why = "class $simple body not parsed (in $([System.IO.Path]::GetFileName($hostFile)))" }; continue }

    $joined = ($body -join "`n")
    $calls = [regex]::Matches($joined, '\b(terrain[A-Z]\w*)\s*\(') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    # ignore comments-only mentions
    if ($calls.Count -eq 0) {
        $bad += [pscustomobject]@{ File = $f.Name; Why = "$simple -> NO terrainXxx() call" }
    } else {
        $okCount++
    }
}

Write-Output ("checked {0} biome files; {1} route into an RWG terrain function" -f $files.Count, $okCount)
Write-Output ''
Write-Output '=== NOT routing into RWG terrain ==='
foreach ($b in $bad) { Write-Output ("  {0,-46} {1}" -f $b.File, $b.Why) }
if ($bad.Count -eq 0) { Write-Output '  (none)' }
