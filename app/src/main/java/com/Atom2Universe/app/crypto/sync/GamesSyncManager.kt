package com.Atom2Universe.app.crypto.sync

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.crypto.clicker.ClickerDatabase
import com.Atom2Universe.app.crypto.clicker.ClickerStateEntity
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
        // Conflit clicker : atomes différents
        val localAtoms  = local.clicker?.atoms
        val remoteAtoms = remote.clicker?.atoms
        if (localAtoms != null && remoteAtoms != null && localAtoms != remoteAtoms) return true

        // Conflit gacha : au moins un élément avec un compte différent
        val localCopies  = local.gacha?.copies  ?: emptyMap()
        val remoteCopies = remote.gacha?.copies ?: emptyMap()
        val allKeys = localCopies.keys + remoteCopies.keys
        if (allKeys.any { (localCopies[it] ?: 0) != (remoteCopies[it] ?: 0) }) return true

        return false
    }

    // ─── Construction de l'état local ────────────────────────────────────────

    private suspend fun buildLocalSyncFile(): GamesSyncFile {
        val clickerEntity = ClickerDatabase.getInstance(appContext).dao().load()

        val collectionStore = PeriodicCollectionStore(appContext)
        val copies = (1..118)
            .associateWith { collectionStore.getCopyCount(it) }
            .filter { it.value > 0 }

        return GamesSyncFile(
            lastModified = System.currentTimeMillis(),
            clicker      = clickerEntity?.toSyncData(),
            gacha        = GachaSyncData(copies)
        )
    }

    // ─── Application d'une save choisie ──────────────────────────────────────

    private suspend fun applyLocally(file: GamesSyncFile) {
        file.clicker?.let { clickerData ->
            ClickerDatabase.getInstance(appContext).dao().save(clickerData.toEntity())
            Log.d(TAG, "État clicker appliqué")
        }

        file.gacha?.let { gachaData ->
            val store = PeriodicCollectionStore(appContext)
            store.reset()
            gachaData.copies.forEach { (atomicNumber, count) ->
                store.setCopyCount(atomicNumber, count)
            }
            Log.d(TAG, "Collection gacha appliquée (${gachaData.copies.size} éléments)")
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
        starCoreLevel  = starCoreLevel
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
        starCoreLevel  = starCoreLevel
    )
}
