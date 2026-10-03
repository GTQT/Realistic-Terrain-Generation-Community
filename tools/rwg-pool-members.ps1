param(
    [Parameter(Mandatory = $true)][string]$RwgRoot,
    [string[]]$Placements = @('SMALL', 'SMALL_ISLAND', 'LARGE_ISLAND', 'ISLAND')
)

# Lists which biomes RWG puts into the SMALL / ISLAND pools, by scanning a window around
# each `BiomePlacement.<X>` occurrence for biome tokens. Used for phase C2/C3 inference.

foreach ($f in (Get-ChildItem $RwgRoot -Recurse -Include 'Support*.java')) {
    $ls = [System.IO.File]::ReadAllLines($f.FullName, [System.Text.Encoding]::UTF8)
    for ($i = 0; $i -lt $ls.Count; $i++) {
        $m = [regex]::Match($ls[$i], 'BiomePlacement\.(\w+)')
        if (-not $m.Success) { continue }
        if ($Placements -notcontains $m.Groups[1].Value) { continue }

        $lo = [Math]::Max(0, $i - 30)
        $tokens = @()
        for ($j = $lo; $j -le $i; $j++) {
            foreach ($tm in [regex]::Matches($ls[$j], '(RWGBiomes\.\w+|BiomeGenBase\.\w+|BOPCBiomes\.\w+|BiomeManager\.\w+)')) {
                if ($tokens -notcontains $tm.Groups[1].Value) { $tokens += $tm.Groups[1].Value }
            }
        }
        $cat = '?'
        for ($j = $i; $j -ge $lo; $j--) {
            $cm = [regex]::Match($ls[$j], 'BiomeCategory\.(\w+)')
            if ($cm.Success) { $cat = $cm.Groups[1].Value; break }
        }
        Write-Output ("{0,-16} {1,-22} L{2,-5} {3}" -f $m.Groups[1].Value, $cat, ($i + 1), ($tokens -join ', '))
    }
}
