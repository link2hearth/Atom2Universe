package com.Atom2Universe.app.zoomcanvas.core

/**
 * Le journal de ce qui a changé dans la scène depuis la dernière écriture : les objets posés ou
 * remplacés (leur dernier état seulement), ceux qui ont été retirés, et les couches touchées.
 * Les couches y écrivent ; l'enregistrement le vide ([ZoomScene.drainChanges]) et n'écrit que ça.
 *
 * Écrire un trait coûte donc un trait, pas tout le dessin.
 */
class ChangeLog {
    class Upsert(val depth: Long, val entry: Entry)

    internal val upserts = LinkedHashMap<Long, Upsert>()
    internal val deletes = HashSet<Long>()
    internal val touchedLayers = HashSet<Long>()

    val isEmpty: Boolean get() = upserts.isEmpty() && deletes.isEmpty()

    fun upsert(depth: Long, e: Entry) {
        upserts[e.id] = Upsert(depth, e)
        deletes.remove(e.id)
        touchedLayers.add(depth)
    }

    /** Un objet retiré : même s'il n'a jamais été écrit, on garde l'ordre de l'effacer (c'est sans effet s'il n'y est pas). */
    fun delete(depth: Long, id: Long) {
        upserts.remove(id)
        deletes.add(id)
        touchedLayers.add(depth)
    }

    fun clear() {
        upserts.clear()
        deletes.clear()
        touchedLayers.clear()
    }

    /**
     * Remet dans le journal un lot dont l'écriture a échoué (disque plein…). Ce qui s'est passé
     * depuis est plus récent : il l'emporte.
     */
    internal fun requeue(d: SceneDelta) {
        for (u in d.upserts) {
            if (u.entry.id !in upserts && u.entry.id !in deletes) upserts[u.entry.id] = u
            touchedLayers.add(u.depth)
        }
        for (id in d.deletes) if (id !in upserts) deletes.add(id)
    }
}

/**
 * Une couche à écrire : son ancre, et son résumé (éléments au total, dont [erasers] coups de gomme,
 * et la boîte de ce qu'on y voit) : de quoi se passer de ses éléments quand elle est déchargée.
 */
class LayerRow(
    val depth: Long,
    val ax: Double,
    val ay: Double,
    val count: Int,
    val erasers: Int,
    val bounds: DoubleArray?,
) {
    /** Ce qu'on voit dans la couche (pour la galerie). */
    val itemCount: Int get() = count - erasers
}

/**
 * Un lot à écrire, détaché de la scène : il ne contient que des objets immuables, on peut donc
 * l'écrire sur un autre fil pendant que l'écran continue de dessiner.
 */
class SceneDelta(
    val upserts: List<ChangeLog.Upsert>,
    val deletes: LongArray,
    /** Les couches dont la ligne est à écrire (nouvelles, déplacées, ou dont le contenu a changé). */
    val layers: List<LayerRow>,
    /** Les couches gardées : de [firstDepth] à [lastDepth] ; les lignes hors de cette plage disparaissent. */
    val firstDepth: Long,
    val lastDepth: Long,
    val camDepth: Long,
    val cx: Double,
    val cy: Double,
    val zoom: Double,
    val nextId: Long,
    val contentVersion: Long,
    val cameraVersion: Long,
    /** Les couches touchées par ce lot, avec leur version : une fois écrit, elles ne sont plus à écrire (voir [ZoomScene.markSaved]). */
    val layerVersions: Map<Long, Long> = emptyMap(),
    /** Les tuiles de la couche de pixels à écrire (le « Canvas »), et son ordre par rapport au dessin si celui-ci a changé. */
    val pixels: PixelDelta = PixelDelta.EMPTY,
    val pixelsAbove: Boolean = false,
    val pixelSettingsChanged: Boolean = false,
) {
    val itemsChanged: Boolean get() = upserts.isNotEmpty() || deletes.isNotEmpty() || !pixels.isEmpty
}
