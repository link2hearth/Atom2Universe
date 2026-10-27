package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Family.*
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion.*
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Shape.*

/**
 * Every swallowable thing, its real-world size in metres and where its picture lives.
 * Pictures come from 6-column WebP sheets in assets/glutton/<sheet>.webp; the [Shape] is the
 * code-drawn stand-in used until a sheet exists. tools/glutton/prompts.py reads this file.
 */
object GluttonCatalog {
    enum class Family { FOOD, TOY, HOME, PLANT, ANIMAL, GARDEN, VEHICLE, STREET, BUILDING, TREASURE }
    /** STILL waits; WANDER drifts; FLEE runs once edible; CHASE bumps you while you are smaller; PATROL drives lanes. */
    enum class Motion { STILL, WANDER, FLEE, CHASE, PATROL_H, PATROL_V }
    enum class Shape {
        CRUMB, BERRY, COOKIE, DONUT, SOCK, DUCK, POTPLANT, LAMP, CHAIR, TABLE, SOFA, FRIDGE,
        FLOWERS, BIN, BIKE, WHEELBARROW, BENCH, PARASOL, TREE, PLAYHOUSE, VAN, CARAVAN, SHED, GREENHOUSE,
        CONE, SCOOTER, CAR, BUS, KIOSK, FOUNTAIN, HOUSE, APARTMENT, TOWER, STADIUM, CITYHALL, CLOCKTOWER,
        BALL, BOX, DISC, CAN, MOUSE, CAT, VACUUM, DOG, HEN, PIGEON, MOWER, TRAIN, GNOME, STATUE, TEDDY, BALLOON, GOLD
    }
    /** scale = world units per metre; the gobbler always starts about 16 units wide so every scene feels alike. */
    enum class Scene(val scale: Float, val width: Float, val height: Float, val budget: Float, val par: Float, val startMetres: Float) {
        ROOM(320f, 1700f, 1900f, 120f, 80f, .10f),
        GARDEN(32f, 2100f, 2300f, 125f, 85f, 1.06f),
        CITY(3.2f, 2500f, 2500f, 150f, 105f, 9.4f)
    }
    enum class Sheet(val scene: Scene) {
        ROOM_A(Scene.ROOM), ROOM_B(Scene.ROOM), ROOM_C(Scene.ROOM),
        GARDEN_A(Scene.GARDEN), GARDEN_B(Scene.GARDEN), GARDEN_C(Scene.GARDEN),
        CITY_A(Scene.CITY), CITY_B(Scene.CITY)
    }
    const val ANY = -1
    const val COLUMNS = 6

    class Prop(val index: Int, val id: String, val sheet: Sheet, val cell: Int, val metres: Float, val family: Family,
        val shape: Shape, val motion: Motion, val count: Int, val where: Int, val speed: Float) {
        val scene get() = sheet.scene
        val radius get() = metres * .5f * scene.scale
        val goal get() = id == "fridge" || id == "greenhouse" || id == "town_hall"
        val treasure get() = family == TREASURE
        val moves get() = motion != STILL
    }

    private class Builder(val sheet: Sheet, val target: ArrayList<Prop>) {
        var cell = 0
        fun p(id: String, metres: Float, family: Family, shape: Shape, count: Int = 1, where: Int = ANY,
            motion: Motion = STILL, speed: Float = 0f) {
            target += Prop(target.size, id, sheet, cell++, metres, family, shape, motion, count, where, speed)
        }
    }
    private fun ArrayList<Prop>.sheet(sheet: Sheet, block: Builder.() -> Unit) { Builder(sheet, this).block() }

    val props: List<Prop> = ArrayList<Prop>(170).apply {
        sheet(Sheet.ROOM_A) {
            p("crumb", .025f, FOOD, CRUMB, 36)
            p("cereal_loop", .03f, FOOD, DONUT, 28)
            p("pea", .02f, FOOD, BALL, 22)
            p("raisin", .025f, FOOD, CRUMB, 18)
            p("button", .03f, HOME, DISC, 18)
            p("coin", .035f, HOME, DISC, 16)
            p("candy", .04f, FOOD, BALL, 20)
            p("marble", .035f, TOY, BALL, 16, 6)
            p("lego_brick", .04f, TOY, BOX, 20, 6)
            p("dice", .035f, TOY, BOX, 10, 6)
            p("bottle_cap", .04f, HOME, DISC, 12, 2)
            p("cherry", .05f, FOOD, BERRY, 14, 2)
            p("cookie", .08f, FOOD, COOKIE, 14)
            p("strawberry", .06f, FOOD, BERRY, 12, 2)
            p("macaron", .07f, FOOD, COOKIE, 10, 2)
            p("key", .08f, HOME, DISC, 6, 0)
            p("toy_soldier", .09f, TOY, GNOME, 10, 6)
            p("spoon", .15f, HOME, DISC, 6, 2)
            p("pencil", .17f, HOME, CAN, 8, 0)
            p("toy_car", .25f, TOY, CAR, 3, motion = PATROL_H, speed = 100f)
            p("rubber_duck", .12f, TOY, DUCK, 6, 6)
            p("mug", .11f, HOME, CAN, 8, 2)
            p("donut", .12f, FOOD, DONUT, 8, 2)
            p("sock", .18f, HOME, SOCK, 8, 3)
        }
        sheet(Sheet.ROOM_B) {
            p("apple", .09f, FOOD, BALL, 8, 2)
            p("phone", .15f, HOME, BOX, 4, 0)
            p("cupcake", .09f, FOOD, COOKIE, 8, 5)
            p("remote", .17f, HOME, BOX, 4, 5)
            p("tennis_ball", .07f, TOY, BALL, 8, 7)
            p("book", .25f, HOME, BOX, 8, 0)
            p("teddy", .3f, TOY, TEDDY, 5, 6)
            p("slipper", .28f, HOME, SOCK, 6, 3)
            p("flower_pot", .3f, PLANT, POTPLANT, 7, 1)
            p("teapot", .25f, HOME, CAN, 4, 2)
            p("football", .22f, TOY, BALL, 5, 7)
            p("backpack", .4f, HOME, BOX, 3, 0)
            p("pillow", .45f, HOME, BOX, 6, 5)
            p("toy_robot", .3f, TOY, GNOME, 5, 6)
            p("laptop", .35f, HOME, BOX, 3, 0)
            p("toaster", .3f, HOME, BOX, 3, 2)
            p("kettle", .25f, HOME, CAN, 3, 2)
            p("fishbowl", .3f, ANIMAL, BALL, 3, 1)
            p("mouse", .1f, ANIMAL, MOUSE, 5, motion = FLEE, speed = 110f)
            p("cat", .45f, ANIMAL, CAT, 2, motion = CHASE, speed = 120f)
            p("robot_vacuum", .35f, HOME, VACUUM, 2, motion = CHASE, speed = 105f)
            p("chair", .5f, HOME, CHAIR, 7, 4)
            p("stool", .45f, HOME, CHAIR, 5, 3)
            p("lamp", .55f, HOME, LAMP, 3, 8)
        }
        sheet(Sheet.ROOM_C) {
            p("tv", .7f, HOME, BOX, 1, 5)
            p("nightstand", .55f, HOME, TABLE, 2, 8)
            p("side_table", .6f, HOME, TABLE, 2, 1)
            p("dog_bed", .7f, HOME, SOFA, 1, 7)
            p("bookshelf", .9f, HOME, BOX, 2, 0)
            p("armchair", .8f, HOME, SOFA, 2, 5)
            p("dining_table", .85f, HOME, TABLE, 1, 4)
            p("sofa", .9f, HOME, SOFA, 1, 8)
            p("fridge", 1f, HOME, FRIDGE, 1, 2)
            p("golden_spoon", .17f, TREASURE, GOLD, 1, 0)
            p("golden_egg", .32f, TREASURE, GOLD, 1, 8)
            p("golden_trophy", .6f, TREASURE, GOLD, 1, 3)
        }
        sheet(Sheet.GARDEN_A) {
            p("daisy", .3f, PLANT, FLOWERS, 50)
            p("mushroom", .3f, PLANT, POTPLANT, 24)
            p("pine_cone", .2f, PLANT, CRUMB, 30)
            p("watering_can", .4f, GARDEN, CAN, 10)
            p("bucket", .35f, GARDEN, CAN, 10)
            p("garden_gnome", .5f, GARDEN, GNOME, 10)
            p("hen", .4f, ANIMAL, HEN, 5, 1, FLEE, 90f)
            p("rabbit", .35f, ANIMAL, MOUSE, 4, motion = FLEE, speed = 130f)
            p("pigeon", .3f, ANIMAL, PIGEON, 6, motion = FLEE, speed = 150f)
            p("hedgehog", .25f, ANIMAL, BALL, 3, motion = WANDER, speed = 40f)
            p("pumpkin", .5f, FOOD, BALL, 12, 7)
            p("lantern", .4f, GARDEN, LAMP, 12)
            p("mailbox", .6f, GARDEN, BOX, 5, 3)
            p("skateboard", .8f, TOY, SCOOTER, 5, 6)
            p("tricycle", .7f, TOY, BIKE, 4, 6)
            p("bird_bath", .9f, GARDEN, FOUNTAIN, 4, 4)
            p("wheelbarrow", .9f, GARDEN, WHEELBARROW, 5, 7)
            p("scooter", .9f, VEHICLE, SCOOTER, 5, 3)
            p("dog", .8f, ANIMAL, DOG, 2, motion = CHASE, speed = 115f)
            p("hose_reel", .6f, GARDEN, DISC, 6, 4)
            p("flower_bed", 2f, PLANT, FLOWERS, 8, 1)
            p("barbecue", 1.2f, GARDEN, CAN, 4, 5)
            p("lawn_mower", 1f, GARDEN, MOWER, 2, motion = PATROL_H, speed = 70f)
            p("bench", 1.6f, GARDEN, BENCH, 6, 4)
        }
        sheet(Sheet.GARDEN_B) {
            p("bike", 1.7f, VEHICLE, BIKE, 5, 3)
            p("parasol", 2f, GARDEN, PARASOL, 4, 5)
            p("picnic_table", 1.8f, GARDEN, TABLE, 3, 5)
            p("swing", 2.2f, TOY, BENCH, 2, 6)
            p("doghouse", 1.3f, GARDEN, PLAYHOUSE, 2, 7)
            p("sandbox", 2f, TOY, BOX, 2, 6)
            p("statue", 1.8f, GARDEN, STATUE, 3, 4)
            p("goat", 1.4f, ANIMAL, DOG, 3, 1, FLEE, 120f)
            p("scarecrow", 1.8f, GARDEN, GNOME, 3, 1)
            p("trampoline", 2.5f, TOY, DISC, 2, 6)
            p("fountain", 2.5f, GARDEN, FOUNTAIN, 2, 4)
            p("hot_tub", 2.2f, GARDEN, CAN, 2, 8)
            p("kayak", 3f, VEHICLE, CAN, 2, 8)
            p("slide", 3.5f, TOY, BENCH, 2, 6)
            p("playhouse", 3f, GARDEN, PLAYHOUSE, 2, 6)
            p("shed", 3.5f, GARDEN, SHED, 3, 0)
            p("tractor", 3.5f, VEHICLE, VAN, 1, motion = PATROL_V, speed = 90f)
            p("car", 4.2f, VEHICLE, CAR, 2, motion = PATROL_H, speed = 200f)
            p("pony", 2f, ANIMAL, DOG, 2, 1, FLEE, 150f)
            p("apple_tree", 4.5f, PLANT, TREE, 5, 1)
            p("pine", 5f, PLANT, TREE, 5, 0)
            p("gazebo", 5f, GARDEN, PLAYHOUSE, 1, 5)
            p("van", 5.5f, VEHICLE, VAN, 2, 3)
            p("caravan", 6f, VEHICLE, CARAVAN, 2, 8)
        }
        sheet(Sheet.GARDEN_C) {
            p("hay_bale", 1.5f, PLANT, CAN, 8, 1)
            p("beehive", 1f, GARDEN, BOX, 5, 0)
            p("well", 2f, GARDEN, CAN, 1, 3)
            p("boat", 6f, VEHICLE, CAN, 1, 8)
            p("pool", 6f, GARDEN, DISC, 1, 8)
            p("treehouse", 6f, PLANT, TREE, 1, 0)
            p("oak", 7f, PLANT, TREE, 2, 0)
            p("windmill", 7f, BUILDING, TOWER, 1, 5)
            p("greenhouse", 8f, GARDEN, GREENHOUSE, 1, 2)
            p("golden_gnome", 1.7f, TREASURE, GOLD, 1, 0)
            p("golden_beehive", 2.9f, TREASURE, GOLD, 1, 8)
            p("golden_fountain", 5.1f, TREASURE, GOLD, 1, 3)
        }
        sheet(Sheet.CITY_A) {
            p("phone_booth", 3f, STREET, BOX, 14)
            p("hot_dog_cart", 3f, STREET, KIOSK, 12)
            p("pigeon_flock", 3f, ANIMAL, PIGEON, 8, motion = FLEE, speed = 150f)
            p("bus_stop", 4f, STREET, KIOSK, 12)
            p("car", 4.2f, VEHICLE, CAR, 5, motion = PATROL_H, speed = 220f)
            p("taxi", 4.5f, VEHICLE, CAR, 3, motion = PATROL_V, speed = 200f)
            p("police_car", 4.8f, VEHICLE, CAR, 2, motion = CHASE, speed = 140f)
            p("kiosk", 5f, STREET, KIOSK, 10)
            p("statue", 6f, STREET, STATUE, 8)
            p("delivery_van", 6f, VEHICLE, VAN, 2, motion = PATROL_H, speed = 170f)
            p("food_truck", 7f, VEHICLE, VAN, 7)
            p("fountain", 8f, STREET, FOUNTAIN, 6, 4)
            p("billboard", 8f, STREET, BOX, 8)
            p("truck", 9f, VEHICLE, VAN, 2, motion = PATROL_V, speed = 160f)
            p("fire_truck", 10f, VEHICLE, BUS, 1, motion = PATROL_H, speed = 180f)
            p("cottage", 10f, BUILDING, HOUSE, 12, 6)
            p("bus", 11f, VEHICLE, BUS, 2, motion = PATROL_H, speed = 150f)
            p("house", 12f, BUILDING, HOUSE, 14, 7)
            p("cafe", 12f, BUILDING, HOUSE, 8, 3)
            p("hot_air_balloon", 10f, VEHICLE, BALLOON, 2, motion = WANDER, speed = 45f)
            p("bakery", 14f, BUILDING, HOUSE, 6, 3)
            p("tram", 15f, VEHICLE, TRAIN, 1, motion = PATROL_V, speed = 150f)
            p("carousel", 15f, STREET, PARASOL, 3, 4)
            p("supermarket", 40f, BUILDING, APARTMENT, 3, 5)
        }
        sheet(Sheet.CITY_B) {
            p("mobile_crane", 15f, VEHICLE, TOWER, 4, 8)
            p("water_tower", 20f, BUILDING, TOWER, 3, 0)
            p("townhouse", 18f, BUILDING, APARTMENT, 12, 0)
            p("apartment", 25f, BUILDING, APARTMENT, 10, 5)
            p("lighthouse", 25f, BUILDING, TOWER, 1, 2)
            p("school", 30f, BUILDING, HOUSE, 3, 3)
            p("cinema", 30f, BUILDING, APARTMENT, 3, 4)
            p("fire_station", 28f, BUILDING, HOUSE, 1, 8)
            p("church", 35f, BUILDING, TOWER, 3, 0)
            p("parking_garage", 35f, BUILDING, APARTMENT, 3, 8)
            p("train", 40f, VEHICLE, TRAIN, 1, motion = PATROL_H, speed = 200f)
            p("ferris_wheel", 40f, STREET, DISC, 1, 2)
            p("museum", 45f, BUILDING, CITYHALL, 1, 3)
            p("hospital", 45f, BUILDING, APARTMENT, 2, 5)
            p("factory", 50f, BUILDING, APARTMENT, 3, 8)
            p("mall", 55f, BUILDING, APARTMENT, 2, 4)
            p("stadium", 60f, BUILDING, STADIUM, 1, 6)
            p("skyscraper", 60f, BUILDING, TOWER, 4, 2)
            p("cathedral", 70f, BUILDING, TOWER, 1, 0)
            p("town_hall", 90f, BUILDING, CLOCKTOWER, 1, 1)
            p("golden_statue", 15.8f, TREASURE, GOLD, 1, 0)
            p("golden_bell", 29f, TREASURE, GOLD, 1, 8)
            p("golden_crown", 55f, TREASURE, GOLD, 1, 2)
        }
    }

    fun forScene(scene: Scene) = props.filter { it.scene == scene }
    fun goal(scene: Scene) = props.first { it.scene == scene && it.goal }
}
