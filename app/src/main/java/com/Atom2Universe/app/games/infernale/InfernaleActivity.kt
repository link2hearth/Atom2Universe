package com.Atom2Universe.app.games.infernale

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * L'editeur de la machine infernale : un tableau ouvert depuis [InfernaleMenuActivity], un
 * plateau vide, et toutes les pieces a portee du doigt.
 *
 * C'est un bac a sable : on pose ce qu'on veut ou on veut, on lance, on regarde. Chaque
 * changement est sauvegarde tout seul — le montage, jamais l'etat d'une machine en marche.
 *
 * L'activite ne fait que trois choses — montrer la reserve, transmettre les boutons, et
 * ranger le montage. Tout le reste est dans [Partie] et [InfernaleView], qui se testent
 * sans elle.
 */
class InfernaleActivity : ThemedActivity(), InfernaleView.Listener {

    companion object {
        /** L'identifiant du tableau a ouvrir, passe par le menu. */
        const val EXTRA_ID = "tableau_id"

        /** Delai avant d'ecrire sur le disque : un geste en rafale ne fait qu'une ecriture. */
        private const val DELAI_SAUVEGARDE = 500L
    }

    private lateinit var vue: InfernaleView
    private lateinit var etat: TextView
    private lateinit var titre: TextView
    private lateinit var reserve: LinearLayout
    private lateinit var ligneReglage: View
    private lateinit var libelleReglage: TextView
    private lateinit var boutonMiroir: TextView
    private lateinit var boutonSupprimer: TextView
    private lateinit var boutonLien: TextView
    private lateinit var boutonTraces: TextView
    private lateinit var boutonLancer: TextView

    private val sauvegardes by lazy { sauvegardesInfernale() }
    private val main = Handler(Looper.getMainLooper())
    private val ecrire = Runnable { sauver() }
    private var tableau: TableauSauve? = null

    /** Ce qui est deja sur le disque : on n'ecrit que si le montage a vraiment change. */
    private var derniereSauvegarde: Pair<List<Pose>, List<Lien>> = emptyList<Pose>() to emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = intent.getStringExtra(EXTRA_ID)?.let { sauvegardes.charger(it) }
        if (t == null) {
            finish()
            return
        }
        tableau = t
        setContentView(R.layout.activity_infernale)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vue = findViewById(R.id.infernale_view)
        etat = findViewById(R.id.infernale_status)
        titre = findViewById(R.id.infernale_board)
        reserve = findViewById(R.id.infernale_stock)
        ligneReglage = findViewById(R.id.infernale_slope_row)
        libelleReglage = findViewById(R.id.infernale_slope_label)
        boutonMiroir = findViewById(R.id.infernale_btn_mirror)
        boutonSupprimer = findViewById(R.id.infernale_btn_delete)
        boutonLancer = findViewById(R.id.infernale_btn_launch)
        boutonLien = findViewById(R.id.infernale_btn_link)
        boutonTraces = findViewById(R.id.infernale_btn_ghost)
        boutonTraces.setOnClickListener {
            vue.effacerTraces()
            rafraichir()
        }
        vue.listener = this
        titre.text = t.nom

        findViewById<ImageButton>(R.id.infernale_btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.infernale_btn_frame).setOnClickListener { vue.recadrer() }
        boutonMiroir.setOnClickListener {
            val type = typeEnMain() ?: return@setOnClickListener
            vue.basculerMiroir(type)
            surChangement()
        }
        boutonLien.setOnClickListener {
            vue.basculerLien()
            rafraichir()
        }
        boutonSupprimer.setOnClickListener {
            vue.supprimerDesignee()
            surChangement()
        }
        findViewById<TextView>(R.id.infernale_btn_clear).setOnClickListener {
            vue.surPartie { it.vider() }
            vue.effacerTraces()
            vue.typeChoisi = null
            surChangement()
        }
        boutonLancer.setOnClickListener { lancerOuArreter() }

        val partie = Partie(t.graine).also { it.charger(t.poses, t.liens) }
        derniereSauvegarde = partie.placees() to partie.liens()
        vue.jouer(partie)
        rafraichir()
    }

    override fun onResume() {
        super.onResume()
        if (tableau != null) vue.reprendre()
    }

    override fun onPause() {
        if (tableau != null) {
            vue.suspendre()
            // Rien ne doit se perdre parce qu'on a quitte l'ecran dans la demi-seconde.
            main.removeCallbacks(ecrire)
            sauver()
        }
        super.onPause()
    }

    // ── Sauvegarde ───────────────────────────────────────────────────────────

    private fun sauver() {
        val t = tableau ?: return
        val etat = vue.surPartie { it.placees() to it.liens() } ?: return
        if (etat == derniereSauvegarde) return
        sauvegardes.enregistrer(
            TableauSauve(t.id, t.nom, t.graine, etat.first, System.currentTimeMillis(), etat.second)
        )
        derniereSauvegarde = etat
    }

    // ── Lancement ────────────────────────────────────────────────────────────

    /** Lance la machine, ou l'arrete et remet le montage comme il etait. */
    private fun lancerOuArreter() {
        val partie = vue.partieCourante() ?: return
        vue.typeChoisi = null
        if (partie.lancee) {
            // Les traces restent : on les compare a l'essai suivant, jusqu'a ce qu'on les efface.
            vue.surPartie { it.arreter() }
        } else {
            vue.effacerTraces()
            vue.surPartie { it.lancer() }
        }
        rafraichir()
    }

    // ── Retours de la vue ────────────────────────────────────────────────────

    /** Le montage a bouge : on rafraichit l'ecran et on programme l'ecriture. */
    override fun surChangement() {
        rafraichir()
        main.removeCallbacks(ecrire)
        main.postDelayed(ecrire, DELAI_SAUVEGARDE)
    }

    // ── Interface ────────────────────────────────────────────────────────────

    private fun rafraichir() {
        val partie = vue.partieCourante() ?: return
        val type = typeEnMain()
        etat.text = when {
            // Le mode lien dit tout de suite quoi faire : c'est le geste le moins evident du jeu.
            vue.modeLien -> getString(R.string.infernale_link_hint)
            // **La piece en main s'explique elle-meme.** Plusieurs pieces ne ressemblent a
            // rien de connu — un tambour, une poulie a godet — et une vignette de soixante
            // pixels n'a jamais dit a quoi une piece sert. Tant qu'on en tient une, la ligne
            // d'etat la nomme et dit ce qu'elle fait ; le compteur reprend sa place des
            // qu'on la lache.
            type != null -> getString(
                R.string.infernale_piece_named, getString(nom(type)), getString(role(type))
            )
            partie.posees == 0 -> getString(R.string.infernale_hint)
            else -> getString(R.string.infernale_placed, partie.posees)
        }
        majReglage()
        boutonLancer.text = getString(
            if (partie.lancee) R.string.infernale_stop else R.string.infernale_launch
        )
        boutonTraces.visibility = if (!partie.lancee && vue.aDesTraces()) View.VISIBLE else View.GONE
        construireReserve(partie)
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
        TypePiece.BALLON -> R.string.infernale_piece_balloon
        TypePiece.PENDULE -> R.string.infernale_piece_pendulum
        TypePiece.AIMANT -> R.string.infernale_piece_magnet
        TypePiece.CANON -> R.string.infernale_piece_cannon
        TypePiece.PIC -> R.string.infernale_piece_spike
        TypePiece.PLAQUE -> R.string.infernale_piece_plate
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
        TypePiece.BALLON -> R.string.infernale_role_balloon
        TypePiece.PENDULE -> R.string.infernale_role_pendulum
        TypePiece.AIMANT -> R.string.infernale_role_magnet
        TypePiece.CANON -> R.string.infernale_role_cannon
        TypePiece.PIC -> R.string.infernale_role_spike
        TypePiece.PLAQUE -> R.string.infernale_role_plate
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
        // Le bouton de lien n'apparait que sur une plaque ou un canon pose : c'est lui qu'on
        // relie. Il s'allume tant que le prochain glissement trace un lien.
        val liable = designee && Liens.liable(type)
        boutonLien.visibility = if (liable) View.VISIBLE else View.GONE
        boutonLien.setTextColor(if (vue.modeLien) 0xFF0B1020.toInt() else 0xFFFFC65A.toInt())
        boutonLien.setBackgroundColor(if (vue.modeLien) 0xFFFFC65A.toInt() else 0xFF2A2414.toInt())
    }

    /**
     * Reconstruit la reserve : une case par type de piece.
     *
     * Elle se refait entierement a chaque changement plutot que de se mettre a jour
     * case par case. C'est quelques vues recreees par pose, ce qui n'est rien a
     * l'echelle d'un geste de doigt, et ca supprime toute une classe de bugs ou
     * l'affichage et l'etat finissent par ne plus dire la meme chose.
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
