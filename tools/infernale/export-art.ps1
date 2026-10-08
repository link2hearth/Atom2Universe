param(
    [string]$OutputDirectory = 'build/infernale-art',
    [string]$GradleCache = 'C:/Users/link2/.gradle/caches/modules-2/files-2.1',
    [string]$JavaDirectory = 'C:/Program Files/Android/Android Studio/jbr'
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Find-Jar([string]$group, [string]$artifact, [string]$version) {
    $base = Join-Path $GradleCache "$group/$artifact/$version"
    $jar = Get-ChildItem -LiteralPath $base -Recurse -Filter "$artifact-$version.jar" | Select-Object -First 1
    if (!$jar) { throw "Dépendance locale manquante : $artifact $version" }
    return $jar.FullName
}
$stdlib = Find-Jar 'org.jetbrains.kotlin' 'kotlin-stdlib' '2.0.21'
$annotations = Find-Jar 'org.jetbrains' 'annotations' '13.0'
$compiler = @(
    (Find-Jar 'org.jetbrains.kotlin' 'kotlin-compiler-embeddable' '2.0.21'),
    $stdlib,
    (Find-Jar 'org.jetbrains.kotlin' 'kotlin-script-runtime' '2.0.21'),
    (Find-Jar 'org.jetbrains.kotlin' 'kotlin-reflect' '1.6.10'),
    (Find-Jar 'org.jetbrains.intellij.deps' 'trove4j' '1.0.20200330'),
    (Find-Jar 'org.jetbrains.kotlinx' 'kotlinx-coroutines-core-jvm' '1.6.4'),
    $annotations
) -join [IO.Path]::PathSeparator
$out = [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDirectory))
New-Item -ItemType Directory -Path $out -Force | Out-Null
$art = Join-Path $repoRoot 'app/src/main/java/com/Atom2Universe/app/games/infernale/art'
$sources = @('InfernalePixels.kt', 'InfernaleMechanicalSprites.kt', 'InfernaleFluidSprites.kt', 'InfernaleDeviceSprites.kt') | ForEach-Object { Join-Path $art $_ }
$sources += Join-Path $PSScriptRoot 'ExportArt.kt'
$jarOut = Join-Path $out 'export.jar'
& (Join-Path $JavaDirectory 'bin/java.exe') -cp $compiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$stdlib;$annotations" -d $jarOut @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation du renderer hors Android échouée.' }
& (Join-Path $JavaDirectory 'bin/java.exe') '-Djava.awt.headless=true' -cp "$jarOut;$stdlib" com.Atom2Universe.app.games.infernale.art.ExportArtKt $repoRoot $out
if ($LASTEXITCODE -ne 0) { throw 'Vérification graphique échouée.' }
