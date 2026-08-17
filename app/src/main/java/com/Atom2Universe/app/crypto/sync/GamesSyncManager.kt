package com.Atom2Universe.app.crypto.sync

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.Atom2Universe.app.crypto.clicker.BigBangBonus
import com.Atom2Universe.app.crypto.clicker.BigBangRepository
import com.Atom2Universe.app.crypto.clicker.ClickerAchievementRepository
import com.Atom2Universe.app.crypto.clicker.ClickerDatabase
import com.Atom2Universe.app.crypto.clicker.ClickerStateEntity
import com.Atom2Universe.app.crypto.clicker.CritRepository
import com.Atom2Universe.app.crypto.clicker.ElementTokenRepository
import com.Atom2Universe.app.crypto.clicker.FactoryRepository
import com.Atom2Universe.app.crypto.clicker.FactoryType
import com.Atom2Universe.app.crypto.clicker.GachaTicketStateEntity
import com.Atom2Universe.app.crypto.clicker.GameStats
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.fusion.FusionRecipe
import com.Atom2Universe.app.crypto.fusion.FusionStore
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GamesSyncManager {

    private const val TAG = "GamesSyncManager"
    private const val SYNC_FILE = "games_state.json"

    private lateinit var appContext: Context
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
        Log.d(TAG, "GamesSyncManager initialized")
    }

    // ─── Résultat de sync ─────────────────────────────────────────────────────

    sealed class SyncResult {
        data class Success(val message: String) : SyncResult()
        /** Les deux saves existent et diffèrent — l'utilisateur doit choisir. */
        data class Conflict(val local: GamesSyncFile, val remote: GamesSyncFile) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    // ─── Point d'entrée : sync initial ───────────────────────────────────────

    suspend fun syncGames(): SyncResult = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext SyncResult.Error("Not initialized")

        try {
            val driveClient = getDriveClient()
                ?: return@withContext SyncResult.Error("Non connecté à Google")

            val localFile = buildLocalSyncFile()

            // Télécharger l'état Drive
            Log.d(TAG, "Téléchargement depuis Drive…")
            val remoteFile = driveClient.readJsonFile(SYNC_FILE)?.let {
                try { GamesSyncFile.fromJson(it) } catch (e: Exception) {
                    Log.e(TAG, "Erreur parsing remote", e); null
                }
            }

            // Pas de save distante → premier sync, on uploade silencieusement
            if (remoteFile == null) {
                driveClient.writeJsonFile(SYNC_FILE, localFile.toJson())
                Log.d(TAG, "Premier sync — sauvegarde locale uploadée")
                return@withContext SyncResult.Success("Sauvegarde initiale envoyée sur Drive")
            }

            // Les deux saves sont identiques → rien à faire
            if (!conflictExists(localFile, remoteFile)) {
                Log.d(TAG, "Saves identiques, aucune action")
                return@withContext SyncResult.Success("Déjà à jour")
            }

            // Conflit détecté → l'utilisateur doit choisir
            Log.d(TAG, "Conflit détecté — demande utilisateur")
            SyncResult.Conflict(local = localFile, remote = remoteFile)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur sync", e)
            SyncResult.Error("Erreur : ${e.message}")
        }
    }

    // ─── Résolution du conflit par l'utilisateur ──────────────────────────────

    suspend fun resolveConflict(chosen: GamesSyncFile): SyncResult = withContext(Dispatchers.IO) {
        try {
            val driveClient = getDriveClient()
                ?: return@withContext SyncResult.Error("Non connecté à Google")

            // Uploader la save choisie
            val uploaded = driveClient.writeJsonFile(SYNC_FILE, chosen.toJson())
            if (!uploaded) return@withContext SyncResult.Error("Échec de l'upload")

            // Appliquer localement
            applyLocally(chosen)

            Log.d(TAG, "Conflit résolu, save appliquée")
            SyncResult.Success("Synchronisation réussie")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur résolution conflit", e)
            SyncResult.Error("Erreur : ${e.message}")
        }
    }

    // ─── Détection de conflit ─────────────────────────────────────────────────

    private fun conflictExists(local: GamesSyncFile, remote: GamesSyncFile): Boolean {
        // Conflit clicker : atomes, totaux et niveaux achetés. perClick/perSecond sont
        // dérivés (recalculés depuis les niveaux et les usines) : les comparer
        // produirait de faux conflits après un simple rééquilibrage du jeu.
        val lc = local.clicker
        val rc = remote.clicker
        if (lc?.atoms != rc?.atoms) return true
        if (lc?.lifetime != rc?.lifetime) return true
        if (lc?.allTimeTotalAtoms != rc?.allTimeTotalAtoms) return true
        if (lc?.godFingerLevel != rc?.godFingerLevel) return true
        if (lc?.starCoreLevel != rc?.starCoreLevel) return true
        if (lc?.apcToApsLevel != rc?.apcToApsLevel) return true
        if (lc?.apsToApcLevel != rc?.apsToApcLevel) return true

        // Blocs v2. Les défauts (null → vide/0) évitent qu'une save v1 déclenche un
        // conflit alors que le local est lui aussi vierge sur ces points.
        // Seulement si les deux fichiers portent l'info : sinon une save v1 déclencherait
        // un conflit que la résolution ne pourrait de toute façon pas appliquer.
        if (local.version >= 2 && remote.version >= 2) {
            if (local.neutrinos != remote.neutrinos) return true
            if (local.lifetimeNeutrinos != remote.lifetimeNeutrinos) return true
        }

        if ((local.factories?.counts ?: emptyMap<String, Int>()) !=
            (remote.factories?.counts ?: emptyMap<String, Int>())) return true

        val lb = local.bigBang
        val rb = remote.bigBang
        if ((lb?.unlocked ?: false) != (rb?.unlocked ?: false)) return true
        if ((lb?.bigBangCount ?: 0) != (rb?.bigBangCount ?: 0)) return true
        if ((lb?.levels ?: emptyMap<String, Int>()) != (rb?.levels ?: emptyMap<String, Int>())) return true

        val lcr = local.crit
        val rcr = remote.crit
        if ((lcr?.chanceLevel ?: 0) != (rcr?.chanceLevel ?: 0)) return true
        if ((lcr?.damageLevel ?: 0) != (rcr?.damageLevel ?: 0)) return true

        if ((local.achievements ?: emptyList()).toSet() !=
            (remote.achievements ?: emptyList()).toSet()) return true

        if ((local.highScores?.values ?: emptyMap<String, Double>()) !=
            (remote.highScores?.values ?: emptyMap<String, Double>())) return true

        val lf = local.fusion
        val rf = remote.fusion
        if ((lf?.quarks ?: 0) != (rf?.quarks ?: 0)) return true
        if ((lf?.totalWins ?: 0) != (rf?.totalWins ?: 0)) return true
        if ((lf?.bonusMultApc ?: 0.0) != (rf?.bonusMultApc ?: 0.0)) return true
        if ((lf?.bonusMultAps ?: 0.0) != (rf?.bonusMultAps ?: 0.0)) return true
        if ((lf?.tries ?: emptyMap<String, Int>()) != (rf?.tries ?: emptyMap<String, Int>())) return true
        if ((lf?.wins ?: emptyMap<String, Int>()) != (rf?.wins ?: emptyMap<String, Int>())) return true

        // Conflit gacha : au moins un élément avec un compte différent
        if (countsDiffer(local.gacha?.copies, remote.gacha?.copies)) return true

        // Compteurs permanents : absents avant la v3. Les comparer avec une save plus ancienne
        // ferait remonter un conflit que la résolution ne saurait pas trancher utilement.
        if (local.version >= 3 && remote.version >= 3) {
            if (countsDiffer(local.gacha?.totalEver, remote.gacha?.totalEver)) return true
            if (countsDiffer(local.gacha?.fusionCopies, remote.gacha?.fusionCopies)) return true
        }

        // Conflit tokens éléments
        if (local.elementTokens != remote.elementTokens) return true

        // Conflit tickets gacha
        if (local.gachaTickets?.totalTickets != remote.gachaTickets?.totalTickets) return true

        // Conflit stats de jeux
        if (local.gameStats != remote.gameStats) return true

        return false
    }

    /** Deux tables atomicNumber → compteur diffèrent ? Une clé absente vaut 0. */
    private fun countsDiffer(a: Map<Int, Int>?, b: Map<Int, Int>?): Boolean {
        val left  = a ?: emptyMap()
        val right = b ?: emptyMap()
        return (left.keys + right.keys).any { (left[it] ?: 0) != (right[it] ?: 0) }
    }

    // ─── Construction de l'état local ────────────────────────────────────────

    private suspend fun buildLocalSyncFile(): GamesSyncFile {
        val db = ClickerDatabase.getInstance(appContext)
        val clickerEntity = db.dao().load()

        val collectionStore = PeriodicCollectionStore(appContext)
        val copies = (1..118)
            .associateWith { collectionStore.getCopyCount(it) }
            .filter { it.value > 0 }
        // Compteurs permanents : ils portent les bonus d'éléments et de collection de raretés.
        // Sans eux, une restauration sur un nouvel appareil repartirait des seules copies en
        // stock et effacerait tous les bonus des éléments consommés par des fusions.
        val totalEver = (1..118)
            .associateWith { collectionStore.getTotalEverCount(it) }
            .filter { it.value > 0 }
        val fusionCopies = (1..118)
            .associateWith { collectionStore.getFusionCount(it) }
            .filter { it.value > 0 }

        val elementTokens = ElementTokenRepository(appContext).getBalance()

        val ticketEntity = db.gachaTicketDao().load()
        val gachaTickets = ticketEntity?.let {
            GachaTicketSyncData(totalTickets = it.totalTickets, lastTicketAwardMs = it.lastTicketAwardMs)
        }

        val rawStats = GameStatsRepository(appContext).load()
        val gameStats = GameStatsSyncData(
            solitairePlayed      = rawStats.solitairePlayed,
            solitaireWon         = rawStats.solitaireWon,
            colorStackHardPlayed = rawStats.colorStackHardPlayed,
            colorStackHardWon    = rawStats.colorStackHardWon,
            colorStackHardBestMs = rawStats.colorStackHardBestMs,
            sudokuPlayed         = rawStats.sudokuPlayed,
            sudokuWon            = rawStats.sudokuWon,
            chessPlayed          = rawStats.chessPlayed,
            chessWon             = rawStats.chessWon,
            draughtsPlayed       = rawStats.draughtsPlayed,
            draughtsWon          = rawStats.draughtsWon,
            game2048Played       = rawStats.game2048Played,
            game2048Won          = rawStats.game2048Won,
            blackjackPlayed      = rawStats.blackjackPlayed,
            blackjackWon         = rawStats.blackjackWon,
            pipeTapHardWon       = rawStats.pipeTapHardWon,
            hexRunnerBestMs      = rawStats.hexRunnerBestMs
        )

        // Usines et Big Bang : on ne stocke que les entrées non nulles pour garder le
        // fichier compact. La restauration remet explicitement à 0 les absentes.
        val factoryRepo = FactoryRepository(appContext)
        val factories = FactoriesSyncData(
            factoryRepo.getAllCounts()
                .entries.associate { (type, count) -> type.id to count }
                .filterValues { it > 0 }
        )

        val bigBangRepo = BigBangRepository(appContext)
        val bigBang = BigBangSyncData(
            unlocked     = bigBangRepo.isUnlocked(),
            bigBangCount = bigBangRepo.getBigBangCount(),
            levels       = BigBangBonus.entries
                .associate { it.id to bigBangRepo.getLevel(it) }
                .filterValues { it > 0 }
        )

        val critRepo = CritRepository(appContext)
        val crit = CritSyncData(
            chanceLevel = critRepo.getCritChanceLevel(),
            damageLevel = critRepo.getCritDamageLevel()
        )

        val fusionStore = FusionStore(appContext)
        val fusion = FusionSyncData(
            quarks       = fusionStore.getQuarks(),
            totalWins    = fusionStore.getTotalWins(),
            bonusMultApc = fusionStore.getBonusMultApc(),
            bonusMultAps = fusionStore.getBonusMultAps(),
            tries        = FusionRecipe.entries
                .associate { it.id to fusionStore.getTries(it) }.filterValues { it > 0 },
            wins         = FusionRecipe.entries
                .associate { it.id to fusionStore.getWins(it) }.filterValues { it > 0 }
        )

        val neutrinoRepo = NeutrinoRepository(appContext)

        return GamesSyncFile(
            lastModified  = System.currentTimeMillis(),
            clicker       = clickerEntity?.toSyncData(),
            gacha         = GachaSyncData(copies, totalEver, fusionCopies),
            elementTokens = elementTokens,
            gachaTickets  = gachaTickets,
            gameStats     = gameStats,
            neutrinos         = neutrinoRepo.getBalance(),
            lifetimeNeutrinos = neutrinoRepo.getLifetimeNeutrinos(),
            factories     = factories,
            bigBang       = bigBang,
            crit          = crit,
            achievements  = ClickerAchievementRepository(appContext).loadUnlocked().toList(),
            highScores    = readHighScores(),
            fusion        = fusion
        )
    }

    // ─── Application d'une save choisie ──────────────────────────────────────

    private suspend fun applyLocally(file: GamesSyncFile) {
        // Une save v1 ne transporte pas les neutrinos : le champ y vaut 0 par défaut.
        // L'appliquer quand même viderait le solde local au lieu de le restaurer.
        val carriesNeutrinos = file.version >= 2

        file.clicker?.let { clickerData ->
            val entity = clickerData.toEntity()
            ClickerDatabase.getInstance(appContext).dao().save(
                if (carriesNeutrinos) entity.copy(neutrinosCount = file.neutrinos) else entity
            )
            Log.d(TAG, "État clicker appliqué")
        }

        // Usines : toutes les entrées absentes du fichier valent 0, sinon les usines
        // locales survivraient à la restauration et donneraient une partie hybride.
        file.factories?.let { data ->
            val factoryRepo = FactoryRepository(appContext)
            FactoryType.entries.forEach { type ->
                factoryRepo.setCount(type, data.counts[type.id] ?: 0)
            }
            Log.d(TAG, "Usines appliquées (${data.counts.size} types possédés)")
        }

        file.bigBang?.let { data ->
            val bigBangRepo = BigBangRepository(appContext)
            bigBangRepo.setUnlocked(data.unlocked)
            bigBangRepo.setBigBangCount(data.bigBangCount)
            BigBangBonus.entries.forEach { bonus ->
                bigBangRepo.setLevel(bonus, data.levels[bonus.id] ?: 0)
            }
            Log.d(TAG, "Big Bang appliqué (${data.bigBangCount} prestige(s))")
        }

        file.crit?.let { data ->
            val critRepo = CritRepository(appContext)
            critRepo.setCritChanceLevel(data.chanceLevel)
            critRepo.setCritDamageLevel(data.damageLevel)
            Log.d(TAG, "Niveaux de crit appliqués")
        }

        file.achievements?.let { ids ->
            ClickerAchievementRepository(appContext).saveUnlocked(ids.toSet())
            Log.d(TAG, "Succès appliqués (${ids.size})")
        }

        if (carriesNeutrinos) {
            NeutrinoRepository(appContext).let { repo ->
                repo.setBalance(file.neutrinos)
                repo.setLifetimeNeutrinos(file.lifetimeNeutrinos)
                Log.d(TAG, "Neutrinos appliqués (${file.neutrinos})")
            }
        }

        file.highScores?.let { data ->
            writeHighScores(data)
            Log.d(TAG, "Records appliqués (${data.values.size})")
        }

        // Comme pour les usines : les recettes absentes du fichier sont remises à 0,
        // sinon les compteurs locaux survivraient à la restauration.
        file.fusion?.let { data ->
            val fusionStore = FusionStore(appContext)
            fusionStore.setQuarks(data.quarks)
            fusionStore.setTotalWins(data.totalWins)
            fusionStore.setBonusMultApc(data.bonusMultApc)
            fusionStore.setBonusMultAps(data.bonusMultAps)
            FusionRecipe.entries.forEach { recipe ->
                fusionStore.setRecipeCounters(
                    recipe,
                    tries = data.tries[recipe.id] ?: 0,
                    wins  = data.wins[recipe.id] ?: 0
                )
            }
            Log.d(TAG, "Fusion appliquée (${data.quarks} quarks, ${data.totalWins} réussites)")
        }

        file.gacha?.let { gachaData ->
            val store = PeriodicCollectionStore(appContext)
            store.reset()
            // Union des clés : un élément entièrement consommé par une fusion n'apparaît plus
            // dans `copies` mais garde des compteurs permanents à restaurer.
            val atomicNumbers = gachaData.copies.keys +
                gachaData.totalEver.keys +
                gachaData.fusionCopies.keys
            atomicNumbers.forEach { atomicNumber ->
                store.restoreElement(
                    atomicNumber = atomicNumber,
                    copies       = gachaData.copies[atomicNumber] ?: 0,
                    totalEver    = gachaData.totalEver[atomicNumber] ?: 0,
                    fusionCopies = gachaData.fusionCopies[atomicNumber] ?: 0
                )
            }
            Log.d(TAG, "Collection gacha appliquée (${atomicNumbers.size} éléments)")
        }

        ElementTokenRepository(appContext).setBalance(file.elementTokens)
        Log.d(TAG, "Tokens éléments appliqués (${file.elementTokens})")

        file.gachaTickets?.let { t ->
            ClickerDatabase.getInstance(appContext).gachaTicketDao().save(
                GachaTicketStateEntity(id = 0, totalTickets = t.totalTickets, lastTicketAwardMs = t.lastTicketAwardMs)
            )
            Log.d(TAG, "Tickets gacha appliqués (${t.totalTickets})")
        }

        file.gameStats?.let { s ->
            GameStatsRepository(appContext).save(
                GameStats(
                    solitairePlayed      = s.solitairePlayed,
                    solitaireWon         = s.solitaireWon,
                    colorStackHardPlayed = s.colorStackHardPlayed,
                    colorStackHardWon    = s.colorStackHardWon,
                    colorStackHardBestMs = s.colorStackHardBestMs,
                    sudokuPlayed         = s.sudokuPlayed,
                    sudokuWon            = s.sudokuWon,
                    chessPlayed          = s.chessPlayed,
                    chessWon             = s.chessWon,
                    draughtsPlayed       = s.draughtsPlayed,
                    draughtsWon          = s.draughtsWon,
                    game2048Played       = s.game2048Played,
                    game2048Won          = s.game2048Won,
                    blackjackPlayed      = s.blackjackPlayed,
                    blackjackWon         = s.blackjackWon,
                    pipeTapHardWon       = s.pipeTapHardWon,
                    hexRunnerBestMs      = s.hexRunnerBestMs
                )
            )
            Log.d(TAG, "Stats de jeux appliquées")
        }
    }

    // ─── Helpers Drive ────────────────────────────────────────────────────────

    private fun getDriveClient(): GoogleDriveAppDataClient? {
        val signInManager = GoogleSignInManager(appContext)
        if (!signInManager.isSignedIn()) return null
        val account = signInManager.getSignedInAccount() ?: return null
        return GoogleDriveAppDataClient(appContext, account)
    }

    // ─── Conversions entity ↔ sync data ──────────────────────────────────────

    private fun ClickerStateEntity.toSyncData() = ClickerSyncData(
        atoms     = LayeredNumberData(atomsSign, atomsLayer, atomsMantissa, atomsExponent, atomsValue),
        lifetime  = LayeredNumberData(lifetimeSign, lifetimeLayer, lifetimeMantissa, lifetimeExponent, lifetimeValue),
        perClick  = LayeredNumberData(perClickSign, perClickLayer, perClickMantissa, perClickExponent, perClickValue),
        perSecond = LayeredNumberData(perSecondSign, perSecondLayer, perSecondMantissa, perSecondExponent, perSecondValue),
        godFingerLevel = godFingerLevel,
        starCoreLevel  = starCoreLevel,
        apcToApsLevel  = apcToApsLevel,
        apsToApcLevel  = apsToApcLevel,
        allTimeTotalAtoms = LayeredNumberData(
            allTimeTotalSign, allTimeTotalLayer,
            allTimeTotalMantissa, allTimeTotalExponent, allTimeTotalValue
        )
    )

    private fun ClickerSyncData.toEntity() = ClickerStateEntity(
        id = 0,
        atomsSign = atoms.sign, atomsLayer = atoms.layer,
        atomsMantissa = atoms.mantissa, atomsExponent = atoms.exponent, atomsValue = atoms.value,
        lifetimeSign = lifetime.sign, lifetimeLayer = lifetime.layer,
        lifetimeMantissa = lifetime.mantissa, lifetimeExponent = lifetime.exponent, lifetimeValue = lifetime.value,
        perClickSign = perClick.sign, perClickLayer = perClick.layer,
        perClickMantissa = perClick.mantissa, perClickExponent = perClick.exponent, perClickValue = perClick.value,
        perSecondSign = perSecond.sign, perSecondLayer = perSecond.layer,
        perSecondMantissa = perSecond.mantissa, perSecondExponent = perSecond.exponent, perSecondValue = perSecond.value,
        godFingerLevel = godFingerLevel,
        starCoreLevel  = starCoreLevel,
        apcToApsLevel  = apcToApsLevel,
        apsToApcLevel  = apsToApcLevel,
        allTimeTotalSign     = allTimeTotalAtoms.sign,
        allTimeTotalLayer    = allTimeTotalAtoms.layer,
        allTimeTotalMantissa = allTimeTotalAtoms.mantissa,
        allTimeTotalExponent = allTimeTotalAtoms.exponent,
        allTimeTotalValue    = allTimeTotalAtoms.value
    )

    // ─── High scores : lecture/écriture des SharedPreferences des jeux ────────

    private fun readHighScores(): HighScoresSyncData {
        val values = mutableMapOf<String, Double>()
        HIGH_SCORE_KEYS.forEach { k ->
            val p = appContext.getSharedPreferences(k.prefsName, Context.MODE_PRIVATE)
            // Jamais joué : on n'invente pas un score de 0, on laisse la clé absente.
            if (!p.contains(k.key)) return@forEach
            values[k.id] = when (k.type) {
                HighScoreType.INT   -> p.getInt(k.key, 0).toDouble()
                HighScoreType.LONG  -> p.getLong(k.key, 0L).toDouble()
                HighScoreType.FLOAT -> p.getFloat(k.key, 0f).toDouble()
            }
        }
        return HighScoresSyncData(values)
    }

    private fun writeHighScores(data: HighScoresSyncData) {
        HIGH_SCORE_KEYS.forEach { k ->
            // Absent de la sauvegarde choisie : on ne touche pas au record local.
            val v = data.values[k.id] ?: return@forEach
            appContext.getSharedPreferences(k.prefsName, Context.MODE_PRIVATE).edit {
                when (k.type) {
                    HighScoreType.INT   -> putInt(k.key, v.toInt())
                    HighScoreType.LONG  -> putLong(k.key, v.toLong())
                    HighScoreType.FLOAT -> putFloat(k.key, v.toFloat())
                }
            }
        }
    }
}
