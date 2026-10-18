package com.Atom2Universe.app.games.infernale

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * L'outil qui **montre** les pieces, faute de pouvoir lancer l'application.
 *
 * Il fait tourner le vrai moteur et peint avec le vrai pinceau de [InfernaleView] — pas un
 * dessin refait pour l'occasion — puis assemble quelques images par piece en planche, dans
 * `app/build/apercu/infernale/`. C'est la seule facon de repondre a « a quoi ressemble cette
 * piece, et que fait-elle quand elle bouge » sans appareil.
 *
 * Il ne verifie rien et n'echoue jamais : ce qu'il produit se juge a l'oeil. Banc d'aperçu,
 * pas garde-fou ; il est rapide, mais il n'a pas a tourner a chaque modification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfernaleApercuTest {

    private class Scene(
        val nom: String,
        val titre: String,
        val poses: List<Pose>,
        val billeX: Float,
        val billeY: Float,
        val instants: List<Float>,
        val minX: Float,
        val maxX: Float,
        val hauteur: Float = 3.2f
    )

    private val dossier = File("build/apercu/infernale").also { it.mkdirs() }

    private fun champ(o: Any, nom: String) =
        o.javaClass.getDeclaredField(nom).apply { isAccessible = true }

    private fun peindre(view: InfernaleView, w: Int, h: Int, scene: Scene, t: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val echelle = h / (scene.hauteur + 0.45f)
        champ(view, "echelle").setFloat(view, echelle)
        champ(view, "origineX").setFloat(view, w / 2f - (scene.minX + scene.maxX) / 2f * echelle)
        champ(view, "basY").setFloat(view, h - 0.45f * echelle)
        champ(view, "horloge").setFloat(view, t)
        val m = view.javaClass.getDeclaredMethod("peindre", Canvas::class.java)
        m.isAccessible = true
        m.invoke(view, Canvas(bmp))
        return bmp
    }

    private fun jouer(scene: Scene) {
        val ctx = RuntimeEnvironment.getApplication() as Application
        val view = InfernaleView(ctx)
        val w = 560
        val h = 360
        view.layout(0, 0, w, h)
        val partie = Partie(1L)
        view.jouer(partie)
        champ(view, "largeurVue").setInt(view, w)
        champ(view, "hauteurVue").setInt(view, h)
        // La bille de la scene est une piece comme une autre, posee en premier.
        val bille = pose(TypePiece.BILLE, scene.billeX, scene.billeY - Pieces.BILLE_RAYON)
        for (p in listOf(bille) + scene.poses) check(partie.poser(p) == Refus.OK) { "${scene.nom} : $p refusee" }
        // Les ombres de fond et le halo dependent de l'echelle : on les prepare une fois cadre.
        val prep = view.javaClass.getDeclaredMethod("preparerDecor", Long::class.javaPrimitiveType)
        prep.isAccessible = true

        val feuille = Bitmap.createBitmap(w * scene.instants.size, h + 34, Bitmap.Config.ARGB_8888)
        val c = Canvas(feuille)
        c.drawColor(0xFF10131C.toInt())
        val titre = Paint().apply { color = Color.WHITE; textSize = 22f; isAntiAlias = true }
        c.drawText(scene.titre, 10f, 24f, titre)

        partie.lancer()
        var t = 0f
        for ((i, instant) in scene.instants.withIndex()) {
            while (t < instant) {
                partie.avancer(1f / 120f)
                t += 1f / 120f
            }
            champ(view, "echelle").setFloat(view, h / (scene.hauteur + 0.45f))
            prep.invoke(view, partie.graine)
            val image = peindre(view, w, h, scene, t)
            c.drawBitmap(image, (i * w).toFloat(), 34f, null)
            val etiquette = Paint().apply {
                color = 0xFFFFD27A.toInt(); textSize = 18f; isAntiAlias = true
            }
            c.drawText("t = %.2f s".format(instant), i * w + 10f, 56f, etiquette)
        }
        FileOutputStream(File(dossier, "${scene.nom}.png")).use {
            feuille.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun pose(type: TypePiece, x: Float, y: Float, reglage: Float = 0f, miroir: Boolean = false,
                     taille: Float = 0f) = Pose(type, x, y, reglage, taille = taille, miroir = miroir)

    @Test
    fun planches() {
        val scenes = listOf(
            Scene(
                "01_rampe", "RAMPE : planche scellee, la bille roule dessus",
                listOf(pose(TypePiece.RAMPE, 0f, 1.2f, 20f)),
                billeX = -0.5f, billeY = 2.6f,
                instants = listOf(0f, 0.55f, 0.85f, 1.4f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "02_plot", "PLOT : la bille rebondit et repart ailleurs",
                listOf(pose(TypePiece.PLOT, 0f, 0.9f)),
                billeX = -0.08f, billeY = 2.4f,
                instants = listOf(0f, 0.45f, 0.65f, 1.0f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "03_bloc", "BLOC : mur ou butee, il barre ou renvoie",
                listOf(pose(TypePiece.RAMPE, -0.2f, 1.3f, 12f), pose(TypePiece.BLOC, 0.85f, 0.2f)),
                billeX = -0.9f, billeY = 2.4f,
                instants = listOf(0f, 0.7f, 1.2f, 1.9f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "04_domino", "DOMINO : tombe quand on le pousse, et pousse le suivant",
                listOf(
                    pose(TypePiece.RAMPE, -0.9f, 1.4f, 14f),
                    pose(TypePiece.DOMINO, 0.2f, 0f), pose(TypePiece.DOMINO, 0.55f, 0f),
                    pose(TypePiece.DOMINO, 0.9f, 0f), pose(TypePiece.DOMINO, 1.25f, 0f)
                ),
                billeX = -1.5f, billeY = 2.5f,
                instants = listOf(0f, 1.4f, 2.1f, 2.8f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "05_bascule", "BASCULE : ce qui tombe d'un cote souleve l'autre",
                listOf(
                    pose(TypePiece.BASCULE, 0f, 0f),
                    pose(TypePiece.BILLE, 0.5f, 0.65f)
                ),
                billeX = -0.5f, billeY = 2.2f,
                instants = listOf(0f, 0.65f, 0.85f, 1.3f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "06_tremplin", "TREMPLIN : volet sur ressort, la bille repart en l'air",
                listOf(pose(TypePiece.TREMPLIN, 0f, 0f)),
                billeX = 0.25f, billeY = 1.9f,
                instants = listOf(0f, 0.55f, 0.75f, 1.2f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "07_ventilateur", "VENTILATEUR : un jet d'air pousse la bille",
                listOf(pose(TypePiece.VENTILATEUR, -1.5f, 0.5f, 0f)),
                billeX = -0.3f, billeY = 1.8f,
                instants = listOf(0f, 0.5f, 0.9f, 1.5f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "08_tambour", "TAMBOUR : peau tendue, la bille repart presque aussi haut",
                listOf(pose(TypePiece.TAMBOUR, 0f, 0.8f)),
                billeX = 0f, billeY = 2.6f,
                instants = listOf(0f, 0.55f, 0.75f, 1.2f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "09_poulie", "POULIE : la chute dans le godet fait monter le contrepoids",
                listOf(pose(TypePiece.POULIE, 0f, 0f)),
                billeX = -0.55f, billeY = 2.6f,
                instants = listOf(0f, 0.6f, 1.0f, 2.0f), minX = -2.2f, maxX = 2.2f, hauteur = 3.2f
            ),
            Scene(
                "10_tapis", "TAPIS : transporte a plat",
                listOf(pose(TypePiece.TAPIS, 0f, 0.4f, 0f), pose(TypePiece.RAMPE, -1.2f, 1.3f, 20f)),
                billeX = -1.8f, billeY = 2.3f,
                instants = listOf(0f, 0.8f, 1.5f, 2.6f), minX = -2.6f, maxX = 2.6f
            ),
            Scene(
                "11_bille", "BILLE du joueur : une 2e source de mouvement, qui attend d'etre liberee",
                listOf(
                    pose(TypePiece.BILLE, 0.7f, 1.22f),
                    pose(TypePiece.BLOC, 0.7f, 1.0f)
                ),
                billeX = -0.2f, billeY = 2.4f,
                instants = listOf(0f, 0.4f, 0.8f, 1.4f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "12_torche", "TORCHE : elle eclaire, et c'est tout",
                listOf(pose(TypePiece.TORCHE, -1.3f, 1.0f)),
                billeX = 0f, billeY = 1.5f,
                instants = listOf(0f, 0.3f, 0.6f), minX = -2.2f, maxX = 2.2f
            ),
            Scene(
                "13_ballon", "BALLON : monte avec son panier, un ventilateur le pousse",
                listOf(
                    pose(TypePiece.BALLON, 0f, 0f, taille = 0.45f),
                    pose(TypePiece.VENTILATEUR, -1.5f, 1.6f, 0f)
                ),
                billeX = 1.8f, billeY = 0.4f,
                instants = listOf(0f, 0.8f, 1.6f, 2.6f), minX = -2.2f, maxX = 2.8f, hauteur = 4.2f
            ),
            Scene(
                "14_pendule", "PENDULE : un boulet de fonte qui balaie de cote",
                listOf(
                    pose(TypePiece.PENDULE, 0f, 1.9f, 60f, taille = 1.5f),
                    pose(TypePiece.DOMINO, 0.6f, 0f), pose(TypePiece.DOMINO, 0.95f, 0f)
                ),
                billeX = -2f, billeY = 0.4f,
                instants = listOf(0f, 0.6f, 1.0f, 1.6f), minX = -2.4f, maxX = 2.4f, hauteur = 3.2f
            ),
            Scene(
                "15_pic", "PIC : le ballon creve, le panier retombe avec sa bille",
                listOf(
                    pose(TypePiece.BALLON, 0f, 0f, taille = 0.7f),
                    pose(TypePiece.PIC, 0f, 4.8f, 270f)
                ),
                billeX = 0f, billeY = 0.6f,
                instants = listOf(0f, 1.5f, 2.4f, 2.7f, 4.2f), minX = -2.2f, maxX = 2.2f, hauteur = 5.6f
            ),
            Scene(
                "16_aimant_canon", "AIMANT et CANON : le canon tire, l'aimant devie la bille",
                listOf(
                    pose(TypePiece.CANON, -2.2f, 0.6f, 35f, miroir = true),
                    pose(TypePiece.AIMANT, 1.2f, 2.2f)
                ),
                billeX = 2.2f, billeY = 0.4f,
                instants = listOf(0f, 0.5f, 1.0f, 1.6f), minX = -3f, maxX = 3f, hauteur = 3.6f
            )
        )
        for (s in scenes) {
            try {
                jouer(s)
            } catch (e: Throwable) {
                println("APERCU ${s.nom} : ${e.javaClass.simpleName} ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
