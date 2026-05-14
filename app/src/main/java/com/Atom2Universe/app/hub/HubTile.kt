package com.Atom2Universe.app.hub

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

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
    val activityClass: Class<*>? = null,
    var quickAccessItems: List<QuickAccessItem> = emptyList()
)
