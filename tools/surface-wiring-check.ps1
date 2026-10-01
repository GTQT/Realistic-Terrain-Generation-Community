param([string]$Root = (Split-Path -Parent $PSScriptRoot))

# Verify that every biome's initSurface() really returns one of RWG's ported shared surface
# classes (rtg.api.world.surface.Surface<Xxx>), not one of the old per-biome inner classes.
#
# Rule 2 (added below) covers Rule 1's blind spot: Rule 1 only asks "is it a shared class",
# so it cannot tell "should be SurfaceOcean but is SurfaceMountainSnow". Both paint sand/gravel,
# the difference only shows up on the sea floor. RWG reference: RealisticBiomeOcean.rReplace
# (shallow ? sand : gravel, 6 blocks deep) + Support.java:156-175 (the ocean slot table).

$ErrorActionPreference = 'Stop'
$shared = @(
    'SurfaceGrassland','SurfaceGrasslandMix1','SurfaceGrasslandMixBig','SurfaceMountainStone',
    'SurfaceMountainStoneMix1','SurfaceMountainSnow','SurfaceMountainPolar','SurfaceTundra',
    'SurfacePolar','SurfaceCanyon','SurfaceGrassCanyon','SurfaceMesa','SurfaceRedDesert',
    'SurfaceDesert','SurfaceDesertMountain','SurfaceDesertOasis','SurfaceDuneValley',
    'SurfaceIslandMountainStone','SurfaceMarshFix','SurfaceRiverOasis','SurfaceGeneric',
    # Extracted from an INLINE rReplace inside RWG's biome class (RealisticBiomeCoastDunes:66-106);
    # RWG has no rwg/surface/SurfaceCoastDunes.java, so it was never counted as "ported". See CHANGELOG.
    'SurfaceCoastDunes',
    # Same story: RWG's RealisticBiomeOcean.rReplace (ocean floor: sand when shallow, gravel when deep).
    'SurfaceOcean',
    # Also an inline rReplace: RWG RealisticBiomeCoastIce:58-92 (snow + gravel, packed_ice/ice on cliffs).
    'SurfaceCoastIce'
)

# Rule 2 table: file base name -> @(expected surface class, expected value of its last argument).
# '' = do not check the argument ('any' = decided by the constructor argument).
# Derived from RWG's own pairing:
#   RealisticBiomeOcean.rReplace      -> ocean floor (shallow ? sand : gravel)
#   RealisticBiomeCoastDunes.rReplace -> warm coast  (sand/sandstone, cobble+stone cliffs, cliff>1.3)
#   RealisticBiomeCoastIce.rReplace   -> ice coast   (snow + gravel, packed_ice/ice cliffs, cliff>1.4)
# Rule 1 alone cannot catch "shared class but the WRONG shared class" (both paint sand/gravel);
# that is exactly how a tropical biome ended up with the ice-coast surface.
$expectedSurface = [ordered]@{
    'RealisticBiomeRtgOcean'           = @('SurfaceOcean', 'any')
    'RealisticBiomeVanillaDeepOcean'   = @('SurfaceOcean', 'false')
    'RealisticBiomeVanillaOcean'       = @('SurfaceOcean', 'true')
    'RealisticBiomeVanillaFrozenOcean' = @('SurfaceOcean', 'true')
    'RealisticBiomeBOPKelpForest'      = @('SurfaceOcean', 'true')
    'RealisticBiomeBOPCoralReef'       = @('SurfaceOcean', 'true')
    'RealisticBiomeVanillaColdBeach'   = @('SurfaceCoastIce', '')
    'RealisticBiomeVanillaBeach'       = @('SurfaceCoastDunes', '')
    'RealisticBiomeVanillaStoneBeach'  = @('SurfaceCoastDunes', '')
    'RealisticBiomeBOPGravelBeach'     = @('SurfaceCoastDunes', '')
    'RealisticBiomeBOPOriginBeach'     = @('SurfaceCoastDunes', '')
    'RealisticBiomeBOPWhiteBeach'      = @('SurfaceCoastDunes', '')
}

$ok = 0; $inner = 0; $none = 0
$bad = @()
$oceanBad = @()
$oceanSeen = @()
foreach ($d in Get-ChildItem (Join-Path $Root 'src\main\java\rtg\world\biome\realistic') -Directory) {
    foreach ($f in Get-ChildItem $d.FullName -Filter 'RealisticBiome*.java') {
        $t = [System.IO.File]::ReadAllText($f.FullName)
        $i = $t.IndexOf('public SurfaceBase initSurface()')
        if ($i -lt 0) { continue }
        $braceOpen = $t.IndexOf('{', $i)
        $depth = 0; $j = $braceOpen
        while ($j -lt $t.Length) {
            $ch = $t[$j]
            if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { break } }
            $j++
        }
        $body = $t.Substring($braceOpen + 1, $j - $braceOpen - 1)
        # Strip comments first: the javadoc inside initSurface() quotes RWG source lines such as
        # "new RealisticBiomeOcean(...)", which would otherwise be matched as the return value.
        $code = [regex]::Replace($body, '/\*.*?\*/', '', 'Singleline')
        $code = [regex]::Replace($code, '//[^\r\n]*', '')
        $m = [regex]::Match($code, 'new\s+(\w+)\s*\(')
        if (-not $m.Success) { $none++; continue }
        $cls = $m.Groups[1].Value

        # ---- Rule 2: extract the constructor arguments (text between the matching parens) ----
        $argText = ''
        $open = $code.IndexOf('(', $m.Index)
        if ($open -ge 0) {
            $d2 = 0; $k = $open
            while ($k -lt $code.Length) {
                $ch = $code[$k]
                if ($ch -eq '(') { $d2++ } elseif ($ch -eq ')') { $d2--; if ($d2 -eq 0) { break } }
                $k++
            }
            $argText = ($code.Substring($open + 1, $k - $open - 1) -replace '\s+', ' ').Trim()
        }

        if ($shared -contains $cls) { $ok++ }
        else { $inner++; $bad += "$($f.BaseName) -> $cls" }

        if ($expectedSurface.Contains($f.BaseName)) {
            $expectCls = $expectedSurface[$f.BaseName][0]
            $expectArg = $expectedSurface[$f.BaseName][1]
            $oceanSeen += "$($f.BaseName) -> $cls($argText)"
            if ($cls -ne $expectCls) {
                $oceanBad += "$($f.BaseName) -> $cls($argText)   [RULE2: must be $expectCls]"
            } elseif ($expectArg -ne '' -and $expectArg -ne 'any') {
                $lastArg = ($argText -split ',')[-1].Trim()
                if ($lastArg -ne $expectArg) {
                    $oceanBad += "$($f.BaseName) -> $cls($argText)   [RULE2: last arg must be $expectArg]"
                }
            }
        }
    }
}
Write-Output ("shared={0}  still-inner={1}  no-new={2}" -f $ok, $inner, $none)
if ($bad.Count -gt 0) { Write-Output '--- not wired ---'; $bad | Select-Object -First 20 }

Write-Output '--- RULE2: coast / ocean floor (RWG inline rReplace) ---'
$oceanSeen | ForEach-Object { Write-Output "  $_" }
if ($oceanBad.Count -gt 0) {
    Write-Output ("RULE2 FAIL ({0})" -f $oceanBad.Count)
    $oceanBad | ForEach-Object { Write-Output "  $_" }
} else {
    Write-Output ("RULE2 PASS ({0} coast/ocean files)" -f $oceanSeen.Count)
}
