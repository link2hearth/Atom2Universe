package com.Atom2Universe.app.games

import androidx.annotation.DrawableRes

/**
 * Represents a game tile in the games selection screen.
 * Each game will have its own tile with metadata.
 */
data class GameTile(
    val id: String,
    val titleResId: Int,
    val descriptionResId: Int,
    @DrawableRes val iconResId: Int = android.R.drawable.ic_menu_compass,
    val activityClass: Class<*>? = null, // null = coming soon / not implemented
    val statusResId: Int? = null // Optional status text (e.g., "Coming soon")
)
