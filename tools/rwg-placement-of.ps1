param(
    [Parameter(Mandatory = $true)][string]$RwgRoot,
    [Parameter(Mandatory = $true)][string]$Placement
)

# Precise extraction: for each `BiomePlacement.<Placement>` occurrence, walk back to the
# nearest `addBiome(` and pick the FIRST biome token after it (the biome argument).
# Avoids the window-based noise of the earlier script.

foreach ($f in (Get-ChildItem $RwgRoot -Recurse -Include 'Support*.java')) {
    $ls = [System.IO.File]::ReadAllLines($f.FullName, [System.Text.Encoding]::UTF8)
    for ($i = 0; $i -lt $ls.Count; $i++) {
        if ($ls[$i] -notmatch ("BiomePlacement\." + $Placement + "\b")) { continue }

        # walk back to the nearest addBiome(
        $anchor = -1
        for ($j = $i; $j -ge [Math]::Max(0, $i - 40); $j--) {
            if ($ls[$j] -match 'addBiome\s*\(') { $anchor = $j; break }
        }
        $biome = '?'
        if ($anchor -ge 0) {
            for ($j = $anchor; $j -le $i; $j++) {
                $m = [regex]::Match($ls[$j], '(RWGBiomes\.\w+|BiomeGenBase\.\w+|BOPCBiomes\.\w+|BiomeManager\.\w+|RealisticBiomeBase\.\w+)')
                if ($m.Success) { $biome = $m.Groups[1].Value; break }
            }
        }
        $cat = '?'
        for ($j = $i; $j -ge [Math]::Max(0, $i - 40); $j--) {
            $cm = [regex]::Match($ls[$j], 'BiomeCategory\.(\w+)')
            if ($cm.Success) { $cat = $cm.Groups[1].Value; break }
        }
        Write-Output ("{0,-22} {1,-8} {2,-14} L{3}" -f $biome, $cat, (Split-Path $f -Leaf), ($i + 1))
    }
}
