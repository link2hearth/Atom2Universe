package com.Atom2Universe.app.games.puzzles.pattern

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.sync.SharedGameStats
import com.Atom2Universe.app.crypto.sync.SyncedStat
import com.Atom2Universe.app.games.kit.PuzzleActivity
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Le mode « Images » commun aux puzzles qui dessinent une image ([PatternPictures]) : Logimage
 * et Mosaïque. Chaque jeu a sa propre progression (préférences « kit_<jeu>_levels ») : réussir
 * une image débloque la suivante, la galerie montre les finies en couleurs et permet de les
 * rejouer, un bouton remet tout à zéro.
 *
 * Le jeu fournit sa pastille « Images » ([picturesPreset]), la grille vierge d'une image
 * ([fromPicture]), ses grilles au hasard ([generateRandom]), et ajoute [galleryChip] à ses
 * commandes.
 */
abstract class PictureModeActivity<S : Any> : PuzzleActivity<S>() {

    protected abstract val picturesPreset: Int
    protected abstract fun pictureOf(state: S): Int
    protected abstract fun fromPicture(index: Int): S
    protected abstract fun generateRandom(preset: Int, random: Random): S

    protected val levels by lazy { getSharedPreferences(levelsPrefs(gameKey), MODE_PRIVATE) }
    protected lateinit var galleryChip: TextView
        private set

    /** L'image choisie dans la galerie, sinon -1 (la suivante à faire). */
    @Volatile private var requestedLevel = -1
    @Volatile private var shownPicture = -1

    /** Nombre d'images réussies : les images 0 à unlocked-1 sont finies, la suivante est jouable. */
    private val unlocked get() = levels.getInt(KEY_UNLOCKED, 0)

    final override fun generate(preset: Int, random: Random): S {
        if (preset != picturesPreset) return generateRandom(preset, random)
        val count = PatternPictures.all.size
        // « Nouvelle partie » : la prochaine image à faire ; une fois tout fini, on fait le tour.
        val level = requestedLevel.takeIf { it >= 0 } ?: when {
            unlocked < count -> unlocked
            else -> (shownPicture + 1).mod(count)
        }
        requestedLevel = -1
        return fromPicture(level)
    }

    override fun bestKey(preset: Int, state: S) =
        pictureOf(state).let { if (it >= 0) "$KEY_BEST_PICTURE$it" else super.bestKey(preset, state) }

    override fun onSolved(state: S) {
        val p = pictureOf(state)
        if (p >= 0 && p + 1 > unlocked) levels.edit { putInt(KEY_UNLOCKED, p + 1) }
    }

    override fun statusText(state: S): String? {
        val p = pictureOf(state)
        return when {
            p < 0 -> null
            isSolved(state) -> getString(PatternPictures.all[p].nameRes)
            else -> getString(R.string.pattern_picture_number, p + 1, PatternPictures.all.size)
        }
    }

    override fun onStateShown(state: S) {
        shownPicture = pictureOf(state)
        if (::galleryChip.isInitialized) galleryChip.visibility = if (shownPicture >= 0) View.VISIBLE else View.GONE
    }

    /** La pastille qui ouvre la galerie, visible seulement en mode Images. */
    protected fun makeGalleryChip(): TextView = TextView(this).apply {
        setText(R.string.pattern_gallery)
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        setOnClickListener { showGallery() }
        visibility = View.GONE
        galleryChip = this
        post { styleChip(this, false); current?.let { onStateShown(it) } }
    }

    /**
     * La galerie des images, en grille de miniatures : les finies dessinées en couleurs avec leur
     * nom, la prochaine à faire en grille vide, les suivantes sous un cadenas. Toucher une image
     * finie (pour la rejouer) ou la prochaine la lance ; toucher une image verrouillée ne fait rien.
     */
    private fun showGallery() {
        val count = PatternPictures.all.size
        val done = unlocked.coerceAtMost(count)
        var dialog: AlertDialog? = null
        val columns = 3
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }
        for (start in 0 until count step columns) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in start until start + columns) {
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                }
                // Une case vide en fin de dernière rangée garde les miniatures à la même taille.
                if (i >= count) { row.addView(View(this), params); continue }
                val mode = when {
                    i < done -> PictureThumb.DONE
                    i == done -> PictureThumb.NEXT
                    else -> PictureThumb.LOCKED
                }
                row.addView(galleryTile(i, mode) {
                    dialog?.dismiss()
                    requestedLevel = i
                    newGame(picturesPreset)
                }, params)
            }
            grid.addView(row)
        }
        val builder = com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setTitle(getString(R.string.pattern_gallery_title, done, count))
            .setView(ScrollView(this).apply { addView(grid) })
            .setPositiveButton(R.string.close, null)
        // Rien à effacer tant qu'aucune image n'est finie : pas de bouton.
        if (done > 0) builder.setNeutralButton(R.string.pattern_reset) { _, _ -> confirmReset() }
        dialog = builder.show()
    }

    /** Une case de la galerie : la miniature, puis le nom (image finie) ou le numéro. */
    private fun galleryTile(index: Int, mode: Int, onOpen: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = (6 * dp).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            cornerRadius = AppearanceStyle.corner(this@PictureModeActivity, 12f)
            setColor(palette.raised)
            val current = index == shownPicture
            setStroke((if (current) 2 * dp else dp).toInt().coerceAtLeast(1), if (current) palette.accent else palette.outline)
        }
        addView(PictureThumb(context, PatternPictures.all[index], mode, palette),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(TextView(context).apply {
            text = if (mode == PictureThumb.DONE) getString(PatternPictures.all[index].nameRes)
            else getString(R.string.pattern_level, index + 1)
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(if (mode == PictureThumb.LOCKED) palette.tertiary else palette.text)
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (mode != PictureThumb.LOCKED) setOnClickListener { onOpen() }
    }

    /**
     * Remise à zéro, après confirmation : plus aucune image finie (la galerie se vide, seule la
     * première est jouable) et les meilleurs temps des images sont oubliés. Si une image était
     * à l'écran, on repart de la première.
     */
    private fun confirmReset() {
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setTitle(R.string.pattern_reset_title)
            .setPositiveButton(R.string.pattern_reset) { _, _ ->
                // Une suppression datée, comme sur la page des stats : sinon la sync Drive, qui
                // garde le meilleur record de tous les appareils, ramènerait la progression.
                // Les meilleurs temps vivent dans les réglages communs du kit (« kit_<jeu> »).
                lifecycleScope.launch {
                    SharedGameStats.delete(this@PictureModeActivity, listOf(
                        SyncedStat(levelsPrefs(gameKey), KEY_UNLOCKED),
                        SyncedStat(PuzzleActivity.PREFS_PREFIX + gameKey, KEY_BEST_PICTURE, isPrefix = true),
                    ))
                    if (shownPicture >= 0) {
                        requestedLevel = -1
                        newGame(picturesPreset)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_BEST_PICTURE = "best_picture_"

        /** Les jeux qui ont un mode Images (pour la page des stats et la sync Drive). */
        val GAMES = listOf("pattern", "mosaic")

        fun levelsPrefs(game: String) = "${PuzzleActivity.PREFS_PREFIX}${game}_levels"

        /** Le nombre d'images réussies dans ce jeu. */
        fun picturesDone(context: Context, game: String) =
            context.getSharedPreferences(levelsPrefs(game), Context.MODE_PRIVATE).getInt(KEY_UNLOCKED, 0)
    }
}
