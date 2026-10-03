param(
    # Path to RWG's rwg/support directory (contains Support*.java).
    [Parameter(Mandatory = $true)][string]$RwgSupportDir,
    # Path to rtgc's RtgBiomeCategorizer.java (contains the RWG_PLACEMENTS table).
    [Parameter(Mandatory = $true)][string]$TableFile,
    # Rows that legitimately have no RWG source, as "name|reason" pairs. Anything not
    # listed here and not found in RWG is treated as an INVENTED placement -> exit 1.
    [string[]]$RtgcSpecific = @()
)

# Cross-checks RtgBiomeCategorizer.RWG_PLACEMENTS against RWG's Support*.java.
#
# Why this exists: the whole point of the table is that it is COPIED, not inferred.
# A careful transcription is not evidence; a mechanical match is. This tool fails
# loudly when a table row has no counterpart in RWG, so "I made it up" cannot pass
# review silently.
#
# This file must stay pure ASCII: Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI
# and mangles any non-ASCII literal, which breaks the parser.

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- RWG side ----
function Get-RwgRows {
    param([string]$Dir)

    $rows = @()
    foreach ($f in (Get-ChildItem $Dir -Filter 'Support*.java')) {
        $ls = [System.IO.File]::ReadAllLines($f.FullName, [System.Text.Encoding]::UTF8)
        for ($i = 0; $i -lt $ls.Count; $i++) {
            if ($ls[$i] -notmatch 'BiomePlacement\.(\w+)') { continue }
            $placement = $Matches[1]

            # Walk back to decide whether this is a real addBiome(...) call.
            # Support.listFor(...) also mentions every placement constant; those lines
            # are NOT registrations and must be ignored.
            $isCall = $false
            $callStart = -1
            for ($j = $i; $j -ge [Math]::Max(0, $i - 40); $j--) {
                if ($ls[$j] -match 'listFor\s*\(') { break }
                if ($ls[$j] -match 'addBiome\s*\(') { $isCall = $true; $callStart = $j; break }
            }
            if (-not $isCall) { continue }

            # Biome token: nearest one between the addBiome( we found and this line.
            $biome = $null
            for ($j = $i; $j -ge $callStart; $j--) {
                if ($ls[$j] -match '(BOPCBiomes|RWGBiomes|BiomeGenBase)\.(\w+)') {
                    # `RWGBiomes.baseRiverXxx` is the RIVER argument, not the biome -> ignore it,
                    # otherwise every entry whose biome arg is an array element (b[i]) looks like
                    # a registration of baseRiverCold / baseRiverWet.
                    if ($Matches[2] -notmatch '^baseRiver') { $biome = $Matches[2]; break }
                }
                # Support.java builds a couple of biomes into locals first.
                if ($ls[$j] -match '\b(icePlainsSpikes|sunflowerPlains)\b') {
                    $biome = $Matches[1]; break
                }
            }
            if (-not $biome) {
                # SupportTC resolves Thaumcraft biomes by DISPLAY NAME, so the addBiome() body
                # contains no biome token at all:
                #     if ("Magical Forest".equals(b[i].biomeName)) { ... new RealisticBiomeSupport(b[i], ...)
                # Look just above the addBiome( call for that literal and use it as the key.
                for ($j = $callStart; $j -ge [Math]::Max(0, $callStart - 8); $j--) {
                    if ($ls[$j] -match '"([^"]+)"\s*\.equals\s*\(') {
                        $biome = $Matches[1]; break
                    }
                }
            }
            if (-not $biome) { continue }

            # Category: nearest BiomeCategory.X between the addBiome( and this line.
            $category = '?'
            for ($j = $i; $j -ge $callStart; $j--) {
                if ($ls[$j] -match 'BiomeCategory\.(\w+)') { $category = $Matches[1]; break }
            }

            $rows += [pscustomobject]@{
                Biome     = $biome
                Category  = $category
                Placement = $placement
                File      = $f.BaseName
                Line      = $i + 1
            }
        }
    }
    return $rows
}

# RWG names -> the normalized key used by the Java table (registry path, lower case,
# underscores removed). Only the two Support.java locals need a rename, because the
# local name is not the biome name; everything else is just case/underscore folding.
function ConvertTo-Key {
    param([string]$Biome)
    switch ($Biome) {
        'icePlainsSpikes' { return 'mutatediceflats' }   # Biomes.MUTATED_ICE_FLATS
        'sunflowerPlains' { return 'mutatedplains' }     # Biomes.MUTATED_PLAINS
        # SupportTC keys are DISPLAY names ("Magical Forest"), so spaces must go too.
        default { return (($Biome -replace '_', '') -replace '\s', '').ToLowerInvariant() }
    }
}

# --------------------------------------------------------------- Java side ----
function Get-TableRows {
    param([string]$Path)
    $txt = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $rows = @()
    foreach ($m in [regex]::Matches($txt, 'new\s+RwgPlace\(\s*"([^"]+)"\s*,\s*Climate\.(\w+)\s*,\s*Placement\.(\w+)\s*\)')) {
        $rows += [pscustomobject]@{
            Name      = $m.Groups[1].Value
            Climate   = $m.Groups[2].Value
            Placement = $m.Groups[3].Value
        }
    }
    return $rows
}

# ------------------------------------------------------------------- check ----
$rwg = Get-RwgRows -Dir $RwgSupportDir
$table = Get-TableRows -Path $TableFile

if ($table.Count -eq 0) {
    Write-Output 'FAIL: no RwgPlace rows parsed from the table file (regex drift?)'
    exit 2
}

# name -> set of "CATEGORY/PLACEMENT"
$rwgIndex = @{}
foreach ($r in $rwg) {
    $k = (ConvertTo-Key $r.Biome)
    $v = "$($r.Category)/$($r.Placement)"
    if (-not $rwgIndex.ContainsKey($k)) { $rwgIndex[$k] = New-Object System.Collections.Generic.List[string] }
    if (-not $rwgIndex[$k].Contains($v)) { $rwgIndex[$k].Add($v) }
}

$exceptions = @{}
foreach ($e in $RtgcSpecific) {
    $parts = $e -split '\|', 2
    $exceptions[$parts[0]] = $parts[1]
}

Write-Output ("RWG registration rows parsed : {0}" -f $rwg.Count)
Write-Output ("Java table rows parsed       : {0}" -f $table.Count)
Write-Output ''

$invented = @()
$mismatch = @()
$okCount = 0

foreach ($t in $table) {
    $key = $t.Name
    if ($exceptions.ContainsKey($key)) {
        Write-Output ("  [rtgc-only ] {0,-22} {1}/{2}  <- {3}" -f $key, $t.Climate, $t.Placement, $exceptions[$key])
        continue
    }
    if (-not $rwgIndex.ContainsKey($key)) {
        $invented += $key
        continue
    }
    $want = "$($t.Climate)/$($t.Placement)"
    if ($rwgIndex[$key].Contains($want)) {
        $okCount++
        Write-Output ("  [copied    ] {0,-22} {1}/{2}" -f $key, $t.Climate, $t.Placement)
    } else {
        $mismatch += ("{0}: table says {1}, RWG says {2}" -f $key, $want, ($rwgIndex[$key] -join ','))
    }
}

Write-Output ''
Write-Output ("copied from RWG : {0}" -f $okCount)
Write-Output ("rtgc-specific   : {0}" -f $exceptions.Count)

# RWG entries with no table row = known gaps (rtgc has no such biome). Informational.
Write-Output ''
Write-Output '--- RWG entries with no table row (expected gaps, listed for the record) ---'
$gap = 0
foreach ($k in ($rwgIndex.Keys | Sort-Object)) {
    $has = $false
    foreach ($t in $table) { if ($t.Name -eq $k) { $has = $true; break } }
    if (-not $has) {
        Write-Output ("  {0,-24} {1}" -f $k, ($rwgIndex[$k] -join ','))
        $gap++
    }
}
Write-Output ("  total gaps: {0}" -f $gap)

Write-Output ''
if ($invented.Count -gt 0) {
    Write-Output ("FAIL: table rows with NO RWG source (invented): {0}" -f ($invented -join ', '))
}
if ($mismatch.Count -gt 0) {
    foreach ($m in $mismatch) { Write-Output ("FAIL: {0}" -f $m) }
}
if ($invented.Count -gt 0 -or $mismatch.Count -gt 0) { exit 1 }

Write-Output 'PASS: every RwgPlace row is backed by an RWG Support*.java registration.'
exit 0
