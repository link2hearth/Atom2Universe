package com.Atom2Universe.app.science

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.ImmersiveDialog
import kotlin.math.min

/**
 * La fiche des modules de science : une fenêtre qui prend tout l'écran, peinte de la couleur de son contenu
 * jusque sous les barres système (plus de bande grise), avec son propre défilement. Remplace la feuille du
 * bas, dont le cadre fixe et le défilement imbriqué coupaient le texte et laissaient un bandeau en bas.
 * En haut, une bannière porte la flèche (retour à la fiche précédente, sinon fermeture) et le nom de la fiche.
 * La fenêtre est posée par-dessus l'écran : la bannière est un peu transparente, le reste très légèrement.
 *
 * Une même fenêtre sert pour toute la navigation entre fiches : `show` change le contenu sans rouvrir.
 * Sur un écran large et bas (tablette à l'horizontale, téléphone couché), le dessin reste à gauche, entier,
 * et seul le texte défile à droite ; sinon tout défile dans une seule colonne.
 */
class ScienceFiche(
    private val activity: Activity,
    private val palette: SciencePalette,
    private val backLabel: Int,
    private val closeLabel: Int,
    private val onClosed: () -> Unit
) {
    private class FicheDialog(context: Context) : ImmersiveDialog(context, android.R.style.Theme_DeviceDefault_NoActionBar) {
        var onBack: (() -> Unit)? = null

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onBackPressed() {
            val back = onBack
            if (back != null) back() else super.onBackPressed()
        }
    }

    private companion object {
        /** Opacité de la bannière et du reste de la page (1 = opaque) : les deux seuls réglages de transparence. */
        const val BANNER_OPACITY = 0.95f
        const val PAGE_OPACITY = 0.94f
    }

    private var dialog: FicheDialog? = null
    private var holder: FrameLayout? = null
    private val bannerColor = ColorUtils.setAlphaComponent(palette.surface, (BANNER_OPACITY * 255).toInt())
    private val pageColor = ColorUtils.setAlphaComponent(palette.surface, (PAGE_OPACITY * 255).toInt())
    private val density get() = activity.resources.displayMetrics.density

    val isShowing: Boolean get() = dialog?.isShowing == true

    /** Taille réelle de la fenêtre de l'écran (celle de la fiche est la même). */
    private fun windowWidth() = activity.window.decorView.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
    private fun windowHeight() = activity.window.decorView.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels

    /** Vrai quand le dessin se place à gauche et le texte à droite. À lire avant de construire le dessin. */
    val wide: Boolean get() = windowWidth() >= dp(600) && windowWidth() >= windowHeight() * 1.2f

    /**
     * Affiche une fiche. [title] : son nom, dans la bannière ; [top] : ce qui précède le dessin ; [media] : le dessin
     * et ses boutons (à gauche en mode large, sa partie qui s'étire doit alors avoir une hauteur de 0 et un poids
     * de 1) ; [body] : le texte. [onBack] : le retour à la fiche précédente, ou `null` s'il n'y en a pas (la flèche
     * ferme alors la fiche).
     */
    fun show(title: CharSequence, top: View?, media: View?, body: View?, onBack: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val d = dialog?.takeIf { it.isShowing } ?: create()
        d.onBack = onBack
        val page = if (media != null && wide) splitPage(title, top, media, body, onBack) else columnPage(title, top, media, body, onBack)
        holder?.removeAllViews()
        holder?.addView(page, FrameLayout.LayoutParams(-1, -1))
        ViewCompat.requestApplyInsets(page)
        if (!d.isShowing) d.show()
    }

    fun dismiss() { dialog?.dismiss() }

    // ------------------------------------------------------------------ la fenêtre

    private fun create(): FicheDialog {
        val d = FicheDialog(activity)
        d.useFullScreenWindow()
        val content = FrameLayout(activity)
        d.setContentView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        holder = content
        dialog = d
        d.setOnDismissListener {
            if (dialog === d) {
                dialog = null
                holder = null
                onClosed()
            }
        }
        return d
    }

    // ------------------------------------------------------------------ les deux mises en page

    /** Tout dans une seule colonne qui défile, sous la bannière. */
    private fun columnPage(title: CharSequence, top: View?, media: View?, body: View?, onBack: (() -> Unit)?): View {
        val page = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        page.addView(banner(title, onBack), LinearLayout.LayoutParams(-1, -2))
        page.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        page.addView(region(scrolling(top, media, body), left = true, right = true), LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    /** Le dessin à gauche, entier, et le texte qui défile à droite ; la bannière couvre les deux. */
    private fun splitPage(title: CharSequence, top: View?, media: View, body: View?, onBack: (() -> Unit)?): View {
        // La place du dessin suit la hauteur de l'écran (il est plus haut que large), sans dépasser la moitié.
        val plateWidth = (windowHeight() - dp(190)) / 1.1f
        val left = (plateWidth + dp(24)).toInt().coerceIn(dp(260), windowWidth() / 2)

        val mediaPane = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(8), dp(16))
        }
        mediaPane.addView(media, LinearLayout.LayoutParams(-1, -1))

        val columns = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        columns.addView(region(mediaPane, left = true), LinearLayout.LayoutParams(left, -1))
        columns.addView(divider(), LinearLayout.LayoutParams(1, -1))
        columns.addView(region(scrolling(top, null, body), right = true), LinearLayout.LayoutParams(0, -1, 1f))

        val page = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        page.addView(banner(title, onBack), LinearLayout.LayoutParams(-1, -2))
        page.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        page.addView(columns, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun scrolling(top: View?, media: View?, body: View?): ScrollView {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(28))
        }
        top?.let { column.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        media?.let { column.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }) }
        body?.let { column.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        // Sur un grand écran la colonne reste lisible : elle ne s'étire pas sur toute la largeur.
        val columnWidth = if (windowWidth() > dp(680)) dp(680) else ViewGroup.LayoutParams.MATCH_PARENT
        return ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = true
            addView(column, FrameLayout.LayoutParams(columnWidth, -2, Gravity.CENTER_HORIZONTAL))
        }
    }

    /**
     * La bannière : la flèche à gauche (retour à la fiche précédente, sinon fermeture) et le nom de la fiche.
     * Son fond, translucide, passe sous la barre d'état.
     */
    private fun banner(title: CharSequence, onBack: (() -> Unit)?): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(4), 0, dp(16), 0)
        }
        row.addView(icon(R.drawable.ic_arrow_back_24, if (onBack != null) backLabel else closeLabel) {
            if (onBack != null) onBack() else dismiss()
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val name = TextView(activity).apply {
            text = title
            textSize = 19f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.text)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), dp(6), 0, dp(6))
        }
        ViewCompat.setAccessibilityHeading(name, true)
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        return FrameLayout(activity).apply {
            setBackgroundColor(bannerColor)
            addView(row, FrameLayout.LayoutParams(-1, -2))
            fitInsets(this, left = true, top = true, right = true)
        }
    }

    /** Une zone de la page : son fond (légèrement translucide) va jusqu'au bord, son contenu reste hors des barres. */
    private fun region(content: View, left: Boolean = false, right: Boolean = false): View =
        FrameLayout(activity).apply {
            setBackgroundColor(pageColor)
            addView(content, FrameLayout.LayoutParams(-1, -1))
            fitInsets(this, left = left, right = right, bottom = true)
        }

    /** Les côtés demandés de la zone gardent leurs distances aux barres système, à l'encoche et au clavier. */
    private fun fitInsets(view: View, left: Boolean = false, top: Boolean = false, right: Boolean = false, bottom: Boolean = false) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(if (left) safe.left else 0, if (top) safe.top else 0, if (right) safe.right else 0, if (bottom) safe.bottom else 0)
            insets
        }
    }

    private fun divider() = View(activity).apply { setBackgroundColor(ColorUtils.setAlphaComponent(palette.outline, 140)) }

    private fun icon(drawable: Int, title: Int, action: () -> Unit) = ImageButton(activity).apply {
        setImageResource(drawable)
        setColorFilter(palette.text)
        background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent, 55)), null, palette.shape(Color.WHITE, 24f))
        contentDescription = activity.getString(title)
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * density).toInt()
}

/**
 * Une fenêtre qui prend tout l'écran, bords compris : son [background] (transparent ou translucide) va jusque sous les
 * barres système, qui sont transparentes, et c'est le contenu qui se tient hors de leur zone (voir `fitInsets`).
 */
internal fun Dialog.useFullScreenWindow(background: Int = Color.TRANSPARENT) {
    val window = window ?: return
    window.setBackgroundDrawable(ColorDrawable(background))
    window.setFormat(PixelFormat.TRANSLUCENT)
    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    @Suppress("DEPRECATION") run {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    if (Build.VERSION.SDK_INT >= 28) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

/**
 * Place un dessin de proportions fixes (hauteur = largeur × [ratio]) dans la place qu'on lui laisse : jamais coupé,
 * jamais plus large que [maxWidth]. Si la hauteur est imposée (colonne de gauche d'un écran large), le dessin
 * s'y inscrit et se centre ; sinon il suit la largeur.
 */
class FitFrame(context: Context, private val ratio: Float, private val maxWidth: Int) : FrameLayout(context) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val wMode = MeasureSpec.getMode(widthSpec)
        val hMode = MeasureSpec.getMode(heightSpec)
        val hSize = MeasureSpec.getSize(heightSpec)
        var w = if (wMode == MeasureSpec.UNSPECIFIED) maxWidth else min(MeasureSpec.getSize(widthSpec), maxWidth)
        var h = (w * ratio).toInt()
        if (hMode != MeasureSpec.UNSPECIFIED && h > hSize) {
            h = hSize
            w = (h / ratio).toInt()
        }
        getChildAt(0)?.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        val ownW = if (wMode == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthSpec) else w
        val ownH = if (hMode == MeasureSpec.EXACTLY) hSize else h
        setMeasuredDimension(ownW, ownH)
    }
}
