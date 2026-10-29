$base = 'D:\Project Management\dam-platform\dam-platform\codebase\backend'
Get-ChildItem $base -Recurse -Filter '*.java' -File | Where-Object { $_.FullName -notmatch '\\target\\' } | ForEach-Object {
    $rel = $_.FullName.Substring($base.Length + 1)
    Write-Host $rel
}
