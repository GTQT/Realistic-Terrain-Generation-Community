param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [Parameter(Mandatory = $true)][string]$RwgSupport
)

# Re-extract the "MC biome -> RWG terrain + surface" table from RWG support/Support*.java.
# MUST strip comments first: SupportBOP.java keeps commented-out registrations that are NOT
# in effect, and an earlier extraction wrongly took them as real mappings.
# NOTE: -RwgSupport is passed on the command line because this file has no BOM and is read
# as ANSI, which would corrupt a CJK path literal embedded here.
#
# Columns: Biome, Terrain, Surface, Args. Both halves matter: rtgc had a biome wired to the
# right terrain but the wrong surface (and vice versa), which no tool could see until the
# Terrain column was added.

$ErrorActionPreference = 'Stop'
$src = $RwgSupport
$enc = New-Object System.Text.UTF8Encoding($false)

function Strip-Comments([string]$t) {
    $t = [regex]::Replace($t, '(?s)/\*.*?\*/', ' ')
    $t = [regex]::Replace($t, '(?m)//.*$', ' ')
    return $t
}

function Extract-Support([string]$path) {
    $t = Strip-Comments ([System.IO.File]::ReadAllText($path))
    $out = @()
    $idx = 0
    while ($true) {
        $i = $t.IndexOf('new RealisticBiomeSupport(', $idx)
        if ($i -lt 0) { break }
        $j = $i + 'new RealisticBiomeSupport('.Length
        $depth = 1
        while ($depth -gt 0 -and $j -lt $t.Length) {
            $ch = $t[$j]
            if ($ch -eq '(') { $depth++ } elseif ($ch -eq ')') { $depth-- }
            $j++
        }
        $inside = $t.Substring($i, $j - $i)
        $bio = [regex]::Match($inside, '^\s*new RealisticBiomeSupport\(\s*([^,]+),')
        $sur = [regex]::Match($inside, 'new (Surface\w+)\s*\(', 'Singleline')
        $ter = [regex]::Match($inside, 'new (Terrain\w+)\s*\(', 'Singleline')
        if ($bio.Success -and $sur.Success) {
            $cls = $sur.Groups[1].Value
            $p = $inside.IndexOf('new ' + $cls + '(') + ('new ' + $cls + '(').Length
            $d = 1
            while ($d -gt 0 -and $p -lt $inside.Length) {
                $ch = $inside[$p]
                if ($ch -eq '(') { $d++ } elseif ($ch -eq ')') { $d-- }
                $p++
            }
            $argStart = $inside.IndexOf('new ' + $cls + '(') + ('new ' + $cls + '(').Length
            $args = ($inside.Substring($argStart, ($p - 1) - $argStart) -replace '\s+', ' ').Trim()
            $terrain = if ($ter.Success) { $ter.Groups[1].Value } else { '' }
            $out += [pscustomobject]@{
                Biome   = $bio.Groups[1].Value.Trim()
                Terrain = $terrain
                Surface = $cls
                Args    = $args
            }
        }
        $idx = $j
    }
    return $out
}

$all = @()
foreach ($f in @('Support.java', 'SupportBOP.java', 'SupportEBXL.java', 'SupportTC.java', 'SupportCC.java')) {
    $p = Join-Path $src $f
    $r = Extract-Support $p
    Write-Output ("{0}: {1} rows" -f $f, $r.Count)
    $all += $r
}
$all | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $Root 'docs\_rwg_support_map.csv')
Write-Output ("total {0} rows (comments stripped)" -f $all.Count)
$all | Group-Object Surface | Sort-Object Count -Descending | Select-Object Count, Name | Format-Table -AutoSize
