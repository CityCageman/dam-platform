@echo off
setlocal
set B=D:\Project Management\dam-platform\dam-platform\codebase\backend
set NA=--spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false
set PROF=--spring.profiles.active=dev
set JVM=-Xmx512m -Xms256m

start "dam-auth" /min java %JVM% -jar "%B%\dam-auth\target\dam-auth-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-catalog" /min java %JVM% -jar "%B%\dam-catalog\target\dam-catalog-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-lineage" /min java %JVM% -jar "%B%\dam-lineage\target\dam-lineage-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-standard" /min java %JVM% -jar "%B%\dam-standard\target\dam-standard-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-quality" /min java %JVM% -jar "%B%\dam-quality\target\dam-quality-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-security" /min java %JVM% -jar "%B%\dam-security\target\dam-security-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-lifecycle" /min java %JVM% -jar "%B%\dam-lifecycle\target\dam-lifecycle-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-operations" /min java %JVM% -jar "%B%\dam-operations\target\dam-operations-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-agent" /min java %JVM% -jar "%B%\dam-agent\target\dam-agent-1.0.0-exec.jar" %PROF% %NA%
timeout /t 2 /nobreak >nul
start "dam-gateway" /min java %JVM% -jar "%B%\dam-gateway\target\dam-gateway-1.0.0-exec.jar" %PROF% %NA%
echo ALL STARTED
endlocal
