package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion.*
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Shape.*

/**
 * Every swallowable thing and its real-world size in metres: objects only, from a sesame seed on the
 * kitchen floor to the towers downtown. Each theme lists its objects in slot order; slot / 24 is the
 * WebP page (assets/glutton/<theme>_<page>.webp, 6 × 4 cells) and slot % 24 the cell. The [Shape] is
 * the code-drawn stand-in used until the page exists. tools/glutton/prompts.py reads this file.
 */
object GluttonCatalog {
    /** STILL waits; WANDER drifts about; CHASE bumps you while you are smaller; PATROL drives straight lines. */
    enum class Motion { STILL, WANDER, CHASE, PATROL }
    enum class Shape {
        CRUMB, BERRY, COOKIE, DONUT, SOCK, DUCK, POTPLANT, LAMP, CHAIR, TABLE, SOFA, FRIDGE,
        FLOWERS, BIN, BIKE, WHEELBARROW, BENCH, PARASOL, TREE, PLAYHOUSE, VAN, CARAVAN, SHED, GREENHOUSE,
        CONE, SCOOTER, CAR, BUS, KIOSK, FOUNTAIN, HOUSE, APARTMENT, TOWER, STADIUM, CITYHALL, CLOCKTOWER,
        BALL, BOX, DISC, CAN, VACUUM, MOWER, TRAIN, GNOME, STATUE, TEDDY, BALLOON, HOURGLASS
    }
    /** The three rings of the world, from the start outwards: inside your house, its suburb, downtown. */
    enum class Theme { ROOM, SUBURB, CITY }
    const val COLUMNS = 6
    const val ROWS = 4
    const val PAGE = COLUMNS * ROWS

    class Prop(val index: Int, val id: String, val theme: Theme?, val slot: Int, val metres: Float,
        val shape: Shape, val motion: Motion, val speed: Float) {
        val page get() = slot / PAGE
        val cell get() = slot % PAGE
        /** Radius in metres. */
        val radius get() = metres * .5f
        val moves get() = motion != STILL
        /** Drawn in code only: the same picture must work at every scale of the world. */
        val bonus get() = theme == null
    }

    private class Builder(val theme: Theme?, val target: ArrayList<Prop>) {
        var slot = 0
        fun p(id: String, metres: Float, shape: Shape, motion: Motion = STILL, speed: Float = 0f) {
            target += Prop(target.size, id, theme, if (theme == null) -1 else slot++, metres, shape, motion, speed)
        }
    }
    private fun ArrayList<Prop>.theme(theme: Theme?, block: Builder.() -> Unit) { Builder(theme, this).block() }

    /** Speeds are metres per second. Within a theme, objects are listed from the smallest to the largest. */
    val props: List<Prop> = ArrayList<Prop>(220).apply {
        theme(Theme.ROOM) {
            p("sesame_seed", .004f, CRUMB)
            p("peppercorn", .005f, BALL)
            p("sugar_sprinkle", .006f, CRUMB)
            p("lentil", .007f, DISC)
            p("rice_grain", .008f, CRUMB)
            p("popcorn_kernel", .009f, CRUMB)
            p("coffee_bean", .01f, BERRY)
            p("thumbtack", .011f, CONE)
            p("staple", .012f, BOX)
            p("glass_bead", .013f, BALL)
            p("raisin", .015f, BERRY)
            p("sunflower_seed", .018f, CRUMB)
            p("jelly_bean", .02f, BERRY)
            p("almond", .022f, BERRY)
            p("marble", .025f, BALL)
            p("coin", .026f, DISC)
            p("cherry", .028f, BERRY)
            p("bottle_cap", .03f, DISC)
            p("lego_brick", .032f, BOX)
            p("paperclip", .034f, BOX)
            p("walnut", .038f, BALL)
            p("strawberry", .04f, BERRY)
            p("golf_ball", .043f, BALL)
            p("eraser", .045f, BOX)
            p("matchbox", .053f, BOX)
            p("door_key", .058f, BOX)
            p("egg", .06f, BALL)
            p("tennis_ball", .067f, BALL)
            p("lipstick", .072f, CAN)
            p("cookie", .078f, COOKIE)
            p("apple", .082f, BALL)
            p("soap_bar", .09f, BOX)
            p("mug", .11f, CAN)
            p("toy_car", .12f, CAR, PATROL, .5f)
            p("alarm_clock", .13f, DISC)
            p("smartphone", .15f, BOX)
            p("potted_cactus", .16f, POTPLANT)
            p("tv_remote", .18f, BOX)
            p("banana", .19f, SOCK)
            p("toy_robot", .2f, BOX, WANDER, .3f)
            p("hairbrush", .21f, BOX)
            p("soccer_ball", .22f, BALL)
            p("hardcover_book", .24f, BOX)
            p("toy_train", .25f, TRAIN, PATROL, .5f)
            p("kettle", .26f, CAN)
            p("slipper", .27f, SOCK)
            p("toaster", .28f, BOX)
            p("teddy_bear", .3f, TEDDY)
            p("drone", .32f, DISC, WANDER, .8f)
            p("laptop", .33f, BOX)
            p("robot_vacuum", .35f, VACUUM, CHASE, .4f)
            p("rc_car", .38f, CAR, CHASE, 1.5f)
            p("wastepaper_basket", .4f, BIN)
            p("globe", .42f, BALL)
            p("wooden_stool", .45f, CHAIR)
            p("microwave", .49f, BOX)
            p("desk_lamp", .53f, LAMP)
            p("nightstand", .58f, TABLE)
            p("fire_extinguisher", .6f, CAN)
            p("computer_monitor", .64f, BOX)
            p("houseplant", .68f, POTPLANT)
            p("bed_pillow", .72f, SOCK)
            p("suitcase", .76f, BOX)
            p("floor_fan", .82f, LAMP)
            p("beanbag", .86f, BALL)
            p("dining_chair", .9f, CHAIR)
            p("armchair", .95f, SOFA)
            p("flat_screen_tv", .98f, BOX)
            p("chest_of_drawers", 1f, BOX)
            p("upright_vacuum", 1.1f, VACUUM)
            p("exercise_bike", 1.2f, BIKE)
            p("desk", 1.4f, TABLE)
            p("upright_piano", 1.5f, BOX)
            p("floor_lamp", 1.6f, LAMP)
            p("bathtub", 1.7f, BOX)
            p("fridge", 1.8f, FRIDGE)
            p("bookshelf", 1.85f, FRIDGE)
            p("dining_table", 1.9f, TABLE)
            p("christmas_tree", 1.95f, TREE)
            p("double_bed", 2.05f, SOFA)
            p("grandfather_clock", 2.1f, CLOCKTOWER)
            p("sofa", 2.2f, SOFA)
            p("wardrobe", 2.3f, FRIDGE)
            p("kitchen_island", 2.45f, TABLE)
        }
        theme(Theme.SUBURB) {
            p("beach_ball", .5f, BALL)
            p("watering_can", .52f, CAN)
            p("terracotta_planter", .55f, POTPLANT)
            p("hose_reel", .58f, DISC)
            p("garden_gnome", .6f, GNOME)
            p("robot_mower", .62f, MOWER, WANDER, .5f)
            p("cooler_box", .65f, BOX)
            p("car_tyre", .66f, DONUT)
            p("tricycle", .7f, BIKE)
            p("traffic_cone", .72f, CONE)
            p("fire_hydrant", .76f, CAN)
            p("bird_bath", .8f, FOUNTAIN)
            p("skateboard", .82f, SCOOTER)
            p("plastic_chair", .86f, CHAIR)
            p("kick_scooter", .9f, SCOOTER)
            p("pool_ring", .95f, DONUT)
            p("rain_barrel", 1f, CAN)
            p("doghouse", 1.05f, PLAYHOUSE)
            p("wheelie_bin", 1.1f, BIN)
            p("kettle_barbecue", 1.15f, BALL)
            p("mailbox", 1.2f, BOX)
            p("hay_bale", 1.25f, DISC)
            p("wheelbarrow", 1.4f, WHEELBARROW)
            p("push_mower", 1.48f, MOWER)
            p("sunflower", 1.55f, FLOWERS)
            p("kiddie_pool", 1.7f, DISC)
            p("bicycle", 1.75f, BIKE)
            p("garden_bench", 1.8f, BENCH)
            p("dumpster", 1.85f, BIN)
            p("picnic_table", 1.9f, TABLE)
            p("vending_machine", 1.95f, FRIDGE)
            p("garden_statue", 2f, STATUE)
            p("playhouse", 2.05f, PLAYHOUSE)
            p("motorcycle", 2.1f, BIKE, PATROL, 7f)
            p("hot_tub", 2.2f, CAN)
            p("camping_tent", 2.3f, PLAYHOUSE)
            p("golf_cart", 2.4f, CAR, PATROL, 3f)
            p("parasol", 2.5f, PARASOL)
            p("garden_fountain", 2.6f, FOUNTAIN)
            p("garden_shed", 2.8f, SHED)
            p("basketball_hoop", 3f, LAMP)
            p("newspaper_kiosk", 3.2f, KIOSK)
            p("gazebo", 3.4f, PARASOL)
            p("trampoline", 3.6f, DISC)
            p("hatchback", 3.9f, CAR, PATROL, 5f)
            p("bus_shelter", 4.2f, KIOSK)
            p("apple_tree", 4.5f, TREE)
            p("street_lamp", 4.8f, LAMP)
            p("bouncy_castle", 5f, PLAYHOUSE)
            p("delivery_van", 5.2f, VAN, PATROL, 6f)
            p("tractor", 5.4f, MOWER)
            p("pickup_truck", 5.6f, CAR, PATROL, 6f)
            p("caravan", 6f, CARAVAN)
            p("treehouse", 6.2f, PLAYHOUSE)
            p("double_garage", 6.5f, SHED)
            p("greenhouse", 7f, GREENHOUSE)
            p("motorhome", 7.2f, VAN)
            p("minibus", 7.4f, BUS)
            p("backyard_pool", 7.8f, DISC)
            p("birch_tree", 8f, TREE)
            p("box_truck", 8.5f, VAN, PATROL, 6f)
            p("cottage", 9f, HOUSE)
            p("bungalow", 9.5f, HOUSE)
            p("utility_pole", 10.5f, LAMP)
            p("pine_tree", 11f, TREE)
            p("school_bus", 11.5f, BUS, PATROL, 6f)
            p("house", 12f, HOUSE)
            p("shipping_container", 12.2f, BOX)
            p("fuel_station", 13f, KIOSK)
            p("grain_silo", 14f, CAN)
            p("oak_tree", 15f, TREE)
            p("red_barn", 15.5f, SHED)
        }
        theme(Theme.CITY) {
            p("police_car", 6f, CAR, CHASE, 9f)
            p("market_stall", 6.2f, KIOSK)
            p("ambulance", 6.5f, VAN, PATROL, 8f)
            p("food_truck", 7f, VAN)
            p("bandstand", 8f, PARASOL)
            p("plaza_fountain", 8.5f, FOUNTAIN)
            p("obelisk", 9f, STATUE)
            p("bakery", 9.5f, HOUSE)
            p("fire_truck", 10f, VAN, PATROL, 7f)
            p("cafe", 10.5f, HOUSE)
            p("corner_shop", 11f, KIOSK)
            p("city_bus", 12f, BUS, PATROL, 6f)
            p("billboard", 12.5f, BOX)
            p("townhouse", 14f, HOUSE)
            p("mobile_crane", 15f, VAN)
            p("chapel", 16f, CLOCKTOWER)
            p("tanker_truck", 16.5f, VAN)
            p("metro_car", 17.5f, TRAIN, PATROL, 8f)
            p("tram", 19f, TRAIN, PATROL, 6f)
            p("water_tower", 19.8f, TOWER)
            p("freight_locomotive", 20.5f, TRAIN)
            p("clock_tower", 22f, CLOCKTOWER)
            p("fire_station", 24f, BOX)
            p("hot_air_balloon", 25f, BALLOON)
            p("lighthouse", 28f, TOWER)
            p("apartment_block", 30f, APARTMENT)
            p("theatre", 32f, BOX)
            p("cinema", 35f, BOX)
            p("school", 40f, APARTMENT)
            p("ferris_wheel", 42f, DISC)
            p("church", 45f, CLOCKTOWER)
            p("town_hall", 48f, CITYHALL)
            p("parking_garage", 50f, BOX)
            p("museum", 55f, CITYHALL)
            p("gasometer", 58f, CAN)
            p("tower_crane", 60f, TOWER)
            p("hotel", 62f, APARTMENT)
            p("factory", 70f, BOX)
            p("hospital", 75f, APARTMENT)
            p("wind_turbine", 78f, TOWER)
            p("cathedral", 95f, CLOCKTOWER)
            p("radio_telescope", 100f, DISC)
            p("ferry", 110f, BUS)
            p("office_tower", 120f, TOWER)
            p("aircraft_hangar", 130f, SHED)
            p("arena", 145f, STADIUM)
            p("opera_house", 155f, CITYHALL)
            p("cooling_tower", 165f, CAN)
            p("glass_tower", 180f, TOWER)
            p("university_hall", 190f, CITYHALL)
            p("high_speed_train", 200f, TRAIN, PATROL, 8f)
            p("power_plant", 220f, BOX)
            p("parliament", 240f, CITYHALL)
            p("airship", 245f, BALLOON)
            p("skyscraper", 260f, TOWER)
            p("shopping_mall", 280f, BOX)
            p("stadium", 290f, STADIUM)
            p("cruise_ship", 300f, BUS)
            p("tv_tower", 310f, TOWER)
            p("airport_terminal", 320f, BOX)
        }
        // Drawn in code only.
        theme(null) {
            p("hourglass", 1f, HOURGLASS)
        }
    }

    val hourglass: Prop get() = props.first { it.id == "hourglass" }
    /** The two-storey family house you start in, swallowed like anything else once you are big enough. */
    val house: Prop get() = props.first { it.id == "house" }
    fun forTheme(theme: Theme) = props.filter { it.theme == theme }
    fun pageCount(theme: Theme) = (props.count { it.theme == theme } + PAGE - 1) / PAGE
}
