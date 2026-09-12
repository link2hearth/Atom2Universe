package com.Atom2Universe.app.games.survivor

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde de Survivor : les records d'un cote, la partie en cours de l'autre.
 *
 * La partie en cours est ecrite en JSON dans les preferences. On ne garde que ce qui
 * ne se recalcule pas : le joueur, les ennemis, les formations et les minuteries de
 * vague. Tout l'ephemere (projectiles, explosions, particules) est abandonne — il
 * serait reconstruit en une fraction de seconde de jeu de toute facon.
 *
 * Deux regles tiennent l'ensemble :
 *  - on ne sauvegarde et ne charge que depuis le fil de rendu, quand la boucle est
 *    a l'arret ou entre deux images : jamais en parallele d'un `update`.
 *  - une sauvegarde illisible (version inconnue, JSON tronque) est effacee plutot
 *    que reparee a moitie.
 */
object SurvivorSave {

    private const val TAG = "SurvivorSave"
    private const val PREFS = "survivor_save"
    private const val KEY_RUN = "run"
    private const val KEY_BEST_TIME = "best_time"
    private const val KEY_BEST_KILLS = "best_kills"
    private const val VERSION = 1

    /** Le strict necessaire pour peindre la carte « Reprendre » sans tout recharger. */
    class RunSummary(
        val survivalTime: Float,
        val level: Int,
        val wave: Int,
        val kills: Int,
        val weapons: List<WeaponType>
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ─── Records ──────────────────────────────────────────────────────────────

    fun loadBest(ctx: Context, game: SurvivorGame) {
        val p = prefs(ctx)
        game.bestTime = p.getFloat(KEY_BEST_TIME, 0f)
        game.bestKills = p.getInt(KEY_BEST_KILLS, 0)
    }

    fun saveBest(ctx: Context, game: SurvivorGame) {
        if (game.bestTime <= 0f) return
        prefs(ctx).edit()
            .putFloat(KEY_BEST_TIME, game.bestTime)
            .putInt(KEY_BEST_KILLS, game.bestKills)
            .apply()
    }

    // ─── Partie en cours ──────────────────────────────────────────────────────

    fun clearRun(ctx: Context) {
        prefs(ctx).edit().remove(KEY_RUN).apply()
    }

    /** Lit juste l'en-tete de la sauvegarde ; `null` s'il n'y a rien de jouable. */
    fun peekRun(ctx: Context): RunSummary? {
        val raw = prefs(ctx).getString(KEY_RUN, null) ?: return null
        return try {
            val o = JSONObject(raw)
            if (o.optInt("v") != VERSION) { clearRun(ctx); return null }
            val p = o.getJSONObject("p")
            val weapons = p.optJSONArray("wp")?.let { arr ->
                (0 until arr.length()).mapNotNull { WeaponType.entries.getOrNull(arr.getInt(it)) }
            } ?: emptyList()
            RunSummary(
                survivalTime = o.optDouble("st").toFloat(),
                level = p.optInt("lv", 1),
                wave = o.optInt("w", 1),
                kills = p.optInt("k"),
                weapons = weapons
            )
        } catch (e: Exception) {
            Log.w(TAG, "sauvegarde illisible, on repart a zero", e)
            clearRun(ctx)
            null
        }
    }

    /**
     * Ecrit la partie en cours. Sans effet si la partie n'est pas en cours
     * (menu, choix d'arme, mort) : il n'y aurait rien a reprendre.
     */
    fun saveRun(ctx: Context, game: SurvivorGame) {
        val inRun = game.phase == GamePhase.PLAYING ||
                    game.phase == GamePhase.LEVEL_UP ||
                    game.phase == GamePhase.PAUSED
        if (!inRun) return
        try {
            prefs(ctx).edit().putString(KEY_RUN, encode(game).toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "sauvegarde impossible", e)
        }
    }

    /**
     * Recharge la partie sauvegardee dans [game] et la laisse **en pause** :
     * personne ne revient dans une partie pour se faire toucher avant d'avoir vu l'ecran.
     * Retourne `false` si rien n'a pu etre charge — le jeu est alors intact.
     */
    fun loadRun(ctx: Context, game: SurvivorGame): Boolean {
        val raw = prefs(ctx).getString(KEY_RUN, null) ?: return false
        return try {
            val o = JSONObject(raw)
            if (o.optInt("v") != VERSION) { clearRun(ctx); return false }
            decode(o, game)
            game.phase = GamePhase.PAUSED
            true
        } catch (e: Exception) {
            Log.w(TAG, "chargement impossible", e)
            clearRun(ctx)
            false
        }
    }

    // ─── Encodage ─────────────────────────────────────────────────────────────

    private fun encode(game: SurvivorGame): JSONObject {
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("st", game.survivalTime.toDouble())
        o.put("w", game.wave)
        o.put("spc", game.spawnCd.toDouble())
        o.put("wvc", game.waveCd.toDouble())
        o.put("bsc", game.bossCd.toDouble())
        o.put("aur", game.auraCd.toDouble())
        o.put("fmc", game.formationCd.toDouble())
        // Un choix d'amelioration ouvert au moment de quitter est rendu au joueur,
        // qui le retrouvera avec trois cartes fraichement tirees.
        o.put("plu", game.pendingLevelUps + if (game.phase == GamePhase.LEVEL_UP) 1 else 0)
        o.put("as", game.appearanceSerial)
        o.put("bas", game.bossAppearanceSerial)
        o.put("p", encodePlayer(game.player))

        // Les formations sont ecrites d'abord : chaque ennemi ne garde que leur index.
        val fIndex = HashMap<SFormation, Int>()
        val fArr = JSONArray()
        game.formations.forEachIndexed { i, f ->
            fIndex[f] = i
            fArr.put(JSONObject()
                .put("x", f.worldX.toDouble()).put("y", f.worldY.toDouble())
                .put("vx", f.vx.toDouble()).put("vy", f.vy.toDouble())
                .put("en", f.entering))
        }
        o.put("f", fArr)

        // Decalage de chaque ennemi dans sa formation, retrouve par son slot.
        val offsets = HashMap<SEnemy, FormationSlot>()
        for (f in game.formations) for (slot in f.slots) offsets[slot.enemy] = slot

        val eArr = JSONArray()
        for (e in game.enemies) {
            if (e.hp <= 0f) continue
            val je = JSONObject()
            je.put("t", e.type.ordinal)
            je.put("x", e.x.toDouble()); je.put("y", e.y.toDouble())
            je.put("hp", e.hp.toDouble()); je.put("mhp", e.maxHp.toDouble())
            je.put("sp", e.baseSpeed.toDouble()); je.put("dm", e.damage.toDouble())
            je.put("xd", e.xpDrop.toDouble())
            // Le constructeur applique un facteur 1,65 : on range le rayon d'origine.
            je.put("r", (e.radius / 1.65f).toDouble())
            je.put("sf", e.slowFactor.toDouble()); je.put("sc", e.slowCd.toDouble())
            je.put("oa", e.orbitAngle.toDouble()); je.put("orr", e.orbitRadius.toDouble())
            je.put("eo", e.erraticOffset.toDouble()); je.put("ec", e.erraticCd.toDouble())
            je.put("shc", e.shootCd.toDouble())
            je.put("bd", e.burnDmg.toDouble()); je.put("bt", e.burnTimer.toDouble())
            je.put("pd", e.poisonDmg.toDouble()); je.put("pt", e.poisonTimer.toDouble())
            je.put("ptc", e.poisonTickCd.toDouble())
            je.put("vv", e.visualVariant); je.put("vp", e.visualPalette)
            je.put("vs", e.visualSeed.toDouble())
            val slot = offsets[e]
            val f = e.formation
            if (slot != null && f != null && fIndex.containsKey(f)) {
                je.put("fi", fIndex[f]!!)
                je.put("fx", slot.offX.toDouble()); je.put("fy", slot.offY.toDouble())
            }
            eArr.put(je)
        }
        o.put("e", eArr)
        return o
    }

    private fun encodePlayer(p: SPlayer): JSONObject {
        val jp = JSONObject()
        jp.put("x", p.x.toDouble()); jp.put("y", p.y.toDouble())
        jp.put("hp", p.hp.toDouble()); jp.put("mhp", p.maxHp.toDouble())
        jp.put("sh", p.shield.toDouble()); jp.put("msh", p.maxShield.toDouble())
        jp.put("xp", p.xp.toDouble()); jp.put("xpn", p.xpToNext.toDouble())
        jp.put("lv", p.level); jp.put("spd", p.speed.toDouble())
        jp.put("rv", p.revivesLeft); jp.put("k", p.kills)

        val upg = JSONObject()
        for ((id, lvl) in p.upgrades) upg.put(id, lvl)
        jp.put("u", upg)

        val wp = JSONArray()
        for (w in p.weapons) wp.put(w.ordinal)
        jp.put("wp", wp)

        val wcd = JSONObject()
        for ((w, cd) in p.weaponCds) wcd.put(w.ordinal.toString(), cd.toDouble())
        jp.put("wcd", wcd)
        return jp
    }

    // ─── Decodage ─────────────────────────────────────────────────────────────

    private fun decode(o: JSONObject, game: SurvivorGame) {
        game.prepareForLoad()

        game.survivalTime = o.optDouble("st").toFloat()
        game.wave = o.optInt("w", 1).coerceAtLeast(1)
        game.spawnCd = o.optDouble("spc").toFloat()
        game.waveCd = o.optDouble("wvc", SurvivorGame.WAVE_DUR.toDouble()).toFloat()
        game.bossCd = o.optDouble("bsc", SurvivorGame.BOSS_INTERVAL.toDouble()).toFloat()
        game.auraCd = o.optDouble("aur").toFloat()
        game.formationCd = o.optDouble("fmc", 180.0).toFloat()
        game.pendingLevelUps = o.optInt("plu")
        game.appearanceSerial = o.optInt("as")
        game.bossAppearanceSerial = o.optInt("bas")

        decodePlayer(o.getJSONObject("p"), game.player)
        game.recomputeDerivedStats()

        val fArr = o.optJSONArray("f") ?: JSONArray()
        val formations = ArrayList<SFormation>(fArr.length())
        for (i in 0 until fArr.length()) {
            val jf = fArr.getJSONObject(i)
            formations.add(SFormation(
                worldX = jf.optDouble("x").toFloat(),
                worldY = jf.optDouble("y").toFloat(),
                vx = jf.optDouble("vx").toFloat(),
                vy = jf.optDouble("vy").toFloat(),
                entering = jf.optBoolean("en", false)
            ))
        }

        val eArr = o.optJSONArray("e") ?: JSONArray()
        for (i in 0 until eArr.length()) {
            val je = eArr.getJSONObject(i)
            val type = EnemyType.entries.getOrNull(je.optInt("t")) ?: continue
            val e = SEnemy(
                x = je.optDouble("x").toFloat(), y = je.optDouble("y").toFloat(),
                hp = je.optDouble("hp").toFloat(), maxHp = je.optDouble("mhp", 1.0).toFloat(),
                baseSpeed = je.optDouble("sp").toFloat(), damage = je.optDouble("dm").toFloat(),
                xpDrop = je.optDouble("xd").toFloat(), radius = je.optDouble("r", 14.0).toFloat(),
                type = type,
                slowFactor = je.optDouble("sf", 1.0).toFloat(), slowCd = je.optDouble("sc").toFloat(),
                orbitAngle = je.optDouble("oa").toFloat(), orbitRadius = je.optDouble("orr").toFloat(),
                erraticOffset = je.optDouble("eo").toFloat(), erraticCd = je.optDouble("ec").toFloat(),
                shootCd = je.optDouble("shc", 2.0).toFloat(),
                burnDmg = je.optDouble("bd").toFloat(), burnTimer = je.optDouble("bt").toFloat(),
                poisonDmg = je.optDouble("pd").toFloat(), poisonTimer = je.optDouble("pt").toFloat(),
                poisonTickCd = je.optDouble("ptc").toFloat()
            )
            if (e.hp <= 0f) continue
            e.visualVariant = je.optInt("vv")
            e.visualPalette = je.optInt("vp")
            e.visualSeed = je.optDouble("vs").toFloat()
            val f = formations.getOrNull(je.optInt("fi", -1))
            if (f != null) {
                e.formation = f
                f.slots.add(FormationSlot(e, je.optDouble("fx").toFloat(), je.optDouble("fy").toFloat()))
            }
            game.enemies.add(e)
        }
        // Une formation videe de ses rangs n'a plus rien a deplacer.
        game.formations.addAll(formations.filter { it.slots.isNotEmpty() })
    }

    private fun decodePlayer(jp: JSONObject, p: SPlayer) {
        p.x = jp.optDouble("x").toFloat(); p.y = jp.optDouble("y").toFloat()
        p.maxHp = jp.optDouble("mhp", 100.0).toFloat().coerceAtLeast(1f)
        p.hp = jp.optDouble("hp", 100.0).toFloat().coerceIn(0f, p.maxHp)
        p.maxShield = jp.optDouble("msh").toFloat()
        p.shield = jp.optDouble("sh").toFloat().coerceIn(0f, p.maxShield)
        p.xpToNext = jp.optDouble("xpn", 30.0).toFloat().coerceAtLeast(1f)
        p.xp = jp.optDouble("xp").toFloat().coerceAtLeast(0f)
        p.level = jp.optInt("lv", 1).coerceAtLeast(1)
        p.speed = jp.optDouble("spd", 180.0).toFloat()
        p.revivesLeft = jp.optInt("rv")
        p.kills = jp.optInt("k")
        p.iframeCd = 0f; p.shieldRegenDelay = 0f; p.lifeStealCd = 0f

        p.upgrades.clear()
        jp.optJSONObject("u")?.let { u ->
            val keys = u.keys()
            while (keys.hasNext()) { val id = keys.next(); p.upgrades[id] = u.optInt(id) }
        }

        p.weapons.clear()
        jp.optJSONArray("wp")?.let { arr ->
            for (i in 0 until arr.length())
                WeaponType.entries.getOrNull(arr.getInt(i))?.let { w -> if (w !in p.weapons) p.weapons.add(w) }
        }
        if (p.weapons.isEmpty()) p.weapons.add(WeaponType.PROJECTILE)

        p.weaponCds.clear()
        jp.optJSONObject("wcd")?.let { wcd ->
            val keys = wcd.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val w = k.toIntOrNull()?.let { ord -> WeaponType.entries.getOrNull(ord) } ?: continue
                p.weaponCds[w] = wcd.optDouble(k).toFloat()
            }
        }
    }
}
