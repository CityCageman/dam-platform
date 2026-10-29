$base = 'D:\Project Management\dam-platform\dam-platform\codebase\backend'
Get-ChildItem $base -Recurse -Filter '*.java' -File | Where-Object { $_.FullName -notmatch '\\target\\' } | ForEach-Object {
    $lines = Get-Content $_.FullName -Encoding UTF8
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '@TableName\(') {
            $module = $_.FullName.Split('\')[-5]
            $cls = $_.Name
            Write-Host ($module + ' | ' + $cls + ' | L' + ($i + 1) + ' | ' + $lines[$i].Trim())
        }
    }
}
