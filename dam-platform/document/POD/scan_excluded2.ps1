$base = 'D:\Project Management\dam-platform\dam-platform\codebase\backend'
Get-ChildItem $base -Recurse -Filter '*.java' -File | Where-Object { $_.FullName -notmatch '\\target\\' -and $_.FullName -match '\\entity\\' } | ForEach-Object {
    $lines = Get-Content $_.FullName -Encoding UTF8
    $cls = $_.Name -replace '\.java$', ''
    $pending = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match 'exist\s*=\s*false') { $pending = $true }
        if ($pending -and $lines[$i] -match 'private\s+[\w<>,\[\]\.]+\s+(\w+);') {
            Write-Host ($cls + ' | ' + $Matches[1])
            $pending = $false
        }
    }
}
