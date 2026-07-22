param(
    [string]$AppName = "FingerprintClient_IDSL_DEVICEFIX_REBUILD",
    [string]$AppVersion = "1.0.0",
    [string]$TomcatHome = "C:\Users\HP\Downloads\apache-tomcat-9.0.115-windows-x64 (1)\apache-tomcat-9.0.115",
    [string]$BundledJreHome = "C:\Program Files\Java\jre1.8.0_202",
    [string]$SqlAuthDllPath = "C:\Users\HP\Downloads\sqljdbc_13.2.1.0_enu\sqljdbc_13.2\enu\auth\x64\mssql-jdbc_auth-13.2.1.x64.dll"
)

$ErrorActionPreference = "Stop"

function Resolve-ToolPath {
    param(
        [string]$Preferred,
        [string]$CommandName
    )

    if ($Preferred -and (Test-Path $Preferred)) {
        return $Preferred
    }

    $command = Get-Command $CommandName -ErrorAction Stop
    return $command.Source
}

function Invoke-RobocopySafe {
    param(
        [string]$Source,
        [string]$Destination
    )

    if (!(Test-Path $Source)) {
        throw "Missing path: $Source"
    }

    $null = New-Item -ItemType Directory -Path $Destination -Force
    robocopy $Source $Destination /E | Out-Null
    if ($LASTEXITCODE -gt 7) {
        throw "robocopy failed from '$Source' to '$Destination' with exit code $LASTEXITCODE"
    }
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

$JPackageExe = Resolve-ToolPath -Preferred "C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot\bin\jpackage.exe" -CommandName "jpackage"
$JavacExe = Resolve-ToolPath -Preferred "C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot\bin\javac.exe" -CommandName "javac"
$JarExe = Resolve-ToolPath -Preferred "C:\Program Files\Java\jdk1.8.0_202\bin\jar.exe" -CommandName "jar"

$DistRoot = Join-Path $ProjectRoot "dist\client"
$PackageDir = Join-Path $DistRoot $AppName
$ZipPath = Join-Path $DistRoot ($AppName + ".zip")

$LauncherBuildRoot = Join-Path $ProjectRoot ("packaging\client-launcher\build-" + $AppName)
$LauncherClasses = Join-Path $LauncherBuildRoot "classes"
$LauncherInput = Join-Path $LauncherBuildRoot "input"

$WebappSource = Join-Path $ProjectRoot "demo\src\main\webapp"
$CompiledClasses = Join-Path $ProjectRoot "demo\target\classes"
$ExplodedWarDir = Join-Path $ProjectRoot ("demo\target\FingerprintDeviceDemo-" + $AppName)
$WarOutput = Join-Path $ProjectRoot ("demo\target\" + $AppName + ".war")

$LauncherSource = Join-Path $ProjectRoot "packaging\client-launcher\src\com\timmy\deploy\ClientLauncher.java"
$LauncherJar = Join-Path $LauncherInput "client-launcher.jar"

$null = New-Item -ItemType Directory -Path $DistRoot -Force

if (Test-Path $PackageDir) {
    Remove-Item -Path $PackageDir -Recurse -Force
}
if (Test-Path $ZipPath) {
    Remove-Item -Path $ZipPath -Force
}
if (Test-Path $LauncherBuildRoot) {
    Remove-Item -Path $LauncherBuildRoot -Recurse -Force
}
if (Test-Path $ExplodedWarDir) {
    Remove-Item -Path $ExplodedWarDir -Recurse -Force
}
if (Test-Path $WarOutput) {
    Remove-Item -Path $WarOutput -Force
}

$null = New-Item -ItemType Directory -Path $LauncherClasses -Force
$null = New-Item -ItemType Directory -Path $LauncherInput -Force

Invoke-RobocopySafe -Source $WebappSource -Destination $ExplodedWarDir
Invoke-RobocopySafe -Source $CompiledClasses -Destination (Join-Path $ExplodedWarDir "WEB-INF\classes")

Push-Location $ExplodedWarDir
& $JarExe -cf $WarOutput .
if ($LASTEXITCODE -ne 0) {
    throw "WAR build failed with exit code $LASTEXITCODE"
}
Pop-Location

& $JavacExe -encoding UTF-8 -source 8 -target 8 -d $LauncherClasses $LauncherSource
if ($LASTEXITCODE -ne 0) {
    throw "Launcher compile failed with exit code $LASTEXITCODE"
}

& $JarExe cfe $LauncherJar com.timmy.deploy.ClientLauncher -C $LauncherClasses .
if ($LASTEXITCODE -ne 0) {
    throw "Launcher JAR build failed with exit code $LASTEXITCODE"
}

& $JPackageExe --type app-image `
    --name $AppName `
    --dest $DistRoot `
    --input $LauncherInput `
    --main-jar client-launcher.jar `
    --main-class com.timmy.deploy.ClientLauncher `
    --vendor IDSL `
    --app-version $AppVersion `
    --win-console
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

$AppDir = Join-Path $PackageDir "app"
$TomcatTarget = Join-Path $AppDir "tomcat"
$JreTarget = Join-Path $AppDir "jre"

Invoke-RobocopySafe -Source $TomcatHome -Destination $TomcatTarget
Invoke-RobocopySafe -Source $BundledJreHome -Destination $JreTarget

$TomcatBin = Join-Path $TomcatTarget "bin"
$BundledDllInTomcat = Get-ChildItem -Path $TomcatBin -Filter "mssql-jdbc_auth-*.x64.dll" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $BundledDllInTomcat) {
    if (Test-Path $SqlAuthDllPath) {
        Copy-Item -Path $SqlAuthDllPath -Destination (Join-Path $TomcatBin (Split-Path $SqlAuthDllPath -Leaf)) -Force
    }
}

$BundledDllInTomcat = Get-ChildItem -Path $TomcatBin -Filter "mssql-jdbc_auth-*.x64.dll" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $BundledDllInTomcat) {
    throw "Missing SQL auth DLL in package. Put mssql-jdbc_auth-*.x64.dll in $TomcatBin or provide -SqlAuthDllPath."
}

Copy-Item -Path $WarOutput -Destination (Join-Path $AppDir "demo.war") -Force
Copy-Item -Path $WarOutput -Destination (Join-Path $TomcatTarget "webapps\demo.war") -Force

$ExplodedDemo = Join-Path $TomcatTarget "webapps\demo"
if (Test-Path $ExplodedDemo) {
    Remove-Item -Path $ExplodedDemo -Recurse -Force
}

$ServerXmlPath = Join-Path $TomcatTarget "conf\server.xml"
(Get-Content $ServerXmlPath) -replace '<Server port="8005" shutdown="SHUTDOWN">', '<Server port="18005" shutdown="SHUTDOWN">' | Set-Content $ServerXmlPath -Encoding UTF8

$StartScriptPath = Join-Path $AppDir "start-tomcat.cmd"
$StopScriptPath = Join-Path $AppDir "stop-tomcat.cmd"

@'
@echo off
setlocal EnableExtensions
set "APP_DIR=%~dp0"
if "%APP_DIR:~-1%"=="\" set "APP_DIR=%APP_DIR:~0,-1%"
set "LOG_DIR=%APP_DIR%\logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set "START_LOG=%LOG_DIR%\start-tomcat.log"

call :log "========== START REQUEST =========="
set "CATALINA_HOME=%APP_DIR%\tomcat"
set "CATALINA_BASE=%CATALINA_HOME%"
set "JAVA_HOME=%APP_DIR%\jre"
set "JRE_HOME=%JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

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

call :log "JAVA_HOME=%JAVA_HOME%"
call :log "CATALINA_HOME=%CATALINA_HOME%"
call :log "JAVA_OPTS=%JAVA_OPTS%"

call "%CATALINA_HOME%\bin\catalina.bat" configtest >> "%START_LOG%" 2>&1
if errorlevel 1 (
  call :log "ERROR: catalina configtest failed."
  exit /b 21
)

call "%CATALINA_HOME%\bin\startup.bat" >> "%START_LOG%" 2>&1
if errorlevel 1 (
  set "START_EXIT=1"
) else (
  set "START_EXIT=0"
)
call :log "startup.bat exit=%START_EXIT%"

set "SHUTDOWN_OK="
for /l %%I in (1,1,12) do (
  netstat -ano | findstr /R /C:":18005 .*LISTENING" >nul 2>&1
  if not errorlevel 1 (
    set "SHUTDOWN_OK=1"
    goto :portDone
  )
  >nul ping -n 2 127.0.0.1
)
:portDone

if defined SHUTDOWN_OK (
  call :log "Tomcat detected on shutdown port 18005."
) else (
  call :log "WARN: Shutdown port 18005 not listening after startup. Check tomcat\logs\catalina*.log"
)

set "HTTP_OK="
for /l %%I in (1,1,10) do (
  netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul 2>&1
  if not errorlevel 1 (
    set "HTTP_OK=1"
    goto :httpDone
  )
  >nul ping -n 2 127.0.0.1
)
:httpDone
if defined HTTP_OK (
  call :log "HTTP port 8080 detected."
) else (
  call :log "WARN: HTTP port 8080 not listening yet."
)

exit /b %START_EXIT%

:log
echo [%date% %time%] %~1>> "%START_LOG%"
echo [%date% %time%] %~1
exit /b 0
'@ | Set-Content -Path $StartScriptPath -Encoding ASCII

@'
@echo off
setlocal EnableExtensions
set "APP_DIR=%~dp0"
if "%APP_DIR:~-1%"=="\" set "APP_DIR=%APP_DIR:~0,-1%"
set "LOG_DIR=%APP_DIR%\logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set "STOP_LOG=%LOG_DIR%\stop-tomcat.log"

call :log "========== STOP REQUEST =========="
set "CATALINA_HOME=%APP_DIR%\tomcat"
set "CATALINA_BASE=%CATALINA_HOME%"
set "JAVA_HOME=%APP_DIR%\jre"
set "JRE_HOME=%JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JAVA_OPTS=-Djava.library.path=%CATALINA_HOME%\bin"
set "CATALINA_OPTS=%JAVA_OPTS%"

set "STOP_EXIT=1"
set "SHUTDOWN_PORT_OPEN=1"
for /l %%I in (1,1,20) do (
  call "%CATALINA_HOME%\bin\shutdown.bat" >> "%STOP_LOG%" 2>&1
  if errorlevel 1 (
    call :log "shutdown attempt %%I exit=1"
  ) else (
    set "STOP_EXIT=0"
    call :log "shutdown attempt %%I exit=0"
  )
  netstat -ano | findstr /R /C:":18005 .*LISTENING" >nul 2>&1
  if errorlevel 1 (
    set "SHUTDOWN_PORT_OPEN=0"
    goto :stopDone
  )
  >nul ping -n 2 127.0.0.1
)
:stopDone

if "%SHUTDOWN_PORT_OPEN%"=="0" (
  call :log "Tomcat shutdown port closed."
  set "STOP_EXIT=0"
) else (
  call :log "WARN: Shutdown port still open. Verify tomcat status."
)

exit /b %STOP_EXIT%

:log
echo [%date% %time%] %~1>> "%STOP_LOG%"
echo [%date% %time%] %~1
exit /b 0
'@ | Set-Content -Path $StopScriptPath -Encoding ASCII

$ReadmePath = Join-Path $PackageDir "README_DEPLOY.txt"
@"
FingerprintClient deployment

1) Extract this full folder (do not copy only the EXE file).
2) Run $AppName.exe
3) Open http://localhost:8080/demo/
4) Stop with: $AppName.exe --stop

Crash diagnostics:
- app\logs\launcher.log
- app\logs\start-tomcat.log
- app\logs\stop-tomcat.log
- app\tomcat\logs\catalina*.log
"@ | Set-Content -Path $ReadmePath -Encoding ASCII

Compress-Archive -Path $PackageDir -DestinationPath $ZipPath -Force

Write-Host ""
Write-Host "Build completed."
Write-Host "Folder: $PackageDir"
Write-Host "Zip   : $ZipPath"
