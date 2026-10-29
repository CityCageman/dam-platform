$f = 'D:\Project Management\dam-platform\dam-platform\db\create_schema_doc.sql'
$lines = Get-Content $f -Encoding UTF8
foreach ($line in $lines) {
    if ($line -match '^CREATE TABLE\s+([a-z_]+)\.([a-z_]+)') {
        Write-Host ($Matches[1] + '.' + $Matches[2])
    }
}
