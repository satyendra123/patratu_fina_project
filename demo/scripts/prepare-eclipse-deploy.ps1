param(
    [string]$ProjectDir = "C:\Users\HP\Desktop\JAVA_2512_working\JAVA_2512\demo",
    [string]$WtpAppDir = "C:\Users\HP\eclipse-workspace\.metadata\.plugins\org.eclipse.wst.server.core\tmp1\wtpwebapps\FingerprintDeviceDemo",
    [string]$JdkBin = "C:\Program Files\Java\jdk1.8.0_202\bin"
)

$ErrorActionPreference = "Stop"

$javac = Join-Path $JdkBin "javac.exe"
if (-not (Test-Path $javac)) {
    throw "javac not found at $javac"
}

Set-Location $ProjectDir
New-Item -ItemType Directory -Force -Path "target\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "target" | Out-Null

rg --files src/main/java -g '*.java' | Set-Content 'target\javac-sources.txt'

$libs = Get-ChildItem 'src/main/webapp/WEB-INF/lib/*.jar' | ForEach-Object { $_.FullName }
$libs += 'C:\Users\HP\.m2\repository\javax\javaee-api\7.0\javaee-api-7.0.jar'
$cp = $libs -join ';'

& $javac -encoding UTF-8 -source 1.8 -target 1.8 -cp $cp -d 'target/classes' @target/javac-sources.txt
if ($LASTEXITCODE -ne 0) {
    throw "javac failed"
}

$dstClasses = Join-Path $WtpAppDir "WEB-INF\classes"
New-Item -ItemType Directory -Force -Path $dstClasses | Out-Null
Copy-Item -Path (Join-Path $ProjectDir 'target\classes\*') -Destination $dstClasses -Recurse -Force

$listenerClass = Join-Path $dstClasses 'com\timmy\util\InitializationListener.class'
$wsClass = Join-Path $dstClasses 'com\timmy\websocket\WSServer.class'

Write-Output ("listenerClassExists=" + (Test-Path $listenerClass))
Write-Output ("wsClassExists=" + (Test-Path $wsClass))
Write-Output ("deployClassesDir=" + $dstClasses)
