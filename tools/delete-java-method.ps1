param(
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$SignaturePattern,
    [switch]$WhatIf
)

# Deletes one Java method (with its preceding javadoc) located by $SignaturePattern.
# Prints the removed line range so the deletion can be reviewed.
$ls = New-Object System.Collections.Generic.List[string]
foreach ($l in [System.IO.File]::ReadAllLines($File, [System.Text.Encoding]::UTF8)) { [void]$ls.Add($l) }

$sig = -1
for ($i = 0; $i -lt $ls.Count; $i++) {
    if ($ls[$i] -match $SignaturePattern) { $sig = $i; break }
}
if ($sig -lt 0) { Write-Output "NOT FOUND: $SignaturePattern"; exit 1 }

# walk up over the javadoc
$start = $sig
$j = $sig - 1
while ($j -ge 0) {
    $t = $ls[$j].TrimStart()
    if ($t.StartsWith('*') -or $t.StartsWith('/**') -or $t.StartsWith('*/') -or $t.StartsWith('//')) { $j-- } else { break }
}
if ($j -lt $sig - 1) { $start = $j + 1 }

# balanced braces from the signature line
$depth = 0; $end = $sig; $seen = $false
for ($k = $sig; $k -lt $ls.Count; $k++) {
    foreach ($ch in $ls[$k].ToCharArray()) {
        if ($ch -eq '{') { $depth++; $seen = $true }
        elseif ($ch -eq '}') { $depth-- }
    }
    if ($seen -and $depth -eq 0) { $end = $k; break }
}
# swallow one trailing blank line
if ($end + 1 -lt $ls.Count -and $ls[$end + 1].Trim() -eq '') { $end = $end + 1 }

Write-Output ("DELETE {0}  L{1}..L{2}  ({3} lines)  [{4}]" -f `
    [System.IO.Path]::GetFileName($File), ($start + 1), ($end + 1), ($end - $start + 1), $ls[$sig].Trim())

if (-not $WhatIf) {
    $out = New-Object System.Collections.Generic.List[string]
    for ($k = 0; $k -lt $ls.Count; $k++) { if ($k -lt $start -or $k -gt $end) { [void]$out.Add($ls[$k]) } }
    [System.IO.File]::WriteAllLines($File, $out, (New-Object System.Text.UTF8Encoding($false)))
}
