package com.Atom2Universe.app.hub

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlin.reflect.KClass

data class QuickAccessItem(
    val label: String,
    val activityClassName: String,
    val colorHex: String? = null
)

/**
 * Donnees pour une tuile de hub generalisee
 */
data class HubTile(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val defaultColorRes: Int,
    var customColorHex: String? = null,
    var textColorMode: String = "auto",
    val artworkClass: KClass<out android.graphics.drawable.Drawable>? = null,
    val activityClass: Class<*>? = null,
    var quickAccessItems: List<QuickAccessItem> = emptyList(),
    /** Faux pour une tuile dont l'illustration se suffit : seul le titre reste affiche dessus. */
    val showDescription: Boolean = true,
    /**
     * Pastille de notification : 0 = rien a signaler. C'est le hub qui la remplit, la tuile ne
     * sait pas ce qu'elle compte (recoltes pretes, etc.), et rien n'en est sauvegarde.
     */
    var notificationCount: Int = 0
)
