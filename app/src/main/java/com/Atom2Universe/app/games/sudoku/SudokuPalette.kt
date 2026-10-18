package com.Atom2Universe.app.games.sudoku

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R

/** Shared by the full grid and the clicker's compact digit row. */
internal object SudokuPalette {
    fun color(context: Context, resource: Int): Int {
        if (!AppearanceStyle.isLight(context)) return ContextCompat.getColor(context, resource)
        val accent = AppearanceStyle.color(context, R.attr.a2uMusicAccent)
        return when (resource) {
            R.color.sudoku_cell_background, R.color.sudoku_cell_fixed_background -> Color.WHITE
            R.color.sudoku_border_thin, R.color.sudoku_border_thick -> Color.BLACK
            R.color.sudoku_cell_selected_background -> ColorUtils.blendARGB(Color.WHITE, accent, 0.20f)
            R.color.sudoku_cell_related_background -> ColorUtils.blendARGB(Color.WHITE, accent, 0.05f)
            R.color.sudoku_cell_same_value_background -> ColorUtils.blendARGB(Color.WHITE, accent, 0.12f)
            R.color.sudoku_cell_error_background -> 0xFFFFE5E5.toInt()
            R.color.sudoku_cell_conflict_background -> 0xFFFFEDD5.toInt()
            else -> ContextCompat.getColor(context, resource)
        }
    }
}
