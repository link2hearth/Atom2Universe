package com.Atom2Universe.app.games

import android.content.Intent
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.MainClickerActivity
import com.Atom2Universe.app.games.blackjack.BlackjackActivity
import com.Atom2Universe.app.games.roulette.RouletteActivity
import com.Atom2Universe.app.games.theline.TheLineActivity
import com.Atom2Universe.app.games.chess.ChessActivity
import com.Atom2Universe.app.games.draughts.DraughtsActivity
import com.Atom2Universe.app.games.flappycat.FlappyCatActivity
import com.Atom2Universe.app.clickerstats.ClickerStatsActivity
import com.Atom2Universe.app.games.link.LinkActivity
import com.Atom2Universe.app.games.starswar.StarsWarActivity
import com.Atom2Universe.app.games.wavesurf.WaveSurfActivity
import com.Atom2Universe.app.games.hexrunner.HexRunnerActivity
import com.Atom2Universe.app.games.memory.MemoryActivity
import com.Atom2Universe.app.games.circles.CirclesActivity
import com.Atom2Universe.app.games.pipetap.PipeTapActivity
import com.Atom2Universe.app.games.starbridges.StarBridgesActivity
import com.Atom2Universe.app.quiz.QuizActivity
import com.Atom2Universe.app.games.colorstack.ColorStackActivity
import com.Atom2Universe.app.games.game2048.Game2048Activity
import com.Atom2Universe.app.games.particules.ParticulesActivity
import com.Atom2Universe.app.games.roguelike.RoguelikeActivity
import com.Atom2Universe.app.games.bigger.BiggerActivity
import com.Atom2Universe.app.games.match3.Match3Activity
import com.Atom2Universe.app.games.survivor.SurvivorActivity
import com.Atom2Universe.app.games.reflex.ReflexActivity
import com.Atom2Universe.app.games.solitaire.SolitaireActivity
import com.Atom2Universe.app.games.sudoku.SudokuActivity
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile

class GamesActivity : BaseHubActivity() {

    override fun getLayoutResId(): Int = R.layout.activity_base_hub

    override fun getPrefsName(): String = "games_hub_prefs"

    override fun getParentHubPrefsName(): String = "audio_hub_prefs"
    override fun getParentTileId(): String = "games"

    override fun getHubTitle(): Int = R.string.games_title

    override fun getHubSubtitle(): Int? = null

    override fun getDefaultTiles(): List<HubTile> = listOf(
        // Clicker en tête
        HubTile(
            id = "clicker",
            titleRes = R.string.hub_clicker_title,
            descriptionRes = R.string.hub_clicker_desc,
            iconRes = R.drawable.ic_clicker,
            defaultColorRes = R.color.audio_hub_tile_clicker,
            activityClass = MainClickerActivity::class.java
        ),
        // Quiz en 2ème
        HubTile(
            id = "quiz",
            titleRes = R.string.games_quiz_title,
            descriptionRes = R.string.games_quiz_desc,
            iconRes = android.R.drawable.ic_menu_help,
            defaultColorRes = R.color.game_tile_quiz,
            activityClass = QuizActivity::class.java
        ),
        // Survivor
        HubTile(
            id = "survivor",
            titleRes = R.string.survivor_title,
            descriptionRes = R.string.survivor_description,
            iconRes = android.R.drawable.ic_menu_compass,
            defaultColorRes = R.color.game_tile_survivor,
            activityClass = SurvivorActivity::class.java
        ),
        // Roguelike
        HubTile(
            id = "roguelike",
            titleRes = R.string.roguelike_title,
            descriptionRes = R.string.roguelike_description,
            iconRes = android.R.drawable.ic_menu_mapmode,
            defaultColorRes = R.color.game_tile_roguelike,
            activityClass = RoguelikeActivity::class.java
        ),
        // Arcade : casse-briques + shmup
        HubTile(
            id = "particules",
            titleRes = R.string.particules_title,
            descriptionRes = R.string.particules_description,
            iconRes = android.R.drawable.ic_menu_rotate,
            defaultColorRes = R.color.game_tile_particules,
            activityClass = ParticulesActivity::class.java
        ),
        HubTile(
            id = "starswar",
            titleRes = R.string.stars_war_title,
            descriptionRes = R.string.stars_war_description,
            iconRes = android.R.drawable.ic_menu_compass,
            defaultColorRes = R.color.game_tile_starswar,
            activityClass = StarsWarActivity::class.java
        ),
        // Jeux de plateau
        HubTile(
            id = "chess",
            titleRes = R.string.chess_title,
            descriptionRes = R.string.chess_description,
            iconRes = android.R.drawable.ic_menu_sort_by_size,
            defaultColorRes = R.color.game_tile_chess,
            activityClass = ChessActivity::class.java
        ),
        HubTile(
            id = "draughts",
            titleRes = R.string.draughts_title,
            descriptionRes = R.string.draughts_description,
            iconRes = android.R.drawable.ic_menu_sort_by_size,
            defaultColorRes = R.color.game_tile_draughts,
            activityClass = DraughtsActivity::class.java
        ),
        // Cartes & mémoire
        HubTile(
            id = "solitaire",
            titleRes = R.string.solitaire_title,
            descriptionRes = R.string.solitaire_description,
            iconRes = android.R.drawable.ic_menu_gallery,
            defaultColorRes = R.color.game_tile_solitaire,
            activityClass = SolitaireActivity::class.java
        ),
        HubTile(
            id = "memory",
            titleRes = R.string.memory_title,
            descriptionRes = R.string.memory_description,
            iconRes = android.R.drawable.ic_menu_gallery,
            defaultColorRes = R.color.game_tile_memory,
            activityClass = MemoryActivity::class.java
        ),
        HubTile(
            id = "blackjack",
            titleRes = R.string.blackjack_title,
            descriptionRes = R.string.blackjack_description,
            iconRes = android.R.drawable.ic_menu_gallery,
            defaultColorRes = R.color.game_tile_blackjack,
            activityClass = BlackjackActivity::class.java
        ),
        HubTile(
            id = "roulette",
            titleRes = R.string.roulette_title,
            descriptionRes = R.string.roulette_description,
            iconRes = android.R.drawable.ic_menu_rotate,
            defaultColorRes = R.color.game_tile_roulette,
            activityClass = RouletteActivity::class.java
        ),
        // Arcade
        HubTile(
            id = "flappycat",
            titleRes = R.string.flappy_cat_title,
            descriptionRes = R.string.flappy_cat_description,
            iconRes = android.R.drawable.ic_menu_compass,
            defaultColorRes = R.color.game_tile_flappycat,
            activityClass = FlappyCatActivity::class.java
        ),
        HubTile(
            id = "reflex",
            titleRes = R.string.reflex_title,
            descriptionRes = R.string.reflex_description,
            iconRes = android.R.drawable.ic_menu_view,
            defaultColorRes = R.color.game_tile_reflex,
            activityClass = ReflexActivity::class.java
        ),
        HubTile(
            id = "wavesurf",
            titleRes = R.string.wave_surf_title,
            descriptionRes = R.string.wave_surf_description,
            iconRes = android.R.drawable.ic_menu_compass,
            defaultColorRes = R.color.game_tile_wavesurf,
            activityClass = WaveSurfActivity::class.java
        ),
        HubTile(
            id = "hexrunner",
            titleRes = R.string.hex_runner_hub_title,
            descriptionRes = R.string.hex_runner_hub_desc,
            iconRes = android.R.drawable.ic_menu_rotate,
            defaultColorRes = R.color.game_tile_hexrunner,
            activityClass = HexRunnerActivity::class.java
        ),
        // Puzzle
        HubTile(
            id = "game2048",
            titleRes = R.string.game2048_title,
            descriptionRes = R.string.game2048_description,
            iconRes = android.R.drawable.ic_menu_sort_by_size,
            defaultColorRes = R.color.game_tile_game2048,
            activityClass = Game2048Activity::class.java
        ),
        HubTile(
            id = "sudoku",
            titleRes = R.string.sudoku_title,
            descriptionRes = R.string.sudoku_description,
            iconRes = android.R.drawable.ic_dialog_dialer,
            defaultColorRes = R.color.game_tile_sudoku,
            activityClass = SudokuActivity::class.java
        ),
        HubTile(
            id = "colorstack",
            titleRes = R.string.color_stack_title,
            descriptionRes = R.string.color_stack_description,
            iconRes = android.R.drawable.ic_menu_slideshow,
            defaultColorRes = R.color.game_tile_colorstack,
            activityClass = ColorStackActivity::class.java
        ),
        HubTile(
            id = "pipetap",
            titleRes = R.string.pipetap_title,
            descriptionRes = R.string.pipetap_description,
            iconRes = android.R.drawable.ic_menu_rotate,
            defaultColorRes = R.color.game_tile_pipetap,
            activityClass = PipeTapActivity::class.java
        ),
        HubTile(
            id = "circles",
            titleRes = R.string.circles_hub_title,
            descriptionRes = R.string.circles_hub_desc,
            iconRes = android.R.drawable.ic_menu_rotate,
            defaultColorRes = R.color.game_tile_circles,
            activityClass = CirclesActivity::class.java
        ),
        HubTile(
            id = "starbridges",
            titleRes = R.string.starbridges_title,
            descriptionRes = R.string.starbridges_description,
            iconRes = android.R.drawable.ic_menu_compass,
            defaultColorRes = R.color.game_tile_starbridges,
            activityClass = StarBridgesActivity::class.java
        ),
        HubTile(
            id = "theline",
            titleRes = R.string.the_line_title,
            descriptionRes = R.string.the_line_description,
            iconRes = android.R.drawable.ic_menu_edit,
            defaultColorRes = R.color.the_line_active,
            activityClass = TheLineActivity::class.java
        ),
        HubTile(
            id = "link",
            titleRes = R.string.link_title,
            descriptionRes = R.string.link_description,
            iconRes = android.R.drawable.ic_menu_share,
            defaultColorRes = R.color.game_tile_link,
            activityClass = LinkActivity::class.java
        ),
        // Match 3
        HubTile(
            id = "match3",
            titleRes = R.string.match3_title,
            descriptionRes = R.string.match3_description,
            iconRes = android.R.drawable.ic_menu_slideshow,
            defaultColorRes = R.color.game_tile_match3,
            activityClass = Match3Activity::class.java
        ),
        // Bigger (Suika)
        HubTile(
            id = "bigger",
            titleRes = R.string.bigger_title,
            descriptionRes = R.string.bigger_description,
            iconRes = android.R.drawable.ic_menu_upload,
            defaultColorRes = R.color.game_tile_bigger,
            activityClass = BiggerActivity::class.java
        ),
        // Stats tout en bas
        HubTile(
            id = "clicker_stats",
            titleRes = R.string.clicker_stats_title,
            descriptionRes = R.string.main_hub_clicker_stats_desc,
            iconRes = android.R.drawable.ic_menu_info_details,
            defaultColorRes = R.color.main_hub_tile_clicker_stats,
            activityClass = ClickerStatsActivity::class.java
        )
    )

    override fun onTileClicked(tile: HubTile) {
        if (tile.activityClass != null) {
            startActivity(Intent(this, tile.activityClass))
        } else {
            Toast.makeText(this, R.string.games_coming_soon, Toast.LENGTH_SHORT).show()
        }
    }
}
