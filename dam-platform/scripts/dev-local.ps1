# =============================================================================
# DAM 平台 Windows 本地一键启动脚本（PowerShell 5.1+）
#
# 用法（在项目根目录）：
#   .\scripts\dev-local.cmd                 首次引导配置并启动
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-local.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-local.ps1 -Action stop
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-local.ps1 -NoBuild
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-local.ps1 -Migrate
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-local.ps1 -Migrate -Yes
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-local.ps1 -NoOpen
#
# 环境变量保存在 %USERPROFILE%\.dam-local.json，不要提交 git。
# 仅管理本脚本启动的进程（PID 在 %TEMP%\dam-local\pids），不会动系统服务。
# =============================================================================
param(
    [string]$Action = "start",
    [switch]$NoBuild,
    [switch]$Migrate,
    [switch]$Yes,
    [switch]$NoOpen,
    [switch]$Setup
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "codebase\backend"
$Frontend = Join-Path $Root "codebase\frontend"
$DbDir = Join-Path $Root "db"

$EnvFile = Join-Path $HOME ".dam-local.json"
$RunDir = Join-Path $env:TEMP "dam-local"
$LogDir = Join-Path $RunDir "logs"
$PidDir = Join-Path $RunDir "pids"

$Services = @("auth", "gateway", "catalog", "lineage", "standard", "quality", "security", "lifecycle", "operations", "agent")
$ServicePorts = @{
    auth = 8081; gateway = 8080; catalog = 8092; lineage = 8093
    standard = 8084; quality = 8085; security = 8086; lifecycle = 8087
    operations = 8088; agent = 8089
}
$FrontendPort = 3000

New-Item -ItemType Directory -Force -Path $LogDir, $PidDir | Out-Null

function Write-Step([string]$Message) { Write-Host "[dam] $Message" -ForegroundColor Cyan }
function Write-Warn([string]$Message) { Write-Host "[dam] $Message" -ForegroundColor Yellow }
function Write-Fail([string]$Message) { Write-Host "[dam] $Message" -ForegroundColor Red }
function Die([string]$Message) { Write-Fail $Message; exit 1 }

function Show-Usage {
    $text = @"
DAM 平台 Windows 本地一键启动脚本

用法：
  .\scripts\dev-local.cmd                         首次引导配置并启动
  .\scripts\dev-local.cmd -Action stop            停止
  .\scripts\dev-local.cmd -Action status          查看状态
  .\scripts\dev-local.cmd -Action logs            查看日志
  .\scripts\dev-local.cmd -Action restart         重启
  .\scripts\dev-local.cmd -NoBuild                跳过 Maven 打包
  .\scripts\dev-local.cmd -Migrate                启动前执行 v4/v5/v7（需 psql）
  .\scripts\dev-local.cmd -Migrate -Yes           迁移不二次确认
  .\scripts\dev-local.cmd -NoOpen                 不自动打开浏览器
  .\scripts\dev-local.cmd -Setup                  重新生成环境配置

环境文件：$HOME\.dam-local.json（不要提交 git）
PID/日志：$RunDir
"@
    Write-Host $text
}

function Setup-Env {
    if ((Test-Path $EnvFile) -and $Action -ne "setup") { return }
    if (Test-Path $EnvFile) {
        $ans = Read-Host "已存在 $EnvFile，覆盖重新生成？[y/N]"
        if ($ans -notmatch '^[yY]') { return }
    }

    $dbHost = Read-Host "数据库 host [10.162.3.16]"
    if ([string]::IsNullOrWhiteSpace($dbHost)) { $dbHost = "10.162.3.16" }

    $portText = Read-Host "数据库 port [11001]"
    $dbPort = 11001
    if (-not [int]::TryParse($portText, [ref]$dbPort)) { $dbPort = 11001 }

    $dbName = Read-Host "数据库名 [damplatform]"
    if ([string]::IsNullOrWhiteSpace($dbName)) { $dbName = "damplatform" }

    $dbUser = Read-Host "数据库用户 [dam]"
    if ([string]::IsNullOrWhiteSpace($dbUser)) { $dbUser = "dam" }

    $secure = Read-Host "数据库口令" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $dbPass = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    if ([string]::IsNullOrEmpty($dbPass)) { Die "数据库口令不能为空" }

    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 48
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $jwt = [Convert]::ToBase64String($bytes)

    $chatKey = Read-Host "LLM Chat API Key（可留空，AI 功能不可用）"
    $codeKey = Read-Host "LLM Code API Key（可留空）"

    $config = [ordered]@{
        DbHost = $dbHost
        DbPort = [int]$dbPort
        DbName = $dbName
        DbUser = $dbUser
        DbPassword = $dbPass
        JwtSecret = $jwt
        ChatKey = $chatKey
        CodeKey = $codeKey
    }
    $config | ConvertTo-Json | Set-Content -Path $EnvFile -Encoding UTF8
    try {
        & icacls $EnvFile /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
    } catch { }
    Write-Step "环境配置已写入 $EnvFile"
}

function Load-Env {
    if (-not (Test-Path $EnvFile)) { Die "缺少 $EnvFile，请运行：.\scripts\dev-local.cmd -Setup" }
    $script:Config = Get-Content -Raw -Path $EnvFile | ConvertFrom-Json
    if (-not $Config.DbPassword) { Die "DbPassword 未配置" }
    if (-not $Config.JwtSecret -or $Config.JwtSecret.Length -lt 32) { Die "JwtSecret 至少 32 个字符" }

    $script:DbHost = [string]$Config.DbHost
    $script:DbPort = [int]$Config.DbPort
    $script:DbName = [string]$Config.DbName
    $script:DbUser = [string]$Config.DbUser
    $script:DbPassword = [string]$Config.DbPassword

    $env:SPRING_PROFILES_ACTIVE = "dev"
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($Config.DbHost):$($Config.DbPort)/$($Config.DbName)"
    $env:SPRING_DATASOURCE_USERNAME = $Config.DbUser
    $env:SPRING_DATASOURCE_PASSWORD = $Config.DbPassword
    $env:SA_TOKEN_JWT_SECRET_KEY = $Config.JwtSecret
    $env:DAM_SECURITY_CRYPTO_SECRET = "dam-platform-crypto-secret-2026"
    $env:DAM_CORS_ORIGINS = "http://*:3000"
    $env:SPRING_CLOUD_NACOS_DISCOVERY_ENABLED = "false"
    $env:SPRING_CLOUD_NACOS_CONFIG_ENABLED = "false"
    $env:DAM_ROUTE_AUTH = "http://127.0.0.1:8081"
    $env:DAM_ROUTE_CATALOG = "http://127.0.0.1:8092"
    $env:DAM_ROUTE_LINEAGE = "http://127.0.0.1:8093"
    $env:DAM_ROUTE_STANDARD = "http://127.0.0.1:8084"
    $env:DAM_ROUTE_QUALITY = "http://127.0.0.1:8085"
    $env:DAM_ROUTE_SECURITY = "http://127.0.0.1:8086"
    $env:DAM_ROUTE_LIFECYCLE = "http://127.0.0.1:8087"
    $env:DAM_ROUTE_OPERATIONS = "http://127.0.0.1:8088"
    $env:DAM_ROUTE_MODEL_GATEWAY = "http://127.0.0.1:8089"
    $env:DAM_ROUTE_AI_WORKBENCH = "http://127.0.0.1:8090"
    $env:DAM_LINEAGE_URL = "http://127.0.0.1:8093"
    $env:DAM_MODEL_GATEWAY_URL = "http://127.0.0.1:8089"
        if ($Config.CodeKey) { $env:DAM_MODEL_GATEWAY_CODE_API_KEY = $Config.CodeKey }
}

function Test-PortInUse([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne(300)) { return $false }
        $client.EndConnect($iar)
        return $true
    } catch { return $false }
    finally { $client.Close() }
}

function Wait-Http([string]$Url, [int]$TimeoutSec = 120) {
    for ($i = 0; $i -lt $TimeoutSec; $i += 2) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Test-Tools {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) { Die "未找到 java，请安装 JDK 21" }
    $script:Java = $javaCmd.Source
    $versionText = (& $script:Java -version 2>&1 | Out-String)
    if ($versionText -match 'version "(\d+)') { $major = [int]$Matches[1] } else { $major = 0 }
    if ($major -ne 21) { Die "需要 JDK 21，当前 java 主版本：$major" }
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = Split-Path (Split-Path $script:Java -Parent) -Parent
    }

    $mvnCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mvnCmd) { $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue }
    if (-not $mvnCmd) { Die "未找到 mvn，请安装 Maven" }
    $script:Mvn = $mvnCmd.Source

    $nodeCmd = Get-Command node.exe -ErrorAction SilentlyContinue
    if (-not $nodeCmd) { $nodeCmd = Get-Command node -ErrorAction SilentlyContinue }
    if (-not $nodeCmd) { Die "未找到 node，请安装 Node.js 20+" }
    $script:Node = $nodeCmd.Source
    $nodeVersion = (& $script:Node -v | Out-String).Trim()
    $nodeMajor = 0
    if ($nodeVersion -match 'v(\d+)') { $nodeMajor = [int]$Matches[1] }
    if ($nodeMajor -lt 20) { Die "需要 Node.js 20+，当前：$nodeVersion" }

    $npmCmd = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npmCmd) { $npmCmd = Get-Command npm -ErrorAction SilentlyContinue }
    if (-not $npmCmd) { Die "未找到 npm" }
    $script:Npm = $npmCmd.Source
}

function Build-Backend {
    if ($NoBuild) {
        Write-Step "跳过 Maven 打包（-NoBuild）"
    } else {
        Write-Step "开始 Maven 打包（首次可能较慢）..."
        Push-Location $Backend
        try {
            & $script:Mvn -DskipTests package
            if ($LASTEXITCODE -ne 0) { Die "Maven 打包失败" }
        } finally { Pop-Location }
    }
    foreach ($s in $Services) {
        $jar = Join-Path $Backend "dam-$s\target\dam-$s-1.0.0-exec.jar"
        if (-not (Test-Path $jar)) { Die "缺少 jar：$jar，请先打包" }
    }
}

function Run-Migrations {
    if (-not $Migrate) { return }
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psqlCmd) { Die "未找到 psql，无法执行迁移" }
    Write-Warn "即将向 ${DbHost}:${DbPort}/${DbName} 执行 v4、v5、v7 迁移"
    if (-not $Yes) {
        $ans = Read-Host "确认执行？输入 yes 继续"
        if ($ans -ne "yes") { Die "已取消" }
    }
    $env:PGPASSWORD = $DbPassword
    try {
        foreach ($file in @("v4_lineage_layers.sql", "v5_ai_tasks.sql", "v7_lifecycle_execution.sql")) {
            $path = Join-Path $DbDir $file
            & $psqlCmd.Source -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -f $path
            if ($LASTEXITCODE -ne 0) { Die "迁移失败：$file" }
        }
        Write-Step "v4/v5/v7 迁移完成（v6 请按 db/v6_lineage_identity_README.md 单独执行）"
    } finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}

function Check-Schema {
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psqlCmd) { Write-Warn "未找到 psql，跳过数据库 schema 检查"; return }
    $env:PGPASSWORD = $DbPassword
    try {
        $layer = (& $psqlCmd.Source -h $DbHost -p $DbPort -U $DbUser -d $DbName -tAc "SELECT count(*) FROM information_schema.columns WHERE table_schema='dam_metadata' AND table_name='metadata_table' AND column_name='layer'" 2>$null | Out-String).Trim()
        if ($layer -eq "0" -or [string]::IsNullOrWhiteSpace($layer)) {
            Write-Warn "数据库缺少 v4 血缘层级列：请执行 db/v4_lineage_layers.sql（或用 -Migrate）"
        }
        $ai = (& $psqlCmd.Source -h $DbHost -p $DbPort -U $DbUser -d $DbName -tAc "SELECT to_regclass('dam_asset.ai_task')" 2>$null | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($ai)) {
            Write-Warn "数据库缺少 v5 AI 任务表：请执行 db/v5_ai_tasks.sql（或用 -Migrate）"
        }
    } catch { Write-Warn "数据库 schema 检查失败：$($_.Exception.Message)" }
    finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}

function Stop-All {
    $names = @("frontend") + $Services
    foreach ($name in $names) {
        $pidFile = Join-Path $PidDir "$name.pid"
        if (Test-Path $pidFile) {
            $procId = 0
            [int]::TryParse((Get-Content -Raw $pidFile).Trim(), [ref]$procId) | Out-Null
            if ($procId -gt 0) {
                $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
                if ($proc) { Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue }
            }
            Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Step "已停止本脚本启动的进程"
}

function Start-Backend {
    $existing = Get-ChildItem $PidDir -Filter "*.pid" -ErrorAction SilentlyContinue
    if ($existing) { Stop-All }

    foreach ($s in $Services) {
        $port = $ServicePorts[$s]
        if (Test-PortInUse $port) {
            Die "端口 $port 已被占用（dam-$s）。请先释放：netstat -ano | findstr :$port"
        }
    }

    foreach ($s in $Services) {
        $jar = Join-Path $Backend "dam-$s\target\dam-$s-1.0.0-exec.jar"
        $outLog = Join-Path $LogDir "dam-$s.out.log"
        $errLog = Join-Path $LogDir "dam-$s.err.log"
        Remove-Item $outLog, $errLog -Force -ErrorAction SilentlyContinue
        Write-Step "启动 dam-$s ..."
        $argLine = "-Dfile.encoding=UTF-8 -Xmx512m -jar `"$jar`""
        $p = Start-Process -FilePath $script:Java -ArgumentList $argLine -WorkingDirectory $Backend `
            -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
        Set-Content -Path (Join-Path $PidDir "dam-$s.pid") -Value $p.Id -Encoding ASCII
        Start-Sleep -Seconds 2
        if (-not (Get-Process -Id $p.Id -ErrorAction SilentlyContinue)) {
            Write-Fail "dam-$s 启动失败，最近错误日志："
            if (Test-Path $errLog) { Get-Content $errLog -Tail 30 }
            Stop-All
            exit 1
        }
    }

    Write-Step "等待网关就绪 ..."
    if (-not (Wait-Http "http://127.0.0.1:8080/api/auth/public-key" 120)) {
        Write-Fail "网关未在 120 秒内就绪，最近日志："
        Get-Content (Join-Path $LogDir "dam-gateway.err.log") -Tail 40 -ErrorAction SilentlyContinue
        Get-Content (Join-Path $LogDir "dam-auth.err.log") -Tail 20 -ErrorAction SilentlyContinue
        Stop-All
        exit 1
    }
}

function Start-Frontend {
    if (Test-PortInUse $FrontendPort) {
        Die "端口 $FrontendPort 已被占用。请先释放：netstat -ano | findstr :$FrontendPort"
    }
    $vite = Join-Path $Frontend "node_modules\vite\bin\vite.js"
    if (-not (Test-Path $vite)) {
        Write-Step "安装前端依赖 ..."
        Push-Location $Frontend
        try {
            & $script:Npm ci --cache (Join-Path $RunDir "npm-cache")
            if ($LASTEXITCODE -ne 0) {
                & $script:Npm install --cache (Join-Path $RunDir "npm-cache")
                if ($LASTEXITCODE -ne 0) { Die "前端依赖安装失败" }
            }
        } finally { Pop-Location }
    }

    $outLog = Join-Path $LogDir "frontend.out.log"
    $errLog = Join-Path $LogDir "frontend.err.log"
    Remove-Item $outLog, $errLog -Force -ErrorAction SilentlyContinue
    Write-Step "启动前端 Vite ..."
    $argLine = "`"$vite`" --host 0.0.0.0 --port 3000 --strictPort"
    $p = Start-Process -FilePath $script:Node -ArgumentList $argLine -WorkingDirectory $Frontend `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
    Set-Content -Path (Join-Path $PidDir "frontend.pid") -Value $p.Id -Encoding ASCII
    Start-Sleep -Seconds 2
    if (-not (Get-Process -Id $p.Id -ErrorAction SilentlyContinue)) {
        Write-Fail "前端启动失败，最近日志："
        Get-Content $errLog -Tail 40 -ErrorAction SilentlyContinue
        Stop-All
        exit 1
    }
    if (-not (Wait-Http "http://127.0.0.1:3000/" 60)) {
        Write-Fail "前端未在 60 秒内就绪，请查看 $errLog"
        Stop-All
        exit 1
    }
}

function Show-Status {
    Write-Host ("{0,-14} {1,-6} {2,-8} {3,-8} {4}" -f "服务", "端口", "进程", "监听", "HTTP")
    foreach ($s in $Services) {
        $port = $ServicePorts[$s]
        $pidFile = Join-Path $PidDir "dam-$s.pid"
        $running = "未运行"
        if (Test-Path $pidFile) {
            $procId = 0
            [int]::TryParse((Get-Content -Raw $pidFile).Trim(), [ref]$procId) | Out-Null
            if ($procId -gt 0 -and (Get-Process -Id $procId -ErrorAction SilentlyContinue)) { $running = "运行中" }
        }
        $listen = "未监听"
        if (Test-PortInUse $port) { $listen = "已监听" }
        $http = "-"
        if ($s -eq "gateway" -and $listen -eq "已监听") {
            try { $http = (Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/auth/public-key" -UseBasicParsing -TimeoutSec 2).StatusCode } catch { $http = "ERR" }
        }
        Write-Host ("{0,-14} {1,-6} {2,-8} {3,-8} {4}" -f "dam-$s", $port, $running, $listen, $http)
    }
    $fPid = Join-Path $PidDir "frontend.pid"
    $fRunning = "未运行"
    if (Test-Path $fPid) {
        $procId = 0
        [int]::TryParse((Get-Content -Raw $fPid).Trim(), [ref]$procId) | Out-Null
        if ($procId -gt 0 -and (Get-Process -Id $procId -ErrorAction SilentlyContinue)) { $fRunning = "运行中" }
    }
    $fListen = "未监听"
    if (Test-PortInUse $FrontendPort) { $fListen = "已监听" }
    Write-Host ("{0,-14} {1,-6} {2,-8} {3,-8} {4}" -f "frontend", $FrontendPort, $fRunning, $fListen, "")
    Write-Host ""
    Write-Host "日志：$LogDir"
}

function Show-Logs {
    Get-ChildItem $LogDir -Filter "*.log" -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "===== $($_.Name) =====" -ForegroundColor Cyan
        Get-Content $_.FullName -Tail 80 -ErrorAction SilentlyContinue
    }
}

function Start-All {
    Setup-Env
    Load-Env
    Test-Tools
    Run-Migrations
    Check-Schema
    Build-Backend
    Start-Backend
    Start-Frontend

    Write-Host ""
    Write-Step "本地环境已启动"
    Write-Host "  前端：http://localhost:3000"
    Write-Host "  网关：http://127.0.0.1:8080"
    Write-Host "  演示账号：admin / admin123（以实际库为准）"
    Write-Host "  日志：$LogDir"
    Write-Host "  停止：.\scripts\dev-local.cmd -Action stop"
    Write-Host "  状态：.\scripts\dev-local.cmd -Action status"
    if (-not $NoOpen) { Start-Process "http://localhost:3000" }
}

if ($Setup) { $Action = "setup" }

switch ($Action.ToLower()) {
    "start" { Start-All }
    "restart" { Stop-All; Start-All }
    "stop" { Stop-All }
    "status" { Show-Status }
    "logs" { Show-Logs }
    "setup" { Setup-Env; Write-Step "配置完成：$EnvFile" }
    "help" { Show-Usage }
    default { Show-Usage; exit 1 }
}
