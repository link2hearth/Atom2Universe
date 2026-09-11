package com.Atom2Universe.app.games.infernale

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * La machine infernale : poser des pieces pour qu'une bille finisse par ouvrir le portail.
 *
 * L'activite ne fait que quatre choses — tirer un tableau, montrer la reserve, transmettre
 * les boutons, et retenir ce qui a ete gagne. Tout le jeu est dans [Partie] et [Tableaux],
 * qui se testent sans elle et sans ecran.
 */
class InfernaleActivity : ThemedActivity(), InfernaleView.Listener {

    private companion object {
        const val PREFS = "infernale"
        const val CLE_NIVEAU = "niveau"
        const val CLE_ETOILES = "etoiles_"
    }

    private lateinit var vue: InfernaleView
    private lateinit var etat: TextView
    private lateinit var titre: TextView
    private lateinit var reserve: LinearLayout
    private lateinit var ligneReglage: View
    private lateinit var libelleReglage: TextView
    private lateinit var boutonMiroir: TextView
    private lateinit var boutonSupprimer: TextView
    private lateinit var boutonLancer: TextView
    private lateinit var prefs: SharedPreferences

    private var niveau = 1
    private var gagneAnnonce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_infernale)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        niveau = prefs.getInt(CLE_NIVEAU, 1)

        vue = findViewById(R.id.infernale_view)
        etat = findViewById(R.id.infernale_status)
        titre = findViewById(R.id.infernale_board)
        reserve = findViewById(R.id.infernale_stock)
        ligneReglage = findViewById(R.id.infernale_slope_row)
        libelleReglage = findViewById(R.id.infernale_slope_label)
        boutonMiroir = findViewById(R.id.infernale_btn_mirror)
        boutonSupprimer = findViewById(R.id.infernale_btn_delete)
        boutonLancer = findViewById(R.id.infernale_btn_launch)
        vue.listener = this

        findViewById<ImageButton>(R.id.infernale_btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.infernale_btn_frame).setOnClickListener { vue.recadrer() }
        boutonMiroir.setOnClickListener {
            val type = typeEnMain() ?: return@setOnClickListener
            vue.basculerMiroir(type)
            rafraichir()
        }
        boutonSupprimer.setOnClickListener {
            vue.supprimerDesignee()
            rafraichir()
        }
        findViewById<TextView>(R.id.infernale_btn_prev).setOnClickListener { allerAu(niveau - 1) }
        findViewById<TextView>(R.id.infernale_btn_next).setOnClickListener { allerAu(niveau + 1) }
        findViewById<TextView>(R.id.infernale_btn_clear).setOnClickListener {
            vue.surPartie { it.tableauRase() }
            vue.effacerTrainee()
            vue.typeChoisi = null
            rafraichir()
        }
        findViewById<TextView>(R.id.infernale_btn_replay).setOnClickListener {
            vue.surPartie { it.rejouer() }
            vue.effacerTrainee()
            rafraichir()
        }
        boutonLancer.setOnClickListener { lancerOuSuivant() }

        charger(niveau)
    }

    override fun onResume() {
        super.onResume()
        vue.reprendre()
    }

    override fun onPause() {
        vue.suspendre()
        super.onPause()
    }

    // ── Niveaux ──────────────────────────────────────────────────────────────

    private fun allerAu(n: Int) {
        // **Aucun verrou.** Les tableaux etaient deverrouilles un a un en gagnant le
        // precedent, ce qui a du sens dans un jeu a progression. Ce qu'on fait ici est un
        // terrain d'experimentation : y interdire un tableau parce qu'on n'a pas fini le
        // precedent n'apporte rien et empeche d'aller chercher la configuration qu'on
        // voulait essayer.
        val vise = n.coerceAtLeast(1)
        if (vise == niveau) return
        niveau = vise
        prefs.edit { putInt(CLE_NIVEAU, niveau) }
        charger(niveau)
    }

    private fun charger(n: Int) {
        gagneAnnonce = false
        etat.text = getString(R.string.infernale_loading)
        etat.setTextColor(0xFF94A3B8.toInt())
        titre.text = getString(R.string.infernale_board, n)
        reserve.removeAllViews()
        // Le tirage ne fait plus qu'une chose couteuse : lacher la bille sur le tableau
        // vide pour verifier qu'il ne se gagne pas tout seul. C'est quelques dizaines de
        // millisecondes, ce qui ne se voit pas mais n'a rien a faire sur le fil principal.
        Thread {
            val tableau = Tableaux.pourNiveau(n)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                vue.jouer(Partie(tableau))
                vue.typeChoisi = null
                rafraichir()
            }
        }.start()
    }

    private fun lancerOuSuivant() {
        val partie = vue.partieCourante() ?: return
        if (partie.gagne) {
            allerAu(niveau + 1)
            return
        }
        vue.surPartie { it.lancer() }
        vue.typeChoisi = null
        rafraichir()
    }

    // ── Retours de la vue ────────────────────────────────────────────────────

    override fun surVictoire() {
        val partie = vue.partieCourante() ?: return
        if (gagneAnnonce) return
        gagneAnnonce = true

        // **Le bareme porte sur la machine, pas sur la patience.** Le joueur a toutes les
        // pieces et peut relancer autant qu'il veut ; compter les essais reviendrait a
        // punir le seul geste qui fait ce jeu, qui est d'essayer en regardant. Ce qu'on
        // note, c'est l'economie : faire tenir la chaine en moins de pieces que le « par ».
        val par = partie.tableau.par
        val etoiles = when {
            partie.posees <= par -> 3
            partie.posees <= par + 3 -> 2
            else -> 1
        }
        val avant = prefs.getInt(CLE_ETOILES + niveau, 0)
        if (etoiles > avant) {
            prefs.edit { putInt(CLE_ETOILES + niveau, etoiles) }
            // Les neutrinos ne sont verses que sur le **progres** : rejouer un tableau
            // deja fini ne rapporte rien, sinon le meilleur rendement du jeu serait de
            // refaire vingt fois le niveau un.
            val gain = NeutrinoRewards.infernale(etoiles) - NeutrinoRewards.infernale(avant)
            if (gain > 0) NeutrinoRepository(this).addBalance(gain)
        }
        etat.setTextColor(0xFF55E08A.toInt())
        etat.text = getString(
            R.string.infernale_won,
            "★".repeat(etoiles) + "☆".repeat(3 - etoiles),
            partie.posees
        )
        majCommandes()
    }

    override fun surEchec() {
        val partie = vue.partieCourante() ?: return
        if (partie.gagne) return
        etat.setTextColor(0xFFE0A055.toInt())
        etat.text = getString(R.string.infernale_failed)
        majCommandes()
    }

    override fun surChangement() = rafraichir()

    // ── Interface ────────────────────────────────────────────────────────────

    private fun rafraichir() {
        val partie = vue.partieCourante() ?: return
        if (!partie.gagne && !partie.lancee) {
            etat.setTextColor(0xFF94A3B8.toInt())
            val type = typeEnMain()
            etat.text = when {
                // **La piece en main s'explique elle-meme.** Neuf pieces dont plusieurs ne
                // ressemblent a rien de connu — un tambour, une poulie a godet — et une
                // vignette de soixante pixels n'a jamais dit a quoi une piece sert. Tant
                // qu'on en tient une, la ligne d'etat la nomme et dit ce qu'elle fait ; le
                // compteur reprend sa place des qu'on la lache.
                type != null -> getString(
                    R.string.infernale_piece_named, getString(nom(type)), getString(role(type))
                )
                partie.posees == 0 -> getString(R.string.infernale_hint)
                else -> getString(R.string.infernale_placed, partie.posees, partie.tableau.par)
            }
        }
        majReglage()
        majCommandes()
        construireReserve(partie)
    }

    private fun majCommandes() {
        val partie = vue.partieCourante()
        val gagne = partie?.gagne == true
        boutonLancer.text = getString(
            if (gagne) R.string.infernale_next else R.string.infernale_launch
        )
        boutonLancer.isEnabled = gagne || partie?.lancee == false
        boutonLancer.alpha = if (boutonLancer.isEnabled) 1f else 0.45f
        titre.text = getString(R.string.infernale_board, niveau) + etoilesDuNiveau()
    }

    private fun etoilesDuNiveau(): String {
        val e = prefs.getInt(CLE_ETOILES + niveau, 0)
        return if (e <= 0) "" else "  " + "★".repeat(e)
    }

    /**
     * La piece « en main » : celle qu'on a choisie dans la reserve, ou celle qu'on a
     * designee sur le tableau. C'est elle que la barre d'outils regle et que la ligne
     * d'etat explique.
     */
    private fun typeEnMain(): TypePiece? {
        vue.typeChoisi?.let { return it }
        val partie = vue.partieCourante() ?: return null
        return partie.placees().getOrNull(vue.selection)?.type
    }

    private fun nom(type: TypePiece): Int = when (type) {
        TypePiece.RAMPE -> R.string.infernale_piece_ramp
        TypePiece.PLOT -> R.string.infernale_piece_plot
        TypePiece.BLOC -> R.string.infernale_piece_block
        TypePiece.DOMINO -> R.string.infernale_piece_domino
        TypePiece.BASCULE -> R.string.infernale_piece_seesaw
        TypePiece.TREMPLIN -> R.string.infernale_piece_trampoline
        TypePiece.VENTILATEUR -> R.string.infernale_piece_fan
        TypePiece.TAMBOUR -> R.string.infernale_piece_drum
        TypePiece.POULIE -> R.string.infernale_piece_pulley
        TypePiece.BILLE -> R.string.infernale_piece_ball
        TypePiece.TAPIS -> R.string.infernale_piece_belt
        TypePiece.TORCHE -> R.string.infernale_piece_torch
    }

    private fun role(type: TypePiece): Int = when (type) {
        TypePiece.RAMPE -> R.string.infernale_role_ramp
        TypePiece.PLOT -> R.string.infernale_role_plot
        TypePiece.BLOC -> R.string.infernale_role_block
        TypePiece.DOMINO -> R.string.infernale_role_domino
        TypePiece.BASCULE -> R.string.infernale_role_seesaw
        TypePiece.TREMPLIN -> R.string.infernale_role_trampoline
        TypePiece.VENTILATEUR -> R.string.infernale_role_fan
        TypePiece.TAMBOUR -> R.string.infernale_role_drum
        TypePiece.POULIE -> R.string.infernale_role_pulley
        TypePiece.BILLE -> R.string.infernale_role_ball
        TypePiece.TAPIS -> R.string.infernale_role_belt
        TypePiece.TORCHE -> R.string.infernale_role_torch
    }

    /**
     * La barre d'outils de la piece en main : son nom, son miroir, sa corbeille.
     *
     * **Il n'y a plus de curseur.** Il y en avait un, qui reglait la pente d'une rampe et
     * la direction d'un jet — deux nombres a traduire depuis une intention geometrique,
     * puis a corriger l'un apres l'autre parce que changer l'angle avait deplace le bout
     * qu'on voulait garder. Les poignees font la meme chose d'un seul geste et sans
     * traduction. Garder les deux aurait ete de l'encombrement.
     */
    private fun majReglage() {
        val type = typeEnMain()
        val designee = vue.selection >= 0
        if (type == null) {
            ligneReglage.visibility = View.GONE
            return
        }
        ligneReglage.visibility = View.VISIBLE
        libelleReglage.text = getString(nom(type))

        val miroitable = type.miroitable
        boutonMiroir.visibility = if (miroitable) View.VISIBLE else View.GONE
        // Le bouton dit l'etat, pas seulement l'action : une piece retournee doit se voir
        // dans la barre, sinon on la retourne deux fois sans s'en apercevoir.
        boutonMiroir.setTextColor(
            if (miroitable && vue.miroir(type)) 0xFF0B1020.toInt() else 0xFFCBD5E1.toInt()
        )
        boutonMiroir.setBackgroundColor(
            if (miroitable && vue.miroir(type)) 0xFF9BC2FF.toInt() else 0xFF1B2540.toInt()
        )
        // La corbeille ne s'ouvre que sur une piece posee : c'est elle qui a remplace
        // l'appui-qui-supprime, lequel rendait tout reglage inatteignable.
        boutonSupprimer.visibility = if (designee) View.VISIBLE else View.GONE
    }

    /**
     * Reconstruit la reserve : une case par type qu'il reste a poser.
     *
     * Elle se refait entierement a chaque changement plutot que de se mettre a jour
     * case par case. C'est quelques vues recreees par pose, ce qui n'est rien a
     * l'echelle d'un geste de doigt, et ca supprime toute une classe de bugs ou
     * l'affichage et le stock finissent par ne plus dire la meme chose.
     */
    private fun construireReserve(partie: Partie) {
        reserve.removeAllViews()
        if (partie.lancee) return
        for (type in TypePiece.entries) reserve.addView(caseReserve(type))
    }

    private fun caseReserve(type: TypePiece): View =
        InfernaleVignette(this).apply {
            this.type = type
            choisie = vue.typeChoisi == type
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 10 }
            setOnClickListener {
                vue.typeChoisi = if (vue.typeChoisi == type) null else type
                rafraichir()
            }
        }
}
