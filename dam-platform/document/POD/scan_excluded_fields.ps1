# 检查 Service/Controller 中通过 LambdaQueryWrapper 引用的 getter，找出可能指向 exist=false 字段的调用
$base = 'D:\Project Management\dam-platform\dam-platform\codebase\backend'
# 收集所有实体的 exist=false 字段名（驼峰）
$excluded = @{}
Get-ChildItem $base -Recurse -Filter '*.java' -File | Where-Object { $_.FullName -notmatch '\\target\\' -and $_.FullName -match '\\entity\\' } | ForEach-Object {
    $lines = Get-Content $_.FullName -Encoding UTF8
    $inEntity = $false
    $fieldName = $null
    $isExcluded = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match 'class\s+\w+\s*\{') { $inEntity = $true }
        if ($line -match 'exist\s*=\s*false') { $isExcluded = $true }
        if ($line -match '^\s*private\s+[\w<>,\[\]\.]+\s+(\w+);') {
            $fieldName = $Matches[1]
            if ($isExcluded) {
                $excluded[$fieldName] = $true
            }
            $isExcluded = $false
        }
    }
}
Write-Host ('EXCLUDED FIELDS (' + $excluded.Count + '):')
$excluded.Keys | Sort-Object | ForEach-Object { Write-Host ('  ' + $_) }
