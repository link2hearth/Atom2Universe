param(
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$GradleCache = 'C:\Users\link2\.gradle\caches'
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = Join-Path $repo 'app/build/natural-check'
New-Item -ItemType Directory -Force $output | Out-Null
$config = Get-Content (Join-Path $repo 'app/src/main/assets/caves/natural_generation.json') -Raw | ConvertFrom-Json
$culture = [Globalization.CultureInfo]::InvariantCulture
$lines = foreach ($b in $config.biomes) {
    $biome = Get-Content (Join-Path $repo "app/src/main/assets/caves/biomes/surface/$($b.id).json") -Raw | ConvertFrom-Json
    @($b.id, $b.base.ToString($culture), $b.amplitude.ToString($culture),
        $b.temperature.ToString($culture), $b.humidity.ToString($culture), $b.rarity.ToString($culture),
        $biome.tree_type, $biome.tree_density_base.ToString($culture)) -join "`t"
}
[IO.File]::WriteAllLines((Join-Path $output 'natural.tsv'), $lines)
$stdlib = Get-ChildItem (Join-Path $GradleCache 'modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib') -Filter 'kotlin-stdlib-*.jar' -Recurse |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object FullName -Descending | Select-Object -First 1
if (!$stdlib) { throw 'Kotlin standard library missing: run compileDebugKotlin first.' }
$cp = "$(Join-Path $repo 'app/build/tmp/kotlin-classes/debug');$output;$($stdlib.FullName)"
& (Join-Path $JavaHome 'bin/javac.exe') -cp $cp -d $output (Join-Path $PSScriptRoot 'NaturalTerrainCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Terrain check compilation failed.' }
& (Join-Path $JavaHome 'bin/java.exe') -cp $cp NaturalTerrainCheck (Join-Path $output 'natural.tsv') (Join-Path $output 'natural-section.png')
if ($LASTEXITCODE -ne 0) { throw 'Terrain checks failed.' }
