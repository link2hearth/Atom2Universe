package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le décor commun à toutes les machines : **le ciel, les astres, les nuages, les
 * oiseaux**.
 *
 * Tout ce qui n'est pas la machine elle-même vit ici. C'est la réponse à une divergence
 * qui s'est installée toute seule : `TrebuchetView` et `GearMachineView` partageaient
 * déjà le **modèle** du ciel — [SkyClock], [SkyState], [CloudField], [BirdField] sont
 * dans `TrebuchetSky.kt` et les deux vues les importent — mais chacune avait **sa
 * copie du peintre**, recopiée de l'autre puis modifiée d'un seul côté. Corriger les
 * phases de la Lune ne corrigeait donc que la moitié du jeu, et une pleine lune noire
 * restait dans l'atelier d'engrenages.
 *
 * **Ce n'est pas la vue qui devait être unique, c'est le décor.** Les deux vues font des
 * métiers étrangers l'un à l'autre — l'une suit un boulet sur six cents mètres de relief
 * avec des cibles destructibles, l'autre est un établi où l'on pose des engrenages sur
 * des calques — et les fondre donnerait une classe de six mille lignes coupée en deux
 * moitiés qui ne se parlent pas. Le décor, lui, est vraiment le même, et une machine à
 * pression ou à poudre le voudra tel quel sans rien en redemander.
 *
 * Ce que la classe ne fait **pas**, délibérément : le sol. Le trébuchet a un relief et
 * des cibles, l'atelier a une dalle plate et une règle graduée ; ce n'est pas le même
 * objet, et le prétendre reviendrait à recommencer le mélange qu'on vient de défaire.
 *
 * @param skyBand part de la hauteur d'écran occupée par le ciel — l'horizon des astres.
 *   L'atelier voit plus de ciel que le champ de tir, parce que sa dalle est plus bas.
 */
class SkyBackdrop(context: Context, private val skyBand: Float = SKY_BAND) {

    private val dp = context.resources.displayMetrics.density

    /**
     * L'heure du jeu et le ciel qu'elle donne.
     *
     * Le ciel n'appartient à aucune partie : il n'est dans le modèle d'aucun jeu, il ne
     * se rejoue pas, il n'entre dans la graine d'aucun niveau. C'est de l'ambiance, et
     * l'ambiance n'a pas à être reproductible — un tir doit donner la même portée à midi
     * et à minuit.
     */
    val clock = SkyClock()
    val sky = SkyState().apply { update(clock.instant) }

    /**
     * Les nuages, et leur flou de coton.
     *
     * **Pas de [android.graphics.BlurMaskFilter].** Le contour cotonneux vient d'un
     * nuancier radial qui dégrade en douceur du centre au bord sans jamais convoluer un
     * seul pixel. Un flou gaussien coûtait une vraie convolution, pixel par pixel, à
     * chacune des soixante boules dessinées par image, et c'est ce qui faisait ramer le
     * jeu pendant un tir — précisément quand la caméra recule et que les nuages
     * grandissent à l'écran.
     */
    val clouds = CloudField()

    /** Les oiseaux : ils volent bas, sous les nuages, et se couchent avec le jour. */
    val birds = BirdField()

    /**
     * Horloge **réelle**, pour le balancement des plantes et le vol des oiseaux.
     *
     * Ce n'est pas l'heure du jeu, qui court soixante-douze fois plus vite et se remonte
     * à la main : un oiseau qui battrait des ailes à la vitesse du ciel ferait un
     * bourdon.
     */
    var ambientClock = 0f
        private set

    // ── Les pinceaux ─────────────────────────────────────────────────────────
    private val pBg = Paint()
    private val pStar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 255, 255) }
    private val pSun = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pHalo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pMoon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2EFE2.toInt()
        isFilterBitmap = true
    }
    private val pMoonGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pMoonDark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pCloud = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBird = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(200, 40, 38, 46)
    }

    private val moonPath = Path()
    private val moonOval = RectF()

    /** La vraie Lune texturée, si la carte lunaire de l'application se charge. */
    private val moonFace = MoonFace(runCatching { context.assets }.getOrNull())

    /**
     * Les nuanciers ne se refabriquent que quand leur teinte ou leur taille bouge : un
     * `RadialGradient` par image serait soixante allocations par seconde pour un astre
     * qui change de couleur en cinq minutes.
     */
    private var bgZenith = 0
    private var bgHorizon = 0
    private var bgShaderHeight = -1f
    private var sunTint = 0
    private var sunShaderRadius = 0f
    private var moonGlowRadius = 0f
    private var cloudTint = 0

    private val starsX = FloatArray(40)
    private val starsY = FloatArray(40)
    private val starsR = FloatArray(40)

    init {
        val r = Random(11)
        for (i in starsX.indices) {
            starsX[i] = r.nextFloat()
            starsY[i] = r.nextFloat()
            starsR[i] = 0.6f + r.nextFloat() * 1.2f
        }
    }

    /**
     * Fait vivre le décor d'une image.
     *
     * **Deux temps, et ils ne sont pas interchangeables.** [ambientDt] est le temps de
     * l'écran : c'est lui qui fait battre les ailes et dériver les nuages. [clockDt] est
     * le temps que le ciel doit avancer, qui n'est pas le même — le joueur peut tenir
     * l'heure au doigt (`clockDt` vaut alors zéro), et l'atelier d'engrenages fait
     * défiler le ciel à la vitesse de la **machine** pendant une charge, dix minutes de
     * mécanisme déplaçant le Soleil de dix minutes, pendant que les oiseaux, eux,
     * continuent de voler à l'heure normale. Ce sont des habitants, pas des rouages.
     */
    fun advance(ambientDt: Float, clockDt: Float, windVx: Float) {
        ambientClock += ambientDt
        clouds.update(ambientDt, windVx)
        birds.update(ambientDt, windVx)
        if (clockDt != 0f) clock.advance(clockDt)
        sky.update(clock.instant)
    }

    /** À la fermeture de la vue : la Lune ne survit pas à son ciel. */
    fun release() {
        moonFace.release()
        moonGlowRadius = 0f
    }

    /**
     * Peint tout le décor, du fond du ciel aux oiseaux.
     *
     * Les astres se posent en **fractions d'écran**, les nuages et les oiseaux en
     * **coordonnées du monde** : le Soleil est à cent cinquante millions de kilomètres,
     * il ne défile pas quand on fait glisser la vue de trois cents mètres, alors qu'un
     * nuage à trois cents mètres d'altitude, si. Un ciel qui suivrait entièrement le
     * cadrage serait un plafond peint.
     */
    fun draw(canvas: Canvas, w: Float, h: Float, camX: Float, camY: Float, camScale: Float) {
        drawSky(canvas, w, h)
        drawMoon(canvas, w, h)
        drawSun(canvas, w, h)
        // Les nuages passent **devant** les astres : c'est ce qui les met au ciel plutôt
        // que sur un mur peint derrière lui.
        drawClouds(canvas, w, h, camX, camY, camScale)
        // Et les oiseaux devant les nuages : ils volent plus bas, donc plus près.
        drawBirds(canvas, w, h, camX, camY, camScale)
    }

    /**
     * Le fond du ciel et ses étoiles.
     *
     * Le dégradé se refabrique quand **la couleur** change et pas seulement quand la
     * hauteur change — c'est ce qui manquait à la copie de l'atelier, qui en allouait un
     * par image. On compare donc les deux couleurs, sans quoi un crépuscule resterait
     * bleu.
     */
    private fun drawSky(canvas: Canvas, w: Float, h: Float) {
        if (bgShaderHeight != h || bgZenith != sky.zenith || bgHorizon != sky.horizon) {
            pBg.shader = LinearGradient(
                0f, 0f, 0f, h, sky.zenith, sky.horizon, Shader.TileMode.CLAMP
            )
            bgShaderHeight = h
            bgZenith = sky.zenith
            bgHorizon = sky.horizon
        }
        canvas.drawRect(0f, 0f, w, h, pBg)

        if (sky.starAlpha > 0.02f) {
            pStar.alpha = (sky.starAlpha * 170f).toInt().coerceIn(0, 255)
            for (i in starsX.indices) {
                canvas.drawCircle(starsX[i] * w, starsY[i] * h * 0.55f, starsR[i] * dp, pStar)
            }
        }
    }

    /**
     * Le Soleil, et ce qu'il en reste quand la Lune passe devant.
     *
     * Deux disques nus faisaient un autocollant. Ce sont deux **dégradés radiaux** — une
     * couronne qui se fond dans le ciel, un disque dont le cœur est plus blanc que le
     * bord — et ça ne coûte rien de plus par image : les nuanciers sont posés à l'origine
     * et c'est le canevas qu'on translate, donc ils ne se refabriquent que quand la
     * teinte franchit un palier, soit une fois toutes les dix secondes.
     *
     * Le couchant est aplati, aussi. L'atmosphère comprime le disque d'un bon dixième au
     * ras de l'horizon ; c'est un détail que tout le monde a déjà vu sans le nommer, et
     * il coûte une mise à l'échelle.
     */
    private fun drawSun(canvas: Canvas, w: Float, h: Float) {
        if (sky.sunAltitude < -0.12f) return
        val cx = skyX(sky.sunX, w)
        val cy = skyY(sky.sunAltitude, h)
        val r = SUN_RADIUS_DP * dp
        val haut = sky.sunAltitude.coerceIn(0f, 1f)

        // La teinte par paliers : orange rasant, blanc chaud au zénith. Vingt-quatre
        // marches sur une matinée de cinq minutes, donc invisibles à l'œil.
        val palier = (haut * 24f).roundToInt() / 24f
        majSunShaders(SkyState.mix(0xFFFF8A2B.toInt(), 0xFFFFF6D0.toInt(), palier), r)

        canvas.save()
        canvas.translate(cx, cy)
        if (haut < HORIZON_SQUASH) {
            canvas.scale(1f, 1f - 0.12f * (1f - haut / HORIZON_SQUASH))
        }

        // Le halo s'éteint quand le Soleil se couche : un halo à midi fait éblouissement,
        // le même à l'horizon ferait une tache.
        pHalo.alpha = ((1f - sky.eclipse) * (0.30f + 0.50f * haut) * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, r * SUN_CORONA, pHalo)
        canvas.drawCircle(0f, 0f, r, pSun)

        // L'éclipse : on repose le disque de la Lune par-dessus, décalé de ce qui reste
        // à couvrir. À couverture pleine il ne dépasse qu'un anneau de couronne.
        if (sky.eclipse > 0f) {
            pMoonDark.color = SkyState.mix(sky.zenith, 0xFF0A0A12.toInt(), 0.7f)
            canvas.drawCircle(r * 2f * (1f - sky.eclipse), 0f, r, pMoonDark)
        }
        canvas.restore()
    }

    /** Refabrique les nuanciers du Soleil, et seulement quand ils ont changé. */
    private fun majSunShaders(teinte: Int, r: Float) {
        if (teinte == sunTint && r == sunShaderRadius) return
        sunTint = teinte
        sunShaderRadius = r
        // Le disque : un cœur presque blanc, un bord qui retombe dans la teinte chaude.
        pSun.shader = RadialGradient(
            0f, 0f, r,
            intArrayOf(
                SkyState.mix(teinte, 0xFFFFFFFF.toInt(), 0.40f),
                teinte,
                SkyState.mix(teinte, 0xFFE0651A.toInt(), 0.26f)
            ),
            floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        // La couronne : opaque au ras du disque, éteinte au bord. Quatre arrêts plutôt
        // que deux — une décroissance linéaire s'arrête net et se voit comme un anneau,
        // alors que deux pentes de plus en plus douces se perdent dans le ciel.
        pHalo.shader = RadialGradient(
            0f, 0f, r * SUN_CORONA,
            intArrayOf(teinte, alpha(teinte, 0.30f), alpha(teinte, 0.09f), alpha(teinte, 0f)),
            floatArrayOf(0f, 1f / SUN_CORONA, 0.46f, 1f), Shader.TileMode.CLAMP
        )
    }

    /**
     * La Lune : **une vraie sphère texturée**, éclairée de côté.
     *
     * C'est la seule façon d'avoir des phases qui ressemblent aux vraies. Découper un
     * disque, même avec la bonne ellipse, donne une forme juste et une Lune fausse : pas
     * de mers, pas de terminateur qui s'estompe, pas de lumière cendrée sur le croissant.
     * Ici on ne découpe rien — [MoonFace] éclaire une sphère avec la carte lunaire de
     * l'application, la même que celle du widget Terre-Lune, et le terminateur tombe où
     * la géométrie le met.
     *
     * **Et ça ne coûte rien par image.** L'image de la Lune est mise en cache et ne se
     * recalcule que quand la phase a bougé d'un tiers de pixel — une fois par minute au
     * rythme du ciel du jeu, sur soixante pixels de côté.
     *
     * Si la carte lunaire manque, on retombe sur [drawMoonShape], qui reste exact.
     */
    private fun drawMoon(canvas: Canvas, w: Float, h: Float) {
        if (sky.moonAltitude < -0.12f) return
        val cx = skyX(sky.moonX, w)
        val cy = skyY(sky.moonAltitude, h)
        val r = MOON_RADIUS_DP * dp
        // Une lune de plein jour est pâle, une lune de nuit est franche.
        val opacite = ((0.35f + 0.65f * sky.starAlpha) * 255f).toInt().coerceIn(0, 255)

        // Le halo : une pleine lune de nuit noire en a un, un croissant de jour non.
        val eclat = sky.moonPhase * sky.starAlpha
        if (eclat > 0.05f) {
            if (moonGlowRadius != r) {
                moonGlowRadius = r
                pMoonGlow.shader = RadialGradient(
                    0f, 0f, r * MOON_GLOW,
                    intArrayOf(alpha(MOON_GLOW_COLOR, 0.30f), alpha(MOON_GLOW_COLOR, 0f)),
                    floatArrayOf(1f / MOON_GLOW, 1f), Shader.TileMode.CLAMP
                )
            }
            pMoonGlow.alpha = (eclat * 255f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, r * MOON_GLOW, pMoonGlow)
            canvas.restore()
        }

        // La clarté du ciel efface la face nocturne : de jour, la Lune n'est qu'un
        // morceau de blanc qui flotte, jamais un disque gris.
        val bmp = moonFace.bitmap(sky.moonPhase, sky.moonWaxing, r.toInt(), 1f - sky.starAlpha)
        if (bmp == null) {
            drawMoonShape(canvas, cx, cy, r, opacite)
            return
        }
        pMoon.alpha = opacite
        val demi = bmp.width / 2f
        canvas.drawBitmap(bmp, cx - demi, cy - demi, pMoon)
    }

    /**
     * La Lune de secours, quand la carte lunaire ne se charge pas.
     *
     * **Le terminateur est une ellipse, pas un cercle.** La toute première version posait
     * un second disque décalé sur le premier, ce qui ne peut produire qu'un croissant —
     * jamais un demi-disque net au quartier — et ce qui surtout inversait la phase, le
     * décalage valant la part *non* éclairée : pleine lune noire, nouvelle lune pleine.
     *
     * La bonne figure est connue : **un demi-limbe circulaire** fermé par **une
     * demi-ellipse** de demi-axe `r × (2k − 1)`. Le signe fait tout — positif, l'ellipse
     * bombe vers l'ombre et donne une gibbeuse ; négatif, elle bombe vers la lumière et
     * creuse un croissant ; nul, le terminateur est droit et on a le quartier. L'aire de
     * cette figure vaut exactement `k` fois le disque, ce qu'un test vérifie.
     */
    private fun drawMoonShape(canvas: Canvas, cx: Float, cy: Float, r: Float, opacite: Int) {
        pMoonDark.color = SkyState.mix(sky.zenith, sky.horizon, 0.35f)
        pMoonDark.alpha = opacite
        canvas.drawCircle(cx, cy, r, pMoonDark)

        val k = sky.moonPhase
        if (k <= 0.005f) return          // nouvelle lune : il n'y a rien à éclairer
        pMoon.alpha = opacite
        if (k >= 0.995f) {               // pleine lune : le disque entier
            canvas.drawCircle(cx, cy, r, pMoon)
            return
        }

        // Le limbe : la moitié droite du disque, du haut vers le bas.
        moonPath.reset()
        moonOval.set(cx - r, cy - r, cx + r, cy + r)
        moonPath.addArc(moonOval, -90f, 180f)

        // Puis le terminateur, du bas vers le haut, sur l'ellipse.
        val demiAxe = r * SkyState.terminatorAxis(k)
        val a = abs(demiAxe)
        if (a < 0.01f * r) {
            moonPath.lineTo(cx, cy - r)  // quartier : le terminateur est un diamètre
        } else {
            moonOval.set(cx - a, cy - r, cx + a, cy + r)
            moonPath.arcTo(moonOval, 90f, if (demiAxe >= 0f) 180f else -180f)
        }
        moonPath.close()

        if (sky.moonWaxing) {
            canvas.drawPath(moonPath, pMoon)
        } else {
            // Décroissante, la même figure vue en miroir : éclairée à gauche.
            canvas.save()
            canvas.scale(-1f, 1f, cx, cy)
            canvas.drawPath(moonPath, pMoon)
            canvas.restore()
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, camX: Float, camY: Float, camScale: Float) {
        // Le blanc n'entre que pour moitié : au-delà, un nuage de nuit redevient une
        // tache claire, et l'illusion tombe. Le nuancier ne se refabrique que si cette
        // teinte change, et elle ne bouge qu'avec l'heure du ciel.
        val tint = SkyState.mix(sky.horizon, 0xFFFFFFFF.toInt(), 0.5f)
        if (tint != cloudTint) {
            cloudTint = tint
            pCloud.shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(tint, tint and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        pCloud.alpha = (0.55f * 255f).toInt()

        val demi = w / 2f / camScale
        for (c in clouds.clouds) {
            // Le nuage se répète tous les [CloudField.SPAN] mètres : on cherche la copie
            // qui tombe dans la fenêtre, s'il y en a une.
            var x = c.x
            x -= floor((x - camX + CloudField.SPAN / 2f) / CloudField.SPAN) * CloudField.SPAN
            if (x < camX - demi - c.size * 3f || x > camX + demi + c.size * 3f) continue

            val cy = sy(c.altitude, h, camY, camScale)
            val taille = c.size * camScale
            if (cy > h + taille * 2f || cy < -taille * 3f) continue

            val cx = sx(x, w, camX, camScale)
            var i = 0
            while (i < c.puffs.size) {
                val r = c.puffs[i + 2] * taille
                if (r < 0.5f) { i += 3; continue }
                // Le nuancier est bâti en rayon unitaire à l'origine : une échelle et une
                // translation du canevas le posent où il faut, sans jamais y toucher
                // lui-même. C'est ce qui évite de refabriquer un nuancier par boule — il
                // y en a jusqu'à soixante par image.
                canvas.save()
                canvas.translate(cx + c.puffs[i] * taille, cy + c.puffs[i + 1] * taille)
                canvas.scale(r, r)
                canvas.drawCircle(0f, 0f, 1f, pCloud)
                canvas.restore()
                i += 3
            }
        }
    }

    /**
     * Les oiseaux : deux traits qui montent et retombent, comme des ailes.
     *
     * Même logique de bande qui se reboucle que [drawClouds], en plus court —
     * [BirdField.SPAN] tient dans ce qu'on voit sans jamais montrer deux fois le même
     * oiseau, puisqu'ils volent bas et près, pas à l'échelle du paysage entier.
     */
    private fun drawBirds(canvas: Canvas, w: Float, h: Float, camX: Float, camY: Float, camScale: Float) {
        // Ils se couchent avec le jour : c'est une opacité, pas un arrêt du vol.
        if (sky.light <= 0.02f) return
        pBird.alpha = (sky.light * 200f).toInt().coerceIn(0, 255)

        val demi = w / 2f / camScale
        for (b in birds.birds) {
            var x = b.x
            x -= floor((x - camX + BirdField.SPAN / 2f) / BirdField.SPAN) * BirdField.SPAN
            if (x < camX - demi - b.span * 6f || x > camX + demi + b.span * 6f) continue

            val spanPx = b.span * camScale
            // Trop petit pour se voir : inutile de tracer deux traits d'un demi-pixel.
            if (spanPx < 1.5f) continue

            val bob = sin(ambientClock * 0.9f + b.bobPhase) * b.span * 1.4f
            val cy = sy(b.altitude + bob, h, camY, camScale)
            if (cy > h + spanPx * 3f || cy < -spanPx * 3f) continue
            val cx = sx(x, w, camX, camScale)

            // Le battement : les pointes montent et retombent, pas le corps.
            val flap = sin(ambientClock * b.flapRate * 6.2832f + b.flapPhase) * 0.5f + 0.5f
            val liftPx = spanPx * (0.15f + 0.55f * flap)
            canvas.drawLine(cx, cy, cx - spanPx, cy - liftPx, pBird)
            canvas.drawLine(cx, cy, cx + spanPx, cy - liftPx, pBird)
        }
    }

    /** L'abscisse d'un astre à l'écran : le lever à gauche, le coucher à droite. */
    private fun skyX(fraction: Float, w: Float) = (0.08f + 0.84f * fraction) * w

    /** Son ordonnée : l'horizon en bas de la bande de ciel, le zénith en haut. */
    private fun skyY(altitude: Float, h: Float): Float {
        val ciel = h * skyBand
        return ciel - altitude.coerceIn(-0.2f, 1f) * ciel * 0.86f
    }

    // Les mêmes projections monde → écran que les deux vues, passées en paramètres
    // plutôt qu'héritées : le décor n'a pas de caméra à lui, il emprunte celle qu'on
    // lui tend.
    private fun sx(x: Float, w: Float, camX: Float, camScale: Float) =
        (x - camX) * camScale + w / 2f

    private fun sy(y: Float, h: Float, camY: Float, camScale: Float) =
        h / 2f - (y - camY) * camScale

    /** La même couleur, à l'opacité donnée. */
    private fun alpha(color: Int, a: Float): Int =
        ((a * 255f).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    companion object {
        /**
         * Part de la hauteur d'écran qu'occupe le ciel, par défaut.
         *
         * C'est l'horizon des astres : au-dessous, le Soleil et la Lune sont couchés.
         */
        const val SKY_BAND = 0.62f

        /**
         * Rayons du Soleil et de la Lune, en points d'écran.
         *
         * Aucun des deux n'est à l'échelle, et ça n'a pas de sens de vouloir l'y mettre :
         * un Soleil à sa taille apparente ferait un demi-degré, soit trois pixels sur un
         * téléphone, et la Lune autant. On peint un ciel, pas une carte du ciel. La Lune
         * est un peu plus grande que le Soleil pour que sa phase se lise, et pour qu'une
         * éclipse la montre en train de le manger.
         */
        const val SUN_RADIUS_DP = 13f
        const val MOON_RADIUS_DP = 15f

        /**
         * Rayon de la couronne solaire et du halo lunaire, en rayons d'astre.
         *
         * La couronne est large parce qu'un halo serré fait un anneau : c'est le dégradé
         * qui doit avoir la place de s'éteindre dans le ciel. Le halo lunaire est plus
         * discret — la Lune n'éblouit personne, elle ne fait que baver un peu sur une
         * nuit noire.
         */
        const val SUN_CORONA = 4.2f
        const val MOON_GLOW = 2.6f

        /** Le bleu très pâle d'un halo de pleine lune. */
        const val MOON_GLOW_COLOR = 0xFFCFE0F2.toInt()

        /**
         * Hauteur en deçà de laquelle le Soleil s'aplatit, en fraction du zénith.
         *
         * L'aplatissement est réel : la réfraction relève le bord inférieur du disque
         * plus que le supérieur, et un Soleil couchant est visiblement ovale. Il ne se
         * voit que très bas, d'où le seuil.
         */
        const val HORIZON_SQUASH = 0.22f
    }
}
