$base = 'D:\Project Management\dam-platform\dam-platform\codebase\backend'
$dirs = Get-ChildItem $base -Directory
foreach ($d in $dirs) {
    $count = (Get-ChildItem $d.FullName -Recurse -Filter '*.java' -File | Measure-Object).Count
    Write-Host ($d.Name + ': ' + $count + ' files')
}
Write-Host '----- total lines -----'
$total = 0
foreach ($d in $dirs) {
    $lines = 0
    Get-ChildItem $d.FullName -Recurse -Filter '*.java' -File | ForEach-Object {
        $lines += (Get-Content $_.FullName | Measure-Object -Line).Lines
    }
    Write-Host ($d.Name + ': ' + $lines + ' lines')
    $total += $lines
}
Write-Host ('TOTAL: ' + $total + ' lines')
