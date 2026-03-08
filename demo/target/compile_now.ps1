$proj='c:\Users\HP\Desktop\JAVA_2512_working\JAVA_2512\demo'
$srcDir=Join-Path $proj 'src\main\java'
$outDir=Join-Path $proj 'target\classes'
$libDir=Join-Path $proj 'src\main\webapp\WEB-INF\lib'
if(Test-Path $outDir){Remove-Item $outDir -Recurse -Force}
New-Item -ItemType Directory -Path $outDir | Out-Null
$cp=((Get-ChildItem $libDir -File -Filter *.jar | ForEach-Object {$_.FullName}) -join ';')
$srcList=Join-Path $proj 'target\sources.txt'
Get-ChildItem $srcDir -Recurse -File -Filter *.java | ForEach-Object {$_.FullName} | Set-Content -Path $srcList -Encoding ASCII
& 'C:\Program Files\Java\jdk1.8.0_202\bin\javac.exe' -encoding UTF-8 -cp "$cp;$outDir" -d $outDir "@$srcList"
if($LASTEXITCODE -ne 0){ exit $LASTEXITCODE }
Write-Output 'COMPILE_OK'
Write-Output ('TARGET_COUNT=' + ((Get-ChildItem -Recurse $outDir | Measure-Object).Count))
Get-ChildItem -Recurse $outDir -Filter InitializationListener.class | Select-Object FullName
Get-ChildItem -Recurse $outDir -Filter AllController*.class | Select-Object FullName | Select-Object -First 5
