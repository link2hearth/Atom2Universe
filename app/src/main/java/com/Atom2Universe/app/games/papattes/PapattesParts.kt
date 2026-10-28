package com.Atom2Universe.app.games.papattes

import com.Atom2Universe.app.R

/** Une tête ne change que l'apparence : elle compte pour la collection. */
enum class Head(val id: String, val nameRes: Int) {
    CAT("cat", R.string.papattes_head_cat),
    FROG("frog", R.string.papattes_head_frog),
    OWL("owl", R.string.papattes_head_owl),
    AXOLOTL("axolotl", R.string.papattes_head_axolotl),
    HAMSTER("hamster", R.string.papattes_head_hamster),
    CAPYBARA("capybara", R.string.papattes_head_capybara),
    SNAIL("snail", R.string.papattes_head_snail),
    DRAGON("dragon", R.string.papattes_head_dragon),
    PANDA("panda", R.string.papattes_head_panda),
    FOX("fox", R.string.papattes_head_fox),
    RABBIT("rabbit", R.string.papattes_head_rabbit),
    PENGUIN("penguin", R.string.papattes_head_penguin),
    KOALA("koala", R.string.papattes_head_koala),
    HEDGEHOG("hedgehog", R.string.papattes_head_hedgehog),
    RACCOON("raccoon", R.string.papattes_head_raccoon),
    SLOTH("sloth", R.string.papattes_head_sloth),
    OTTER("otter", R.string.papattes_head_otter),
    RED_PANDA("redpanda", R.string.papattes_head_redpanda),
    MOUSE("mouse", R.string.papattes_head_mouse),
    PIG("pig", R.string.papattes_head_pig),
    DUCKLING("duckling", R.string.papattes_head_duckling),
    BAT("bat", R.string.papattes_head_bat),
    LION("lion", R.string.papattes_head_lion),
    MONKEY("monkey", R.string.papattes_head_monkey),
    UNICORN("unicorn", R.string.papattes_head_unicorn),
    SHIBA("shiba", R.string.papattes_head_shiba),
    SHEEP("sheep", R.string.papattes_head_sheep)
}

enum class Weight(val nameRes: Int) {
    LIGHT(R.string.papattes_weight_light),
    MEDIUM(R.string.papattes_weight_medium),
    HEAVY(R.string.papattes_weight_heavy)
}

/** Ce qu'un corps sait faire en plus de peser. */
enum class Power(val nameRes: Int) {
    NONE(R.string.papattes_power_none),
    /** Flotte : l'eau porte la bestiole au lieu de l'avaler. */
    FLOAT(R.string.papattes_power_float),
    /** Plane : la chute est freinée quand les deux pieds quittent le sol. */
    GLIDE(R.string.papattes_power_glide),
    /** Ancre : le vent ne la pousse presque plus. */
    ANCHOR(R.string.papattes_power_anchor),
    /** Rebondit : un choc du corps contre le sol la relance au lieu de la faire tomber, et les bonds portent plus loin. */
    BOUNCE(R.string.papattes_power_bounce)
}

/** Le corps pèse et porte un pouvoir. */
enum class Body(val id: String, val nameRes: Int, val weight: Weight, val power: Power) {
    POMPOM("pompom", R.string.papattes_body_pompom, Weight.MEDIUM, Power.NONE),
    BEE("bee", R.string.papattes_body_bee, Weight.LIGHT, Power.GLIDE),
    SHELL("shell", R.string.papattes_body_shell, Weight.HEAVY, Power.ANCHOR),
    WOOL("wool", R.string.papattes_body_wool, Weight.MEDIUM, Power.NONE),
    BUOY("buoy", R.string.papattes_body_buoy, Weight.LIGHT, Power.FLOAT),
    PUMPKIN("pumpkin", R.string.papattes_body_pumpkin, Weight.HEAVY, Power.NONE),
    DANDELION("dandelion", R.string.papattes_body_dandelion, Weight.LIGHT, Power.GLIDE),
    JELLY("jelly", R.string.papattes_body_jelly, Weight.MEDIUM, Power.BOUNCE),
    WALNUT("walnut", R.string.papattes_body_walnut, Weight.HEAVY, Power.ANCHOR),
    CLOUD("cloud", R.string.papattes_body_cloud, Weight.LIGHT, Power.GLIDE),
    BEACH_BALL("beachball", R.string.papattes_body_beachball, Weight.LIGHT, Power.FLOAT),
    BOULDER("boulder", R.string.papattes_body_boulder, Weight.HEAVY, Power.ANCHOR),
    CAULDRON("cauldron", R.string.papattes_body_cauldron, Weight.HEAVY, Power.ANCHOR),
    RUBBER_BALL("rubberball", R.string.papattes_body_rubberball, Weight.MEDIUM, Power.BOUNCE),
    MARSHMALLOW("marshmallow", R.string.papattes_body_marshmallow, Weight.LIGHT, Power.BOUNCE),
    STRAWBERRY("strawberry", R.string.papattes_body_strawberry, Weight.MEDIUM, Power.NONE),
    CACTUS("cactus", R.string.papattes_body_cactus, Weight.MEDIUM, Power.NONE),
    PINEAPPLE("pineapple", R.string.papattes_body_pineapple, Weight.HEAVY, Power.NONE);

    /** Masse en kilogrammes du corps et de la tête réunis. */
    val mass: Float get() = when (weight) { Weight.LIGHT -> 3.2f; Weight.MEDIUM -> 4.2f; Weight.HEAVY -> 5.4f }
}

/**
 * Trois façons d'avancer, toutes au même doigt :
 * - [WALK] : tenir avance la jambe arrière, lâcher pose le pied ;
 * - [HOP] : tenir accroupit (plus on tient, plus le bond porte), lâcher fait bondir ;
 * - [GLIDE] : tenir pousse pour prendre de l'élan, lâcher laisse filer ; un appui bref fait sauter.
 */
enum class Gait(val nameRes: Int) {
    WALK(R.string.papattes_gait_walk),
    HOP(R.string.papattes_gait_hop),
    GLIDE(R.string.papattes_gait_glide)
}

/**
 * La paire de jambes fait la démarche. Longueurs en mètres, couples en N·m, vitesses en rad/s.
 *
 * Marche : [swingRate] est la vitesse à laquelle la jambe libre part vers l'avant tant que le doigt
 * reste posé, [pushRate] celle à laquelle la jambe d'appui pousse le corps.
 * Saut : [jumpSpeed] (m/s) est atteinte après [chargeTime] secondes d'appui, sous l'angle
 * [jumpAngle] (degrés au-dessus de l'horizontale) ; [rebound] relance un petit bond à l'atterrissage.
 * [reach] est le trou le plus large que ces jambes enjambent sans peine : les trous d'eau du
 * parcours sont taillés à leur mesure (voir [PapattesCourse.gapScale]).
 */
enum class Legs(
    val id: String,
    val nameRes: Int,
    val traitRes: Int,
    val gait: Gait,
    val thigh: Float,
    val shin: Float,
    val footRadius: Float,
    val mass: Float,
    val hipTorque: Float,
    val kneeTorque: Float,
    val swingRate: Float = 1.7f,
    val pushRate: Float = 1f,
    val friction: Float = 1f,
    val kneeBend: Float = 1f,
    val jumpSpeed: Float = 0f,
    val jumpAngle: Float = 0f,
    val chargeTime: Float = 0f,
    val rebound: Float = 0f,
    val reach: Float = PapattesCourse.WIDEST_GAP
) {
    STILTS("stilts", R.string.papattes_legs_stilts, R.string.papattes_trait_stilts, Gait.WALK,
        .50f, .60f, .06f, .9f, 70f, 60f, 1.7f, 1.0f, 1.0f, 1.0f),
    FLAMINGO("flamingo", R.string.papattes_legs_flamingo, R.string.papattes_trait_flamingo, Gait.WALK,
        .55f, .85f, .07f, .8f, 80f, 70f, 1.35f, .8f, 1.0f, 1.1f),
    DACHSHUND("dachshund", R.string.papattes_legs_dachshund, R.string.papattes_trait_dachshund, Gait.WALK,
        .28f, .32f, .055f, .7f, 55f, 45f, 2.9f, 1.9f, 2.5f, 1.45f, reach = .46f),
    GECKO("gecko", R.string.papattes_legs_gecko, R.string.papattes_trait_gecko, Gait.WALK,
        .42f, .50f, .065f, .8f, 70f, 60f, 1.7f, 1.0f, 6.0f, 1.0f),
    OSTRICH("ostrich", R.string.papattes_legs_ostrich, R.string.papattes_trait_ostrich, Gait.WALK,
        .60f, .70f, .07f, 1.0f, 70f, 80f, 1.6f, .95f, 1.0f, 1.1f),
    FROG("frog", R.string.papattes_legs_frog, R.string.papattes_trait_frog, Gait.HOP,
        .36f, .42f, .08f, .8f, 90f, 120f, kneeBend = 1.5f, jumpSpeed = 4.3f, jumpAngle = 52f, chargeTime = .6f),
    SPRING("spring", R.string.papattes_legs_spring, R.string.papattes_trait_spring, Gait.HOP,
        .30f, .55f, .07f, .8f, 90f, 120f, kneeBend = 1.2f, jumpSpeed = 4.6f, jumpAngle = 66f, chargeTime = .45f, rebound = .5f),
    KANGAROO("kangaroo", R.string.papattes_legs_kangaroo, R.string.papattes_trait_kangaroo, Gait.HOP,
        .40f, .60f, .08f, .9f, 100f, 140f, kneeBend = 1.6f, jumpSpeed = 5.1f, jumpAngle = 40f, chargeTime = .8f),
    SKATES("skates", R.string.papattes_legs_skates, R.string.papattes_trait_skates, Gait.GLIDE,
        .40f, .45f, .06f, .8f, 80f, 80f, friction = 0f, kneeBend = .3f, reach = .75f),
    ROLLERS("rollers", R.string.papattes_legs_rollers, R.string.papattes_trait_rollers, Gait.GLIDE,
        .40f, .45f, .07f, .8f, 80f, 80f, friction = 0f, kneeBend = .3f, reach = .75f);

    val length: Float get() = thigh + shin

    /** Portée d'un bond à pleine charge sur du plat, en mètres. */
    val maxJump: Float get() = jumpSpeed * jumpSpeed * kotlin.math.sin(2 * Math.toRadians(jumpAngle.toDouble())).toFloat() / 9.81f

    /** Vitesse de croisière en glisse sur une surface, en m/s : les patins filent sur la glace, les roulettes sur le dur. */
    fun glideSpeed(surface: Surface): Float = when (this) {
        SKATES -> when (surface) { Surface.ICE -> 5f; Surface.SNOW -> 2.8f; Surface.WOOD -> 3f; Surface.GRASS -> 1.5f; Surface.MUD -> 1.1f; Surface.WATER -> 1.2f }
        ROLLERS -> when (surface) { Surface.ICE -> 3.8f; Surface.SNOW -> 1.2f; Surface.WOOD -> 4.4f; Surface.GRASS -> 3.2f; Surface.MUD -> 1.2f; Surface.WATER -> 1f }
        else -> 0f
    }
}

/** Une bestiole : trois pièces. Son code tient dans un entier, de 0 à [COUNT] − 1. */
data class Critter(val head: Head, val body: Body, val legs: Legs) {
    val code: Int get() = (head.ordinal * Body.entries.size + body.ordinal) * Legs.entries.size + legs.ordinal

    companion object {
        val COUNT = Head.entries.size * Body.entries.size * Legs.entries.size
        val STARTER = Critter(Head.CAT, Body.POMPOM, Legs.STILTS)

        fun fromCode(code: Int): Critter? {
            if (code !in 0 until COUNT) return null
            val legs = code % Legs.entries.size
            val rest = code / Legs.entries.size
            return Critter(Head.entries[rest / Body.entries.size], Body.entries[rest % Body.entries.size], Legs.entries[legs])
        }
    }
}

/** Toutes les pièces dans un seul espace d'indices, pour la collection : têtes, puis corps, puis jambes. */
object Parts {
    val COUNT = Head.entries.size + Body.entries.size + Legs.entries.size
    fun index(head: Head) = head.ordinal
    fun index(body: Body) = Head.entries.size + body.ordinal
    fun index(legs: Legs) = Head.entries.size + Body.entries.size + legs.ordinal
    fun of(critter: Critter) = intArrayOf(index(critter.head), index(critter.body), index(critter.legs))
}
