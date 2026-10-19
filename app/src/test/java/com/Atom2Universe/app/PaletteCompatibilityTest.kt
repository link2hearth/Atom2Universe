package com.Atom2Universe.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.appcompat.view.ContextThemeWrapper
import com.Atom2Universe.app.audio.AudioStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 31, 34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaletteCompatibilityTest {
    @Test
    fun surfacesAndAccentFollowSelectionAfterLiveChanges() {
        val base = RuntimeEnvironment.getApplication()
        val context = ContextThemeWrapper(base, R.style.Theme_A2U_Classic)
        for (brightness in AppBrightness.entries) {
            for (preset in listOf(AppTheme.GREEN, AppTheme.ORANGE, AppTheme.BLUE)) {
                AppThemeManager.setSelection(context, AppAppearance.ROUNDED, brightness,
                    false, AppThemeManager.getEffects(context), preset,
                    AppThemeManager.Spectrum(0, 0))
                AppThemeManager.refreshContextTheme(context)
                assertEquals("background: $brightness/$preset", token(context, R.attr.a2uBackgroundColor),
                    AudioStyle.background(context))
                assertEquals("surface: $brightness/$preset", token(context, R.attr.a2uSurfaceColor),
                    AudioStyle.surface(context))
                assertEquals("text: $brightness/$preset", token(context, R.attr.a2uTextColor),
                    AudioStyle.primaryText(context))
                assertEquals("accent: $brightness/$preset", token(context, R.attr.a2uMusicAccent),
                    token(context, androidx.appcompat.R.attr.colorPrimary))
                val background = AppearanceStyle.screen(context)!!
                val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                background.setBounds(0, 0, 100, 100)
                background.draw(Canvas(bitmap))
                assertEquals("screen drawable: $brightness/$preset",
                    token(context, R.attr.a2uBackgroundColor), bitmap.getPixel(50, 99))
                bitmap.recycle()
                for (resource in listOf(R.drawable.audio_panel_background,
                    R.drawable.library_project_background, R.drawable.all_tracks_tile_bg,
                    R.drawable.all_tracks_icon_bg)) {
                    val panel = context.getDrawable(resource)!!
                    val panelBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                    panel.setBounds(0, 0, 100, 100)
                    panel.draw(Canvas(panelBitmap))
                    assertEquals("panel $resource: $brightness/$preset",
                        token(context, R.attr.a2uSurfaceColor), panelBitmap.getPixel(50, 50))
                    panelBitmap.recycle()
                }
            }
        }
    }

    private fun token(context: Context, attr: Int) = AppearanceStyle.color(context, attr)
}
