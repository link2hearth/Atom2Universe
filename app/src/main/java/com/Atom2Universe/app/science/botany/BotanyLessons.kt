package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

data class BotanyLesson(val id: String, val title: Int, val body: Int, val specimen: String, val view: PlantView)

object BotanyLessons {
    val all = listOf(
        BotanyLesson("habit", R.string.bot_lesson_habit, R.string.bot_lesson_habit_body, "campestre", PlantView.HABIT),
        BotanyLesson("simple", R.string.bot_lesson_simple, R.string.bot_lesson_simple_body, "aesculus", PlantView.LEAF),
        BotanyLesson("arrangement", R.string.bot_lesson_arrangement, R.string.bot_lesson_arrangement_body, "cornus", PlantView.LEAF),
        BotanyLesson("margins", R.string.bot_lesson_margins, R.string.bot_lesson_margins_body, "carpinus", PlantView.LEAF),
        BotanyLesson("head", R.string.bot_lesson_head, R.string.bot_lesson_head_body, "leucanthemum", PlantView.FLOWER),
        BotanyLesson("umbel", R.string.bot_lesson_umbel, R.string.bot_lesson_umbel_body, "daucus", PlantView.FLOWER),
        BotanyLesson("pea", R.string.bot_lesson_pea, R.string.bot_lesson_pea_body, "cytisus", PlantView.FLOWER),
        BotanyLesson("fruit", R.string.bot_lesson_fruit, R.string.bot_lesson_fruit_body, "aesculus", PlantView.FRUIT),
        BotanyLesson("cones", R.string.bot_lesson_cones, R.string.bot_lesson_cones_body, "abies", PlantView.FRUIT),
        BotanyLesson("grasses", R.string.bot_lesson_grasses, R.string.bot_lesson_grasses_body, "briza", PlantView.FLOWER),
        BotanyLesson("seasons", R.string.bot_lesson_seasons, R.string.bot_lesson_seasons_body, "corylus", PlantView.FLOWER),
        BotanyLesson("names", R.string.bot_lesson_names, R.string.bot_lesson_names_body, "ficaria", PlantView.HABIT),
    )
}
