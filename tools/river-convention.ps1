# Reproduces the numeric proof for the 1.0.10 root cause:
# rtgc's RealisticBiomeBase#newrNoise (now deleted) fed terrain functions an
# INVERTED `river`, so `m = noise * strength * river` terms collapsed on land.
#
# Pure arithmetic -- no MC dependency, no file reads. Constants are the defaults
# read from source (RTGConfig: riverDepth=57.0, waterFeatureWidthMultiplier=1.0;
# RTGWorld: LAKE_BOTTOM=53, LAKE_SHORE_LEVEL_BASE=0.035,
# LAKE_DEPRESSION_LEVEL=0.15; WaterLevel: DEFAULT_SEA_LEVEL=63).
#
# Run:  powershell -NoProfile -File tools\river-convention.ps1

$P     = 150.0 / 1600.0                    # ACTUAL_RIVER_PROPORTION
$A     = $P / (1.0 - $P)                   # RIVER_FLATTENING_ADDEND
$K     = (63.0 - 57.0) / (63.0 - 53.0)     # riverAdjustedforDepthDifference slope
$shore = 0.035
$dep   = 0.15

function Convert-Depth([double]$x) { if ($x -gt $P) { return $x }; return $P + ($x - $P) * $K }

function Convert-LakeToRiver([double]$pr) {
    if ($pr -gt $dep)   { return 1.0 }
    if ($pr -lt $shore) { return ($pr / $shore) * $P }
    return $P + (($pr - $shore) / ($dep - $shore)) * (1.0 - $P)
}

# The deleted rtgc chain: lakePressure -> lakeToRiverProportions ->
# riverAdjustedforDepthDifference -> riverFlattening
function Invoke-OldNewrNoise([double]$river, [double]$adjustedLake) {
    $riverAmount = 1.0 - $river
    $rv = [Math]::Max(0.0, (Convert-Depth $riverAmount))
    $al = $adjustedLake
    if ($al -lt $P) { $al = [Math]::Max(0.0, ($al - $P) * 2.0 + $P) }
    if ($rv -lt 1.0 -and $al -lt 1.0) {
        $least = [Math]::Min($al, $rv)
        $den = (1.0 - $rv) / $rv + (1.0 - $al) / $al
        $cr = 1.0 / ($den + 1.0)
        $cr = ($cr + $least) * 0.5
    } else {
        $cr = [Math]::Min($al, $rv)
    }
    $inv = 1.0 - $cr
    $inv = $inv * ($inv / ($inv + 0.05) * 1.05)
    $cr = 1.0 - $inv
    return [Math]::Max(0.0, $cr * (1.0 + $A) - $A)
}

Write-Output ("P={0:F5}  A={1:F5}  K={2}" -f $P, $A, $K)
Write-Output ''
Write-Output 'RWG convention: river = getRiverStrength()+1  (0 = river centre, 1 = inland)'
Write-Output ''
Write-Output 'input   RWG intent      OLD rtgc terrain got      NEW (1.0.10) terrain gets'
foreach ($r in 0.0, 0.05, 0.2, 0.4, 0.5, 0.6, 0.8, 1.0) {
    $old = Invoke-OldNewrNoise $r (Convert-LakeToRiver 1.0)
    Write-Output (" {0,-7} {1,-15} {2,-25} {3}" -f $r, $r, ('{0:F4}' -f $old), ('{0:F4}' -f $r))
}
Write-Output ''
Write-Output 'OLD output ~= 1 - input  ==  landscape.river[k] = -riverValues[k]'
Write-Output '  => it was a converter written for the OLD rtgc convention (1 = strongest river).'
Write-Output '  => migrating biomes to RWG formulas without deleting it inverted all terrain.'
Write-Output ''
Write-Output 'Effect on a term like terrainHilly: m = noise2(x/width) * strength * river'
Write-Output ("  inland relief ratio (old/new) = {0:F4}  -> {1:F1}x too flat" -f (Invoke-OldNewrNoise 1.0 (Convert-LakeToRiver 1.0)), (1.0 / (Invoke-OldNewrNoise 1.0 (Convert-LakeToRiver 1.0))))
