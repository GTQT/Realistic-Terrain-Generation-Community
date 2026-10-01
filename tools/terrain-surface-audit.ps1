param([string]$Root = (Split-Path -Parent $PSScriptRoot))

# Self-check: does every realistic biome's (terrain, surface) pair match its own climate?
#
# Why this tool exists: RealisticBiomeBOPWhiteBeach (BOP white_beach, in-game temperature 1.00)
# had been wired to RWG's COLD coast recipe (terrainCoastIce + SurfaceGrassland(packed_ice,ice)),
# i.e. a tropical biome whose ground was packed ice. RWG's own dispatch rule is
# ChunkManagerRealistic:590-593 -> temperature < 0.15f ? coastIce : coastDunes.
# The old checkers only asked "is it a shared surface class" / "does it route into an RWG terrain
# function", so a *wrong but shared* pairing stayed invisible. This tool compares the palette of
# the surface (and its family) against the biome's own temperature/rainfall/tags, taken from the
# debugLayout output of the last client run.
#
# Output: docs/_terrain_surface_audit.csv (full table) + flagged rows on stdout.
#
# Dependency: build/mcsrc/net/minecraft/init/Biomes.java (the decompiled vanilla source that maps
# field names like MESA_BRYCE to registry paths like mutated_mesa). If it is missing the tool still
# runs, but the vanilla biomes fall back to class-name-derived keys and fewer rows get checked.
# Regenerate it from build/rfg/mcp_patched_minecraft-sources.jar if needed.

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- 1. climate table from the log
$climate = @{}
$logPath = Join-Path $Root 'run\logs\latest.log'
if (Test-Path $logPath) {
    $logText = [System.IO.File]::ReadAllText($logPath)
    # The log line is:  [RTG]   [<CJK>] COLD   plains      temp=0.80 rain=0.40 <CJK>=--
    # NOTE: this .ps1 must stay pure ASCII (PowerShell 5.1 reads BOM-less .ps1 as ANSI, so CJK
    # literals would be mangled) -> the bracket glyph and the tag label are matched structurally.
    foreach ($m in [regex]::Matches($logText,
            '(?m)\[RTG\]\s+\[\S+\]\s+(\w+)\s+(\S+)\s+temp=(-?[\d.]+) rain=(-?[\d.]+)\s+\S+=(\S+)\s*$')) {
        $climate[$m.Groups[2].Value] = [pscustomobject]@{
            Climate = $m.Groups[1].Value
            Temp    = [double]$m.Groups[3].Value
            Rain    = [double]$m.Groups[4].Value
            Tags    = $m.Groups[5].Value
        }
    }
}

# ------------------------------------------- 2. registry field -> registry path (vanilla biomes)
$vanillaField = @{}
$biomesSrc = Join-Path $Root 'build\mcsrc\net\minecraft\init\Biomes.java'
if (Test-Path $biomesSrc) {
    foreach ($m in [regex]::Matches([System.IO.File]::ReadAllText($biomesSrc),
            '(?m)^\s+(\w+)\s*=\s*getRegisteredBiome\("([\w_]+)"\)')) {
        $vanillaField[$m.Groups[1].Value] = $m.Groups[2].Value
    }
}

# ------------------------------- 3. BOP / TC class -> resource path, from BiomeInit registrations
$modClassPath = @{}
$biomeInit = Join-Path $Root 'src\main\java\rtg\init\BiomeInit.java'
if (Test-Path $biomeInit) {
    $lastPath = ''
    foreach ($line in [System.IO.File]::ReadAllLines($biomeInit)) {
        $m1 = [regex]::Match($line, 'getResourceLocation\("([\w_]+)"\)')
        if ($m1.Success) { $lastPath = $m1.Groups[1].Value }
        $m2 = [regex]::Match($line, 'new\s+(RealisticBiome\w+)\s*\(')
        if ($m2.Success -and $lastPath -ne '') {
            $modClassPath[$m2.Groups[1].Value] = $lastPath
            $lastPath = ''
        }
    }
}

# ------------------------------------------------------- 4. palette / family classification
function Get-Palette([string]$surfaceClass, [string]$surfaceArgs) {
    if ($surfaceClass -eq 'SurfaceCoastIce') { return 'ICE' }
    if ($surfaceClass -eq 'SurfacePolar' -or $surfaceClass -eq 'SurfaceMountainPolar') { return 'ICE' }
    if ($surfaceClass -match '^SurfaceDesert|^SurfaceMesa|^SurfaceRedDesert') { return 'DESERT' }
    if ($surfaceClass -eq 'SurfaceCoastDunes') { return 'SAND' }
    if ($surfaceClass -eq 'SurfaceOcean') {
        if ($surfaceArgs -match '\bfalse\b') { return 'GRAVEL' }
        return 'SAND'
    }
    # Generic surfaces: judge from the FIRST TWO arguments (top / filler) only.
    # Trailing args are mix/beach/cliff parameters (e.g. SurfaceMountainSnow's `Blocks.SAND` is
    # only painted near water level), so they must not decide the palette.
    $parts = $surfaceArgs -split ','
    $head = ''
    if ($parts.Count -ge 1) { $head = $parts[0] }
    if ($parts.Count -ge 2) { $head = "$head, $($parts[1])" }
    if ($head -match 'baseBiome\(\)|\bbase\b|topBlock|fillerBlock') { return 'BIOME' }
    if ($head -match 'PACKED_ICE|Blocks\.ICE|SNOW_LAYER|Blocks\.SNOW') { return 'ICE' }
    if ($head -match 'HARDENED_CLAY|getStateClay|CLAY') { return 'DESERT' }
    if ($head -match '\bSAND\b|SANDSTONE') { return 'SAND' }
    if ($head -match '\bGRASS\b|\bDIRT\b') { return 'GRASS' }
    return 'OTHER'
}

function Get-TerrainFamily([string]$terrain) {
    if ($terrain -match '^terrainOcean$') { return 'OCEAN' }
    if ($terrain -match '^terrainCoastDunes$') { return 'COAST_WARM' }
    if ($terrain -match '^terrainCoastIce$') { return 'COAST_COLD' }
    if ($terrain -match '^terrainIsland|^terrainSmallIsland$') { return 'ISLAND' }
    if ($terrain -match '^terrainPolar$') { return 'POLAR' }
    if ($terrain -eq '') { return 'NONE' }
    return 'LAND'
}

function Get-SurfaceFamily([string]$surfaceClass) {
    if ($surfaceClass -eq 'SurfaceOcean') { return 'OCEAN' }
    if ($surfaceClass -eq 'SurfaceCoastDunes') { return 'COAST_WARM' }
    if ($surfaceClass -eq 'SurfaceCoastIce') { return 'COAST_COLD' }
    if ($surfaceClass -eq 'SurfaceIslandMountainStone') { return 'ISLAND' }
    if ($surfaceClass -match '^SurfacePolar|^SurfaceMountainPolar$') { return 'POLAR' }
    return 'LAND'
}

# ------------------------------------------------------------------------------ 5. walk the files
$rows = @()
foreach ($d in Get-ChildItem (Join-Path $Root 'src\main\java\rtg\world\biome\realistic') -Directory) {
    foreach ($f in Get-ChildItem $d.FullName -Filter 'RealisticBiome*.java') {
        $raw = [System.IO.File]::ReadAllText($f.FullName)
        $code = [regex]::Replace($raw, '/\*.*?\*/', '', 'Singleline')
        $code = [regex]::Replace($code, '//[^\r\n]*', '')

        # --- surface class + args (comments stripped: javadoc quotes RWG source lines) ---
        $surfaceClass = ''; $surfaceArgs = ''
        $si = $code.IndexOf('public SurfaceBase initSurface()')
        if ($si -ge 0) {
            $bo = $code.IndexOf('{', $si)
            $depth = 0; $j = $bo
            while ($j -lt $code.Length) {
                $ch = $code[$j]
                if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { break } }
                $j++
            }
            $body = $code.Substring($bo + 1, $j - $bo - 1)
            $sm = [regex]::Match($body, 'new\s+(\w+)\s*\(')
            if ($sm.Success) {
                $surfaceClass = $sm.Groups[1].Value
                $open = $body.IndexOf('(', $sm.Index)
                $d2 = 0; $k = $open
                while ($k -lt $body.Length) {
                    $ch = $body[$k]
                    if ($ch -eq '(') { $d2++ } elseif ($ch -eq ')') { $d2--; if ($d2 -eq 0) { break } }
                    $k++
                }
                $surfaceArgs = ($body.Substring($open + 1, $k - $open - 1) -replace '\s+', ' ').Trim()
            }
        }

        # --- terrain function(s) actually called ---
        $terr = @()
        foreach ($tm in [regex]::Matches($code, '\b(terrain[A-Z]\w*)\s*\(')) {
            if ($terr -notcontains $tm.Groups[1].Value) { $terr += $tm.Groups[1].Value }
        }
        $terrain = ($terr | Sort-Object) -join '+'
        # initTerrain() may hand off to another class' inner terrain (e.g. the "M" variants reuse
        # the base biome's terrain) -> record that reference instead of reporting "no terrain".
        $sharedRef = ''
        $ti = $code.IndexOf('public TerrainBase initTerrain()')
        if ($ti -ge 0) {
            $tbo = $code.IndexOf('{', $ti)
            $tDepth = 0; $tj = $tbo
            while ($tj -lt $code.Length) {
                $ch = $code[$tj]
                if ($ch -eq '{') { $tDepth++ } elseif ($ch -eq '}') { $tDepth--; if ($tDepth -eq 0) { break } }
                $tj++
            }
            $tbody = $code.Substring($tbo + 1, $tj - $tbo - 1)
            $sr = [regex]::Match($tbody, 'new\s+(\w+)\.(\w+)\s*\(')
            if ($sr.Success) { $sharedRef = "$($sr.Groups[1].Value).$($sr.Groups[2].Value)" }
        }
        if ($terrain -eq '' -and $sharedRef -ne '') { $terrain = "shared:$sharedRef" }

        # --- registry key ---
        $key = ''
        $vm = [regex]::Match($code, 'public\s+static\s+Biome\s+biome\s*=\s*Biomes\.(\w+)')
        if (-not $vm.Success) { $vm = [regex]::Match($code, 'super\(\s*Biomes\.(\w+)') }
        if ($vm.Success -and $vanillaField.ContainsKey($vm.Groups[1].Value)) {
            $key = $vanillaField[$vm.Groups[1].Value]
        } elseif ($modClassPath.ContainsKey($f.BaseName)) {
            $key = $modClassPath[$f.BaseName]
        } else {
            # fall back: class name suffix, underscores removed, lower case
            $suffix = $f.BaseName -replace '^RealisticBiome(Vanilla|BOP|TC|EBXL)', ''
            $key = $suffix.ToLower()
        }

        $cl = $climate[$key]                                  # may be $null (ocean/river biomes have no [类] line)
        $palette = Get-Palette $surfaceClass $surfaceArgs
        $tFamily = Get-TerrainFamily $terrain
        $sFamily = Get-SurfaceFamily $surfaceClass

        $flags = @()
        if ($cl) {
            $temp = $cl.Temp
            $snowy = ($cl.Tags -match 'SNOWY') -or ($cl.Climate -eq 'SNOW')
            if ($palette -eq 'ICE' -and $temp -ge 0.5) { $flags += 'ICE-ON-WARM(temp>=0.5)' }
            elseif ($palette -eq 'ICE' -and $temp -ge 0.3) { $flags += 'ICE-ON-MILD(temp>=0.3)' }
            if (($palette -eq 'SAND' -or $palette -eq 'DESERT') -and $temp -le 0.15 -and $snowy) { $flags += 'SAND-ON-SNOW' }
        }
        # Coast / ocean families must agree (this is the class of bug that put the ice-coast surface
        # on a tropical beach). ISLAND / POLAR are informational only - RWG itself pairs
        # TerrainSmallIsland with SurfaceGrassland (Support.java:104-115).
        $special = @('OCEAN', 'COAST_WARM', 'COAST_COLD')
        if (($special -contains $tFamily) -or ($special -contains $sFamily)) {
            if ($tFamily -ne $sFamily) { $flags += "FAMILY($tFamily terrain / $sFamily surface)" }
        }
        if ($flags.Count -eq 0 -and $tFamily -eq 'POLAR' -and $palette -eq 'DESERT') {
            $flags += 'KNOWN-DEVIATION(terrainPolar used as dune recipe; see docs section 20)'
        }

        $rows += [pscustomobject]@{
            File         = $f.BaseName
            Key          = $key
            Climate      = if ($cl) { $cl.Climate } else { '' }
            Temp         = if ($cl) { $cl.Temp } else { '' }
            Rain         = if ($cl) { $cl.Rain } else { '' }
            Tags         = if ($cl) { $cl.Tags } else { '' }
            Terrain      = $terrain
            TerrainFam   = $tFamily
            Surface      = $surfaceClass
            SurfaceFam   = $sFamily
            Palette      = $palette
            SurfaceArgs  = $surfaceArgs
            Flags        = ($flags -join '; ')
        }
    }
}

$rows = $rows | Sort-Object File
$rows | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $Root 'docs\_terrain_surface_audit.csv')

Write-Output ("audited files        : {0}" -f $rows.Count)
Write-Output ("with climate data    : {0}" -f ($rows | Where-Object { $_.Climate -ne '' }).Count)
Write-Output ("without climate data : {0}" -f ($rows | Where-Object { $_.Climate -eq '' }).Count)
Write-Output ''
Write-Output '--- palette distribution ---'
$rows | Group-Object Palette | Sort-Object Count -Descending | ForEach-Object { Write-Output ("  {0,-8} {1}" -f $_.Name, $_.Count) }
Write-Output ''
Write-Output '--- FLAGGED ---'
$bad = $rows | Where-Object { $_.Flags -ne '' }
if ($bad.Count -eq 0) { Write-Output '  (none)' }
foreach ($r in $bad) {
    Write-Output ("  {0,-42} {1,-16} temp={2,-5} {3,-10} {4,-22} {5}" -f `
        $r.File, $r.Key, $r.Temp, $r.Terrain, $r.Surface, $r.Flags)
}
Write-Output ''
Write-Output '--- no climate line (ocean/river biomes are expected here) ---'
$rows | Where-Object { $_.Climate -eq '' } | ForEach-Object {
    Write-Output ("  {0,-42} key={1,-18} {2,-20} {3}" -f $_.File, $_.Key, $_.Terrain, $_.Surface)
}

# --------------------------------------------------------------------------------------------
# 6. cross-check BOTH halves (terrain + surface) against RWG's own registrations
#    docs/_rwg_support_map.csv is extracted from rwg/support/Support*.java with comments stripped
#    (commented-out registrations are NOT in effect and must not count as authority).
# --------------------------------------------------------------------------------------------
$mapPath = Join-Path $Root 'docs\_rwg_support_map.csv'
if (Test-Path $mapPath) {
    $byKey = @{}
    foreach ($r in $rows) {
        $k = ($r.Key -replace '_', '').ToLower()
        if (-not $byKey.ContainsKey($k)) { $byKey[$k] = $r }
    }
    $matched = 0; $pairDiff = @()
    # RWG resolves some vanilla biomes by arithmetic (BiomeGenBase.getBiome(x.biomeID + 128)) and
    # names the local variable after the 1.7.10 field, so the map key does not equal the 1.12
    # registry path. Manual alias table (only entries that actually exist in rtgc):
    #   icePlainsSpikes  = mutated_ice_flats (ice plains + 128)
    #   sunflowerPlains  = mutated_plains    (plains + 128)
    #   megaSpruceTaiga  = mutated_redwood_taiga
    $keyAlias = @{
        'iceplainsspikes' = 'mutatediceflats'
        'sunflowerplains' = 'mutatedplains'
        'megasprucetaiga' = 'mutatedredwoodtaiga'
    }
    # group by key: RWG registers some biomes more than once (e.g. brushland: HOT grassland-hills
    # entry + OASIS dune-valley entry). Matching ANY of them is a pass; only report when none match.
    $mapByKey = @{}
    foreach ($m in Import-Csv $mapPath) {
        $last = ($m.Biome.Trim() -split '\.')[-1]
        $k = ($last -replace '_', '').ToLower()
        if ($keyAlias.ContainsKey($k)) { $k = $keyAlias[$k] }
        if (-not $mapByKey.ContainsKey($k)) { $mapByKey[$k] = @() }
        $mapByKey[$k] += $m
    }
    foreach ($k in $mapByKey.Keys) {
        if (-not $byKey.ContainsKey($k)) { continue }
        $matched++
        $r = $byKey[$k]
        # rtgc names its ports terrainXxx; RWG uses TerrainXxx
        $codeTerr = $r.Terrain
        if ($codeTerr -match '^terrain(\w+)$') { $codeTerr = 'Terrain' + $Matches[1] }
        $ok = $false
        foreach ($m in $mapByKey[$k]) {
            $terrOk = ($m.Terrain -eq '') -or ($codeTerr -eq $m.Terrain) -or ($codeTerr -like 'shared:*')
            $surfOk = ($r.Surface -eq $m.Surface)
            if ($terrOk -and $surfOk) { $ok = $true; break }
        }
        if (-not $ok) {
            $alt = @()
            foreach ($m in $mapByKey[$k]) { $alt += ("{0} + {1}" -f $m.Terrain, $m.Surface) }
            $pairDiff += [pscustomobject]@{
                File     = $r.File
                Key      = $k
                RwgPair  = ($alt -join '  |  ')
                CodePair = ("{0} + {1}" -f $codeTerr, $r.Surface)
            }
        }
    }
    Write-Output ''
    Write-Output ("--- RWG Support*.java pairing check: matched={0}, mismatched={1} ---" -f $matched, $pairDiff.Count)
    foreach ($d in $pairDiff) {
        Write-Output ("  {0,-40} key={1}" -f $d.File, $d.Key)
        Write-Output ("      RWG : {0}" -f $d.RwgPair)
        Write-Output ("      code: {0}" -f $d.CodePair)
    }
}
