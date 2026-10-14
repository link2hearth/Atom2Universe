package com.Atom2Universe.app.games.hamlet

internal enum class Build { SLIM, STOUT, ARMORED, ROBED }
internal enum class Hair { SHORT, MESSY, BRAID, LONG, WAVY, CURLS, SPIKY, BOB, PONYTAIL, PIGTAILS, TOPKNOT, FLAME, BALD, HIDDEN }
internal enum class Headgear {
    NONE, LEAF_CLIP, CAP, STRAW_HAT, REED_HAT, FEATHER_CAP, BERET, HOOD, LEAF_HOOD, HELM, CREST_HELM, GREAT_HELM,
    HORNED_HELM, HORNED_CROWN, WIZARD_HAT, ANTLERS, GOGGLES, LENS_GOGGLES, BANDANA, HEADSCARF, FLOWER_CROWN,
    STAR_CLIPS, TIARA, CIRCLET, GLASSES, MASK, SUN_CROWN, ICE_CROWN
}
internal enum class Weapon {
    BOW, LONGBOW, CROSSBOW, SWORD, RAPIER, CURVED_BLADE, FLAME_SWORD, DAGGER, SPEAR, MALLET, HAMMER, MAUL, PITCHFORK,
    LEAF_STAFF, ROOT_STAFF, SUN_STAFF, STAR_STAFF, ICE_STAFF, SNOW_WAND, STAR_WAND, ORB, FIRE_ORB, BOOK, POTION,
    LUTE, PANPIPES, HARP, LANTERN, LANTERN_STAFF, GEAR_STAFF, WRENCH, BLASTER, TELESCOPE, KEY
}
internal enum class OffHand { NONE, ROUND_SHIELD, LEAF_SHIELD, KITE_SHIELD, TOWER_SHIELD, DRUM, DAGGER }
internal enum class Back { NONE, QUIVER, PINWHEEL_PACK, CHIMNEY_PACK, LENS_PACK, SATCHEL, ICE_WINGS }
internal enum class Pet { NONE, LEAF_WISP, SPIRIT, STAR, GOLEM }

/** One hero's costume. Rarity adds its own layers on top (metal, gems, cape, glow), never here. */
internal class HeroLook(val build: Build, val hair: Hair, val head: Headgear, val weapon: Weapon,
    cloth: Int, light: Int, dark: Int, skin: Int, hairTone: Int, accent: Int,
    val off: OffHand = OffHand.NONE, val back: Back = Back.NONE, val pet: Pet = Pet.NONE,
    /** 0 none, 1 short, 2 full, 3 long. */
    val beard: Int = 0, val cape: Boolean = false, val scarf: Boolean = false) {
    val cloth = cloth or OPAQUE
    val light = light or OPAQUE
    val dark = dark or OPAQUE
    val skin = skin or OPAQUE
    val hairTone = hairTone or OPAQUE
    val accent = accent or OPAQUE
    private companion object { const val OPAQUE = 0xFF000000.toInt() }
}

/** Every hero has its own silhouette: hair, headgear, weapon and, for a few, a companion. */
internal object HamletHeroLooks {
    private val looks = arrayOf(
        // 0 Lina: gardener archer, braid and leaf clip.
        HeroLook(Build.SLIM, Hair.BRAID, Headgear.LEAF_CLIP, Weapon.BOW, 0x4D9264, 0xA6CB79, 0x306344, 0xF5BA8C, 0x814831, 0xEED18A, back = Back.QUIVER),
        // 1 Basile: carpenter, cloth cap, plank shield and mallet.
        HeroLook(Build.STOUT, Hair.SHORT, Headgear.CAP, Weapon.MALLET, 0x508AA6, 0xA3CDD0, 0x315D78, 0xEABA8E, 0x784D36, 0xD8AB64, off = OffHand.ROUND_SHIELD, beard = 1),
        // 2 Maël: hooded scout with a scarf over the mouth.
        HeroLook(Build.SLIM, Hair.HIDDEN, Headgear.HOOD, Weapon.DAGGER, 0x357C7C, 0x84BBB1, 0x234C62, 0xE2A77C, 0x283E43, 0xC0DBC2, scarf = true),
        // 3 Alba: herbalist with long white hair.
        HeroLook(Build.ROBED, Hair.LONG, Headgear.NONE, Weapon.LEAF_STAFF, 0x937CB0, 0xC7ADE0, 0x5D527F, 0xF4CBB5, 0xE6E5E1, 0xC9E5B5),
        // 4 Rose: weaver and singer with a lute.
        HeroLook(Build.SLIM, Hair.CURLS, Headgear.NONE, Weapon.LUTE, 0xC66F8C, 0xF0B1B0, 0x874E78, 0xB97954, 0x493440, 0xF4D48D),
        // 5 Orion: old astronomer, starry hat and star staff.
        HeroLook(Build.ROBED, Hair.LONG, Headgear.WIZARD_HAT, Weapon.STAR_STAFF, 0x445F9D, 0x8499CA, 0x323F76, 0xEEC4A3, 0xD7DFE6, 0xE8C96C, beard = 2),
        // 6 Gaïa: amber guardian, crested helm, spear and kite shield.
        HeroLook(Build.ARMORED, Hair.LONG, Headgear.CREST_HELM, Weapon.SPEAR, 0xB98648, 0xE7C27E, 0x79533A, 0xCB9166, 0x593F2E, 0x5FB8A2, off = OffHand.KITE_SHIELD),
        // 7 Cyran: ember duelist, spiky red hair.
        HeroLook(Build.SLIM, Hair.SPIKY, Headgear.NONE, Weapon.SWORD, 0xB95348, 0xEE9D68, 0x733E45, 0xF0B18A, 0xCB613D, 0xEDC26C, scarf = true),
        // 8 Solène: sun priestess with a radiant halo.
        HeroLook(Build.ROBED, Hair.LONG, Headgear.SUN_CROWN, Weapon.SUN_STAFF, 0xF2EAD6, 0xFFFFFF, 0xC7B188, 0xAC6C4A, 0x433332, 0xE5B755),
        // 9 Bran: antlered keeper of the woods and his leaf spirit.
        HeroLook(Build.STOUT, Hair.SHORT, Headgear.ANTLERS, Weapon.ROOT_STAFF, 0x788958, 0xC2BF76, 0x4C6149, 0xDEAE80, 0x70472F, 0xEAD7A0, pet = Pet.LEAF_WISP, beard = 1),
        // 10 Névé: frost mage, ice crown, crystal wings.
        HeroLook(Build.ROBED, Hair.LONG, Headgear.ICE_CROWN, Weapon.ICE_STAFF, 0x7EAEBE, 0xCEEAF0, 0x556C9F, 0xF1CDBF, 0xDCE9ED, 0xACDFDD, back = Back.ICE_WINGS),
        // 11 Niko: cheerful inventor, goggles, steam pack and blaster.
        HeroLook(Build.SLIM, Hair.MESSY, Headgear.GOGGLES, Weapon.BLASTER, 0xBB9254, 0xE5BD79, 0x76613F, 0xD49A72, 0x61493B, 0x7EC4C2, back = Back.CHIMNEY_PACK),
        // 12 Robin: orchard keeper, straw hat, pitchfork and leafy shield.
        HeroLook(Build.STOUT, Hair.SHORT, Headgear.STRAW_HAT, Weapon.PITCHFORK, 0x7A8F4A, 0xB8C978, 0x4E5E33, 0xF2BE96, 0xC2622D, 0xC94B3B, off = OffHand.LEAF_SHIELD),
        // 13 Aster: watchtower scout, feathered cap.
        HeroLook(Build.SLIM, Hair.BOB, Headgear.FEATHER_CAP, Weapon.BOW, 0x5E7FC0, 0xA9C1EE, 0x3A4F86, 0xF3C4A0, 0xE9C46A, 0xF4F0E6),
        // 14 Mira: travelling apothecary, headscarf, satchel and potion.
        HeroLook(Build.SLIM, Hair.HIDDEN, Headgear.HEADSCARF, Weapon.POTION, 0xB7744A, 0xE3AE7C, 0x7A4A35, 0xC98F68, 0x3E2A22, 0x4FA08C, back = Back.SATCHEL),
        // 15 Sylva: forest singer, flower crown and pan pipes.
        HeroLook(Build.ROBED, Hair.WAVY, Headgear.FLOWER_CROWN, Weapon.PANPIPES, 0x6FAF6A, 0xBFE39A, 0x3F7348, 0xF7CDB0, 0xD9C27A, 0xF28FA8),
        // 16 Lucien: gatekeeper, steel helm, shield and village lantern.
        HeroLook(Build.ARMORED, Hair.SHORT, Headgear.HELM, Weapon.LANTERN, 0x5C79B8, 0xB9CBEE, 0x34497D, 0xF2C6A4, 0xE8CF7A, 0xE8534A, off = OffHand.KITE_SHIELD),
        // 17 Silex: bald, bearded crossbow maker.
        HeroLook(Build.STOUT, Hair.BALD, Headgear.BANDANA, Weapon.CROSSBOW, 0x8A6A4E, 0xBE9A72, 0x5A4232, 0xD9A07A, 0x8E8A86, 0xC9483E, beard = 2),
        // 18 Saule: keeper of winter seeds, willow hair and snow wand.
        HeroLook(Build.ROBED, Hair.LONG, Headgear.CIRCLET, Weapon.SNOW_WAND, 0x4E9C93, 0xA9DDD2, 0x2D6461, 0xF5D2BC, 0x8FBF7A, 0xDDF2F5),
        // 19 Vesper: masked twilight messenger, twin daggers.
        HeroLook(Build.SLIM, Hair.PONYTAIL, Headgear.MASK, Weapon.DAGGER, 0x3E3C6E, 0x7472A8, 0x26254A, 0xE7B998, 0x4A2F5E, 0xE8A35A, off = OffHand.DAGGER),
        // 20 Bram: singing blacksmith, hammer and drum.
        HeroLook(Build.STOUT, Hair.BALD, Headgear.NONE, Weapon.HAMMER, 0xA44B3A, 0xD9805E, 0x6B2F2B, 0xB97954, 0x2E2524, 0x8A6A4E, off = OffHand.DRUM, beard = 1),
        // 21 Jonas: reed-hat weapon master, curved blade.
        HeroLook(Build.SLIM, Hair.SHORT, Headgear.REED_HAT, Weapon.CURVED_BLADE, 0x6E8B5A, 0xA9C48C, 0x465C3B, 0xD9A47E, 0x2D2A26, 0xD8C48A),
        // 22 Céleste: star gardener, star-pinned topknot and a little star.
        HeroLook(Build.ROBED, Hair.TOPKNOT, Headgear.STAR_CLIPS, Weapon.STAR_WAND, 0x3E4C8F, 0x8C9BDB, 0x262F63, 0xF4CDB8, 0xA9D4F0, 0xF6E27A, pet = Pet.STAR),
        // 23 Braise: discreet locksmith, red braid under the hood, heavy iron key.
        HeroLook(Build.SLIM, Hair.BRAID, Headgear.HOOD, Weapon.KEY, 0x4A3E3E, 0x7E6A66, 0x2D2425, 0xE0A882, 0xC4502E, 0xF08A3C, scarf = true),
        // 24 Noisette: mill builder, pigtails, wrench and pinwheel pack.
        HeroLook(Build.SLIM, Hair.PIGTAILS, Headgear.BANDANA, Weapon.WRENCH, 0xA47A4E, 0xD6AD7A, 0x6C4E34, 0xF2C29C, 0x8A5A32, 0x6FAE5E, back = Back.PINWHEEL_PACK),
        // 25 Aurel: market road defender, beret and rapier.
        HeroLook(Build.SLIM, Hair.SHORT, Headgear.BERET, Weapon.RAPIER, 0xE3DCCB, 0xFFFFFF, 0x9AA3B8, 0xF2C6A4, 0xE8C25A, 0x4E79C4, cape = true),
        // 26 Pyra: oven keeper, flame hair and fire orb.
        HeroLook(Build.ROBED, Hair.FLAME, Headgear.NONE, Weapon.FIRE_ORB, 0xA8323A, 0xE0675A, 0x6A1F2A, 0xF0BC94, 0xE8552E, 0xF6B23E),
        // 27 Iris: botanist, glasses and open herbarium.
        HeroLook(Build.ROBED, Hair.BOB, Headgear.GLASSES, Weapon.BOOK, 0x8FA77C, 0xC7D9AE, 0x5A6E4E, 0xF3CBB0, 0x7A4E9E, 0xD9A3E8),
        // 28 Lyra: observatory inventor, lens goggles and telescope.
        HeroLook(Build.SLIM, Hair.PONYTAIL, Headgear.LENS_GOGGLES, Weapon.TELESCOPE, 0x7FB2CF, 0xC6E3F0, 0x4C7896, 0xF1C9A8, 0x3C4E66, 0xC9A15A, back = Back.LENS_PACK),
        // 29 Ferris: horned forge warden and his little iron golem.
        HeroLook(Build.ARMORED, Hair.HIDDEN, Headgear.HORNED_HELM, Weapon.GEAR_STAFF, 0x7A5450, 0xB48478, 0x4A3436, 0xE2A77C, 0x3A2A26, 0xE07A3A, pet = Pet.GOLEM),
        // 30 Ronce: keeper of the oldest path, leaf hood and great bow.
        HeroLook(Build.SLIM, Hair.HIDDEN, Headgear.LEAF_HOOD, Weapon.LONGBOW, 0x2F6B45, 0x6FAE6A, 0x1D4430, 0xE8B48C, 0x5A3A26, 0xC2384B, back = Back.QUIVER, cape = true),
        // 31 Aurore: dawn choir singer, tiara and harp.
        HeroLook(Build.ROBED, Hair.WAVY, Headgear.TIARA, Weapon.HARP, 0xF09A86, 0xFFD2B8, 0xB0606A, 0xF5CBB0, 0xF2B36B, 0xFFF0C8),
        // 32 Atlas: living stone of the ramparts, great helm, maul, tower shield.
        HeroLook(Build.ARMORED, Hair.HIDDEN, Headgear.GREAT_HELM, Weapon.MAUL, 0x8C8378, 0xC2B8A8, 0x5A544E, 0xA8A29A, 0x5A544E, 0xC0703E, off = OffHand.TOWER_SHIELD),
        // 33 Ancien: old spirit shepherd, mossy hood, long beard, lantern crook.
        HeroLook(Build.ROBED, Hair.LONG, Headgear.HOOD, Weapon.LANTERN_STAFF, 0x5E7A4A, 0x9DB57A, 0x3A4E30, 0xE0B48E, 0xEDEDE6, 0x9FF0B8, pet = Pet.SPIRIT, beard = 3),
        // 34 Nova: young astronomer holding a star in winter glass.
        HeroLook(Build.ROBED, Hair.LONG, Headgear.STAR_CLIPS, Weapon.ORB, 0x2F3566, 0x6E78B8, 0x1C2045, 0xF2D0BE, 0x3B4C9A, 0xBFD8FF),
        // 35 Vulcain: master smith in dark armour, horned crown, burning blade.
        HeroLook(Build.ARMORED, Hair.HIDDEN, Headgear.HORNED_CROWN, Weapon.FLAME_SWORD, 0x3A3236, 0x6A5E62, 0x211C20, 0xD49A72, 0x2A2022, 0xFF7A2A, cape = true),
    )
    val size get() = looks.size
    operator fun get(id: Int) = looks[id]
}
