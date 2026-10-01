param(
    # Pass with -RwgRoot '<path>'; no hardcoded default, because Windows PowerShell 5.1
    # reads BOM-less .ps1 as ANSI and would mangle any non-ASCII literal in this file.
    [Parameter(Mandatory = $true)][string]$RwgRoot
)

# Extracts (biome -> BiomePlacement) pairs from RWG's Support*.java so we can infer the
# author's actual rule instead of guessing when filling rtgc's border pools (phase C1).
#
# NOTE (superseded): this script walks back a fixed number of lines and therefore
# mis-attributes the placement constants inside Support.listFor(...) as if they were
# registrations (they are not). It also stops at the first match, so it under-reports
# multi-line addBiome(new RealisticBiomeSupport(...), ...) calls.
# For anything that must be trusted, use tools/rwg-placement-check.ps1, which detects
# real addBiome(...) call sites and cross-checks them against the Java table.
# This file is kept only as the historical record of the first extraction.

$rows = @()
foreach ($f in (Get-ChildItem $RwgRoot -Recurse -Include 'Support*.java')) {
    $ls = [System.IO.File]::ReadAllLines($f.FullName, [System.Text.Encoding]::UTF8)
    for ($i = 0; $i -lt $ls.Count; $i++) {
        if ($ls[$i] -notmatch 'BiomePlacement\.(\w+)') { continue }
        $placement = $Matches[1]
        # walk backwards for the nearest biome token
        $biome = '?'
        for ($j = $i; $j -ge [Math]::Max(0, $i - 12); $j--) {
            if ($ls[$j] -match '(RWGBiomes\.\w+|BOPCBiomes\.\w+|BiomeManager\.\w+)') { $biome = $Matches[1]; break }
        }
        $category = '?'
        for ($j = $i; $j -ge [Math]::Max(0, $i - 12); $j--) {
            if ($ls[$j] -match 'BiomeCategory\.(\w+)') { $category = $Matches[1]; break }
        }
        $rows += [pscustomobject]@{ File = $f.BaseName; Biome = $biome; Category = $category; Placement = $placement }
    }
}

Write-Output ("pairs found: {0}" -f $rows.Count)
Write-Output ''
Write-Output '=== placement distribution ==='
$rows | Group-Object Placement | Sort-Object Count -Descending | ForEach-Object { Write-Output ("  {0,-20} {1}" -f $_.Name, $_.Count) }
Write-Output ''
foreach ($p in ($rows | Group-Object Placement | Sort-Object Count -Descending | ForEach-Object { $_.Name })) {
    Write-Output ("=== {0} ===" -f $p)
    $rows | Where-Object { $_.Placement -eq $p } | ForEach-Object {
        Write-Output ("  {0,-22} {1,-12} {2}" -f $_.Biome, $_.Category, $_.File)
    }
    Write-Output ''
}
