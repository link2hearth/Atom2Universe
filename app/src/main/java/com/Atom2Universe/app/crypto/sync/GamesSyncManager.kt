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
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.fusion.FusionRecipe
import com.Atom2Universe.app.crypto.fusion.FusionStore
import com.Atom2Universe.app.music.sync.DeviceIdentity
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object GamesSyncManager {

    private const val TAG = "GamesSyncManager"
    private const val SYNC_FILE = "games_state.json"
    private const val RESTORE_PREFS = "games_sync_state"
    private const val KEY_RESTORE_GENERATION = "restore_generation"

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

    /**
     * Augmente à chaque partie restaurée depuis le cloud. Le clicker note la valeur
     * à son ouverture ; s'il la retrouve changée en revenant au premier plan, il
     * relit le disque au lieu de continuer avec la partie qu'il avait en mémoire.
     */
    fun restoreGeneration(context: Context): Long =
        context.getSharedPreferences(RESTORE_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_RESTORE_GENERATION, 0L)

    // ─── Point d'entrée : sync initial ───────────────────────────────────────

    /**
     * Records, compteurs et suppressions ne demandent jamais rien à personne : la sync
     * manuelle et la sync automatique passent toutes deux par ici. Le mutex empêche
     * les deux de se croiser (la nuit, ou pendant qu'on appuie sur le bouton).
     */
    private val mutex = Mutex()

    /** Les parts partagées, fusionnées, prêtes à être posées sur n'importe quel fichier. */
    private data class SharedParts(
        val highScores: HighScoresSyncData,
        val devices: Map<String, DeviceCountersData>,
        val resets: Map<String, Long>
    )

    private fun GamesSyncFile.withShared(parts: SharedParts) =
        copy(highScores = parts.highScores, devices = parts.devices, resets = parts.resets)

    /**
     * Deux règles pour deux sortes de données.
     *
     * - La partie du clicker (atomes, usines, gacha, neutrinos…) forme un tout : mélanger
     *   deux parties donnerait une partie qui n'a jamais existé. Si elles diffèrent,
     *   l'utilisateur choisit laquelle garder.
     * - Les records et les compteurs, eux, se fusionnent sans question (voir [mergeShared]).
     *
     * Records et compteurs sont réglés AVANT la question du clicker, pour que refuser
     * de choisir ne prive de rien.
     */
    suspend fun syncGames(): SyncResult = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext SyncResult.Error("Not initialized")

        try {
            val driveClient = getDriveClient()
                ?: return@withContext SyncResult.Error("Non connecté à Google")

            mutex.withLock {
                val remoteRaw = readRemote(driveClient)
                val shared = mergeShared(remoteRaw)
                val localFile = buildLocalSyncFile().withShared(shared)

                // Pas de partie dans le cloud (jamais synchronisé, ou seule la sync
                // automatique est passée) → on publie la nôtre, sans question.
                if (remoteRaw?.clicker == null) {
                    driveClient.writeJsonFile(SYNC_FILE, localFile.toJson())
                    Log.d(TAG, "Aucune partie dans le cloud — partie locale publiée")
                    return@withLock SyncResult.Success("Sauvegarde initiale envoyée sur Drive")
                }

                // Les parties sont identiques → on publie records et compteurs à jour
                if (!conflictExists(localFile, remoteRaw)) {
                    driveClient.writeJsonFile(SYNC_FILE, localFile.toJson())
                    Log.d(TAG, "Parties identiques, records et compteurs publiés")
                    return@withLock SyncResult.Success("Déjà à jour")
                }

                // Les parts partagées du cloud sont publiées tout de suite, sans toucher à sa
                // partie : si l'utilisateur ferme la question sans choisir, rien n'est perdu.
                val remoteFile = remoteRaw.withShared(shared)
                driveClient.writeJsonFile(SYNC_FILE, remoteFile.toJson())

                Log.d(TAG, "Conflit détecté — demande utilisateur")
                SyncResult.Conflict(local = localFile, remote = remoteFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sync", e)
            SyncResult.Error("Erreur : ${e.message}")
        }
    }

    /**
     * La part automatique : records, compteurs et suppressions, jamais la partie du
     * clicker. Appelée par la sync de la nuit et celle qui suit une écoute.
     *
     * Si le cloud n'a encore aucun fichier, on n'y publie que les parts partagées :
     * la partie du clicker attend que l'utilisateur la synchronise lui-même.
     */
    suspend fun syncSharedStats(): SyncResult = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext SyncResult.Error("Not initialized")

        try {
            val driveClient = getDriveClient()
                ?: return@withContext SyncResult.Error("Non connecté à Google")

            mutex.withLock {
                val remoteRaw = readRemote(driveClient)
                val shared = mergeShared(remoteRaw)
                val base = remoteRaw ?: GamesSyncFile(lastModified = System.currentTimeMillis())
                driveClient.writeJsonFile(SYNC_FILE, base.withShared(shared).toJson())
                Log.d(TAG, "Records et compteurs synchronisés (auto)")
                SyncResult.Success("Records et compteurs synchronisés")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sync auto", e)
            SyncResult.Error("Erreur : ${e.message}")
        }
    }

    private suspend fun readRemote(driveClient: GoogleDriveAppDataClient): GamesSyncFile? {
        Log.d(TAG, "Téléchargement depuis Drive…")
        return driveClient.readJsonFile(SYNC_FILE)?.let {
            try { GamesSyncFile.fromJson(it) } catch (e: Exception) {
                Log.e(TAG, "Erreur parsing remote", e); null
            }
        }
    }

    /**
     * Fusionne les parts partagées du cloud avec celles d'ici, applique le résultat
     * localement, et le renvoie pour publication.
     */
    private suspend fun mergeShared(remoteRaw: GamesSyncFile?): SharedParts {
        // 1. Suppressions. Une suppression faite ailleurs et inconnue ici efface la
        //    valeur locale ; une suppression faite ici et inconnue du cloud écarte la
        //    valeur du cloud. On compare motif par motif, la date la plus récente gagne.
        val localResets = SharedGameStats.resets(appContext)
        val remoteResets = remoteRaw?.resets ?: emptyMap()
        val newerHere = localResets.filter { (p, at) -> at > (remoteResets[p] ?: 0L) }.keys
        remoteResets.forEach { (pattern, at) ->
            if (at > (localResets[pattern] ?: 0L)) SharedGameStats.eraseLocal(appContext, pattern)
        }
        val mergedResets = (localResets.keys + remoteResets.keys).associateWith {
            maxOf(localResets[it] ?: 0L, remoteResets[it] ?: 0L)
        }
        SharedGameStats.saveResets(appContext, mergedResets)

        // 2. Tableau général : le meilleur des deux côtés, appliqué tout de suite ici.
        val remoteScores = remoteRaw?.highScores?.let { scores ->
            HighScoresSyncData(scores.values.filterKeys { id ->
                newerHere.none { SharedGameStats.patternMatches(it, id) }
            })
        }
        val bestScores = HighScoresSyncData.merge(readHighScores(), remoteScores)
        writeHighScores(bestScores)

        // 3. Compteurs : les autres appareils tels que le cloud les connaît, sans ce
        //    qu'une suppression plus récente que leur dernière publication a effacé.
        val myId = DeviceIdentity.getDeviceId(appContext)
        val others = (remoteRaw?.devices ?: emptyMap())
            .filterKeys { it != myId }
            .mapValues { (_, device) ->
                device.copy(counters = device.counters.filter { (id, _) ->
                    SharedGameStats.resetTimeFor(mergedResets, id) <= device.updatedAt
                })
            }
        SharedGameStats.saveOtherDevices(appContext, others)
        val devices = others + (myId to DeviceCountersData(
            name      = SharedGameStats.deviceName(appContext),
            updatedAt = System.currentTimeMillis(),
            counters  = SharedGameStats.readLocalCounters(appContext)
        ))

        return SharedParts(bestScores, devices, mergedResets)
    }

    // ─── Résolution du conflit par l'utilisateur ──────────────────────────────

    suspend fun resolveConflict(chosen: GamesSyncFile): SyncResult = withContext(Dispatchers.IO) {
        try {
            val driveClient = getDriveClient()
                ?: return@withContext SyncResult.Error("Non connecté à Google")

            mutex.withLock {
                // La question a pu rester ouverte longtemps, et la sync automatique passer
                // entre-temps : on refusionne les parts partagées plutôt que de publier
                // celles, périmées, que portait le fichier choisi.
                val shared = mergeShared(readRemote(driveClient))
                val finalFile = chosen.withShared(shared)

                // Uploader la save choisie
                val uploaded = driveClient.writeJsonFile(SYNC_FILE, finalFile.toJson())
                if (!uploaded) return@withLock SyncResult.Error("Échec de l'upload")

                // Appliquer localement
                applyLocally(finalFile)
                // Un clicker resté ouvert en arrière-plan tient encore l'ancienne partie en
                // mémoire : sans ce signal, sa sauvegarde automatique l'écrirait par-dessus.
                appContext.getSharedPreferences(RESTORE_PREFS, Context.MODE_PRIVATE).edit {
                    putLong(KEY_RESTORE_GENERATION, restoreGeneration(appContext) + 1)
                }

                Log.d(TAG, "Conflit résolu, save appliquée")
                SyncResult.Success("Synchronisation réussie")
            }
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

        // Les records ne sont pas comparés ici : ils se fusionnent, sans conflit possible.

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

        // Les stats de parties ne sont plus comparées : elles s'additionnent par appareil.

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

        // Records : déjà fusionnés dans les deux fichiers proposés, writeHighScores
        // n'écrit que ce qui améliore l'existant.
        file.highScores?.let { data ->
            writeHighScores(data)
            Log.d(TAG, "Records appliqués (${data.values.size})")
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

    private suspend fun readHighScores(): HighScoresSyncData {
        val values = mutableMapOf<String, Double>()
        // Particules : ses records sont dans sa base Room, pas dans des préférences.
        ParticulesRecords.read(appContext).forEach { (key, v) ->
            if (HIGH_SCORE_KEYS.any { it.matches(ParticulesRecords.SOURCE, key) }) {
                values[highScoreId(ParticulesRecords.SOURCE, key)] = v
            }
        }
        HIGH_SCORE_KEYS.forEach { k ->
            if (k.prefsName == ParticulesRecords.SOURCE) return@forEach
            val p = appContext.getSharedPreferences(k.prefsName, Context.MODE_PRIVATE)
            // Jamais joué : la clé est absente (ou vaut 0), on n'invente pas de score.
            p.all.forEach { (key, raw) ->
                if (!k.matches(k.prefsName, key)) return@forEach
                val v = (raw as? Number)?.toDouble() ?: return@forEach
                if (v > 0.0) values[highScoreId(k.prefsName, key)] = v
            }
        }
        return HighScoresSyncData(values)
    }

    /** N'écrit que ce qui améliore le record local : relancer la sync ne dégrade jamais rien. */
    private suspend fun writeHighScores(data: HighScoresSyncData) {
        val particules = ParticulesRecords.read(appContext)
        data.values.forEach { (id, v) ->
            // Inconnu de cette version : on ne sait ni où ni sous quel type l'écrire.
            val (k, key) = findHighScoreKey(id) ?: return@forEach
            if (v <= 0.0) return@forEach
            val isParticules = k.prefsName == ParticulesRecords.SOURCE
            val p = appContext.getSharedPreferences(k.prefsName, Context.MODE_PRIVATE)
            val current = if (isParticules) {
                particules[key]
            } else {
                (p.all[key] as? Number)?.toDouble()?.takeIf { it > 0.0 }
            }
            val improves = current == null ||
                if (k.better == RecordDirection.LOWER) v < current else v > current
            if (!improves) return@forEach
            if (isParticules) {
                ParticulesRecords.write(appContext, key, v)
                return@forEach
            }
            p.edit {
                when (k.type) {
                    HighScoreType.INT   -> putInt(key, v.toInt())
                    HighScoreType.LONG  -> putLong(key, v.toLong())
                    HighScoreType.FLOAT -> putFloat(key, v.toFloat())
                }
            }
        }
    }
}
