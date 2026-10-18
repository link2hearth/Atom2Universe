# Analyse arithmetique, sans execution du jeu ni simulation des collisions.
# Formules relevees dans SurvivorGame.kt le 13 septembre 2026.
$ErrorActionPreference = 'Stop'
$rows = foreach ($minutes in @(0, 2, 5, 10, 15, 20)) {
    $wave = 1 + 2 * $minutes
    $scale = 1 + ($wave - 1) * 0.06
    $interval = if ($minutes -lt 2) { 0.95 + $minutes * 0.06 } else { [Math]::Max(0.38, 1.25 - $minutes * 0.09) }
    $active = if ($minutes -eq 0) { 1 } else { 0.8 }
    $limit = if ($minutes -lt 2) { 2 * (16 + [Math]::Floor($minutes * 2)) } else { 2 * [Math]::Min(60, 20 + [Math]::Floor(($minutes - 2) * 5)) }
    # Proposition : progression quadratique douce en minutes.
    $newScale = 1 + 0.16 * $minutes + 0.012 * $minutes * $minutes
    [pscustomobject]@{ Minutes=$minutes; Wave=$wave; Zombie=8*$scale; Fast=4*$scale; Erratic=6*$scale; Orbiter=16*$scale; Shooter=5*$scale; Boss=188*$scale; SpawnPerSecond=[Math]::Round($active*2/($interval+0.075),3); Limit=$limit; ProposedZombie=10*$newScale; ProposedBoss=260*$newScale }
}
$builds = @(
    @{Budget=0; Standard=@(0,0,0); Laser=@(0,0,0); Orbital=@(0,0,0,0); Bomb=@(0,0,0)},
    @{Budget=6; Standard=@(2,2,2); Laser=@(2,2,2); Orbital=@(2,1,2,1); Bomb=@(2,2,2)},
    @{Budget=12; Standard=@(4,4,4); Laser=@(5,4,3); Orbital=@(4,2,4,2); Bomb=@(5,3,4)},
    @{Budget=24; Standard=@(8,8,8); Laser=@(11,10,3); Orbital=@(8,5,6,5); Bomb=@(10,10,4)}
)
$weapons = foreach ($b in $builds) {
    $d,$r,$n = $b.Standard
    $ld,$lr,$ln = $b.Laser
    $od,$orate,$on,$om = $b.Orbital
    $bd,$br,$bn = $b.Bomb
    foreach ($allocation in @($b.Standard,$b.Laser,$b.Orbital,$b.Bomb)) {
        if (($allocation | Measure-Object -Sum).Sum -ne $b.Budget) { throw 'Budget incorrect' }
    }
    [pscustomobject]@{
        Budget=$b.Budget
        Projectile=6*(1+0.3*$d)*2.2*(1+0.1*$r)*(1+$n)
        LaserPerBeamTarget=7*(1+0.3*$ld)*1.3*(1+0.15*$lr)
        LaserSeparateTargets=7*(1+0.3*$ld)*1.3*(1+0.15*$lr)*(1+$ln)
        AuraPerTarget=3*(1+0.3*$d)*2.5*(1+0.15*$r)
        AuraRadius=120*(1+0.15*$n)
        ChainOneTarget=5*(1+0.3*$d)*1.2*(1+0.1*$r)
        ChainAllTargets=5*(1+0.3*$d)*1.2*(1+0.1*$r)*(3+$n)
        BounceOneTarget=7*(1+0.3*$d)*1.1*(1+0.1*$r)
        BounceAllTargets=7*(1+0.3*$d)*1.1*(1+0.1*$r)*(2+$n)
        OrbitalShots=3*(1+0.3*$od)*1.5*(1+0.15*$orate)*(2+$on)*(1+$om)
        OrbitalContactCeiling=3*(1+0.3*$od)*(2+$on)/0.3
        BombPerTargetAllBombs=20*(1+0.3*$bd)*0.5*(1+0.1*$br)*(1+$bn)
    }
}
$splits = foreach ($s in @(0,1,3,5)) {
    $p = $s * 0.1
    $expected = 0.0
    for ($depth=0; $depth -le 10; $depth++) { $expected += [Math]::Pow(1+$p,$depth) }
    [pscustomobject]@{SplitRank=$s; BounceRank=8; ExpectedTargetsUnlimited=$expected}
}
[pscustomobject]@{ Enemies=$rows; Weapons=$weapons; ChainSplit=$splits } | ConvertTo-Json -Depth 5
