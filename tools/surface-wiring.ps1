param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [switch]$Apply
)

# Wire rtgc per-biome surfaces to the ported RWG shared surface classes.
# Mapping source: docs/_rwg_support_map.csv (extracted from RWG support/Support*.java).
# Key: rtgc file RealisticBiome<Mod><Name>.java  <->  RWG <ModCBiomes>.<name>
#
# !! HISTORICAL ONE-SHOT TOOL !! The migration it performs is DONE (see tools/surface-wiring-check.ps1,
# which now also enforces the coast/ocean-surface rule). Running this script with -Apply rewrites every
# initSurface() body from its rule table, so it can silently undo hand-written wiring; it is kept
# for reference / for a future surface extraction round. Without -Apply it only refreshes
# docs/_surface_wiring_report.csv.
#
# The RULE: rows are a TERRAIN-KEYED GUESS and are NOT authority: docs/_rwg_support_map.csv (RWG
# Support*.java, comments stripped) is. For the ~41 biomes that have an RWG entry, cross-check with
# tools/terrain-surface-audit.ps1 BEFORE re-applying anything (that tool found 3 biomes whose
# terrain/surface pair was RWG's, but not the pair RWG assigned to them).
#
# Known trap (fixed here): rule regexes were matched against the RAW file text, so a rule word
# appearing inside a comment (e.g. "terrainFlatLakes" in a javadoc sentence) selected the wrong
# surface. Comments are now stripped before rule matching.

$ErrorActionPreference = 'Stop'
$mapPath = Join-Path $Root 'docs\_rwg_support_map.csv'
$enc = New-Object System.Text.UTF8Encoding($false)

$rows = Import-Csv -Path $mapPath
$byKey = @{}
foreach ($r in $rows) {
    $biomeExpr = $r.Biome.Trim()
    $last = ($biomeExpr -split '\.')[-1]
    if ($last.Length -eq 0) { continue }
    $key = $last.Substring(0, 1).ToLower() + $last.Substring(1)
    # first row wins (duplicates are variant biomes sharing a name)
    if (-not $byKey.ContainsKey($key)) { $byKey[$key] = $r }
}

$blockMap = @{
    'stone'                 = 'Blocks.STONE.getDefaultState()'
    'cobblestone'           = 'Blocks.COBBLESTONE.getDefaultState()'
    'sand'                  = 'Blocks.SAND.getDefaultState()'
    'sandstone'             = 'Blocks.SANDSTONE.getDefaultState()'
    'dirt'                  = 'Blocks.DIRT.getDefaultState()'
    'grass'                 = 'Blocks.GRASS.getDefaultState()'
    'snow'                  = 'Blocks.SNOW.getDefaultState()'
    'ice'                   = 'Blocks.ICE.getDefaultState()'
    'packed_ice'            = 'Blocks.PACKED_ICE.getDefaultState()'
    'gravel'                = 'Blocks.GRAVEL.getDefaultState()'
    'clay'                  = 'Blocks.CLAY.getDefaultState()'
    'hardened_clay'         = 'Blocks.HARDENED_CLAY.getDefaultState()'
}

function Split-Args([string]$s) {
    $out = @()
    $depth = 0
    $cur = ''
    foreach ($ch in $s.ToCharArray()) {
        if ($ch -eq '(' -or $ch -eq '{') { $depth++ }
        elseif ($ch -eq ')' -or $ch -eq '}') { $depth-- }
        if ($ch -eq ',' -and $depth -eq 0) { $out += $cur.Trim(); $cur = ''; continue }
        $cur += $ch
    }
    if ($cur.Trim().Length -gt 0) { $out += $cur.Trim() }
    return $out
}

function Convert-Arg([string]$a, [ref]$clayMeta) {
    $a = $a.Trim()
    if ($a -match '^[\w.]+\.topBlock$')    { return 'baseBiome().topBlock' }
    if ($a -match '^[\w.]+\.fillerBlock$') { return 'baseBiome().fillerBlock' }
    if ($a -eq 'null') { return 'null' }
    if ($a -eq 'true' -or $a -eq 'false') { return $a }
    if ($a -match '^\(byte\)\s*(-?\d+)$') { return "(byte) $($matches[1])" }
    if ($a -match '^-?\d+(\.\d+)?f?$') { return $a }
    if ($a -match '^Blocks\.(\w+)$') {
        $n = $matches[1]
        if ($n -eq 'stained_hardened_clay') {
            $m = $clayMeta.Value
            return "BlockUtil.getStateClay(EnumDyeColor.byMetadata($m))"
        }
        if ($blockMap.ContainsKey($n)) { return $blockMap[$n] }
        return "Blocks.$($n.ToUpper()).getDefaultState()"
    }
    # unknown -> keep verbatim (will surface as a compile error for review)
    return $a
}

function Build-Ctor([string]$surfaceClass, [string]$argText) {
    $args = Split-Args $argText
    if ($args.Count -eq 0) { return "new $surfaceClass(getConfig())" }
    $clay = 0
    $conv = @()
    for ($i = 0; $i -lt $args.Count; $i++) {
        if ($args[$i] -match 'stained_hardened_clay' -and ($i + 1) -lt $args.Count -and $args[$i+1] -match '^\d+$') {
            $clay = [int]$args[$i+1]
        }
        $conv += (Convert-Arg $args[$i] ([ref]$clay))
    }
    # first two converted args are top / filler
    $top = $conv[0]; $fill = if ($conv.Count -gt 1) { $conv[1] } else { $conv[0] }
    $rest = @()
    if ($conv.Count -gt 2) { $rest = $conv[2..($conv.Count-1)] }
    $all = @("getConfig()", $top, $fill) + $rest
    return "new $surfaceClass(" + ($all -join ', ') + ")"
}

$report = @()

# For biomes with no direct Support*.java entry (vanilla MC biomes: RWG has no counterpart,
# they map to RWG's OWN custom biomes). Rule derived from the terrain class that RWG pairs
# with each surface -- see docs/rwg-port-gaps.md. Ordered: first match wins.
$rules = @(
    @{ Rx='terrainHilly\([^)]*150f, 50f'; Surface='SurfaceDesertMountain'; Args='@top, @fill, false, null, 0f, 1.5f, 60f, 65f, 1.5f' },
    @{ Rx='terrainHilly\([^)]*120f, 50f'; Surface='SurfaceMountainStone';  Args='@top, @fill, false, null, 1f, 1.5f, 60f, 65f, 1.5f' },
    @{ Rx='terrainHilly\([^)]*120f, 0f, 260f, 120f'; Surface='SurfaceMountainStoneMix1'; Args='@top, @fill, false, null, 0f, 1.5f, 60f, 65f, 1.5f, Blocks.STONE.getDefaultState(), 0.20f' },
    @{ Rx='terrainMountainRiver';         Surface='SurfaceMountainSnow';      Args='@top, @fill, true, Blocks.SAND.getDefaultState(), 0.2f' },
    @{ Rx='terrainMountainSpikes';        Surface='SurfaceMountainSnow';      Args='@top, @fill, false, null, 0.2f' },
    @{ Rx='terrainFlatLakes';             Surface='SurfaceMountainSnow';      Args='@top, @fill, true, Blocks.SAND.getDefaultState(), 0.2f' },
    @{ Rx='terrainSwampMountain';         Surface='SurfaceMountainStone';     Args='@top, @fill, false, null, 0.95f' },
    @{ Rx='terrainGrasslandMountains';    Surface='SurfaceMountainStone';     Args='@top, @fill, false, null, 0.6f' },
    @{ Rx='terrainHilly';                 Surface='SurfaceMountainStone';     Args='@top, @fill, false, null, 0f, 1.5f, 60f, 65f, 1.5f' },
    @{ Rx='terrainGrasslandHills';        Surface='SurfaceGrassland';         Args='@top, @fill, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()' },
    @{ Rx='terrainMarsh';                 Surface='SurfaceGrassland';         Args='@top, @fill, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()' },
    @{ Rx='terrainSwampRiver';            Surface='SurfaceGrassland';         Args='@top, @fill, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()' },
    @{ Rx='terrainSmallSupport';          Surface='SurfaceGrassland';         Args='@top, @fill, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()' },
    @{ Rx='terrainSmallIsland';           Surface='SurfaceGrassland';         Args='@top, @fill, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()' },
    @{ Rx='terrainGrasslandFlats';        Surface='SurfaceGrassland';         Args='@top, @fill, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()' },
    @{ Rx='terrainMountain';              Surface='SurfaceTundra';            Args='@top, @fill' },
    @{ Rx='terrainHighland';              Surface='SurfaceGrassland';         Args='@top, @fill, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()' },
    @{ Rx='terrainMesa';                  Surface='SurfaceMesa';              Args='Blocks.SAND.getDefaultState(), Blocks.SAND.getDefaultState(), (byte) 1' },
    @{ Rx='terrainCanyon';                Surface='SurfaceCanyon';            Args='Blocks.SAND.getDefaultState(), Blocks.SAND.getDefaultState(), (byte) 1, 0' },
    @{ Rx='terrainCoastIce';              Surface='SurfaceGrassland';         Args='Blocks.PACKED_ICE.getDefaultState(), Blocks.PACKED_ICE.getDefaultState(), Blocks.PACKED_ICE.getDefaultState(), Blocks.ICE.getDefaultState()' },
    @{ Rx='terrainIslandTropical';        Surface='SurfaceIslandMountainStone'; Args='@top, @fill, 67, Blocks.SAND.getDefaultState(), 0f' },
    @{ Rx='terrainIslandTundra';          Surface='SurfaceIslandMountainStone'; Args='@top, @fill, 67, Blocks.SAND.getDefaultState(), 0f' },
    @{ Rx='terrainPolar';                 Surface='SurfacePolar';             Args='Blocks.SNOW.getDefaultState(), Blocks.SNOW.getDefaultState()' }
)
# Deliberately NOT rewired (documented): terrainOcean (5), terrainCoastDunes (4), '-' (3).
# RWG's coast/ocean biomes are dedicated classes without a shared Surface* counterpart.

$dirs = @('vanilla', 'biomesoplenty', 'thaumcraft')
foreach ($d in $dirs) {
    $dir = Join-Path $Root "src\main\java\rtg\world\biome\realistic\$d"
    if (-not (Test-Path $dir)) { continue }
    foreach ($f in Get-ChildItem $dir -Filter 'RealisticBiome*.java') {
        $name = $f.BaseName
        $m = [regex]::Match($name, '^RealisticBiome(Vanilla|BOP|TC|EBXL)(\w+)$')
        if (-not $m.Success) { continue }
        $suffix = $m.Groups[2].Value
        $key = $suffix.Substring(0,1).ToLower() + $suffix.Substring(1)
        $ctor = $null
        $used = ''
        if ($byKey.ContainsKey($key)) {
            $row = $byKey[$key]
            $ctor = Build-Ctor $row.Surface $row.Args
            $used = 'SUPPORT:' + $row.Surface
        } else {
            $text0 = [System.IO.File]::ReadAllText($f.FullName)
            # strip comments: rule keywords inside javadoc must not select a surface (see header)
            $text0 = [regex]::Replace($text0, '/\*.*?\*/', '', 'Singleline')
            $text0 = [regex]::Replace($text0, '//[^\r\n]*', '')
            foreach ($r in $rules) {
                if ($text0 -match $r.Rx) {
                    $a = $r.Args -replace '@top', 'baseBiome().topBlock' -replace '@fill', 'baseBiome().fillerBlock'
                    $ctor = "new $($r.Surface)(getConfig(), $a)"
                    $used = 'RULE:' + $r.Surface
                    break
                }
            }
        }
        if (-not $ctor) {
            $report += [pscustomobject]@{ File=$name; Status='SKIP'; Surface=''; Key=$key }
            continue
        }
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $rx = [regex]'(?s)(public\s+SurfaceBase\s+initSurface\s*\(\s*\)\s*\{)(.*?)(\n\s*\})'
        $mm = $rx.Match($text)
        if (-not $mm.Success) {
            $report += [pscustomobject]@{ File=$name; Status='NO_INITSURFACE'; Surface=$used; Key=$key }
            continue
        }
        $newBody = "`r`n        return $ctor;`r`n    }"
        $newText = $text.Substring(0, $mm.Index) + $mm.Groups[1].Value + $newBody + $text.Substring($mm.Index + $mm.Length)

        # Ensure the imports the generated constructor needs are present.
        $imports = @()
        $cls = ([regex]::Match($ctor, '^new (\w+)\(')).Groups[1].Value
        if ($cls -and $text -notmatch "import rtg\.api\.world\.surface\.$cls;") {
            $imports += "import rtg.api.world.surface.$cls;"
        }
        if ($ctor -match '\bBlocks\.' -and $text -notmatch 'import net\.minecraft\.init\.Blocks;') {
            $imports += 'import net.minecraft.init.Blocks;'
        }
        if ($ctor -match '\bBlockUtil\.' -and $text -notmatch 'import rtg\.api\.util\.BlockUtil;') {
            $imports += 'import rtg.api.util.BlockUtil;'
        }
        if ($ctor -match '\bEnumDyeColor\.' -and $text -notmatch 'import net\.minecraft\.item\.EnumDyeColor;') {
            $imports += 'import net.minecraft.item.EnumDyeColor;'
        }
        if ($imports.Count -gt 0) {
            $im = [regex]::Matches($newText, '(?m)^import [^\r\n]+;\r?\n')
            if ($im.Count -gt 0) {
                $last = $im[$im.Count - 1]
                $ins = ($imports -join "`r`n") + "`r`n"
                $newText = $newText.Substring(0, $last.Index + $last.Length) + $ins + $newText.Substring($last.Index + $last.Length)
            }
        }
        if ($Apply) { [System.IO.File]::WriteAllText($f.FullName, $newText, $enc) }
        $report += [pscustomobject]@{ File=$name; Status=if($Apply){'WIRED'}else{'DRY'}; Surface=$used; Key=$key }
    }
}

$report | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $Root 'docs\_surface_wiring_report.csv')
Write-Output ("total={0} wired={1} skip={2}" -f $report.Count,
    ($report | Where-Object { $_.Status -eq 'WIRED' -or $_.Status -eq 'DRY' }).Count,
    ($report | Where-Object { $_.Status -eq 'SKIP' }).Count)
$report | Group-Object Surface | Sort-Object Count -Descending | Select-Object Count, Name | Format-Table -AutoSize

