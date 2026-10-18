package com.Atom2Universe.app.games.trebuchet

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * **Le cadrage : où est la caméra, et où le sol tombe dans l'image.**
 *
 * Troisième pièce partagée entre le trébuchet et l'atelier d'engrenages, après
 * [TrebuchetGround] et [ShotTrail]. Une caméra ne regarde pas une machine, elle regarde
 * un **site** : le sol posé en bas de l'image, la vue qui recule quand le tir monte, les
 * butées qui empêchent de partir dans le vide. Rien de tout ça ne change selon qu'on a
 * lancé le boulet avec un contrepoids ou avec de l'air comprimé.
 *
 * ### Le plancher, et pourquoi c'est lui qui compte
 *
 * Le « sol », dans une image, **c'est le point le plus bas qu'on voie, pas l'altitude
 * zéro**. Les deux vues avaient chacune leur `groundCamY`, et elles ne disaient pas la
 * même chose :
 *
 * ```
 * trébuchet   camFloor + (h/2 - inset·dp) / échelle
 * atelier                (h/2 - inset·dp) / échelle      <- pas de plancher
 * ```
 *
 * Sans plancher, la ligne de sol est clouée à `h - inset·dp` quel que soit le terrain.
 * Un site bâti au fond d'un vallon a donc ses pieds `profondeur × échelle` pixels plus
 * bas — hors de l'écran dès que ça dépasse la marge. Mesuré le 04/09/2026 sur une
 * tablette 1920 × 1200 :
 *
 * ```
 * graine 2, sol à -8,3 m
 *   tout le site  (8 px/m) : pied du village à 1204 px chez l'atelier, 1132 au trébuchet
 *   un bâtiment  (64 px/m) : pied du village à 1670 px chez l'atelier, 1132 au trébuchet
 * PIRE : 470 px sous le bord bas de l'écran (sur 1200).
 * ```
 *
 * Le trébuchet le pose toujours à 1132 px, quelle que soit l'échelle : c'est ce que fait
 * le plancher. Le seuil est simple — le village sort de l'écran dès que `profondeur ×
 * échelle` dépasse la marge du bas, soit **7 px/m** pour un vallon de huit mètres. C'est
 * n'importe quel cadrage plus serré que deux cent soixante-quatorze mètres de terrain.
 *
 * Et ça ne s'arrête pas au bas de l'image : les fenêtres de cadrage se comptent **depuis
 * le plancher** (`boulet.y - floor`, `site.baseHeight - floor`), sinon une fenêtre réglée
 * sur la seule altitude du boulet le laisse sortir par le haut au-dessus d'un vallon.
 * L'atelier les comptait depuis zéro.
 *
 * Le plancher est **lissé**, comme tout le reste du cadrage : il change quand un creux
 * entre dans la vue, et un plancher qui sauterait ferait sauter l'horizon avec lui.
 *
 * ### Ce que cette pièce ne fait pas
 *
 * Elle ne choisit **pas** ce qu'on regarde. Quel plan pour quelle phase, quelles butées,
 * quelle vitesse de rattrapage : ça, c'est le jeu, et les deux jeux ont raison d'en
 * décider différemment. Ici il n'y a que la mécanique — poser, rattraper, borner,
 * projeter — c'est-à-dire tout ce qui n'avait aucune raison d'exister en double.
 */
class ShotCamera(private val groundInsetDp: Float) {

    var x = 0f
    var y = 2f
    var scale = 60f

    /** Jusqu'où le sol descend dans ce qu'on regarde. Jamais au-dessus de zéro. */
    var floor = 0f
        private set

    /**
     * Faux tant que le cadrage n'a jamais été posé : la première image saute au but au
     * lieu d'y glisser depuis un cadrage qui n'a jamais existé.
     *
     * La vue le remet à faux quand la surface change de taille, et à vrai quand le joueur
     * pose lui-même le cadrage au doigt — un pincement est un cadrage posé.
     */
    var ready = false

    /** Vrai si le cadrage a bougé depuis l'image précédente. Voir [beginFrame]. */
    var moving = true
        private set

    private var w = 0f
    private var h = 0f
    private var dp = 1f
    private var prevX = Float.NaN
    private var prevY = Float.NaN
    private var prevScale = Float.NaN

    /** La vue a une surface : avant ça, tout calcul de cadrage est un calcul sur zéro. */
    val hasSurface: Boolean get() = w > 0f && h > 0f

    fun resize(width: Int, height: Int, density: Float) {
        w = width.toFloat()
        h = height.toFloat()
        dp = density
    }

    /**
     * L'ordonnée de caméra qui pose le sol au bas de l'image, la bande de terre des
     * bornes de distance gardée dessous.
     */
    fun groundY(atScale: Float = scale): Float = floor + (h / 2f - groundInsetDp * dp) / atScale

    /**
     * Relit jusqu'où le sol descend dans la portion visible.
     *
     * @param lowest le point le plus bas du relief entre deux abscisses — en pratique
     *   `terrain::lowestBetween`. La pièce ne connaît pas le terrain, elle le demande.
     */
    fun updateFloor(dt: Float, lowest: (Float, Float) -> Float) {
        if (!hasSurface || scale <= 0f) return
        val halfW = w / 2f / scale
        val aim = min(0f, lowest(x - halfW, x + halfW))
        floor = if (!ready) aim else floor + (aim - floor) * (dt * FLOOR_RATE).coerceIn(0f, 1f)
    }

    /**
     * Pose le cadrage d'un coup.
     *
     * Le plancher se prend **une fois la vue posée**, sinon il se lisserait depuis un
     * cadrage qui n'a jamais existé et le premier dixième de seconde du niveau se
     * jouerait avec l'horizon en train de glisser.
     */
    fun snap(dt: Float, targetX: Float, targetScale: Float, lowest: (Float, Float) -> Float) {
        x = targetX
        scale = targetScale
        updateFloor(dt, lowest)
        y = groundY()
        ready = true
    }

    /**
     * Rattrape la cible sans à-coups, puis recale le sol.
     *
     * Le sol se recale sur l'échelle **réelle** et non sur celle qu'on visait : le zoom
     * et le déplacement se rattrapent à des vitesses différentes, et l'écart, si petit
     * soit-il, ferait respirer l'horizon à chaque image. Il n'y a rien de pire à regarder
     * qu'un sol qui flotte.
     *
     * Le plancher se relit **après** que le cadrage a bougé : c'est ce qu'on voit
     * maintenant qui décide jusqu'où le sol descend, pas ce qu'on voyait à l'image
     * d'avant.
     */
    fun follow(
        dt: Float,
        targetX: Float,
        targetScale: Float,
        rate: Float,
        lowest: (Float, Float) -> Float
    ) {
        if (!ready) {
            snap(dt, targetX, targetScale, lowest)
            return
        }
        x += (targetX - x) * (dt * rate).coerceIn(0f, 1f)
        scale += (targetScale - scale) * (dt * SCALE_RATE).coerceIn(0f, 1f)
        updateFloor(dt, lowest)
        y = groundY()
    }

    /**
     * Les butées du cadrage libre.
     *
     * La hauteur, elle, ne se règle pas : le sol est calé en bas de l'image et n'en bouge
     * plus, zoom compris. Une vue de tir se lit comme une gravure — l'horizon toujours à
     * la même place, et seule la distance qui défile.
     */
    fun clamp(left: Float, right: Float, minScale: Float, maxScale: Float) {
        if (!hasSurface || scale <= 0f) return
        scale = scale.coerceIn(minScale, maxScale)
        val halfW = w / 2f / scale
        x = if (right - left <= halfW * 2f) {
            // Dézoom complet : le terrain est plus étroit que la vue, on le centre.
            (left + right) / 2f
        } else {
            x.coerceIn(left + halfW, right - halfW)
        }
        y = groundY()
    }

    /**
     * Relève si le cadrage a bougé, au début de chaque image.
     *
     * C'est ce qui permet aux calques du décor — traces, fantômes — de se garder d'une
     * image à l'autre : tant que la caméra ne bouge pas, il n'y a rien à repeindre.
     *
     * **La comparaison se fait au demi-pixel près, pas à l'égalité stricte.** Le suivi
     * souple rattrape sa cible de façon asymptotique : à l'égalité stricte, la caméra
     * n'est jamais déclarée immobile avant longtemps, et le calque se refait à chaque
     * image pour un déplacement de un millième de pixel. L'atelier comparait à l'égalité
     * stricte, le trébuchet au demi-pixel — mesuré le 04/09/2026 sur un recadrage de cent
     * mètres à trente pixels par mètre : **186 images** pour que l'égalité stricte se
     * déclare immobile, **80** pour le demi-pixel. Cent six images de calque repeint pour
     * rien, presque deux secondes, après **chaque** changement de cadrage.
     */
    fun beginFrame() {
        moving = !sameFraming(prevX, prevY, prevScale)
        prevX = x
        prevY = y
        prevScale = scale
    }

    /**
     * Ce cadrage-ci est-il le même que celui-là, à moins d'un demi-pixel d'écran ?
     *
     * Sert aussi aux calques qui gardent leur propre cadrage de référence : un calque
     * peint pour une caméra reste valable tant qu'elle n'a pas bougé pour de bon.
     */
    fun sameFraming(px: Float, py: Float, ps: Float): Boolean {
        if (ps <= 0f || scale <= 0f) return false
        val demiDiagonale = hypot(w * 0.5f, h * 0.5f)
        if (abs(ps - scale) / scale * demiDiagonale > SLOP_PX) return false
        if (abs(px - x) * scale > SLOP_PX) return false
        if (abs(py - y) * scale > SLOP_PX) return false
        return true
    }

    fun sx(worldX: Float): Float = (worldX - x) * scale + w / 2f
    fun sy(worldY: Float): Float = h / 2f - (worldY - y) * scale
    fun worldX(px: Float): Float = (px - w / 2f) / scale + x
    fun worldY(py: Float): Float = y - (py - h / 2f) / scale

    companion object {
        /** Vitesse de lissage du plancher : on descend dans le vallon comme on y marcherait. */
        const val FLOOR_RATE = 4f

        /** Vitesse de rattrapage de l'échelle. Le déplacement, lui, se règle par phase. */
        const val SCALE_RATE = 2.5f

        /** Écart de cadrage encore considéré comme nul, en pixels d'écran. */
        const val SLOP_PX = 0.5f
    }
}

/**
 * **Attraper l'heure : l'appui long qui fait défiler le ciel.**
 *
 * Un doigt posé dans le ciel et tenu quatre dixièmes de seconde prend la main sur
 * l'horloge ; la vitesse suit alors **l'écart au point d'appui** et non la position
 * absolue du doigt — sinon un appui près du bord droit partirait à pleine vitesse avant
 * d'avoir bougé. Au point d'appui la vitesse est nulle : attraper l'heure l'arrête, et
 * c'est le meilleur moyen de regarder un crépuscule aussi longtemps qu'on veut.
 *
 * Le geste était écrit deux fois, à l'identique, une par vue. Il n'avait pas encore
 * divergé — mais rien ne l'en empêchait, et c'est exactement l'état dans lequel étaient
 * la trace et les fantômes la veille de leur divergence.
 *
 * La vue garde ce qui la regarde : **où** le ciel commence. Le trébuchet exige en plus
 * qu'aucune pièce ne soit saisie ; l'atelier, que le doigt soit au-dessus du relief. Elle
 * le tranche elle-même et le passe à [press].
 */
class TimeScrub {

    private var pressAt = 0L
    private var pressX = 0f
    private var fingerX = 0f
    private var candidate = false

    /** Vrai quand l'appui long a pris : le doigt ne fait plus que le temps. */
    var active = false
        private set

    /** Vitesse du défilement, en heures de monde par seconde réelle. */
    var rate = 0f
        private set

    /** Un doigt se pose. [eligible] dit si cette zone-là se dispute avec autre chose. */
    fun press(x: Float, eligible: Boolean, now: Long = SystemClock.uptimeMillis()) {
        candidate = eligible
        pressAt = now
        pressX = x
        fingerX = x
    }

    /**
     * Le doigt bouge. Rend vrai s'il tient toujours l'heure, auquel cas la vue ne doit
     * **rien** faire d'autre : on balaierait le temps et le terrain à la fois.
     *
     * Un doigt qui part avant la fin de l'appui long voulait déplacer la vue : ce n'est
     * plus un candidat.
     */
    fun move(x: Float, slopPx: Float): Boolean {
        fingerX = x
        if (active) return true
        if (candidate && abs(x - pressX) > slopPx) candidate = false
        return false
    }

    /** Deux doigts, un lever, un changement de mode : l'heure rend la main. */
    fun stop() {
        active = false
        candidate = false
        rate = 0f
    }

    /**
     * L'appui long se mesure **à l'image**, pas au minuteur : un minuteur qui se déclenche
     * pendant qu'on ne regarde pas est un piège à fuites. On le regarde donc à chaque
     * image, là où le temps passe de toute façon.
     */
    fun update(dt: Float, width: Float, clock: SkyClock) {
        if (!active) {
            if (candidate && SystemClock.uptimeMillis() - pressAt >= HOLD_MS) {
                active = true
                pressX = fingerX
            }
            return
        }
        rate = SkyClock.scrubRate(fingerX - pressX, width * THROW)
        clock.scrub(rate * dt)
    }

    /**
     * Ce que le joueur lit pendant qu'il tient l'heure : l'heure qu'il est, et à quelle
     * vitesse elle file.
     *
     * Sans ça le geste serait un secret : rien à l'écran ne dirait qu'on a quitté le
     * cadrage pour le temps, et un ciel qui se met à défiler passerait pour un bug.
     */
    fun draw(canvas: Canvas, paint: Paint, clock: SkyClock, w: Float, h: Float, dp: Float) {
        if (!active) return
        val minutes = ((clock.instant % 86_400_000L) + 86_400_000L) % 86_400_000L / 60_000L
        val heure = minutes / 60
        val minute = minutes % 60
        val sens = when {
            rate > 0.02f -> "▶"
            rate < -0.02f -> "◀"
            else -> "■"
        }
        paint.textSize = 20f * dp
        canvas.drawText(
            "%02d:%02d  %s %.1f h/s".format(heure, minute, sens, abs(rate)),
            w / 2f, h * 0.18f, paint
        )
    }

    companion object {
        /** Durée de l'appui avant que le doigt ne prenne l'heure. */
        const val HOLD_MS = 420L

        /** Course du geste : un tiers de la largeur de l'écran pour aller à fond. */
        const val THROW = 0.33f
    }
}
