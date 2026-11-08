package com.Atom2Universe.app.studio.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.Atom2Universe.app.studio.data.Track
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.TrackKind

/**
 * Les petits dessins des instruments, tracés au pinceau (aucun fichier image) : ils remplacent le nom sur la plaque colorée de
 * chaque piste dans le rythme. Chaque dessin tient dans un carré de 24 unités ; on lui donne le centre, la taille et la couleur.
 */
object InstrumentIcons {

    /** Les sept sons de batterie ont le numéro de leur [Inst] ; viennent ensuite le synthé et le soundfont. */
    const val SYNTH = Inst.DRUM_COUNT
    const val SF2 = Inst.DRUM_COUNT + 1

    fun of(t: Track): Int = when (t.kind) {
        TrackKind.DRUM -> t.voice.coerceIn(0, Inst.DRUM_COUNT - 1)
        TrackKind.SYNTH -> SYNTH
        else -> SF2
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val path = Path()

    /** Dessine l'icône [icon] centrée en ([cx], [cy]), dans un carré de côté [size] (en pixels), de la couleur [color]. */
    fun draw(canvas: Canvas, icon: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val u = size / 24f
        val x0 = cx - 12f * u
        val y0 = cy - 12f * u
        stroke.color = color
        stroke.strokeWidth = 2.2f * u
        fill.color = color

        fun x(v: Float) = x0 + v * u
        fun y(v: Float) = y0 + v * u
        fun line(ax: Float, ay: Float, bx: Float, by: Float) = canvas.drawLine(x(ax), y(ay), x(bx), y(by), stroke)
        fun oval(l: Float, t: Float, r: Float, b: Float, p: Paint) {
            rect.set(x(l), y(t), x(r), y(b))
            canvas.drawOval(rect, p)
        }
        fun round(l: Float, t: Float, r: Float, b: Float, radius: Float, p: Paint) {
            rect.set(x(l), y(t), x(r), y(b))
            canvas.drawRoundRect(rect, radius * u, radius * u, p)
        }

        when (icon) {
            Inst.KICK -> {
                // La grosse caisse vue de face : la peau et son centre
                oval(2f, 2f, 22f, 22f, stroke)
                oval(7f, 7f, 17f, 17f, fill)
            }
            Inst.SNARE -> {
                // Un fût large, sa peau du dessus, et deux baguettes croisées
                round(3f, 12f, 21f, 21f, 2.5f, fill)
                oval(3f, 9f, 21f, 15f, stroke)
                line(5f, 2f, 13f, 8f)
                line(19f, 2f, 11f, 8f)
            }
            Inst.CLAP -> {
                // Deux mains qui se rejoignent, et trois traits de claquement
                canvas.save()
                canvas.rotate(20f, x(6f), y(21f))
                round(3f, 7f, 9f, 21f, 3f, fill)
                canvas.restore()
                canvas.save()
                canvas.rotate(-20f, x(18f), y(21f))
                round(15f, 7f, 21f, 21f, 3f, fill)
                canvas.restore()
                line(12f, 1.5f, 12f, 5f)
                line(6f, 3.5f, 8f, 6f)
                line(18f, 3.5f, 16f, 6f)
            }
            Inst.HAT -> {
                // Charleston fermé : deux cymbales l'une sur l'autre, sur leur pied
                oval(2f, 8f, 22f, 12f, fill)
                oval(2f, 12.8f, 22f, 16.8f, fill)
                line(12f, 17f, 12f, 22f)
                line(7f, 22f, 17f, 22f)
            }
            Inst.OPEN_HAT -> {
                // Charleston ouvert : la cymbale du haut se soulève
                oval(2f, 2f, 22f, 6f, fill)
                oval(2f, 11f, 22f, 15f, fill)
                line(12f, 6f, 12f, 11f)
                line(12f, 15f, 12f, 22f)
                line(7f, 22f, 17f, 22f)
            }
            Inst.TOM -> {
                // Un tom haut sur ses pieds
                round(4f, 8f, 20f, 18f, 3f, fill)
                oval(4f, 5f, 20f, 11f, stroke)
                line(7f, 18f, 5f, 22f)
                line(17f, 18f, 19f, 22f)
            }
            Inst.PERC -> {
                // Une cloche : le corps qui s'évase et sa poignée
                path.reset()
                path.moveTo(x(8f), y(8f))
                path.lineTo(x(16f), y(8f))
                path.lineTo(x(20f), y(21f))
                path.lineTo(x(4f), y(21f))
                path.close()
                canvas.drawPath(path, fill)
                round(10f, 2.5f, 14f, 8f, 1.5f, stroke)
            }
            SYNTH -> {
                // Un clavier : touches blanches, trois noires
                round(2f, 5f, 22f, 19f, 2.5f, stroke)
                line(7f, 11f, 7f, 19f)
                line(12f, 11f, 12f, 19f)
                line(17f, 11f, 17f, 19f)
                round(5.5f, 5f, 8.5f, 13f, 1f, fill)
                round(10.5f, 5f, 13.5f, 13f, 1f, fill)
                round(15.5f, 5f, 18.5f, 13f, 1f, fill)
            }
            else -> {
                // Une croche : un instrument du soundfont
                canvas.save()
                canvas.rotate(-20f, x(9f), y(18f))
                oval(4.5f, 14.5f, 13.5f, 21.5f, fill)
                canvas.restore()
                line(12.5f, 17.5f, 12.5f, 3f)
                path.reset()
                path.moveTo(x(12.5f), y(3f))
                path.quadTo(x(14f), y(9f), x(19.5f), y(11f))
                canvas.drawPath(path, stroke)
            }
        }
    }
}
