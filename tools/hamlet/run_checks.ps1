# Native Hamlet engine checks. Uses the existing Gradle dependency cache; no APK or downloads.
# Default: quick rules. Add -Benches only to run the requested economy simulations.
param([switch]$Benches)
$ErrorActionPreference = 'Stop'
$taskRepository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $taskRepository
$taskGradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$taskCache = Join-Path $taskGradleHome 'caches/modules-2/files-2.1'
function Task-Jar($group, $artifact) {
    $taskResult = Get-ChildItem -LiteralPath (Join-Path (Join-Path $taskCache $group) $artifact) -Recurse -Filter '*.jar' | Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Sort-Object FullName | Select-Object -Last 1
    if (-not $taskResult) { throw "Missing $group/$artifact" }
    return $taskResult.FullName
}
$taskCompiler = @(
    (Task-Jar 'org.jetbrains.kotlin' 'kotlin-compiler-embeddable'),
    (Task-Jar 'org.jetbrains.kotlin' 'kotlin-stdlib'),
    (Task-Jar 'org.jetbrains.kotlin' 'kotlin-reflect'),
    (Task-Jar 'org.jetbrains.kotlin' 'kotlin-script-runtime'),
    (Task-Jar 'org.jetbrains.kotlin' 'kotlin-daemon-embeddable'),
    (Task-Jar 'org.jetbrains.kotlinx' 'kotlinx-coroutines-core-jvm'),
    (Task-Jar 'org.jetbrains' 'annotations')
) -join ';'
$taskDeps = @(
    (Task-Jar 'org.jetbrains.kotlin' 'kotlin-stdlib'),
    (Task-Jar 'org.json' 'json'),
    (Task-Jar 'junit' 'junit'),
    (Task-Jar 'org.hamcrest' 'hamcrest-core'),
    (Task-Jar 'org.jetbrains' 'annotations')
) -join ';'
$taskSources = Get-ChildItem app/src/main/java/com/Atom2Universe/app/games/hamlet -Filter '*.kt' | Where-Object { $_.Name -notlike 'HamletArt*' -and (Get-Content -Raw $_.FullName) -notmatch '(?m)^import (android|androidx|com\.Atom2Universe\.app\.R)' } | ForEach-Object { $_.FullName }
$taskTests = Get-ChildItem app/src/test/java/com/Atom2Universe/app/games/hamlet -Filter '*.kt' | Where-Object { $_.Name -notmatch 'Apercu' -and ($Benches -or $_.Name -notmatch 'Bench') }
$taskSources += $taskTests | ForEach-Object { $_.FullName }
$taskTestNames = $taskTests | ForEach-Object { 'com.Atom2Universe.app.games.hamlet.' + $_.BaseName }
$taskClasses = Join-Path (Get-Location) 'app/build/hamlet-check-classes'
$taskJava = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
& $taskJava -Xmx1g -XX:ActiveProcessorCount=2 -cp $taskCompiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $taskDeps -d $taskClasses @taskSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Push-Location app
try {
    $taskTemp = Join-Path (Get-Location) 'build/hamlet-temp'
    New-Item -Path $taskTemp -ItemType Directory -Force | Out-Null
    & $taskJava -Xmx1g -XX:ActiveProcessorCount=2 "-Djava.io.tmpdir=$taskTemp" -cp "$taskClasses;$taskDeps" org.junit.runner.JUnitCore @taskTestNames
    exit $LASTEXITCODE
} finally { Pop-Location }
