@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "APP_DIR=%~dp0"
if "%APP_DIR:~-1%"=="\" set "APP_DIR=%APP_DIR:~0,-1%"
set "LOG_DIR=%APP_DIR%\logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set "SERVICE_LOG=%LOG_DIR%\service-run.log"
set "ENV_FILE=%APP_DIR%\.env"
if exist "%ENV_FILE%" call :loadEnvFile "%ENV_FILE%"

set "CATALINA_HOME=%APP_DIR%\tomcat"
set "CATALINA_BASE=%CATALINA_HOME%"
set "JAVA_HOME=%APP_DIR%\jre"
set "JRE_HOME=%JAVA_HOME%"
set "HTTP_PORT=8080"
set "DB_PORT=1433"
set "DB_WAIT_LOOPS=90"
set "SCHEDULER_CRON=0 30 18 * * *"
set "SCHEDULER_ZONE=Asia/Kolkata"
if defined FP_SCHEDULER_CRON set "SCHEDULER_CRON=%FP_SCHEDULER_CRON%"
if defined FP_SCHEDULER_ZONE set "SCHEDULER_ZONE=%FP_SCHEDULER_ZONE%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

call :log "========== SERVICE START =========="
call :log "APP_DIR=%APP_DIR%"
call :log "HTTP_PORT=%HTTP_PORT% DB_PORT=%DB_PORT%"

if not exist "%JAVA_HOME%\bin\java.exe" (
  call :log "ERROR: Missing java.exe at %JAVA_HOME%\bin\java.exe"
  exit /b 11
)

set "SQL_AUTH_DLL="
for %%F in ("%CATALINA_HOME%\bin\mssql-jdbc_auth-*.x64.dll") do (
  if exist "%%~fF" (
    set "SQL_AUTH_DLL=%%~fF"
    goto :dllFound
  )
)
:dllFound

if defined SQL_AUTH_DLL (
  set "JAVA_OPTS=-Djava.library.path=%CATALINA_HOME%\bin -Dsqlserver.auth.dll.path=%SQL_AUTH_DLL%"
) else (
  set "JAVA_OPTS=-Djava.library.path=%CATALINA_HOME%\bin"
  call :log "WARN: SQL auth DLL not found under %CATALINA_HOME%\bin"
)
set "CATALINA_OPTS=%JAVA_OPTS%"
call :log "SCHEDULER_CRON=%SCHEDULER_CRON% SCHEDULER_ZONE=%SCHEDULER_ZONE%"

call :log "Running pre-clean stop sequence."
call "%APP_DIR%\stop-tomcat.cmd" >> "%SERVICE_LOG%" 2>&1

for /l %%I in (1,1,12) do (
  call :portsClosed
  if "%PORTS_CLOSED%"=="1" goto :portsReady
  >nul ping -n 2 127.0.0.1
)
call :log "WARN: HTTP port still busy after stop script. Forcing cleanup."
call :killListeningPids %HTTP_PORT%
call :killTomcatJavaProcs

for /l %%I in (1,1,10) do (
  call :portsClosed
  if "%PORTS_CLOSED%"=="1" goto :portsReady
  >nul ping -n 2 127.0.0.1
)
call :log "ERROR: Previous Tomcat instance is still present. Exiting to allow NSSM retry."
exit /b 41

:portsReady
if "%DB_PORT%"=="0" (
  call :log "SQL port pre-check disabled (DB_PORT=0)."
) else (
  call :log "Waiting for SQL Server listener on %DB_PORT% before Tomcat start."
  call :waitForPort %DB_PORT% %DB_WAIT_LOOPS%
  if not "%PORT_READY%"=="1" (
    call :log "WARN: SQL port %DB_PORT% did not open in time. Continuing Tomcat start."
  )
)

call :log "Starting Tomcat in foreground (catalina.bat run)."
call "%CATALINA_HOME%\bin\catalina.bat" run >> "%SERVICE_LOG%" 2>&1
set "EXIT_CODE=%ERRORLEVEL%"
call :log "catalina run exited with code %EXIT_CODE%"
exit /b %EXIT_CODE%

:portsClosed
set "PORTS_CLOSED=1"
netstat -ano | findstr /R /C:":%HTTP_PORT% .*LISTENING" >nul 2>&1 && set "PORTS_CLOSED=0"
exit /b 0

:waitForPort
set "WAIT_PORT=%~1"
set "WAIT_LOOPS=%~2"
set "PORT_READY=0"
for /l %%I in (1,1,%WAIT_LOOPS%) do (
  netstat -ano | findstr /R /C:":%WAIT_PORT% .*LISTENING" >nul 2>&1
  if not errorlevel 1 (
    set "PORT_READY=1"
    call :log "Port %WAIT_PORT% is ready."
    goto :waitPortDone
  )
  if %%I==1 call :log "Port %WAIT_PORT% not ready. Waiting..."
  if %%I==30 call :log "Port %WAIT_PORT% still not ready after 30 checks."
  if %%I==60 call :log "Port %WAIT_PORT% still not ready after 60 checks."
  >nul ping -n 2 127.0.0.1
)
:waitPortDone
exit /b 0

:killListeningPids
set "KILL_PORT=%~1"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%KILL_PORT% .*LISTENING"') do (
  if not "%%P"=="0" (
    taskkill /PID %%P /T /F >> "%SERVICE_LOG%" 2>&1
    if not errorlevel 1 (
      call :log "Killed PID %%P on port %KILL_PORT%"
    ) else (
      call :log "WARN: Failed to kill PID %%P on port %KILL_PORT%"
    )
  )
)
exit /b 0

:killTomcatJavaProcs
for /f %%P in ('powershell -NoProfile -Command "$needle='%CATALINA_BASE%'; Get-CimInstance Win32_Process | Where-Object { $_.Name -ieq 'java.exe' -and $_.CommandLine -like \"*${needle}*\" } | ForEach-Object { $_.ProcessId }"') do (
  if not "%%P"=="" (
    taskkill /PID %%P /T /F >> "%SERVICE_LOG%" 2>&1
    if not errorlevel 1 (
      call :log "Killed Tomcat java PID %%P"
    ) else (
      call :log "WARN: Failed to kill Tomcat java PID %%P"
    )
  )
)
exit /b 0

:log
echo [%date% %time%] %~1>> "%SERVICE_LOG%"
echo [%date% %time%] %~1
exit /b 0

:loadEnvFile
set "ENV_TARGET=%~1"
if not exist "%ENV_TARGET%" exit /b 0
for /f "usebackq tokens=1,* delims==" %%A in (`findstr /R "^[ ]*[A-Za-z_][A-Za-z0-9_]*=" "%ENV_TARGET%"`) do (
  set "ENV_KEY=%%A"
  set "ENV_VALUE=%%B"
  for /f "tokens=* delims= " %%K in ("!ENV_KEY!") do set "ENV_KEY=%%K"
  if /I not "!ENV_KEY!"=="REM" (
    set "!ENV_KEY!=!ENV_VALUE!"
  )
)
exit /b 0
