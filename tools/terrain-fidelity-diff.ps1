param(
    [Parameter(Mandatory = $true)][string]$RwgTerrainDir,
    [Parameter(Mandatory = $true)][string]$TerrainBaseFile,
    [Parameter(Mandatory = $true)][string[]]$Names
)

# Side-by-side printer for RWG terrain functions vs their rtgc ports.
#
# Deliberately NOT an automated diff: earlier attempts at "constant sequence comparison"
# mis-reported twice. This only normalises the *documented porting conventions* and prints
# both bodies so a human/LLM can read them.
#
# Normalisations applied (all documented conventions):
#   perlin.noise2(     -> noise2f(            (RWG NoiseGenerator -> rtgc SimplexNoise)
#   cell.noise(a,b,1D) -> CELLDIST(cell.eval2D(a,b))
#   INV_* constants are left alone; they show up as reported differences.
#
# NOTE: this file must stay pure ASCII. Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI
# and mangles any non-ASCII literal, which breaks the parser (hit that three times now).

function Get-MethodBody {
    param([string[]]$Lines, [string]$Pattern)
    $sig = -1
    for ($i = 0; $i -lt $Lines.Count; $i++) { if ($Lines[$i] -match $Pattern) { $sig = $i; break } }
    if ($sig -lt 0) { return $null }
    $d = 0; $seen = $false; $out = @()
    for ($k = $sig; $k -lt $Lines.Count; $k++) {
        foreach ($ch in $Lines[$k].ToCharArray()) {
            if ($ch -eq '{') { $d++; $seen = $true } elseif ($ch -eq '}') { $d-- }
        }
        if ($seen -and $d -eq 0) { break }
        $out += $Lines[$k]
    }
    return $out
}

function Normalize([string[]]$Body) {
    $res = @()
    foreach ($l in $Body) {
        $t = $l.Trim()
        if ($t -eq '' -or $t.StartsWith('//') -or $t.StartsWith('*') -or $t.StartsWith('/*')) { continue }
        $t = $t -replace 'perlin\.noise2\(', 'noise2f('
        $t = $t -replace 'simplex[0-9]*\.noise2f\(', 'noise2f('
        $t = $t -replace 'simplex\.noise2f\(', 'noise2f('
        $t = $t -replace 'cell\.noise\(([^,]+),\s*([^,]+),\s*1D\)', 'CELLDIST(cell.eval2D($1, $2))'
        $t = $t -replace '\s+', ' '
        $res += $t
    }
    return $res
}

$tbLines = [System.IO.File]::ReadAllLines($TerrainBaseFile, [System.Text.Encoding]::UTF8)

foreach ($n in $Names) {
    $rwgFile = Join-Path $RwgTerrainDir "$n.java"
    if (-not (Test-Path $rwgFile)) { Write-Output "### $n : RWG source missing"; continue }

    $rwgBody = Get-MethodBody ([System.IO.File]::ReadAllLines($rwgFile, [System.Text.Encoding]::UTF8)) 'float\s+generateNoise\s*\('
    $rtgcFn = 'terrain' + $n.Substring(7)
    $rtgcBody = Get-MethodBody $tbLines ("static\s+float\s+" + $rtgcFn + "\s*\(")

    Write-Output ("################ " + $n + "  vs  " + $rtgcFn)
    if (-not $rwgBody) { Write-Output "  RWG body NOT FOUND"; continue }
    if (-not $rtgcBody) { Write-Output "  rtgc body NOT FOUND (not ported, or named differently)"; Write-Output ''; continue }

    $a = Normalize $rwgBody
    $b = Normalize $rtgcBody
    Write-Output ("  lines: RWG={0} rtgc={1}" -f $a.Count, $b.Count)
    $max = [Math]::Max($a.Count, $b.Count)
    for ($i = 0; $i -lt $max; $i++) {
        $l = if ($i -lt $a.Count) { $a[$i] } else { '' }
        $r = if ($i -lt $b.Count) { $b[$i] } else { '' }
        if ($l -eq $r) {
            Write-Output ("     RWG  {0}" -f $l)
        } else {
            Write-Output ("  != RWG  {0}" -f $l)
            Write-Output ("     rtgc {0}" -f $r)
        }
    }
    Write-Output ''
}
