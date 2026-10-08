package com.Atom2Universe.app.cloud.projects

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.sync.SharedGameStats
import com.Atom2Universe.app.music.sync.DriveFileInfo
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Les modules créatifs qui publient leurs projets sur Drive, un fichier zip par projet. */
enum class CloudModule(val prefix: String, val labelRes: Int) {
    PIXEL_ART("px_", R.string.cloud_cat_pixelart),
    ZOOM("zc_", R.string.cloud_cat_zoom),
    CANVAS("cv_", R.string.cloud_cat_canvas);

    /** Le nom du fichier sur Drive. Que des caractères sûrs : l'identifiant d'un projet, jamais son nom. */
    fun fileName(id: String) = "$prefix$id.zip"

    /** L'identifiant du projet que ce nom de fichier désigne, ou null s'il n'est pas de ce module. */
    fun idOf(fileName: String): String? =
        if (fileName.startsWith(prefix) && fileName.endsWith(SUFFIX) && fileName.length > prefix.length + SUFFIX.length)
            fileName.substring(prefix.length, fileName.length - SUFFIX.length)
        else null

    companion object {
        private const val SUFFIX = ".zip"

        fun ofFile(fileName: String): CloudModule? = entries.firstOrNull { it.idOf(fileName) != null }
    }
}

/** Un projet tel qu'il est rangé sur Drive. */
class CloudProject(
    val module: CloudModule,
    val id: String,
    val fileId: String,
    val name: String,
    val size: Long,
    /** Date de dernière écriture du fichier chez Drive. */
    val driveModified: Long,
    /** La version : ne se compare que par égalité. */
    val seq: Long,
    val deviceName: String,
)

/**
 * Ce qu'un module fournit pour que ses projets voyagent : le reste (Drive, versions, conflits)
 * est dans [CloudProjectSync]. Toutes ces fonctions travaillent sur disque : à appeler hors du fil principal.
 */
interface CloudProjectAdapter {
    val module: CloudModule

    /** La date de dernière modification du projet, ou null s'il n'existe pas sur cet appareil. */
    fun localModified(id: String): Long?

    fun nameOf(id: String): String?

    fun exportTo(id: String, out: OutputStream)

    /** Pose le projet sous [id] (en remplaçant l'éventuel projet de même identifiant). Faux = illisible, rien n'a changé. */
    fun importAs(id: String, input: InputStream): Boolean

    /** Pose le projet comme un nouveau projet, son nom suivi d'une espace puis de [nameSuffix]. Retourne son identifiant, ou null. */
    fun importCopy(input: InputStream, nameSuffix: String): String?
}

/**
 * Les projets créatifs sur Drive : même mécanique que la ferme ([com.Atom2Universe.app.games.farm.FarmSyncManager]),
 * mais par projet.
 *
 * Deux valeurs, gardées côte à côte pour chaque projet :
 * - `seq`, la version du cloud sur laquelle ce projet local s'appuie. Tant que le fichier là-haut
 *   porte ce numéro, personne d'autre n'a touché au projet ;
 * - `sent`, la date de modification locale au moment du dernier échange. Si elle a changé, on a
 *   dessiné depuis.
 *
 * Un seul des deux a bougé : la réponse est évidente, on envoie ou on télécharge. Les deux : il
 * existe deux vraies versions, et c'est la personne qui choisit — dont « garder les deux », que
 * seuls des projets indépendants permettent.
 *
 * Rien ne part tout seul : la sync se lance depuis la galerie, quand l'éditeur a fini d'écrire.
 */
object CloudProjectSync {

    private const val TAG = "CloudProjectSync"
    private const val PREFS = "cloud_projects"
    private const val MIME_ZIP = "application/zip"

    /** Deux opérations sur le même projet ne doivent jamais se chevaucher : elles se disputeraient un fichier. */
    private val lock = Mutex()

    sealed class Outcome {
        data object UpToDate : Outcome()
        data object Uploaded : Outcome()
        data object Downloaded : Outcome()
        /** Le cloud a été gardé comme copie ; le projet local, lui, reste à envoyer. */
        data object KeptBoth : Outcome()
        data object NotSignedIn : Outcome()
        /** Drive n'a pas répondu, ou a refusé. Rien n'a été décidé. */
        data object Failed : Outcome()
        /** Le fichier cloud est illisible (ou d'un autre format) : le projet local n'a pas été touché. */
        data object Unreadable : Outcome()
        /** Les deux côtés ont bougé depuis le dernier échange. */
        data class Conflict(val remote: CloudProject) : Outcome()
    }

    enum class Choice { KEEP_LOCAL, KEEP_CLOUD, KEEP_BOTH }

    /** L'état d'un projet, pour la pastille sur sa tuile. */
    enum class Badge { NONE, SYNCED, PENDING }

    // ─── Ce que fait le bouton « Google sync » ────────────────────────────────

    suspend fun sync(context: Context, adapter: CloudProjectAdapter, id: String): Outcome =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val app = context.applicationContext
                val client = driveClient(app) ?: return@withLock Outcome.NotSignedIn
                val localModified = adapter.localModified(id) ?: return@withLock Outcome.Failed
                val remote = when (val found = find(client, adapter.module, id)) {
                    Found.Failed -> return@withLock Outcome.Failed
                    Found.Absent -> null
                    is Found.One -> found.project
                }
                // Rien là-haut (jamais envoyé, ou vidé depuis l'écran Cloud) : ce projet est la seule copie.
                if (remote == null) return@withLock upload(app, client, adapter, id, localModified, null)

                val state = state(app, adapter.module, id)
                when (decide(localModified, state.sent, remote.seq, state.seq)) {
                    Decision.NOTHING -> Outcome.UpToDate
                    Decision.UPLOAD -> upload(app, client, adapter, id, localModified, remote)
                    Decision.DOWNLOAD -> download(app, client, adapter, id, remote)
                    Decision.CONFLICT -> Outcome.Conflict(remote)
                }
            }
        }

    /** Applique le choix fait devant un [Outcome.Conflict]. */
    suspend fun resolve(
        context: Context,
        adapter: CloudProjectAdapter,
        id: String,
        remote: CloudProject,
        choice: Choice,
        copySuffix: String,
    ): Outcome = withContext(Dispatchers.IO) {
        lock.withLock {
            val app = context.applicationContext
            val client = driveClient(app) ?: return@withLock Outcome.NotSignedIn
            val localModified = adapter.localModified(id) ?: return@withLock Outcome.Failed
            // Le cloud a peut-être encore bougé pendant que la question était posée.
            val current = when (val found = find(client, adapter.module, id)) {
                Found.Failed -> return@withLock Outcome.Failed
                Found.Absent -> null
                is Found.One -> found.project
            }
            if (current != null && current.seq != remote.seq) return@withLock Outcome.Conflict(current)
            when (choice) {
                Choice.KEEP_LOCAL -> upload(app, client, adapter, id, localModified, current)
                Choice.KEEP_CLOUD ->
                    if (current == null) Outcome.Failed else download(app, client, adapter, id, current)
                Choice.KEEP_BOTH -> {
                    if (current == null) return@withLock Outcome.Failed
                    val copy = downloadCopy(app, client, adapter, current, copySuffix)
                    if (copy != Outcome.Downloaded) return@withLock copy
                    // Le projet local s'appuie désormais sur cette version du cloud, mais n'est pas
                    // marqué envoyé : il reste « à envoyer » et la prochaine sync le publie par-dessus.
                    prefs(app).edit { putLong(seqKey(adapter.module, id), current.seq) }
                    Outcome.KeptBoth
                }
            }
        }
    }

    enum class Decision { NOTHING, UPLOAD, DOWNLOAD, CONFLICT }

    /**
     * La règle, seule : qui a bougé depuis le dernier échange ?
     *
     * @param sent la date locale notée au dernier échange, null si ce projet n'a jamais rien échangé
     * @param baseSeq la version du cloud sur laquelle ce projet s'appuie (0 si aucune)
     */
    internal fun decide(localModified: Long, sent: Long?, remoteSeq: Long, baseSeq: Long): Decision {
        val localChanged = sent != localModified
        val cloudChanged = remoteSeq != baseSeq
        return when {
            !localChanged && !cloudChanged -> Decision.NOTHING
            !cloudChanged -> Decision.UPLOAD
            !localChanged -> Decision.DOWNLOAD
            else -> Decision.CONFLICT
        }
    }

    // ─── Importer depuis le cloud ─────────────────────────────────────────────

    /** Les projets d'un module qui sont sur Drive, le plus récent d'abord. Null si Drive n'a pas répondu. */
    suspend fun listCloud(context: Context, module: CloudModule): List<CloudProject>? =
        withContext(Dispatchers.IO) {
            val client = driveClient(context.applicationContext) ?: return@withContext null
            val files = client.listFileDetailsChecked() ?: return@withContext null
            files.mapNotNull { toProject(module, it) }.sortedByDescending { it.driveModified }
        }

    /** Apporte sur cet appareil un projet qui n'y est pas (ou le remet à jour s'il y est déjà, sans question). */
    suspend fun fetch(context: Context, adapter: CloudProjectAdapter, remote: CloudProject): Outcome =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val app = context.applicationContext
                val client = driveClient(app) ?: return@withLock Outcome.NotSignedIn
                download(app, client, adapter, remote.id, remote)
            }
        }

    // ─── Retirer du cloud ─────────────────────────────────────────────────────

    /** Supprime la copie cloud d'un projet. Le projet local n'est pas touché. */
    suspend fun removeFromCloud(context: Context, module: CloudModule, id: String): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val app = context.applicationContext
                val client = driveClient(app) ?: return@withLock false
                val files = client.listFileDetailsChecked("name = '${module.fileName(id)}'") ?: return@withLock false
                if (files.isNotEmpty() && client.deleteByIds(files.map { it.id }) != files.size) return@withLock false
                forget(app, module, id)
                true
            }
        }

    /**
     * Oublie ce que ce projet croit avoir échangé : à appeler quand sa copie cloud disparaît par
     * un autre chemin (l'écran Cloud). La prochaine sync le republiera, et la pastille s'éteint.
     */
    fun forget(context: Context, module: CloudModule, id: String) {
        prefs(context.applicationContext).edit {
            remove(seqKey(module, id))
            remove(sentKey(module, id))
        }
    }

    /** Variante pour des fichiers Drive qu'on vient de supprimer : on retrouve les projets par leur nom. */
    fun forgetFiles(context: Context, files: List<DriveFileInfo>) {
        for (f in files) {
            val module = CloudModule.ofFile(f.name) ?: continue
            forget(context, module, module.idOf(f.name) ?: continue)
        }
    }

    // ─── Pastille ─────────────────────────────────────────────────────────────

    /** Lit seulement les préférences : aucun appel réseau, donc sans danger dans un affichage de liste. */
    fun badge(context: Context, module: CloudModule, id: String, localModified: Long): Badge {
        val st = state(context.applicationContext, module, id)
        return when {
            st.sent == null -> Badge.NONE
            st.sent == localModified -> Badge.SYNCED
            else -> Badge.PENDING
        }
    }

    // ─── Envoi et réception ───────────────────────────────────────────────────

    private suspend fun upload(
        context: Context,
        client: GoogleDriveAppDataClient,
        adapter: CloudProjectAdapter,
        id: String,
        localModified: Long,
        remote: CloudProject?,
    ): Outcome {
        val module = adapter.module
        val tmp = tempFile(context, "up_${module.prefix}$id")
        try {
            try {
                tmp.outputStream().buffered().use { adapter.exportTo(id, it) }
            } catch (e: Exception) {
                Log.e(TAG, "Export of ${module.fileName(id)} failed", e)
                return Outcome.Failed
            }
            val seq = nextSeq(maxOf(state(context, module, id).seq, remote?.seq ?: 0L))
            val description = describe(adapter.nameOf(id) ?: id, seq, SharedGameStats.deviceName(context))
            val sent = client.uploadFile(module.fileName(id), tmp, MIME_ZIP, description, remote?.fileId)
            if (sent == null) return Outcome.Failed
            // Envoyer et le noter ne font qu'un : si le travail s'arrêtait entre les deux, Drive
            // porterait une version que cet appareil ne connaît pas, et le prochain échange
            // prendrait notre propre envoi pour celui d'un autre appareil.
            withContext(NonCancellable) {
                prefs(context).edit {
                    putLong(seqKey(module, id), seq)
                    putLong(sentKey(module, id), localModified)
                }
            }
            Log.d(TAG, "${module.fileName(id)} published as version $seq (${tmp.length() / 1024} kB)")
            return Outcome.Uploaded
        } finally {
            tmp.delete()
        }
    }

    private suspend fun download(
        context: Context,
        client: GoogleDriveAppDataClient,
        adapter: CloudProjectAdapter,
        id: String,
        remote: CloudProject,
    ): Outcome {
        val tmp = tempFile(context, "down_${adapter.module.prefix}$id")
        try {
            if (!client.downloadToFile(remote.fileId, tmp)) return Outcome.Failed
            val ok = try {
                tmp.inputStream().buffered().use { adapter.importAs(id, it) }
            } catch (e: Exception) {
                Log.e(TAG, "Import of ${remote.module.fileName(id)} failed", e)
                false
            }
            if (!ok) return Outcome.Unreadable
            val localModified = adapter.localModified(id) ?: return Outcome.Failed
            prefs(context).edit {
                putLong(seqKey(adapter.module, id), remote.seq)
                putLong(sentKey(adapter.module, id), localModified)
            }
            return Outcome.Downloaded
        } finally {
            tmp.delete()
        }
    }

    private suspend fun downloadCopy(
        context: Context,
        client: GoogleDriveAppDataClient,
        adapter: CloudProjectAdapter,
        remote: CloudProject,
        suffix: String,
    ): Outcome {
        val tmp = tempFile(context, "copy_${adapter.module.prefix}${remote.id}")
        try {
            if (!client.downloadToFile(remote.fileId, tmp)) return Outcome.Failed
            val newId = try {
                tmp.inputStream().buffered().use { adapter.importCopy(it, suffix) }
            } catch (e: Exception) {
                Log.e(TAG, "Copy import of ${remote.module.fileName(remote.id)} failed", e)
                null
            }
            return if (newId == null) Outcome.Unreadable else Outcome.Downloaded
        } finally {
            tmp.delete()
        }
    }

    // ─── Outils ───────────────────────────────────────────────────────────────

    private sealed class Found {
        data object Failed : Found()
        data object Absent : Found()
        class One(val project: CloudProject) : Found()
    }

    private suspend fun find(client: GoogleDriveAppDataClient, module: CloudModule, id: String): Found {
        // Un identifiant de projet n'a que des lettres, des chiffres et des tirets ; on le vérifie
        // parce qu'il entre tel quel dans une requête Drive.
        if (!id.all { it.isLetterOrDigit() || it == '-' }) return Found.Failed
        val files = client.listFileDetailsChecked("name = '${module.fileName(id)}'") ?: return Found.Failed
        // Deux appareils qui créent en même temps peuvent laisser deux fichiers de même nom : le plus récent fait foi.
        val newest = files.maxByOrNull { it.modifiedTime } ?: return Found.Absent
        return toProject(module, newest)?.let { Found.One(it) } ?: Found.Failed
    }

    /** Le projet qu'un fichier du dossier Drive représente, ou null si ce n'est pas un projet créatif. */
    fun projectOf(file: DriveFileInfo): CloudProject? =
        CloudModule.ofFile(file.name)?.let { toProject(it, file) }

    private fun toProject(module: CloudModule, file: DriveFileInfo): CloudProject? {
        val id = module.idOf(file.name) ?: return null
        val meta = try { file.description?.let { JSONObject(it) } } catch (e: Exception) { null }
        return CloudProject(
            module = module,
            id = id,
            fileId = file.id,
            name = meta?.optString("name", "")?.takeIf { it.isNotBlank() } ?: id,
            size = file.size,
            driveModified = file.modifiedTime,
            // Sans version écrite (fichier posé autrement) : la date de Drive en tient lieu, jamais 0.
            seq = meta?.optLong("seq", 0L)?.takeIf { it != 0L } ?: file.modifiedTime,
            deviceName = meta?.optString("device", "") ?: "",
        )
    }

    private fun describe(name: String, seq: Long, deviceName: String): String =
        JSONObject().put("v", 1).put("name", name).put("seq", seq).put("device", deviceName).toString()

    /**
     * Un numéro qu'aucun appareil n'a encore employé. Les versions ne se comparent que par égalité :
     * ce qui compte est qu'une ne revienne jamais. Un simple +1 repartirait de 1 après un vidage du
     * cloud et pourrait retomber sur le numéro qu'un autre appareil garde pour base ; une valeur
     * d'horloge ne peut pas. Le max la fait avancer même si l'horloge a reculé.
     */
    private fun nextSeq(base: Long): Long = maxOf(base + 1, System.currentTimeMillis())

    private class State(val seq: Long, val sent: Long?)

    private fun state(context: Context, module: CloudModule, id: String): State {
        val p = prefs(context)
        return State(
            seq = p.getLong(seqKey(module, id), 0L),
            sent = if (p.contains(sentKey(module, id))) p.getLong(sentKey(module, id), 0L) else null,
        )
    }

    private fun seqKey(module: CloudModule, id: String) = "${module.prefix}$id.seq"
    private fun sentKey(module: CloudModule, id: String) = "${module.prefix}$id.sent"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun tempFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "cloud_projects").apply { mkdirs() }
        return File(dir, "$name.tmp")
    }

    private fun driveClient(context: Context): GoogleDriveAppDataClient? {
        val signIn = GoogleSignInManager(context)
        if (!signIn.isSignedIn()) return null
        val account = signIn.getSignedInAccount() ?: return null
        return GoogleDriveAppDataClient(context, account)
    }
}
