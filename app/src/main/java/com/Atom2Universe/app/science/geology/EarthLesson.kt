package com.Atom2Universe.app.science.geology

import androidx.annotation.StringRes
import androidx.annotation.ArrayRes
import com.Atom2Universe.app.R

/** A short reading route, always tied to the diagram currently on screen. */
data class EarthLesson(
    @param:StringRes val summary: Int,
    @param:StringRes val observation: Int,
    @param:StringRes val question: Int,
    @param:StringRes val answer: Int,
    @param:ArrayRes val steps: Int = 0
)

val EarthScene.lesson: EarthLesson
    get() = when (this) {
        EarthScene.GLOBE -> EarthLesson(R.string.geo_globe_summary, R.string.geo_globe_observe, R.string.geo_globe_question, R.string.geo_globe_answer)
        EarthScene.SHELL -> EarthLesson(R.string.geo_shell_summary, R.string.geo_shell_observe, R.string.geo_shell_question, R.string.geo_shell_answer)
        EarthScene.RIDGE -> EarthLesson(R.string.geo_ridge_summary, R.string.geo_ridge_observe, R.string.geo_ridge_question, R.string.geo_ridge_answer)
        EarthScene.SUBDUCTION -> EarthLesson(R.string.geo_subduction_summary, R.string.geo_subduction_observe, R.string.geo_subduction_question, R.string.geo_subduction_answer)
        EarthScene.COLLISION -> EarthLesson(R.string.geo_collision_summary, R.string.geo_collision_observe, R.string.geo_collision_question, R.string.geo_collision_answer, R.array.geo_collision_steps)
        EarthScene.TRANSFORM -> EarthLesson(R.string.geo_transform_summary, R.string.geo_transform_observe, R.string.geo_transform_question, R.string.geo_transform_answer, R.array.geo_transform_steps)
        EarthScene.HOTSPOT -> EarthLesson(R.string.geo_hotspot_summary, R.string.geo_hotspot_observe, R.string.geo_hotspot_question, R.string.geo_hotspot_answer)
        EarthScene.FOLDS -> EarthLesson(R.string.geo_folds_summary, R.string.geo_folds_observe, R.string.geo_folds_question, R.string.geo_folds_answer, R.array.geo_collision_steps)
        EarthScene.FAULTS -> EarthLesson(R.string.geo_faults_summary, R.string.geo_faults_observe, R.string.geo_faults_question, R.string.geo_faults_answer, R.array.geo_faults_steps)
        EarthScene.LANDSCAPE -> EarthLesson(R.string.geo_landscape_summary, R.string.geo_landscape_observe, R.string.geo_landscape_question, R.string.geo_landscape_answer, R.array.geo_landscape_steps)
        EarthScene.STRATA -> EarthLesson(R.string.geo_strata_summary, R.string.geo_strata_observe, R.string.geo_strata_question, R.string.geo_strata_answer, R.array.geo_strata_steps)
        EarthScene.ROCK_CYCLE -> EarthLesson(R.string.geo_cycle_summary, R.string.geo_cycle_observe, R.string.geo_cycle_question, R.string.geo_cycle_answer)
        EarthScene.ARCHIVE -> EarthLesson(R.string.geo_archive_summary, R.string.geo_archive_observe, R.string.geo_archive_question, R.string.geo_archive_answer)
        EarthScene.AGES -> EarthLesson(R.string.geo_time_intro, R.string.geo_date_key, R.string.geo_archive_question, R.string.geo_archive_answer)
    }

val EarthChapter.color: Int
    get() = when (this) {
        EarthChapter.INTERIOR -> 0xffe8a36f.toInt()
        EarthChapter.PLATES -> 0xff61c6c6.toInt()
        EarthChapter.MOUNTAINS -> 0xffa5c58b.toInt()
        EarthChapter.ROCKS -> 0xffc7abdf.toInt()
        EarthChapter.TIME -> 0xffe7c878.toInt()
    }
