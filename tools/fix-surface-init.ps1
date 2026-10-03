param([string]$Root = (Split-Path -Parent $PSScriptRoot))

# Repair initSurface() bodies damaged by tools/surface-wiring.ps1 (its regex stopped at the
# first closing brace, which for many biomes was an early-return guard's brace).
# Rebuild the whole method by brace matching, keeping the first return statement it contains.

$ErrorActionPreference = 'Stop'
$enc = New-Object System.Text.UTF8Encoding($false)
$fixed = 0
$untouched = 0

foreach ($d in @('vanilla', 'biomesoplenty', 'thaumcraft', 'abyssalcraft', 'auxiliarybiomes', 'betteragriculture', 'biomesoplenty', 'thaumcraft')) {
    $dir = Join-Path $Root "src\main\java\rtg\world\biome\realistic\$d"
    if (-not (Test-Path $dir)) { continue }
    foreach ($f in Get-ChildItem $dir -Filter 'RealisticBiome*.java') {
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $sig = 'public SurfaceBase initSurface()'
        $i = $text.IndexOf($sig)
        if ($i -lt 0) { continue }
        $braceOpen = $text.IndexOf('{', $i)
        if ($braceOpen -lt 0) { continue }
        $depth = 0
        $j = $braceOpen
        while ($j -lt $text.Length) {
            $ch = $text[$j]
            if ($ch -eq '{') { $depth++ }
            elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { break } }
            $j++
        }
        if ($j -ge $text.Length) { Write-Output "UNBALANCED: $($f.Name)"; continue }
        $body = $text.Substring($braceOpen + 1, $j - $braceOpen - 1)
        $ret = [regex]::Match($body, '(?s)\breturn\b[^;]*;')
        if (-not $ret.Success) { Write-Output "NO_RETURN: $($f.Name)"; continue }
        $stmt = ($ret.Value -replace '\s+', ' ').Trim()

        # Trailing garbage: when initSurface() had an early-return guard, the wiring regex
        # stopped at the guard's brace and left the rest of the old body behind. If what
        # follows the rebuilt method is not a member declaration, drop it up to the next
        # line that is exactly "    }".
        $tailStart = $j + 1
        $rest = $text.Substring($tailStart)
        $lead = [regex]::Match($rest, '^\s*').Length
        $firstLine = ($rest.Substring($lead) -split "`n")[0].Trim()
        if ($firstLine -match '^(return|else|\})') {
            $m2 = [regex]::Match($rest, '\n    \}')
            if ($m2.Success) {
                $tailStart = $tailStart + $m2.Index + $m2.Length
            }
        }

        $replacement = $sig + " {`r`n        " + $stmt + "`r`n    }"
        $newText = $text.Substring(0, $i) + $replacement + $text.Substring($tailStart)
        if ($newText -ne $text) {
            [System.IO.File]::WriteAllText($f.FullName, $newText, $enc)
            $fixed++
        } else {
            $untouched++
        }
    }
}
Write-Output ("rebuilt={0} unchanged={1}" -f $fixed, $untouched)
