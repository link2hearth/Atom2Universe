package com.Atom2Universe.app.notes.sync

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/*
 * Le cœur de la synchronisation des notes, sans rien d'Android : testé en JVM.
 *
 * Le principe, pour chaque note : trois versions se rencontrent.
 *   - la version d'ici (L),
 *   - la version du cloud (C),
 *   - l'empreinte de ce qu'on a échangé la dernière fois (H0, « la base »).
 * Seul un côté a bougé depuis la base : on prend sa version. Les deux ont bougé : la plus récente
 * gagne, et l'autre n'est jamais perdue, elle devient une note « (conflit) ».
 *
 * Une suppression ne s'écrit que par une pierre tombale (`purged`) : une note absente du cloud n'est
 * pas une note supprimée (deux appareils qui écrivent en même temps peuvent s'effacer l'un l'autre),
 * c'est une note à republier.
 */

/** Une note, telle qu'elle voyage : groupe et tags par leur nom (les identifiants ne valent rien d'un appareil à l'autre). */
data class SyncNote(
    val uuid: String,
    val title: String,
    val content: String,
    val groupName: String?,
    val tags: List<String>,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val colorHex: String?,
    val textColorMode: String,
    val dateCreated: Long,
    val dateModified: Long,
    val deletedAt: Long?,
) {
    /**
     * L'empreinte de ce que la note **dit** : tout sauf les dates. Les noms de groupe et de tag sont
     * comparés sans tenir compte de la casse (c'est ainsi qu'on les retrouve), sinon « Travail » et
     * « travail » se renverraient la même note sans fin.
     */
    fun hash(): String {
        val canonical = listOf(
            title, content,
            groupName?.trim()?.lowercase() ?: "",
            tags.map { it.trim().lowercase() }.sorted().joinToString("\u0001"),
            isPinned.toString(), isFavorite.toString(), colorHex ?: "", textColorMode, (deletedAt ?: 0L).toString(),
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("uuid", uuid).put("title", title).put("content", content)
        .put("group", groupName ?: JSONObject.NULL).put("tags", JSONArray(tags))
        .put("pinned", isPinned).put("favorite", isFavorite)
        .put("color", colorHex ?: JSONObject.NULL).put("textColorMode", textColorMode)
        .put("created", dateCreated).put("modified", dateModified)
        .put("deletedAt", deletedAt ?: JSONObject.NULL)

    companion object {
        fun fromJson(j: JSONObject): SyncNote = SyncNote(
            uuid = j.getString("uuid"),
            title = j.optString("title", ""),
            content = j.optString("content", ""),
            groupName = if (j.isNull("group")) null else j.getString("group"),
            tags = j.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            isPinned = j.optBoolean("pinned", false),
            isFavorite = j.optBoolean("favorite", false),
            colorHex = if (j.isNull("color")) null else j.optString("color"),
            textColorMode = j.optString("textColorMode", "auto"),
            dateCreated = j.optLong("created"),
            dateModified = j.optLong("modified"),
            deletedAt = if (j.isNull("deletedAt")) null else j.getLong("deletedAt"),
        )
    }
}

data class SyncGroup(
    val name: String,
    val description: String,
    val colorHex: String?,
    val textColorMode: String,
    val iconName: String?,
    val position: Int,
    val dateModified: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("description", description).put("color", colorHex ?: JSONObject.NULL)
        .put("textColorMode", textColorMode).put("icon", iconName ?: JSONObject.NULL)
        .put("position", position).put("modified", dateModified)

    companion object {
        fun fromJson(j: JSONObject) = SyncGroup(
            name = j.getString("name"), description = j.optString("description", ""),
            colorHex = if (j.isNull("color")) null else j.optString("color"),
            textColorMode = j.optString("textColorMode", "auto"),
            iconName = if (j.isNull("icon")) null else j.optString("icon"),
            position = j.optInt("position"), dateModified = j.optLong("modified"),
        )
    }
}

data class SyncTag(
    val name: String,
    val colorHex: String?,
    val sortOrder: Int,
    val textColorMode: String,
    val categoryName: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("color", colorHex ?: JSONObject.NULL).put("sortOrder", sortOrder)
        .put("textColorMode", textColorMode).put("category", categoryName ?: JSONObject.NULL)

    companion object {
        fun fromJson(j: JSONObject) = SyncTag(
            name = j.getString("name"), colorHex = if (j.isNull("color")) null else j.optString("color"),
            sortOrder = j.optInt("sortOrder"), textColorMode = j.optString("textColorMode", "auto"),
            categoryName = if (j.isNull("category")) null else j.optString("category"),
        )
    }
}

data class SyncCategory(val name: String, val sortOrder: Int) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("sortOrder", sortOrder)

    companion object {
        fun fromJson(j: JSONObject) = SyncCategory(j.getString("name"), j.optInt("sortOrder"))
    }
}

/** Les trois sortes de noms qui se synchronisent ; la lettre préfixe leurs pierres tombales. */
enum class NameKind(val letter: Char) { GROUP('g'), TAG('t'), CATEGORY('c') }

/** La clé d'un nom : sans casse ni espaces autour, comme partout ailleurs dans les notes. */
fun nameKey(name: String): String = name.trim().lowercase()

/**
 * Le fichier des notes sur Drive.
 *
 * @property purged pierres tombales des notes (uuid → quand), gardées [TOMBSTONE_DAYS] jours
 * @property removed pierres tombales des groupes, tags et catégories (`g:nom`, `t:nom`, `c:nom` → quand)
 */
class NotesSyncFile(
    val format: Int,
    val savedAt: Long,
    val deviceName: String,
    val notes: List<SyncNote>,
    val groups: List<SyncGroup>,
    val tags: List<SyncTag>,
    val categories: List<SyncCategory>,
    val purged: Map<String, Long>,
    val removed: Map<String, Long>,
) {
    fun toJson(): String = build(includeOrigin = true)

    /** Le contenu seul, sans qui l'a écrit ni quand : deux fichiers qui disent la même chose donnent le même texte. */
    fun contentOnly(): String = build(includeOrigin = false)

    private fun build(includeOrigin: Boolean): String {
        val j = JSONObject().put("format", format)
        if (includeOrigin) j.put("savedAt", savedAt).put("device", deviceName)
        j.put("notes", JSONArray(notes.sortedBy { it.uuid }.map { it.toJson() }))
        j.put("groups", JSONArray(groups.sortedBy { nameKey(it.name) }.map { it.toJson() }))
        j.put("tags", JSONArray(tags.sortedBy { nameKey(it.name) }.map { it.toJson() }))
        j.put("categories", JSONArray(categories.sortedBy { nameKey(it.name) }.map { it.toJson() }))
        j.put("purged", JSONObject(purged.toSortedMap()))
        j.put("removed", JSONObject(removed.toSortedMap()))
        return j.toString()
    }

    companion object {
        const val FORMAT = 1
        const val TOMBSTONE_DAYS = 90

        fun empty(deviceName: String, now: Long) =
            NotesSyncFile(FORMAT, now, deviceName, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap())

        /** Null si le texte n'est pas un fichier de notes lisible. */
        fun fromJson(text: String): NotesSyncFile? = try {
            val j = JSONObject(text)
            fun <T> list(key: String, f: (JSONObject) -> T): List<T> =
                j.optJSONArray(key)?.let { a -> (0 until a.length()).map { f(a.getJSONObject(it)) } } ?: emptyList()
            fun map(key: String): Map<String, Long> =
                j.optJSONObject(key)?.let { o -> o.keys().asSequence().associateWith { o.getLong(it) } } ?: emptyMap()
            NotesSyncFile(
                format = j.getInt("format"), savedAt = j.optLong("savedAt"), deviceName = j.optString("device", ""),
                notes = list("notes", SyncNote::fromJson), groups = list("groups", SyncGroup::fromJson),
                tags = list("tags", SyncTag::fromJson), categories = list("categories", SyncCategory::fromJson),
                purged = map("purged"), removed = map("removed"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Ce que cet appareil a échangé la dernière fois : la base de la fusion à trois. */
class NotesSyncState(
    val notes: Map<String, String>,
    val groups: Set<String>,
    val tags: Set<String>,
    val categories: Set<String>,
) {
    fun names(kind: NameKind): Set<String> = when (kind) {
        NameKind.GROUP -> groups
        NameKind.TAG -> tags
        NameKind.CATEGORY -> categories
    }

    fun toJson(): String = JSONObject()
        .put("notes", JSONObject(notes))
        .put("groups", JSONArray(groups.sorted())).put("tags", JSONArray(tags.sorted())).put("categories", JSONArray(categories.sorted()))
        .toString()

    companion object {
        val EMPTY = NotesSyncState(emptyMap(), emptySet(), emptySet(), emptySet())

        fun fromJson(text: String?): NotesSyncState {
            if (text.isNullOrBlank()) return EMPTY
            return try {
                val j = JSONObject(text)
                fun set(key: String) = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                NotesSyncState(
                    notes = j.optJSONObject("notes")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } } ?: emptyMap(),
                    groups = set("groups"), tags = set("tags"), categories = set("categories"),
                )
            } catch (e: Exception) {
                EMPTY
            }
        }
    }
}

// ─── La fusion des notes ─────────────────────────────────────────────────────

/**
 * Ce qu'il faut faire pour que les deux côtés s'accordent.
 *
 * @property expectedLocal l'empreinte qu'avait la note ici quand le plan a été fait (null : elle n'existait pas).
 *   Le plan est calculé sur une photo ; si la note a été modifiée entre-temps, on ne l'écrase pas.
 */
class NotesPlan(
    val applyLocal: List<SyncNote>,
    val deleteLocal: List<String>,
    val newLocal: List<SyncNote>,
    val cloudNotes: List<SyncNote>,
    val purged: Map<String, Long>,
    val hashes: Map<String, String>,
    val expectedLocal: Map<String, String?>,
)

object NotesMerge {

    fun planNotes(
        local: List<SyncNote>,
        cloud: List<SyncNote>,
        purged: Map<String, Long>,
        state: Map<String, String>,
        now: Long,
        conflictSuffix: String,
        newUuid: () -> String,
    ): NotesPlan {
        val locals = local.associateBy { it.uuid }
        val clouds = cloud.associateBy { it.uuid }
        val ids = LinkedHashSet<String>().apply { addAll(locals.keys); addAll(clouds.keys); addAll(state.keys); addAll(purged.keys) }

        val applyLocal = ArrayList<SyncNote>()
        val deleteLocal = ArrayList<String>()
        val newLocal = ArrayList<SyncNote>()
        val cloudOut = LinkedHashMap<String, SyncNote>()
        val purgedOut = LinkedHashMap<String, Long>()
        val hashes = LinkedHashMap<String, String>()
        val expected = LinkedHashMap<String, String?>()
        val horizon = now - NotesSyncFile.TOMBSTONE_DAYS * DAY_MS
        for ((id, at) in purged) if (at >= horizon) purgedOut[id] = at

        for (id in ids) {
            val l = locals[id]
            val c = clouds[id]
            val h0 = state[id]
            when {
                l != null && c != null -> {
                    val hl = l.hash()
                    val hc = c.hash()
                    when {
                        hl == hc -> { cloudOut[id] = c; hashes[id] = hl; purgedOut.remove(id) }
                        h0 != null && hl == h0 -> { applyLocal += c; cloudOut[id] = c; hashes[id] = hc; expected[id] = hl }
                        h0 != null && hc == h0 -> { cloudOut[id] = l; hashes[id] = hl }
                        else -> {
                            // Les deux côtés ont changé : la plus récente gagne, l'autre devient une copie.
                            val localWins = if (l.dateModified != c.dateModified) l.dateModified > c.dateModified else hl > hc
                            val winner = if (localWins) l else c
                            val loser = if (localWins) c else l
                            if (localWins) { cloudOut[id] = l; hashes[id] = hl }
                            else { applyLocal += c; cloudOut[id] = c; hashes[id] = hc; expected[id] = hl }
                            val copy = loser.copy(
                                uuid = newUuid(), title = "${loser.title} $conflictSuffix".trim(),
                                isPinned = false, dateModified = maxOf(winner.dateModified, loser.dateModified),
                            )
                            newLocal += copy; cloudOut[copy.uuid] = copy; hashes[copy.uuid] = copy.hash()
                            purgedOut.remove(id)
                        }
                    }
                }
                l != null -> {
                    val hl = l.hash()
                    if (purged[id] != null && h0 != null && hl == h0) {
                        // Supprimée ailleurs, et jamais touchée ici depuis : elle s'en va aussi d'ici.
                        deleteLocal += id; expected[id] = hl
                    } else {
                        // Neuve, ou le cloud l'a perdue, ou modifiée ici malgré la suppression : on la publie.
                        cloudOut[id] = l; hashes[id] = hl; purgedOut.remove(id)
                    }
                }
                c != null -> {
                    val hc = c.hash()
                    if (h0 != null && hc == h0) {
                        // On l'avait, on ne l'a plus, et le cloud n'a pas bougé : c'est une suppression d'ici.
                        purgedOut[id] = now
                    } else {
                        // Neuve pour cet appareil, ou modifiée ailleurs après qu'on l'a supprimée : elle l'emporte.
                        applyLocal += c; cloudOut[id] = c; hashes[id] = hc; expected[id] = null
                    }
                }
                else -> Unit
            }
        }
        return NotesPlan(applyLocal, deleteLocal, newLocal, cloudOut.values.toList(), purgedOut, hashes, expected)
    }

    // ─── Groupes, tags, catégories : des noms ────────────────────────────────

    /**
     * @property createLocal les entrées du cloud à créer ici
     * @property updateLocal les entrées du cloud qui remplacent celles d'ici (groupes seulement : ils ont une date)
     * @property deleteLocal les clés à supprimer ici, supprimées ailleurs
     * @property cloud la liste à publier
     * @property removed les pierres tombales à publier
     * @property synced les clés à retenir comme échangées
     */
    class NamesPlan<T>(
        val createLocal: List<T>,
        val updateLocal: List<T>,
        val deleteLocal: Set<String>,
        val cloud: List<T>,
        val removed: Map<String, Long>,
        val synced: Set<String>,
    )

    /**
     * @param removed les pierres tombales **de cette sorte** (clés sans préfixe)
     * @param synced les clés qu'on avait déjà échangées
     * @param newer vrai si l'entrée du cloud doit remplacer celle d'ici (quand les deux existent)
     */
    fun <T> planNames(
        local: Map<String, T>,
        cloud: Map<String, T>,
        removed: Map<String, Long>,
        synced: Set<String>,
        now: Long,
        newer: (local: T, cloud: T) -> Boolean,
    ): NamesPlan<T> {
        val horizon = now - NotesSyncFile.TOMBSTONE_DAYS * DAY_MS
        val removedOut = LinkedHashMap<String, Long>()
        for ((k, at) in removed) if (at >= horizon) removedOut[k] = at

        // Supprimé ici : on l'avait échangé, et il n'est plus là.
        for (k in synced) if (k !in local) removedOut[k] = now

        val deleteLocal = LinkedHashSet<String>()
        for (k in local.keys) {
            if (k in removedOut) {
                // Supprimé ailleurs : on le retire d'ici. Mais s'il est né ici depuis (jamais échangé), il revit.
                if (k in synced) deleteLocal += k else removedOut.remove(k)
            }
        }

        val out = LinkedHashMap<String, T>()
        val createLocal = ArrayList<T>()
        val createdKeys = ArrayList<String>()
        val updateLocal = ArrayList<T>()
        for ((k, v) in cloud) {
            if (k in removedOut) continue
            if (k in local) {
                val mine = local.getValue(k)
                if (newer(mine, v)) { updateLocal += v; out[k] = v } else out[k] = mine
            } else {
                createLocal += v; createdKeys += k; out[k] = v
            }
        }
        for ((k, v) in local) if (k !in deleteLocal && k !in out) out[k] = v

        val nowSynced = LinkedHashSet<String>()
        for (k in local.keys) if (k !in deleteLocal) nowSynced += k
        nowSynced += createdKeys
        return NamesPlan(createLocal, updateLocal, deleteLocal, out.values.toList(), removedOut, nowSynced)
    }

    private const val DAY_MS = 24L * 3600_000L
}
