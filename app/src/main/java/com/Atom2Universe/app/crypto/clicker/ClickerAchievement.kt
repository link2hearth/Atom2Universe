package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber

data class ClickerAchievement(
    val id: String,
    val nameRes: Int,
    val flavorRes: Int,
    val targetText: String,
    val target: LayeredNumber
)

object ClickerAchievements {
    val all: List<ClickerAchievement> by lazy {
        listOf(
            ClickerAchievement("scaleHumanCell",         R.string.achievement_human_cell_name,    R.string.achievement_human_cell_flavor,    "10^14",   LayeredNumber.fromLayer0(1.0, 14.0)),
            ClickerAchievement("scaleSandGrain",         R.string.achievement_sand_grain_name,    R.string.achievement_sand_grain_flavor,    "10^19",   LayeredNumber.fromLayer0(1.0, 19.0)),
            ClickerAchievement("scaleAnt",               R.string.achievement_ant_name,           R.string.achievement_ant_flavor,           "10^20",   LayeredNumber.fromLayer0(1.0, 20.0)),
            ClickerAchievement("scaleWaterDrop",         R.string.achievement_water_drop_name,    R.string.achievement_water_drop_flavor,    "5×10^21", LayeredNumber.fromLayer0(5.0, 21.0)),
            ClickerAchievement("scalePaperclip",         R.string.achievement_paperclip_name,     R.string.achievement_paperclip_flavor,     "10^22",   LayeredNumber.fromLayer0(1.0, 22.0)),
            ClickerAchievement("scaleCoin",              R.string.achievement_coin_name,          R.string.achievement_coin_flavor,          "10^23",   LayeredNumber.fromLayer0(1.0, 23.0)),
            ClickerAchievement("scaleApple",             R.string.achievement_apple_name,         R.string.achievement_apple_flavor,         "10^25",   LayeredNumber.fromLayer0(1.0, 25.0)),
            ClickerAchievement("scaleSmartphone",        R.string.achievement_smartphone_name,    R.string.achievement_smartphone_flavor,    "3×10^25", LayeredNumber.fromLayer0(3.0, 25.0)),
            ClickerAchievement("scaleWaterLitre",        R.string.achievement_water_litre_name,   R.string.achievement_water_litre_flavor,   "10^26",   LayeredNumber.fromLayer0(1.0, 26.0)),
            ClickerAchievement("scaleHuman",             R.string.achievement_human_name,         R.string.achievement_human_flavor,         "7×10^27", LayeredNumber.fromLayer0(7.0, 27.0)),
            ClickerAchievement("scalePiano",             R.string.achievement_piano_name,         R.string.achievement_piano_flavor,         "10^29",   LayeredNumber.fromLayer0(1.0, 29.0)),
            ClickerAchievement("scaleCar",               R.string.achievement_car_name,           R.string.achievement_car_flavor,           "10^30",   LayeredNumber.fromLayer0(1.0, 30.0)),
            ClickerAchievement("scaleElephant",          R.string.achievement_elephant_name,      R.string.achievement_elephant_flavor,      "3×10^31", LayeredNumber.fromLayer0(3.0, 31.0)),
            ClickerAchievement("scaleBoeing747",         R.string.achievement_boeing_name,        R.string.achievement_boeing_flavor,        "10^33",   LayeredNumber.fromLayer0(1.0, 33.0)),
            ClickerAchievement("scalePyramid",           R.string.achievement_pyramid_name,       R.string.achievement_pyramid_flavor,       "2×10^35", LayeredNumber.fromLayer0(2.0, 35.0)),
            ClickerAchievement("scaleAtmosphere",        R.string.achievement_atmosphere_name,    R.string.achievement_atmosphere_flavor,    "2×10^44", LayeredNumber.fromLayer0(2.0, 44.0)),
            ClickerAchievement("scaleOceans",            R.string.achievement_oceans_name,        R.string.achievement_oceans_flavor,        "10^47",   LayeredNumber.fromLayer0(1.0, 47.0)),
            ClickerAchievement("scaleEarth",             R.string.achievement_earth_name,         R.string.achievement_earth_flavor,         "10^50",   LayeredNumber.fromLayer0(1.0, 50.0)),
            ClickerAchievement("scaleSun",               R.string.achievement_sun_name,           R.string.achievement_sun_flavor,           "10^57",   LayeredNumber.fromLayer0(1.0, 57.0)),
            ClickerAchievement("scaleMilkyWay",          R.string.achievement_milky_way_name,     R.string.achievement_milky_way_flavor,     "10^69",   LayeredNumber.fromLayer0(1.0, 69.0)),
            ClickerAchievement("scaleLocalGroup",        R.string.achievement_local_group_name,   R.string.achievement_local_group_flavor,   "10^71",   LayeredNumber.fromLayer0(1.0, 71.0)),
            ClickerAchievement("scaleVirgoCluster",      R.string.achievement_virgo_name,         R.string.achievement_virgo_flavor,         "10^74",   LayeredNumber.fromLayer0(1.0, 74.0)),
            ClickerAchievement("scaleObservableUniverse",R.string.achievement_universe_name,      R.string.achievement_universe_flavor,      "10^80",   LayeredNumber.fromLayer0(1.0, 80.0))
        )
    }
}
