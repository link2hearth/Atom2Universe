$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$blocks = Get-ChildItem (Join-Path $repo 'app/src/main/assets/caves/blocks') -Filter '*.json' | ForEach-Object { Get-Content $_.FullName -Raw | ConvertFrom-Json }
$byId = @{}; $byName = @{}
foreach ($b in $blocks) {
    if ($byId.ContainsKey([int]$b.id) -or $byName.ContainsKey($b.name)) { throw "Duplicate definition: $($b.name)" }
    $byId[[int]$b.id] = $b; $byName[$b.name] = $b
}
foreach ($b in $blocks) {
    if ($b.hardness -le 0) { throw "Invalid hardness: $($b.name)" }
    if ($b.drop -and !$byName.ContainsKey($b.drop)) { throw "Invalid drop: $($b.name)" }
    if ($b.placement_rule -notin @('any','solid','soil','sand','cactus','reeds')) { throw "Invalid placement: $($b.name)" }
}
$recipes = Get-ChildItem (Join-Path $repo 'app/src/main/assets/caves/crafts') -Filter '*.json'
foreach ($f in $recipes) {
    $r = Get-Content $f.FullName -Raw | ConvertFrom-Json
    $seen = @{}
    if (!$r.ingredients.Count -or (!$r.result -and !$r.result_item)) { throw "Empty recipe: $f" }
    foreach ($i in $r.ingredients) {
        $ids = if ($i.tag) { @($blocks | Where-Object { $i.tag -in $_.tags } | ForEach-Object { $_.id }) } else { @($i.id) }
        if (!$ids.Count -or $i.count -le 0) { throw "Empty ingredient: $f" }
        foreach ($id in $ids) {
            if ($seen.ContainsKey([int]$id) -or !$byId.ContainsKey([int]$id)) { throw "Unknown/overlapping ingredient: $f" }
            $seen[[int]$id] = $true
        }
    }
    foreach ($tool in $r.tools) {
        if ($seen.ContainsKey([int]$tool) -or !$byId.ContainsKey([int]$tool)) { throw "Invalid/consumed tool: $f" }
    }
    if ($r.result -and (!$byId.ContainsKey([int]$r.result) -or $r.result_count -le 0)) { throw "Invalid output: $f" }
    if ($r.result_item -and !(Test-Path (Join-Path $repo "app/src/main/assets/caves/items/$($r.result_item).json"))) { throw "Unknown weapon: $f" }
}
foreach ($locale in @('values','values-fr')) {
    [xml]$xml = Get-Content (Join-Path $repo "app/src/main/res/$locale/strings_caves.xml") -Raw
    if ($xml.resources.string | Group-Object name | Where-Object Count -gt 1) { throw "Duplicate translations: $locale" }
    foreach ($b in $blocks) {
        if ("cave_block_$($b.name)" -notin $xml.resources.string.name -and "cave_item_$($b.name)" -notin $xml.resources.string.name) { throw "Missing name $locale/$($b.name)" }
    }
}
Write-Output "Validated $($blocks.Count) definitions, $($recipes.Count) recipes and EN/FR names."

# A positive material potential must exist for every transformation. A productive
# cycle would keep lowering its members after all simple paths have been relaxed.
$potential = @{}; foreach ($b in $blocks) { $potential[[int]$b.id] = 1.0 }
$rules = @()
foreach ($b in $blocks) {
    if ($b.drop) { $rules += @{ inputs = @(@{ ids = @([int]$b.id); count = 1 }); result = [int]$byName[$b.drop].id; count = $(if ($b.drop_count) { $b.drop_count } else { 1 }) } }
}
foreach ($file in $recipes) {
    $r = Get-Content $file.FullName -Raw | ConvertFrom-Json
    if (!$r.result) { continue }
    $inputs = @($r.ingredients | ForEach-Object {
        $i = $_
        $ids = if ($i.tag) { @($blocks | Where-Object { $i.tag -in $_.tags } | ForEach-Object { [int]$_.id }) } else { @([int]$i.id) }
        @{ ids = $ids; count = $i.count }
    })
    $rules += @{ inputs = $inputs; result = [int]$r.result; count = $r.result_count }
}
$changed = $false
for ($pass = 0; $pass -le $blocks.Count; $pass++) {
    $changed = $false
    foreach ($rule in $rules) {
        $cost = 0.0
        foreach ($i in $rule.inputs) { $cost += (($i.ids | ForEach-Object { $potential[[int]$_] } | Measure-Object -Minimum).Minimum) * $i.count }
        $next = $cost / $rule.count
        $old = $potential[$rule.result]
        if ($next -lt $old * (1.0 - 1e-10)) { $potential[$rule.result] = $next; $changed = $true }
    }
    if (!$changed) { break }
}
if ($changed) { throw 'Potential material multiplication cycle in harvest/crafting rules.' }
Write-Output 'Harvest/crafting material potential converges: no productive cycle detected.'
