$proj='c:\Users\HP\Desktop\JAVA_2512_working\JAVA_2512\demo'
$srcDir=Join-Path $proj 'src\main\java'
$outDir=Join-Path $proj 'target\manual-compile-check'
$libDir='C:\Users\HP\eclipse-workspace\.metadata\.plugins\org.eclipse.wst.server.core\tmp1\wtpwebapps\FingerprintDeviceDemo\WEB-INF\lib'
if(Test-Path $outDir){Remove-Item $outDir -Recurse -Force}
New-Item -ItemType Directory -Path $outDir | Out-Null
$cp=((Get-ChildItem $libDir -File -Filter *.jar | ForEach-Object {$_.FullName}) -join ';')
$srcList=Join-Path $proj 'target\sources-check.txt'
Get-ChildItem $srcDir -Recurse -File -Filter *.java | ForEach-Object {$_.FullName} | Set-Content -Path $srcList -Encoding ASCII
& 'C:\Program Files\Java\jdk1.8.0_202\bin\javac.exe' -encoding UTF-8 -cp "$cp;$outDir" -d $outDir "@$srcList"
Write-Output ('JAVAC_EXIT=' + $LASTEXITCODE)
Write-Output ('OUT_COUNT=' + ((Get-ChildItem -Recurse $outDir | Measure-Object).Count))
Write-Output ('HAS_INIT=' + (Test-Path (Join-Path $outDir 'com\timmy\util\InitializationListener.class')))
