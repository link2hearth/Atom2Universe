package com.Atom2Universe.app.hub

import android.graphics.drawable.Drawable
import com.Atom2Universe.app.crypto.ClickerHubTileDrawable
import com.Atom2Universe.app.crypto.MainClickerActivity
import com.Atom2Universe.app.games.farm.FarmActivity
import com.Atom2Universe.app.games.farm.FarmHubTileDrawable
import com.Atom2Universe.app.games.survivor.SurvivorActivity
import com.Atom2Universe.app.games.survivor.SurvivorHubTileDrawable
import kotlin.reflect.KClass

/**
 * Les illustrations de tuile, rangees par activite : **le seul endroit ou une illustration est
 * declaree**. Le hub y cherche celle de chaque tuile, et le raccourci celle de son activite.
 *
 * Toutes suivent le meme format : une classe `…HubTileDrawable(context: Context)` qui herite de
 * [CachedHubArtworkDrawable] et ne fait que dessiner son decor dans `render` - le cache, la taille
 * bornee et la mise a l'echelle sont communs. Aucun texte dans le dessin : le titre est pose par
 * la tuile, en bas, et le bas du decor reste donc degage.
 *
 * Un raccourci ajoute sur une tuile du hub principal ne garde que le nom de classe de son activite
 * et une couleur figee au moment de l'ajout - pour une tuile illustree, cette couleur est celle
 * qu'on ne voit jamais sous le dessin, souvent presque noire. Retrouver l'illustration a partir du
 * nom de classe marche aussi pour les raccourcis deja enregistres, sans changer leur format.
 */
object HubTileArtworks {
    private val byActivity: Map<String, KClass<out Drawable>> = mapOf(
        MainClickerActivity::class.java.name to ClickerHubTileDrawable::class,
        FarmActivity::class.java.name to FarmHubTileDrawable::class,
        com.Atom2Universe.app.games.match3.Match3Activity::class.java.name to
            com.Atom2Universe.app.games.match3.Match3HubTileDrawable::class,
        com.Atom2Universe.app.games.starswar.StarsWarActivity::class.java.name to
            com.Atom2Universe.app.games.starswar.SpaceFightHubTileDrawable::class,
        SurvivorActivity::class.java.name to SurvivorHubTileDrawable::class,
        com.Atom2Universe.app.games.nuclea.NucleaActivity::class.java.name to
            com.Atom2Universe.app.games.nuclea.NucleaHubTileDrawable::class,
        com.Atom2Universe.app.games.particules.ParticulesActivity::class.java.name to
            com.Atom2Universe.app.games.particules.ParticulesHubTileDrawable::class,
        com.Atom2Universe.app.games.chess.ChessActivity::class.java.name to
            com.Atom2Universe.app.games.chess.ChessHubTileDrawable::class,
        com.Atom2Universe.app.games.roguelike.RoguelikeActivity::class.java.name to
            com.Atom2Universe.app.games.roguelike.DungeonHubTileDrawable::class,
        com.Atom2Universe.app.games.draughts.DraughtsActivity::class.java.name to
            com.Atom2Universe.app.games.draughts.DraughtsHubTileDrawable::class,
        com.Atom2Universe.app.games.othello.OthelloActivity::class.java.name to
            com.Atom2Universe.app.games.othello.ReversiHubTileDrawable::class,
        com.Atom2Universe.app.games.solitaire.SolitaireActivity::class.java.name to
            com.Atom2Universe.app.games.solitaire.SolitaireHubTileDrawable::class,
        com.Atom2Universe.app.games.memory.MemoryActivity::class.java.name to
            com.Atom2Universe.app.games.memory.MemoryHubTileDrawable::class,
        com.Atom2Universe.app.games.blackjack.BlackjackActivity::class.java.name to
            com.Atom2Universe.app.games.blackjack.BlackjackHubTileDrawable::class,
        com.Atom2Universe.app.games.roulette.RouletteActivity::class.java.name to
            com.Atom2Universe.app.games.roulette.RouletteHubTileDrawable::class,
        com.Atom2Universe.app.games.hotpotato.HotPotatoActivity::class.java.name to
            com.Atom2Universe.app.games.hotpotato.HotPotatoHubTileDrawable::class,
        com.Atom2Universe.app.games.flappycat.FlappyCatActivity::class.java.name to
            com.Atom2Universe.app.games.flappycat.JumpingCatHubTileDrawable::class,
        com.Atom2Universe.app.games.motocross.MotocrossActivity::class.java.name to
            com.Atom2Universe.app.games.motocross.MotocrossHubTileDrawable::class,
        com.Atom2Universe.app.games.toyboxracers.ToyboxRacersActivity::class.java.name to
            com.Atom2Universe.app.games.toyboxracers.ToyboxHubTileDrawable::class,
        com.Atom2Universe.app.games.orbite.OrbiteActivity::class.java.name to
            com.Atom2Universe.app.games.orbite.OrbiteHubTileDrawable::class,
        com.Atom2Universe.app.games.colorstack.ColorStackActivity::class.java.name to
            com.Atom2Universe.app.games.colorstack.ColorStackHubTileDrawable::class,
        com.Atom2Universe.app.games.hexrunner.HexRunnerActivity::class.java.name to
            com.Atom2Universe.app.games.hexrunner.HexRunnerHubTileDrawable::class,
        com.Atom2Universe.app.games.wavesurf.WaveSurfActivity::class.java.name to
            com.Atom2Universe.app.games.wavesurf.WaveSurfHubTileDrawable::class,
        com.Atom2Universe.app.games.cosmorun.CosmoRunActivity::class.java.name to
            com.Atom2Universe.app.games.cosmorun.CosmoRunHubTileDrawable::class,
        com.Atom2Universe.app.games.sokoban.SokobanActivity::class.java.name to
            com.Atom2Universe.app.games.sokoban.SokobanHubTileDrawable::class,
        com.Atom2Universe.app.games.pipetap.PipeTapActivity::class.java.name to
            com.Atom2Universe.app.games.pipetap.PipeTapHubTileDrawable::class,
        com.Atom2Universe.app.games.escapelabyrinth.EscapeLabyrinthActivity::class.java.name to
            com.Atom2Universe.app.games.escapelabyrinth.EscapeLabyrinthHubTileDrawable::class,
        com.Atom2Universe.app.games.game2048.Game2048Activity::class.java.name to
            com.Atom2Universe.app.games.game2048.Game2048HubTileDrawable::class,
        com.Atom2Universe.app.games.sudoku.SudokuActivity::class.java.name to
            com.Atom2Universe.app.games.sudoku.SudokuHubTileDrawable::class,
        com.Atom2Universe.app.games.minesweeper.MinesweeperActivity::class.java.name to
            com.Atom2Universe.app.games.minesweeper.MinesweeperHubTileDrawable::class,
        com.Atom2Universe.app.games.circles.CirclesActivity::class.java.name to
            com.Atom2Universe.app.games.circles.CirclesHubTileDrawable::class,
        com.Atom2Universe.app.games.reflex.ReflexActivity::class.java.name to
            com.Atom2Universe.app.games.reflex.ReflexHubTileDrawable::class,
        com.Atom2Universe.app.games.balance.BalanceActivity::class.java.name to
            com.Atom2Universe.app.games.balance.BalanceHubTileDrawable::class,
        com.Atom2Universe.app.games.trebuchet.TrebuchetActivity::class.java.name to
            com.Atom2Universe.app.games.trebuchet.TrebuchetHubTileDrawable::class,
        com.Atom2Universe.app.games.caves.CaveWorldMenuActivity::class.java.name to
            com.Atom2Universe.app.games.caves.CaveWorldHubTileDrawable::class,
        com.Atom2Universe.app.games.bigger.BiggerActivity::class.java.name to
            com.Atom2Universe.app.games.bigger.BiggerHubTileDrawable::class,
        com.Atom2Universe.app.games.starbridges.StarBridgesActivity::class.java.name to
            com.Atom2Universe.app.games.starbridges.StarBridgesHubTileDrawable::class,
        com.Atom2Universe.app.games.theline.TheLineActivity::class.java.name to
            com.Atom2Universe.app.games.theline.TheLineHubTileDrawable::class,
        com.Atom2Universe.app.games.link.LinkActivity::class.java.name to
            com.Atom2Universe.app.games.link.LinkHubTileDrawable::class,
        com.Atom2Universe.app.games.infernale.InfernaleActivity::class.java.name to
            com.Atom2Universe.app.games.infernale.InfernaleHubTileDrawable::class,
        // Hub Sciences
        com.Atom2Universe.app.periodic.PeriodicTableActivity::class.java.name to
            com.Atom2Universe.app.periodic.PeriodicHubTileDrawable::class,
        com.Atom2Universe.app.science.nuclide.NuclideTableActivity::class.java.name to
            com.Atom2Universe.app.science.nuclide.NuclideHubTileDrawable::class,
        com.Atom2Universe.app.games.gameoflife.GameOfLifeActivity::class.java.name to
            com.Atom2Universe.app.games.gameoflife.GameOfLifeHubTileDrawable::class,
        com.Atom2Universe.app.science.pendulum.DoublePendulumActivity::class.java.name to
            com.Atom2Universe.app.science.pendulum.DoublePendulumHubTileDrawable::class,
        com.Atom2Universe.app.science.solarsystem.SolarSystemActivity::class.java.name to
            com.Atom2Universe.app.science.solarsystem.SolarSystemHubTileDrawable::class,
        com.Atom2Universe.app.science.reaction.ReactionDiffusionActivity::class.java.name to
            com.Atom2Universe.app.science.reaction.ReactionDiffusionHubTileDrawable::class,
        com.Atom2Universe.app.science.boids.BoidsActivity::class.java.name to
            com.Atom2Universe.app.science.boids.BoidsHubTileDrawable::class,
        com.Atom2Universe.app.science.cosmicscale.CosmicScaleActivity::class.java.name to
            com.Atom2Universe.app.science.cosmicscale.CosmicScaleHubTileDrawable::class
    )

    fun forActivity(activityClassName: String): KClass<out Drawable>? = byActivity[activityClassName]

    /**
     * Le libelle pose sur un dessin clair : sa couleur, et de combien l'agrandir. Sans style, le
     * libelle reste blanc et a sa taille normale. Lu pour la tuile du hub ET pour son raccourci.
     */
    class TextStyle(val color: Int, val scale: Float, val lowerTitle: Boolean = false)

    /** Les seules exceptions au titre blanc, agrandi et abaisse de toutes les tuiles illustrees. */
    private val textStyles: Map<String, TextStyle> = mapOf(
        // L'herbe de la ferme est claire : le titre y est ecrit en noir, en grand.
        FarmActivity::class.java.name to TextStyle(android.graphics.Color.BLACK, 1.5f, true)
    )

    private val defaultStyle = TextStyle(android.graphics.Color.WHITE, 1.5f, true)
    fun textStyleFor(activityClassName: String): TextStyle? = textStyles[activityClassName]
        ?: if (activityClassName in byActivity) defaultStyle else null

    private val words = Regex("[\\p{L}\\p{M}]+")
    /** Même casse pour les anciens titres, leurs raccourcis et les prochains décors. */
    fun titleCase(text: String, locale: java.util.Locale): String =
        words.replace(text.lowercase(locale)) { word ->
            word.value.take(1).uppercase(locale) + word.value.drop(1)
        }
}
