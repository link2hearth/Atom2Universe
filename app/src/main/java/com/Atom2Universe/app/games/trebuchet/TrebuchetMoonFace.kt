package com.Atom2Universe.app.games.trebuchet

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * L'éclairage de la Lune, **sans un pixel ni un octet d'Android**.
 *
 * Toute la phase tient dans ces quelques fonctions, et c'est délibéré : elles se
 * mesurent au banc, alors qu'un disque peint ne se vérifie qu'à l'œil. C'est ce qui
 * manquait à la version précédente, où la phase se calculait dans la méthode de dessin
 * et affichait tranquillement son complément — pleine lune noire, nouvelle lune pleine —
 * sans qu'aucun test puisse s'en apercevoir.
 *
 * **Il n'y a pas de formule de phase ici.** On pose le Soleil quelque part autour de la
 * Lune et on demande à chaque point de la sphère s'il le voit. Le terminateur tombe où
 * il doit tomber, droit au quartier et courbe ailleurs, parce que c'est la géométrie qui
 * le place et non un tracé qui l'imite.
 */
object MoonLight {

    /**
     * Le cosinus de l'angle de phase, pour une part éclairée [lit].
     *
     * L'angle de phase est celui que fait le Soleil vu depuis la Lune avec la direction
     * de la Terre : nul à la pleine lune, plat à la nouvelle. La part éclairée d'une
     * sphère vaut `(1 + cos g) / 2`, d'où l'inversion.
     */
    fun cosPhase(lit: Float) = (2f * lit - 1f).coerceIn(-1f, 1f)

    /**
     * Direction Lune → Soleil, composante **horizontale** dans le repère de l'écran.
     *
     * Positive vers la droite quand la Lune croît : c'est ce qui met le croissant du bon
     * côté dans l'hémisphère nord, et c'est la seule ligne qui décide de ce sens.
     */
    fun sunX(lit: Float, waxing: Boolean): Float {
        val c = cosPhase(lit)
        val s = sqrt((1f - c * c).coerceAtLeast(0f))
        return if (waxing) s else -s
    }

    /** Direction Lune → Soleil, composante **en profondeur** : positive vers nous. */
    fun sunZ(lit: Float) = cosPhase(lit)

    /**
     * Le cosinus d'incidence au point du disque de coordonnées réduites ([u], [v]),
     * l'observateur étant en `z = +1`. Négatif du côté nuit, hors disque il vaut zéro.
     */
    fun incidence(u: Float, v: Float, sunX: Float, sunZ: Float): Float {
        val r2 = u * u + v * v
        if (r2 > 1f) return 0f
        return u * sunX + sqrt(1f - r2) * sunZ
    }

    /**
     * La **lumière cendrée** : la part de nuit lunaire qu'on voit quand même.
     *
     * Ce n'est pas un artifice de rendu. La face nocturne de la Lune est éclairée par la
     * Terre, et la Terre vue de la Lune montre la phase **complémentaire** — elle est
     * pleine quand la Lune est nouvelle. D'où le `1 − lit` : c'est au croissant fin que
     * le reste du disque se devine le mieux, exactement comme dans le ciel.
     */
    fun earthshine(lit: Float) = 0.09f * (1f - lit)

    /**
     * L'éclat rendu au point d'incidence [inc], entre la cendre et le plein soleil.
     *
     * La racine aplatit le dégradé : la Lune est un sol poussiéreux qui rétrodiffuse, et
     * une pleine lune se voit **plate**, sans le modelé d'une bille éclairée. Un cosinus
     * nu la ferait ressembler à une boule de billard.
     */
    fun brightness(inc: Float, earthshine: Float): Float {
        if (inc <= 0f) return earthshine
        return earthshine + (1f - earthshine) * sqrt(inc.coerceAtMost(1f))
    }

    /**
     * L'opacité d'un point du disque, selon [daylight] — 0 la nuit, 1 en plein jour.
     *
     * **Une Lune de plein jour ne montre que son croissant.** Sa face nocturne est plus
     * sombre que le ciel bleu derrière elle, donc elle disparaît : on ne voit pas un
     * disque gris dans le ciel de midi, on voit un morceau de blanc qui flotte. Peindre
     * le disque entier en transparence donnait exactement ce disque gris.
     *
     * D'où la règle : le jour, l'opacité suit l'éclat du point ; la nuit, tout le disque
     * est opaque et la lumière cendrée redevient visible.
     */
    fun opacity(brightness: Float, daylight: Float): Float =
        (1f - daylight.coerceIn(0f, 1f) * (1f - brightness)).coerceIn(0f, 1f)
}

/**
 * La Lune telle qu'on la voit : **une vraie sphère texturée**, éclairée de côté.
 *
 * On réutilise la texture lunaire de l'application (`assets/textures/moon.jpg`, celle
 * dont se sert déjà le widget Terre-Lune) et la technique de
 * `com.Atom2Universe.app.crypto.SphereRenderer` : pour chaque pixel du disque, on
 * remonte à la direction 3D correspondante, on lit la couleur dans la carte
 * équirectangulaire, et on regarde si le Soleil éclaire ce point-là.
 *
 * On ne réutilise pas la fonction elle-même, et c'est un choix : `SphereRenderer` veut
 * un repère de caméra en coordonnées écliptiques et calcule la phase à partir de la
 * vraie géométrie. Le ciel du trébuchet est stylisé — pas de latitude, un Soleil qui
 * suit une sinusoïde — donc lui donner les vrais vecteurs mettrait le terminateur dans
 * une orientation qui contredirait le Soleil dessiné à l'écran. Ici la Lune est vue de
 * face, ce qui est toujours vrai (rotation synchrone), et le Soleil tourne autour d'elle
 * selon la phase affichée : trente lignes au lieu de quatre-vingts, et cohérent avec le
 * reste du ciel.
 *
 * **Le coût.** Rien n'est recalculé par image. La correspondance pixel → texture ne
 * dépend pas de la phase : elle est **tabulée une fois** par rayon, avec la couleur déjà
 * assombrie au limbe et la couverture du bord pour l'anticrénelage. Un changement de
 * phase ne repasse donc que sur un produit scalaire et une racine par pixel — et encore,
 * seulement quand la phase a bougé d'un tiers de pixel, soit une fois par minute environ
 * au rythme du ciel du jeu. Le reste du temps, dessiner la Lune coûte un `drawBitmap` de
 * soixante pixels de côté.
 */
class MoonFace(private val assets: AssetManager?) {

    // ── La texture, chargée une fois ─────────────────────────────────────────
    private var texPixels: IntArray? = null
    private var texW = 0
    private var texH = 0
    private var texTried = false

    // ── La table du disque, refaite à chaque changement de rayon ─────────────
    /** Couleur de surface, limbe assombri et bord adouci, hors éclairage. */
    private var face: IntArray? = null
    private var faceU: FloatArray? = null
    private var faceW: FloatArray? = null
    private var faceRadius = 0

    // ── L'image rendue, refaite quand la phase bouge assez ───────────────────
    private var bitmap: Bitmap? = null
    private var pixels: IntArray? = null
    private var drawnLit = -1f
    private var drawnWaxing = true
    private var drawnDay = -1

    /**
     * L'image de la Lune pour la phase donnée, ou `null` si la texture manque — la vue
     * retombe alors sur son tracé géométrique, qui reste juste, seulement moins joli.
     */
    fun bitmap(lit: Float, waxing: Boolean, radiusPx: Int, daylight: Float): Bitmap? {
        val r = radiusPx.coerceIn(4, 180)
        if (!loadTexture()) return null
        if (faceRadius != r) buildFace(r)

        val bmp = bitmap ?: return null
        // Le jour se range par paliers : il ne sert à rien de refaire l'image pour un
        // centième de crépuscule, et sans palier l'aube en demanderait une par image.
        val jour = (daylight.coerceIn(0f, 1f) * DAY_STEPS).toInt()
        // Un demi-axe de terminateur bouge de `2r` pixels pour une phase pleine : en
        // deçà d'un tiers de pixel, refaire l'image ne changerait rien à l'écran.
        if (abs(drawnLit - lit) < LIT_STEP / r && drawnWaxing == waxing && drawnDay == jour) {
            return bmp
        }

        shade(lit, waxing, jour / DAY_STEPS.toFloat(), r)
        drawnLit = lit
        drawnWaxing = waxing
        drawnDay = jour
        return bmp
    }

    /** L'éclairage : la seule partie qui repasse sur les pixels quand la phase bouge. */
    private fun shade(lit: Float, waxing: Boolean, daylight: Float, r: Int) {
        val src = face ?: return
        val us = faceU ?: return
        val ws = faceW ?: return
        val out = pixels ?: return
        val bmp = bitmap ?: return

        val sx = MoonLight.sunX(lit, waxing)
        val sz = MoonLight.sunZ(lit)
        val cendre = MoonLight.earthshine(lit)

        for (i in src.indices) {
            val base = src[i]
            if (base ushr 24 == 0) { out[i] = 0; continue }
            val f = MoonLight.brightness(us[i] * sx + ws[i] * sz, cendre)
            val rr = ((base shr 16 and 0xFF) * f).toInt().coerceIn(0, 255)
            val gg = ((base shr 8 and 0xFF) * f).toInt().coerceIn(0, 255)
            val bb = ((base and 0xFF) * f).toInt().coerceIn(0, 255)
            val aa = ((base ushr 24) * MoonLight.opacity(f, daylight)).toInt().coerceIn(0, 255)
            out[i] = (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        val size = r * 2
        bmp.setPixels(out, 0, size, 0, 0, size, size)
    }

    /**
     * La table du disque : couleur de surface et géométrie, une fois pour toutes.
     *
     * C'est ici que vivent les `atan2` et les `asin`, c'est-à-dire tout ce qui coûte —
     * et ils ne dépendent pas de la phase, donc ils ne sont payés qu'une fois par rayon.
     */
    private fun buildFace(r: Int) {
        val tex = texPixels ?: return
        val size = r * 2
        val n = size * size
        val col = IntArray(n)
        val us = FloatArray(n)
        val ws = FloatArray(n)
        val rf = r.toFloat()

        for (py in 0 until size) {
            for (px in 0 until size) {
                val i = py * size + px
                // Centre du pixel, en coordonnées réduites : le disque va de -1 à 1.
                val u = (px + 0.5f - rf) / rf
                val v = -(py + 0.5f - rf) / rf
                val d = sqrt(u * u + v * v)
                // Couverture du bord : un pixel à cheval sur le limbe est à demi opaque,
                // ce qui remplace l'anticrénelage qu'un `drawCircle` aurait donné.
                val couvre = ((1f - d) * rf + 0.5f).coerceIn(0f, 1f)
                if (couvre <= 0f) { col[i] = 0; continue }

                val w = sqrt((1f - (u * u + v * v)).coerceAtLeast(0f))
                us[i] = u
                ws[i] = w

                // La Lune montre toujours la même face : le centre du disque est le
                // point sous la Terre, donc la longitude zéro de la carte. Le reste
                // suit — est à droite, nord en haut, comme on la voit.
                val lon = atan2(u.toDouble(), w.toDouble())
                val lat = asin(v.toDouble().coerceIn(-1.0, 1.0))
                val tu = (((lon / PI + 1.0) / 2.0) * texW).toInt().coerceIn(0, texW - 1)
                val tv = (((PI / 2.0 - lat) / PI) * texH).toInt().coerceIn(0, texH - 1)
                var c = tex[tv * texW + tu]

                // Un soupçon d'assombrissement au limbe : la Lune est très plate, donc
                // il en faut peu — juste assez pour qu'elle ne soit pas un autocollant.
                val limbe = 0.86f + 0.14f * w
                val rr = ((c shr 16 and 0xFF) * limbe).toInt().coerceIn(0, 255)
                val gg = ((c shr 8 and 0xFF) * limbe).toInt().coerceIn(0, 255)
                val bb = ((c and 0xFF) * limbe).toInt().coerceIn(0, 255)
                c = ((couvre * 255f).toInt().coerceIn(0, 255) shl 24) or
                    (rr shl 16) or (gg shl 8) or bb
                col[i] = c
            }
        }

        bitmap?.recycle()
        face = col
        faceU = us
        faceW = ws
        faceRadius = r
        pixels = IntArray(n)
        bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawnLit = -1f
    }

    /**
     * Charge la carte lunaire, une seule fois, et libère l'image aussitôt lue.
     *
     * On la réduit d'un facteur quatre : la face visible n'occupe que la moitié de la
     * carte, ce qui laisse cent vingt-huit points de texture pour un disque de soixante
     * pixels. Décoder les mille vingt-quatre points d'origine coûterait deux mégaoctets
     * pour un détail que l'écran ne peut pas montrer.
     */
    private fun loadTexture(): Boolean {
        if (texPixels != null) return true
        if (texTried) return false
        texTried = true
        val am = assets ?: return false
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val raw = am.open(TEXTURE).use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            texW = raw.width
            texH = raw.height
            val px = IntArray(texW * texH)
            raw.getPixels(px, 0, texW, 0, 0, texW, texH)
            raw.recycle()
            texPixels = px
            true
        } catch (_: Exception) {
            false
        }
    }

    /** À la fermeture de la vue : la Lune ne survit pas à son ciel. */
    fun release() {
        bitmap?.recycle()
        bitmap = null
        pixels = null
        face = null
        faceU = null
        faceW = null
        faceRadius = 0
        texPixels = null
        texTried = false
    }

    private companion object {
        /** La carte lunaire déjà présente dans l'application, partagée avec le widget. */
        const val TEXTURE = "textures/moon.jpg"

        /**
         * De combien la phase doit bouger pour valoir un nouveau rendu, **multiplié par
         * le rayon**. Le terminateur se déplace de `2r` pixels sur une phase entière,
         * donc `0,15 / r` correspond à un tiers de pixel : invisible, et de l'ordre d'un
         * rendu par minute au rythme du ciel du jeu.
         */
        const val LIT_STEP = 0.15f

        /** Paliers de clarté du ciel : huit suffisent pour un crépuscule d'une minute. */
        const val DAY_STEPS = 8
    }
}
