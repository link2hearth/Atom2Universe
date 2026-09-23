package com.Atom2Universe.app.games.roguelike

import kotlin.random.Random

/** Tables de rencontres locales. Les poids s'appliquent aux groupes, à tous les étages. */
internal object DungeonBestiary {
    private fun population(rats: Int = 0, zombies: Int = 0, skeletons: Int = 0,
        vampires: Int = 0, bats: Int = 0, pirates: Int = 0, brutes: Int = 0, spiders: Int = 0,
        scorpions: Int = 0, demons: Int = 0, plants: Int = 0, felines: Int = 0,
        wolves: Int = 0, bears: Int = 0, goblins: Int = 0, trolls: Int = 0, snakes: Int = 0) = listOf(
        MonsterType.RAT to rats, MonsterType.ZOMBIE to zombies,
        MonsterType.SKELETON to skeletons, MonsterType.VAMPIRE to vampires,
        MonsterType.VAMPIRE_BAT to bats, MonsterType.PIRATE to pirates,
        MonsterType.PIRATE_BRUTE to brutes, MonsterType.SPIDER to spiders,
        MonsterType.SCORPION to scorpions, MonsterType.DEMON to demons,
        MonsterType.CARNIVOROUS_PLANT to plants, MonsterType.FELINE to felines,
        MonsterType.WOLF to wolves, MonsterType.BEAR to bears,
        MonsterType.GOBLIN to goblins, MonsterType.TROLL to trolls, MonsterType.SNAKE to snakes,
    ).filter { it.second > 0 }

    /**
     * Chaque carte a son **monstre fétiche**, le seul du bestiaire à résister à un élément (à 50 %),
     * et il ne vit que là (le propriétaire, 23/09/2026) : un joueur qui connaît le jeu sait qu'au
     * champ de bataille il y a des démons, et laisse sa relique de feu. Zombie : cimetière et
     * crypte ; squelette : donjon ; ours : forêt ; serpent : champs ; démon : champ de bataille ;
     * scorpion : mine et dépôt ; vampire : bibliothèque ; brute : bateau, cabine et port ;
     * gobelin : auberge ; rampant : vaisseau. Le monastère, le village et le camp n'en ont pas.
     * Les autres monstres (rats, araignées, chauves-souris, loups, trolls…) ne résistent à rien.
     */
    private val populations = DungeonTheme.entries.associateWith { theme ->
        when (theme) {
            DungeonTheme.SPACESHIP -> listOf(MonsterType.ALIEN_SCOUT to 40,
                MonsterType.ALIEN_CRAWLER to 35, MonsterType.ALIEN_FLOATER to 25)
            DungeonTheme.CEMETERY -> population(rats = 15, zombies = 45, spiders = 20, bats = 20)
            DungeonTheme.CRYPT -> population(rats = 10, zombies = 50, spiders = 25, bats = 15)
            DungeonTheme.DUNGEON -> population(rats = 20, skeletons = 35, spiders = 20, trolls = 25)
            DungeonTheme.MINE -> population(rats = 20, spiders = 25, scorpions = 35, trolls = 20)
            DungeonTheme.MINE_DEPOT -> population(rats = 35, spiders = 25, scorpions = 15, trolls = 25)
            DungeonTheme.BATTLEFIELD -> population(rats = 20, demons = 45, bats = 15, wolves = 20)
            DungeonTheme.CAMP -> population(rats = 25, trolls = 35, wolves = 40)
            DungeonTheme.LIBRARY -> population(rats = 20, vampires = 50, bats = 15, spiders = 15)
            DungeonTheme.INN -> population(rats = 30, bats = 10, spiders = 10, goblins = 50)
            DungeonTheme.MONASTERY -> population(rats = 30, bats = 25, spiders = 25, wolves = 20)
            DungeonTheme.VILLAGE -> population(rats = 35, bats = 20, spiders = 15, wolves = 30)
            DungeonTheme.FOREST -> population(rats = 15, spiders = 15, wolves = 30, bears = 20,
                felines = 10, plants = 10)
            DungeonTheme.FIELDS -> population(rats = 25, bats = 5, spiders = 10, wolves = 20,
                snakes = 30, plants = 10)
            DungeonTheme.PIRATE -> population(rats = 20, pirates = 60, brutes = 20)
            DungeonTheme.PIRATE_CABIN -> population(rats = 15, pirates = 55, brutes = 25, spiders = 5)
            DungeonTheme.PORT -> population(rats = 35, pirates = 50, brutes = 15)
        }
    }

    private val darkForest = population(rats = 10, bats = 10, spiders = 20, wolves = 25,
        bears = 15, plants = 10, trolls = 10)
    private val jungle = population(rats = 10, spiders = 20, felines = 35, plants = 30, trolls = 5)
    private val dayVillage = population(rats = 45, spiders = 20, wolves = 35)
    private val nightFields = population(rats = 15, bats = 15, spiders = 10, wolves = 25,
        snakes = 25, plants = 10)
    private val dayMonastery = population(rats = 35, spiders = 35, wolves = 30)

    private fun choices(theme: DungeonTheme, backdrop: DungeonBackdrop) = when {
        theme == DungeonTheme.FOREST && backdrop.jungle -> jungle
        theme == DungeonTheme.FOREST && backdrop.night -> darkForest
        theme == DungeonTheme.VILLAGE && !backdrop.night -> dayVillage
        theme == DungeonTheme.FIELDS && backdrop.night -> nightFields
        theme == DungeonTheme.MONASTERY && !backdrop.night -> dayMonastery
        theme == DungeonTheme.CEMETERY && backdrop == DungeonBackdrop.CRYPT -> populations.getValue(DungeonTheme.CRYPT)
        else -> populations.getValue(theme)
    }

    fun roll(theme: DungeonTheme, rng: Random, backdrop: DungeonBackdrop = theme.backdrop(0)): MonsterType {
        val choices = choices(theme, backdrop)
        var draw = rng.nextInt(choices.sumOf { it.second })
        for ((type, weight) in choices) {
            if (draw < weight) return type
            draw -= weight
        }
        error("Empty encounter table: $theme")
    }

    private val habitatsByType = MonsterType.entries.associateWith { type ->
        if (type == MonsterType.PIRATE_CAPTAIN) listOf(DungeonTheme.PIRATE, DungeonTheme.PIRATE_CABIN)
        else DungeonTheme.entries.filter { theme -> theme.backdrops.any { backdrop ->
            choices(theme, backdrop).any { it.first == type }
        } }
    }

    fun habitats(type: MonsterType): List<DungeonTheme> = habitatsByType.getValue(type)

    fun canWander(types: List<MonsterType>, theme: DungeonTheme,
        backdrop: DungeonBackdrop = theme.backdrop(0)): Boolean {
        val local = choices(theme, backdrop)
        return types.all { type -> if (type == MonsterType.PIRATE_CAPTAIN) theme in habitats(type)
            else local.any { it.first == type } }
    }

    /** Un seul capitaine par bateau, sur un emplacement déjà validé par le plan de population. */
    fun captainSite(level: DungeonLevel, spawns: List<PackSpawn>): Pos? {
        val shipSpawns = spawns.filter { level.themeAt(it.pos.x, it.pos.y) in
            listOf(DungeonTheme.PIRATE, DungeonTheme.PIRATE_CABIN) }
        return shipSpawns.maxWithOrNull(compareBy<PackSpawn>(
            { if (level.themeAt(it.pos.x, it.pos.y) == DungeonTheme.PIRATE_CABIN) 1 else 0 },
            { if (it.role == PackRole.GUARD) 1 else 0 },
            { level.campDistances[it.pos.y][it.pos.x] },
        ))?.pos
    }
}
