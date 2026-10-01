param(
    [Parameter(Mandatory = $true)][string]$RwgRoot,
    [Parameter(Mandatory = $true)][string]$RtgcBiomeRoot,
    [Parameter(Mandatory = $true)][string]$TerrainBaseFile
)

# Finds rtgc biome terrain calls whose numeric argument tuple does NOT appear as a
# `new TerrainXxx(...)` anywhere in RWG. Such a tuple is a *candidate* for "invented
# parameters" (A3: RWG-has-no-counterpart mappings must at least be marked inferred).
#
# This is how the VanillaDesertHills/DesertM `GrasslandHills(70f, 200f, 7f, ...)` was found:
# RWG only ever writes (70f, 180f, ...) for steppe/thicket, and both are commented out.
#
# Pure ASCII on purpose: PowerShell 5.1 reads BOM-less .ps1 as ANSI.

function Normalize-Nums([string]$s) {
    # keep only numbers, drop 'f'/'F' suffix and whitespace;
    # RWG writes `.3f` while rtgc writes `0.3f`, so normalise the leading dot away --
    # otherwise identical tuples get reported as mismatches.
    $nums = [regex]::Matches($s, '\d*\.?\d+') | ForEach-Object {
        if ($_.Value.StartsWith('.')) { '0' + $_.Value } else { $_.Value }
    }
    return ($nums -join ',')
}

# ---- 1. RWG side: expand 3-arg TerrainHilly/TerrainGrasslandHills etc. via their ctors ----
# RWG ctors:  TerrainHilly(w,s,lake) -> (w,s,lake,260f,68f)
#             TerrainGrasslandHills(a,b,c,d,e,f,g) has no 3-arg form
$rwgTuples = @{}
foreach ($f in (Get-ChildItem $RwgRoot -Recurse -Include *.java)) {
    $txt = [System.IO.File]::ReadAllText($f.FullName)
    foreach ($m in [regex]::Matches($txt, 'new\s+(Terrain\w+)\s*\(([^;]*?)\)')) {
        $cls = $m.Groups[1].Value
        $args = $m.Groups[2].Value
        if ($args -match '[A-Za-z_]\w*\s*[\.\)]' -or $args -match '^\s*$') { continue }  # skip non-literal
        $key = $cls + '(' + (Normalize-Nums $args) + ')'
        $rwgTuples[$key] = $true
        # TerrainHilly 3-arg constructor delegates to the 5-arg one with 260f/68f
        if ($cls -eq 'TerrainHilly' -and (Normalize-Nums $args).Split(',').Count -eq 3) {
            $rwgTuples['TerrainHilly(' + (Normalize-Nums $args) + ',260,68)'] = $true
        }
    }
}

Write-Output ("RWG terrain tuples collected: {0}" -f $rwgTuples.Count)

# ---- 2. rtgc side: map terrainXxx -> TerrainXxx, compare tuples ----
$rtgcTuples = @{}
foreach ($f in (Get-ChildItem $RtgcBiomeRoot -Recurse -Include 'RealisticBiome*.java')) {
    $ls = [System.IO.File]::ReadAllLines($f.FullName, [System.Text.Encoding]::UTF8)
    for ($i = 0; $i -lt $ls.Count; $i++) {
        if ($ls[$i] -match '^\s*(//|\*)') { continue }
        foreach ($m in [regex]::Matches($ls[$i], '\bterrain([A-Z]\w*)\s*\(([^;]*?)\)\s*;')) {
            $fn = $m.Groups[1].Value
            $args = $m.Groups[2].Value
            $cls = 'Terrain' + $fn
            # drop the first four params (x, y, rtgWorld, river)
            $parts = $args -split ','
            if ($parts.Count -le 4) { continue }
            $rest = ($parts[4..($parts.Count - 1)] -join ',')
            # strip the f/F/d/D numeric suffixes BEFORE looking for identifiers,
            # otherwise the 'f' in `180f` matches [A-Za-z_] and everything looks non-literal.
            $restBare = $rest -replace '(\d)[fFdD]\b', '$1'
            if ($restBare -match '[A-Za-z_]') {
                $rtgcTuples[($cls + '  [NON-LITERAL]  ' + $rest.Trim() + '  @' + $f.Name + ':' + ($i + 1))] = $true
                continue
            }
            $key = $cls + '(' + (Normalize-Nums $rest) + ')'
            $rtgcTuples[($key + '  @' + $f.Name + ':' + ($i + 1))] = $true
        }
    }
}

Write-Output ("rtgc terrain calls collected: {0}" -f $rtgcTuples.Count)
Write-Output ''
Write-Output '=== rtgc tuples with NO matching `new TerrainXxx(...)` in RWG ==='
$noMatch = 0
foreach ($k in ($rtgcTuples.Keys | Sort-Object)) {
    $bare = ($k -split '  @')[0].Trim()
    $match = $rwgTuples.ContainsKey($bare)
    if (-not $match) {
        $noMatch++
        Write-Output ("  {0}" -f $k)
    }
}
Write-Output ("  total: {0}" -f $noMatch)
