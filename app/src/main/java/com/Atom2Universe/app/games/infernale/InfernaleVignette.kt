package com.Atom2Universe.app.games.infernale

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * La case de reserve : l'icone d'une piece, son compte, et son etat de selection.
 *
 * ## Pourquoi une icone dessinee ici, et pas la bibliotheque `art/`
 *
 * `art/` dessine cent vingt-six pieces de machinerie, et elles sont belles — mais ce
 * sont les pieces d'un **autre** vocabulaire : poutres, engrenages, verins, cuves. Aucune
 * ne represente une bascule ni un tambour, et en prendre une « qui ressemble » apprendrait
 * au joueur une correspondance fausse.
 *
 * L'icone est donc dessinee avec les memes couleurs et les memes formes que la piece sur
 * le tableau : le bois est le meme bois, l'ivoire du domino le meme ivoire. Choisir dans
 * la reserve et reconnaitre sur le tableau devient le meme geste, ce qui est exactement ce
 * qu'on demande a une icone.
 */
class InfernaleVignette(ctx: Context) : View(ctx) {

    var type: TypePiece = TypePiece.RAMPE
        set(value) {
            field = value
            invalidate()
        }

    var reste: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var choisie: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val fond = Paint().apply { isAntiAlias = true }
    private val bordure = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
    }
    private val bois = Paint().apply { color = 0xFFB27C48.toInt(); isAntiAlias = true }
    private val boisSombre = Paint().apply { color = 0xFF7C5430.toInt(); isAntiAlias = true }
    private val pierre = Paint().apply { color = 0xFF6C7285.toInt(); isAntiAlias = true }
    private val fer = Paint().apply { color = 0xFF4E5568.toInt(); isAntiAlias = true }
    private val ferClair = Paint().apply { color = 0xFF7C8499.toInt(); isAntiAlias = true }
    private val caoutchouc = Paint().apply { color = 0xFFC03A4E.toInt(); isAntiAlias = true }
    private val caoutchoucClair = Paint().apply { color = 0xFFFF8676.toInt(); isAntiAlias = true }
    private val ivoire = Paint().apply { color = 0xFFE9E2CE.toInt(); isAntiAlias = true }
    private val point = Paint().apply { color = 0xFF3A3226.toInt(); isAntiAlias = true }
    private val peau = Paint().apply { color = 0xFFE0C088.toInt(); isAntiAlias = true }
    private val souffle = Paint().apply {
        color = 0x99BFE4FF.toInt(); strokeWidth = 3f; isAntiAlias = true
    }
    private val corde = Paint().apply {
        color = 0xFFCBA76A.toInt(); strokeWidth = 2.5f; isAntiAlias = true
    }
    private val texte = Paint().apply {
        isAntiAlias = true; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cote = (resources.displayMetrics.density * 62f).toInt()
        setMeasuredDimension(cote, cote)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        fond.color = if (choisie) 0xFF2C4270.toInt() else 0xFF161E33.toInt()
        c.drawRoundRect(2f, 2f, w - 2f, h - 2f, 10f, 10f, fond)
        bordure.color = if (choisie) 0xFF9BC2FF.toInt() else 0xFF2A3450.toInt()
        c.drawRoundRect(2f, 2f, w - 2f, h - 2f, 10f, 10f, bordure)

        val cx = w / 2f
        val cy = h * 0.44f
        val u = w * 0.17f
        icone(c, cx, cy, u)

        texte.color = if (choisie) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt()
        texte.textSize = h * 0.2f
        c.drawText("×$reste", cx, h - h * 0.08f, texte)
    }

    /** Chaque icone est le croquis de ce que la piece fait, pas de ce qu'elle est. */
    private fun icone(c: Canvas, cx: Float, cy: Float, u: Float) {
        when (type) {
            TypePiece.RAMPE -> {
                c.save()
                c.rotate(20f, cx, cy)
                c.drawRect(cx - u * 1.6f, cy - u * 0.2f, cx + u * 1.6f, cy + u * 0.2f, bois)
                c.drawRect(cx - u * 1.6f, cy, cx + u * 1.6f, cy + u * 0.2f, boisSombre)
                c.restore()
            }

            TypePiece.PLOT -> {
                c.drawCircle(cx, cy, u * 1.1f, caoutchouc)
                c.drawCircle(cx, cy, u * 0.68f, caoutchoucClair)
                c.drawCircle(cx, cy, u * 0.32f, caoutchouc)
            }

            TypePiece.BLOC -> {
                c.drawRect(cx - u, cy - u, cx + u, cy + u, pierre)
                c.drawRect(cx - u, cy - u, cx + u, cy - u * 0.5f, ferClair)
            }

            TypePiece.DOMINO -> {
                for (k in -1..1) {
                    val x = cx + k * u * 0.85f
                    c.drawRect(x - u * 0.24f, cy - u * 1.1f, x + u * 0.24f, cy + u * 1.1f, ivoire)
                    c.drawCircle(x, cy - u * 0.5f, u * 0.13f, point)
                    c.drawCircle(x, cy + u * 0.5f, u * 0.13f, point)
                }
            }

            TypePiece.BASCULE -> {
                c.save()
                c.rotate(-14f, cx, cy)
                c.drawRect(cx - u * 1.5f, cy - u * 0.16f, cx + u * 1.5f, cy + u * 0.16f, bois)
                c.restore()
                c.drawRect(cx - u * 0.22f, cy, cx + u * 0.22f, cy + u * 1.1f, fer)
            }

            TypePiece.TREMPLIN -> {
                c.drawRect(cx - u * 1.4f, cy + u * 0.6f, cx - u * 0.9f, cy + u * 1.2f, fer)
                c.save()
                c.rotate(-18f, cx, cy)
                c.drawRect(cx - u * 1.4f, cy - u * 0.16f, cx + u * 1.4f, cy + u * 0.16f, bois)
                c.restore()
                // Le ressort, en trois dents
                var x = cx + u * 0.5f
                while (x < cx + u * 1.5f) {
                    c.drawLine(x, cy + u * 0.4f, x + u * 0.25f, cy + u * 1.1f, corde)
                    c.drawLine(x + u * 0.25f, cy + u * 1.1f, x + u * 0.5f, cy + u * 0.4f, corde)
                    x += u * 0.5f
                }
            }

            TypePiece.VENTILATEUR -> {
                c.drawRect(cx - u * 1.5f, cy - u, cx - u * 0.4f, cy + u, fer)
                for (pale in 0 until 3) {
                    c.save()
                    c.rotate(pale * 120f + 20f, cx - u * 0.95f, cy)
                    c.drawRect(
                        cx - u * 1.05f, cy - u * 0.75f,
                        cx - u * 0.85f, cy, ferClair
                    )
                    c.restore()
                }
                for (k in 0 until 3) {
                    val y = cy - u * 0.6f + k * u * 0.6f
                    c.drawLine(cx - u * 0.2f, y, cx + u * 0.7f + k % 2 * u * 0.4f, y, souffle)
                }
            }

            TypePiece.TAMBOUR -> {
                c.drawRect(cx - u * 1.6f, cy - u * 0.3f, cx + u * 1.6f, cy + u * 0.3f, peau)
                c.drawRect(cx - u * 1.6f, cy - u * 0.3f, cx - u * 1.2f, cy + u * 0.3f, boisSombre)
                c.drawRect(cx + u * 1.2f, cy - u * 0.3f, cx + u * 1.6f, cy + u * 0.3f, boisSombre)
                c.drawCircle(cx, cy - u * 1.1f, u * 0.35f, ivoire)
            }

            TypePiece.POULIE -> {
                c.drawRect(cx - u * 1.5f, cy - u * 1.4f, cx + u * 1.5f, cy - u * 1.1f, fer)
                c.drawCircle(cx - u * 0.8f, cy - u * 1.1f, u * 0.3f, ferClair)
                c.drawCircle(cx + u * 0.8f, cy - u * 1.1f, u * 0.3f, ferClair)
                c.drawLine(cx - u * 0.8f, cy - u * 1.1f, cx - u * 0.8f, cy + u * 0.2f, corde)
                c.drawLine(cx + u * 0.8f, cy - u * 1.1f, cx + u * 0.8f, cy + u * 0.9f, corde)
                c.drawRect(cx - u * 1.2f, cy + u * 0.2f, cx - u * 0.4f, cy + u * 0.8f, boisSombre)
                c.drawRect(cx + u * 0.5f, cy + u * 0.9f, cx + u * 1.1f, cy + u * 1.4f, fer)
            }
        }
    }
}
