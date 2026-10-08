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
        val hauteur: Float = 3.2f,
        val victoire: Boolean = false,
        val boutonX: Float = 7.5f,
        val temoinX: Float = 0f,
        val temoinHaut: Float = 0f,
        val anneaux: List<Point> = emptyList(),
        val tenir: Float = 0f,
        val limite: Float = 0f
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
        val tableau = Tableau(1L, scene.billeX, scene.billeY, scene.boutonX, 0f, 0f, scene.temoinX, scene.temoinHaut,
            scene.anneaux, scene.tenir, scene.limite)
        val partie = Partie(tableau)
        view.jouer(partie)
        champ(view, "largeurVue").setInt(view, w)
        champ(view, "hauteurVue").setInt(view, h)
        for (p in scene.poses) check(partie.poser(p) == Refus.OK) { "${scene.nom} : $p refusee" }
        // Les ombres de fond et le halo dependent de l'echelle : on les prepare une fois cadre.
        val prep = view.javaClass.getDeclaredMethod("preparerDecor", Tableau::class.java)
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
            if (scene.victoire && i == scene.instants.lastIndex && partie.gagne) {
                champ(view, "ouverture").setFloat(view, 1f)
            }
            champ(view, "echelle").setFloat(view, h / (scene.hauteur + 0.45f))
            prep.invoke(view, tableau)
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
                "12_torche_portail", "TORCHE et PORTAIL : la victoire ouvre la porte",
                listOf(pose(TypePiece.TORCHE, -1.3f, 1.0f)),
                billeX = 0f, billeY = 1.5f,
                instants = listOf(0f, 0.3f, 0.6f), minX = -2.2f, maxX = 2.2f, victoire = true, boutonX = 0f
            ),
            Scene(
                "13_temoin", "BILLE DOREE : la bille de depart la libere, elle seule ouvre le portail",
                listOf(
                    pose(TypePiece.RAMPE, -2.2f, 3.7f, 20f),
                    pose(TypePiece.RAMPE, -0.2f, 2.4f, 20f),
                    pose(TypePiece.RAMPE, 1.5f, 1.2f, 20f)
                ),
                billeX = -2.6f, billeY = 5f,
                instants = listOf(0f, 1.3f, 1.9f, 2.9f, 4.4f), minX = -3.2f, maxX = 5.2f,
                hauteur = 5.4f, boutonX = 4.5f, temoinX = -1.2f, temoinHaut = 3.4f, victoire = true
            ),
            Scene(
                "14_anneau", "ANNEAU : il faut passer par le cercle, puis le bouton compte",
                listOf(
                    pose(TypePiece.RAMPE, -2.2f, 3.9f, 20f),
                    pose(TypePiece.RAMPE, -0.3f, 2.7f, 20f),
                    pose(TypePiece.RAMPE, 1.6f, 1.3f, 20f)
                ),
                billeX = -2.6f, billeY = 5f,
                instants = listOf(0f, 1.3f, 2.0f, 2.6f, 4.4f), minX = -3.2f, maxX = 5.2f,
                hauteur = 5.4f, boutonX = 4.5f, victoire = true, anneaux = listOf(Point(0.3f, 2.3f))
            ),
            Scene(
                "15_tenir", "TENIR : la bille doit rester dans la cuvette, la jauge se remplit",
                emptyList(),
                billeX = 1.55f, billeY = 3.4f,
                instants = listOf(0f, 1.2f, 2.0f, 2.8f, 4.2f), minX = -0.4f, maxX = 3.6f,
                hauteur = 4.0f, boutonX = 1.6f, tenir = 2.5f, victoire = true
            ),
            Scene(
                "16_chrono", "CHRONO : le compte a rebours court des le lancement",
                listOf(
                    pose(TypePiece.RAMPE, -2.2f, 3.9f, 20f),
                    pose(TypePiece.RAMPE, -0.3f, 2.7f, 20f),
                    pose(TypePiece.RAMPE, 1.6f, 1.3f, 20f)
                ),
                billeX = -2.6f, billeY = 5f,
                instants = listOf(0f, 1.5f, 3.0f, 4.5f), minX = -3.2f, maxX = 5.2f,
                hauteur = 5.4f, boutonX = 4.5f, limite = 6.5f
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
