package com.Atom2Universe.app.pixelart.io

import com.Atom2Universe.app.pixelart.core.Document

enum class ImageFormat(val mime: String, val extension: String) {
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp");

    companion object {
        fun fromMime(mime: String?): ImageFormat? = when (mime?.lowercase()) {
            "image/png" -> PNG
            "image/gif" -> GIF
            "image/jpeg", "image/jpg" -> JPEG
            "image/webp" -> WEBP
            else -> null
        }

        fun fromName(name: String): ImageFormat? = when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> PNG
            "gif" -> GIF
            "jpg", "jpeg" -> JPEG
            "webp" -> WEBP
            else -> null
        }
    }
}

/**
 * Le fichier image auquel le projet est relié : « Enregistrer » l'écrase.
 * [scale] est l'agrandissement appliqué à l'écriture (un sprite 32×32 exporté ×8 reste ×8).
 */
data class SourceLink(
    val uri: String,
    val name: String,
    val format: ImageFormat,
    val scale: Int = 1,
    /** Faux quand le fournisseur du fichier n'a pas donné le droit d'écriture. */
    val writable: Boolean = true,
)

/** Image de référence posée sous le dessin (calque de calque, jamais exportée). */
data class ReferenceState(
    val scale: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val opacity: Float = 0.5f,
    val visible: Boolean = true,
)

/** Tout ce qui vit à côté du [Document] et est conservé avec lui. */
class ProjectMeta(
    val id: String,
    var name: String,
    var created: Long,
    var modified: Long,
    var primary: Int = 0xFF000000.toInt(),
    var secondary: Int = 0xFFFFFFFF.toInt(),
    var activeLayerId: Int = 0,
    var activeFrameId: Int = 0,
    var link: SourceLink? = null,
    var reference: ReferenceState? = null,
    var hasReference: Boolean = false,
)

class LoadedProject(val doc: Document, val meta: ProjectMeta)

/** Ce que la galerie affiche d'un projet sans l'ouvrir. */
class ProjectSummary(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val frames: Int,
    val modified: Long,
    val linkName: String?,
)
