package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.billiards.core.*

enum class BilliardRoom(val title: Int, val felt: Int, val wood: Int, val floor: Int, val wall: Int) {
    CLUB(R.string.billiard_room_club,0x195F4C,0x543623,0x70533C,0x353E39),
    HOME(R.string.billiard_room_home,0x427D79,0xB78D60,0xC5A078,0xEEE1C7),
    LOFT(R.string.billiard_room_loft,0x275F8F,0x303540,0x696D74,0x7E7774),
    NEON(R.string.billiard_room_neon,0x463C79,0x272333,0x252638,0x222033),
    GARDEN(R.string.billiard_room_garden,0x50765B,0xAC8054,0xA5AE8C,0x9DC6C7)
}
enum class BilliardCue(val title: Int, val mass: Double, val endMass: Double, val color: Int) {
    MAPLE(R.string.billiard_cue_maple,.567,.012,0xDBC494),
    ASH(R.string.billiard_cue_ash,.510,.010,0xBAA27B),
    CARBON(R.string.billiard_cue_carbon,.540,.006,0x30343C),
    CAROM(R.string.billiard_cue_carom,.480,.009,0xAC7955)
}

enum class BilliardControlMode(val title: Int,val gauge: Boolean) {
    GAUGE(R.string.billiard_input_gauge,true),
    GESTURE(R.string.billiard_input_gesture,false),
    TIMING(R.string.billiard_input_timing,true);

    companion object {
        fun restore(name: String?)=entries.firstOrNull { it.name==name } ?: GAUGE
    }
}
enum class BilliardGameStyle(val title: Int) {
    CLASSIC(R.string.billiard_style_classic), ARCADE(R.string.billiard_style_arcade);
    companion object {
        fun restore(name: String?)=entries.firstOrNull { it.name==name } ?: CLASSIC
    }
}
/** How the scene is painted: measured light, or flat cartoon inks with outlined balls. */
enum class BilliardLook(val title: Int) {
    REALISTIC(R.string.billiard_look_realistic), CARTOON(R.string.billiard_look_cartoon);
    companion object {
        fun restore(name: String?)=entries.firstOrNull { it.name==name } ?: REALISTIC
    }
}
data class BilliardConfig(val discipline: Discipline = Discipline.FREE, val room: BilliardRoom = BilliardRoom.CLUB,
    val cue: BilliardCue = BilliardCue.MAPLE, val mode: PlayMode = PlayMode.PRACTICE,
    val cloth: Int = 1, val assistance: Int = 1, val difficulty: Int = 1, val sound: Boolean = true,
    val tableSize: BilliardTableSize = BilliardTableSize.defaultFor(discipline.family),
    val rotationStripes: Boolean = true, val controlMode: BilliardControlMode = BilliardControlMode.GAUGE,
    val style: BilliardGameStyle = BilliardGameStyle.CLASSIC, val finish: Int = 0,
    val look: BilliardLook = BilliardLook.REALISTIC) {
    val cueTint get()=listOf(cue.color,0xAFC9C5,0xC8AA70,0x6A9FB5,0x799F78,0xAD83C6)[finish.coerceIn(0,5)]
}

/** Existing enabled guides adopt the short default once; later full-guide choices persist. */
internal fun restoredBilliardAssistance(value: Int,version: Int): Int =
    if(version<1 && value>0) 1 else value.coerceIn(0,2)

fun Discipline.titleRes(): Int = when(this) {
    Discipline.FREE -> R.string.billiard_free
    Discipline.ONE_CUSHION -> R.string.billiard_one_cushion
    Discipline.THREE_CUSHION -> R.string.billiard_three_cushion
    Discipline.CADRE_47_1 -> R.string.billiard_cadre_47_1
    Discipline.CADRE_47_2 -> R.string.billiard_cadre_47_2
    Discipline.CADRE_71_2 -> R.string.billiard_cadre_71_2
    Discipline.FIVE_PINS -> R.string.billiard_five_pins
    Discipline.NINE_PINS -> R.string.billiard_nine_pins
    Discipline.ARTISTIC -> R.string.billiard_artistic
    Discipline.EIGHT -> R.string.billiard_eight
    Discipline.NINE -> R.string.billiard_nine
    Discipline.TEN -> R.string.billiard_ten
    Discipline.STRAIGHT -> R.string.billiard_straight
    Discipline.ONE_POCKET -> R.string.billiard_one_pocket
    Discipline.BANK -> R.string.billiard_bank
    Discipline.BLACKBALL -> R.string.billiard_blackball
    Discipline.SNOOKER -> R.string.billiard_snooker
    Discipline.SIX_RED -> R.string.billiard_six_red
    Discipline.ENGLISH -> R.string.billiard_english
    Discipline.PYRAMID_FREE -> R.string.billiard_pyramid_free
    Discipline.PYRAMID_DYNAMIC -> R.string.billiard_pyramid_dynamic
    Discipline.HEYBALL -> R.string.billiard_heyball
}
fun Discipline.rulesRes(): Int = when(this) {
    Discipline.FREE -> R.string.billiard_rules_free
    Discipline.ONE_CUSHION -> R.string.billiard_rules_one
    Discipline.THREE_CUSHION -> R.string.billiard_rules_three
    Discipline.CADRE_47_1,Discipline.CADRE_47_2,Discipline.CADRE_71_2 -> R.string.billiard_rules_cadre
    Discipline.FIVE_PINS -> R.string.billiard_rules_pins
    Discipline.NINE_PINS -> R.string.billiard_rules_nine_pins
    Discipline.ARTISTIC -> R.string.billiard_rules_artistic
    Discipline.EIGHT -> R.string.billiard_rules_eight
    Discipline.HEYBALL -> R.string.billiard_rules_heyball
    Discipline.BLACKBALL -> R.string.billiard_rules_blackball
    Discipline.NINE -> R.string.billiard_rules_nine
    Discipline.TEN -> R.string.billiard_rules_ten
    Discipline.STRAIGHT -> R.string.billiard_rules_straight
    Discipline.ONE_POCKET -> R.string.billiard_rules_one_pocket
    Discipline.BANK -> R.string.billiard_rules_bank
    Discipline.SNOOKER,Discipline.SIX_RED -> R.string.billiard_rules_snooker
    Discipline.ENGLISH -> R.string.billiard_rules_english
    Discipline.PYRAMID_FREE -> R.string.billiard_rules_pyramid_free
    Discipline.PYRAMID_DYNAMIC -> R.string.billiard_rules_pyramid_dynamic
}
fun Foul.titleRes(): Int = when(this) {
    Foul.NONE -> R.string.billiard_ready
    Foul.SCRATCH -> R.string.billiard_foul_scratch
    Foul.NO_CONTACT -> R.string.billiard_foul_contact
    Foul.WRONG_BALL -> R.string.billiard_foul_ball
    Foul.NO_RAIL -> R.string.billiard_foul_rail
    Foul.WRONG_POT -> R.string.billiard_foul_pot
    Foul.OWN_PIN -> R.string.billiard_foul_pin
    Foul.OFF_TABLE -> R.string.billiard_foul_off
    Foul.THREE_FOULS -> R.string.billiard_foul_three
    Foul.ILLEGAL_BREAK -> R.string.billiard_foul_break
}
