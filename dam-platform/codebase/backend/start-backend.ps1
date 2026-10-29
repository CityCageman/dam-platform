$backend = 'D:\Project Management\dam-platform\dam-platform\codebase\backend'
$logDir = Join-Path $backend 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$services = 'dam-auth','dam-catalog','dam-lineage','dam-standard','dam-quality','dam-security','dam-lifecycle','dam-operations','dam-agent','dam-gateway'
foreach ($s in $services) {
  $jar = Join-Path $backend "$s\target\$s-1.0.0-exec.jar"
  if (-not (Test-Path $jar)) { Write-Output ("SKIP " + $s); continue }
  $log = Join-Path $logDir ($s + '.log')
  $err = Join-Path $logDir ($s + '.err.log')
  $args = "-Xmx512m -Xms256m -jar `"$jar`" --spring.profiles.active=dev --spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false"
  Start-Process -FilePath "java" -ArgumentList $args -RedirectStandardOutput $log -RedirectStandardError $err -WindowStyle Hidden
  Write-Output ("START " + $s)
  Start-Sleep -Seconds 2
}
Write-Output "ALL STARTED"
