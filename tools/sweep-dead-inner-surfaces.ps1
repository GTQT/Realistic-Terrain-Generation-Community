param(
    # Root of the Java sources, relative to the repo root (defaults to the main source set).
    [string]$SourceRoot = 'src\main\java\rtg',
    # Only touch files under this sub-path (POSIX or Windows separators both work).
    [string]$OnlyUnder = 'world\biome\realistic',
    # Class-name pattern for the inner classes to sweep.
    [string]$ClassPattern = 'class\s+(Surface\w+)\s+extends\s+SurfaceBase',
    # Preview only.
    [switch]$WhatIf,
    # Also drop imports in the touched files whose simple name no longer occurs.
    [switch]$CleanImports
)

# Removes inner `Surface*` classes that nothing references any more.
#
# Background: 1.0.30/1.0.31 ported RWG's shared rtg/api/world/surface/Surface* classes and
# rewired each biome's initSurface() to return the shared class. The OLD inner implementation
# was left in place, so ~119 inner classes became dead weight. This tool proves deadness
# (zero references outside the declaring file, zero references elsewhere inside it) before
# deleting, then optionally strips the imports that are left dangling.
#
# This file must stay pure ASCII: Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI and
# mangles any non-ASCII literal, breaking the parser.

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$root = Join-Path $repoRoot $SourceRoot

$allFiles = Get-ChildItem $root -Recurse -Filter '*.java'

# A mention in a comment is NOT a reference (a javadoc `{@link SurfaceX}` kept one dead class alive
# in an earlier run), and a bare simple name in ANOTHER file binds to the shared
# rtg.api.world.surface.<Name> class, not to a private inner class -> only qualified references count.
function Strip-Comments([string]$t) {
    $t = [regex]::Replace($t, '(?s)/\*.*?\*/', ' ')
    $t = [regex]::Replace($t, '(?m)//.*$', ' ')
    return $t
}

# ------------------------------------------------------------------ scan ----
$targets = @()
foreach ($f in $allFiles) {
    if ($f.FullName -notmatch [regex]::Escape($OnlyUnder)) { continue }
    $ls = [System.IO.File]::ReadAllLines($f.FullName, [System.Text.Encoding]::UTF8)

    # find every inner class matching the pattern, with its brace-matched end
    for ($i = 0; $i -lt $ls.Count; $i++) {
        if ($ls[$i] -notmatch $ClassPattern) { continue }
        $name = $Matches[1]

        $depth = 0; $seen = $false; $end = $i
        for ($k = $i; $k -lt $ls.Count; $k++) {
            foreach ($ch in $ls[$k].ToCharArray()) {
                if ($ch -eq '{') { $depth++; $seen = $true } elseif ($ch -eq '}') { $depth-- }
            }
            if ($seen -and $depth -eq 0) { $end = $k; break }
        }

        # references OUTSIDE the declaring file, qualified by the declaring class
        $decl = $f.BaseName
        $outside = 0
        foreach ($g in $allFiles) {
            if ($g.FullName -eq $f.FullName) { continue }
            $code = Strip-Comments ([System.IO.File]::ReadAllText($g.FullName, [System.Text.Encoding]::UTF8))
            $outside += ([regex]::Matches($code, [regex]::Escape("$decl.$name"))).Count
        }

        # references INSIDE the same file but outside this block (comments stripped)
        $insideText = ''
        for ($k = 0; $k -lt $ls.Count; $k++) {
            if ($k -ge $i -and $k -le $end) { continue }
            $insideText += $ls[$k] + "`n"
        }
        $inside = ([regex]::Matches((Strip-Comments $insideText), "\b$name\b")).Count

        $targets += [pscustomobject]@{
            File = $f.FullName; Name = $name; Start = $i; End = $end
            Outside = $outside; Inside = $inside
        }
    }
}

Write-Output ("inner Surface* classes found : {0}" -f $targets.Count)
$dead = @($targets | Where-Object { $_.Outside -eq 0 -and $_.Inside -eq 0 })
$keep = @($targets | Where-Object { $_.Outside -ne 0 -or $_.Inside -ne 0 })
Write-Output ("  dead (safe to delete)      : {0}" -f $dead.Count)
Write-Output ("  still referenced (kept)    : {0}" -f $keep.Count)
foreach ($k in $keep) {
    Write-Output ("    keep {0,-30} outside={1} inside={2}" -f $k.Name, $k.Outside, $k.Inside)
}

# --------------------------------------------------------------- delete ----
$removedLines = 0
$touched = New-Object System.Collections.Generic.HashSet[string]

foreach ($grp in ($dead | Group-Object File)) {
    $path = $grp.Name
    $ls = New-Object System.Collections.Generic.List[string]
    foreach ($l in [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)) { [void]$ls.Add($l) }

    # delete from the bottom up so earlier indices stay valid
    $items = @($grp.Group | Sort-Object Start -Descending)
    foreach ($t in $items) {
        # swallow a preceding javadoc / line-comment block
        $start = $t.Start
        $j = $t.Start - 1
        while ($j -ge 0) {
            $x = $ls[$j].TrimStart()
            if ($x.StartsWith('*') -or $x.StartsWith('/**') -or $x.StartsWith('*/') -or $x.StartsWith('//')) { $j-- } else { break }
        }
        if ($j -lt $t.Start - 1) { $start = $j + 1 }
        # swallow one trailing blank line
        $end = $t.End
        if ($end + 1 -lt $ls.Count -and $ls[$end + 1].Trim() -eq '') { $end = $end + 1 }

        $removedLines += ($end - $start + 1)
        $out = New-Object System.Collections.Generic.List[string]
        for ($k = 0; $k -lt $ls.Count; $k++) { if ($k -lt $start -or $k -gt $end) { [void]$out.Add($ls[$k]) } }
        $ls = $out
    }

    # ---------------------------------------------------- import cleanup ----
    if ($CleanImports) {
        $kept = New-Object System.Collections.Generic.List[string]
        $imports = @($ls | Where-Object { $_ -match '^\s*import\s+' })
        foreach ($l in $ls) {
            if ($l -notmatch '^\s*import\s+') { [void]$kept.Add($l); continue }
            if ($l -match '\*;') { [void]$kept.Add($l); continue }          # wildcard: keep
            if ($l -match '^\s*import\s+static\s+') { [void]$kept.Add($l); continue }
            $simple = ($l -replace '^\s*import\s+', '' -replace ';.*$', '').Split('.')[-1]
            # count the simple name outside the import lines
            $n = 0
            foreach ($x in $ls) {
                if ($x -match '^\s*import\s+') { continue }
                $n += ([regex]::Matches($x, "\b$simple\b")).Count
            }
            if ($n -gt 0) { [void]$kept.Add($l) }
        }
        $ls = $kept
    }

    [void]$touched.Add($path)
    if (-not $WhatIf) {
        [System.IO.File]::WriteAllLines($path, $ls, (New-Object System.Text.UTF8Encoding($false)))
    }
}

Write-Output ''
Write-Output ("files touched      : {0}" -f $touched.Count)
Write-Output ("lines removed      : {0}" -f $removedLines)
if ($WhatIf) { Write-Output '(WhatIf: nothing written)' }
