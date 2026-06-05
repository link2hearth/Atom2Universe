package com.Atom2Universe.app.crypto

import android.content.Context
import android.net.Uri
import kotlin.math.roundToInt
import androidx.core.content.edit

object MainClickerPreferences {

    private const val PREFS_NAME = "crypto_background_prefs"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_INTERVAL = "interval"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_WIDGET_OPACITY_PERCENT = "widget_opacity_percent"
    private const val KEY_SHOW_STATUS_BAR = "show_status_bar"
    private const val KEY_SHOW_NAV_BAR = "show_nav_bar"
    private const val KEY_REFRESH_INTERVAL = "refresh_interval"
    private const val KEY_CRYPTO_WIDGET_ENABLED = "crypto_widget_enabled"
    private const val KEY_EARTH_WIDGET_ENABLED = "earth_widget_enabled"
    private const val KEY_CHESS_WIDGET_ENABLED = "chess_widget_enabled"
    private const val KEY_DRAUGHTS_WIDGET_ENABLED = "draughts_widget_enabled"
    private const val KEY_DRAUGHTS_WIDGET_OPACITY_PERCENT = "draughts_widget_opacity_percent"
    private const val KEY_BANNER_TOGGLE_DRAUGHTS_VISIBLE = "banner_toggle_draughts_visible"
    private const val KEY_SUDOKU_WIDGET_ENABLED = "sudoku_widget_enabled"
    private const val KEY_EARTH_WIDGET_OPACITY_PERCENT = "earth_widget_opacity_percent"
    private const val KEY_CHESS_WIDGET_OPACITY_PERCENT = "chess_widget_opacity_percent"
    private const val KEY_SUDOKU_WIDGET_OPACITY_PERCENT = "sudoku_widget_opacity_percent"
    private const val KEY_SUDOKU_NUMBERS_OPACITY_PERCENT = "sudoku_numbers_opacity_percent"
    private const val KEY_GAME2048_WIDGET_ENABLED = "game2048_widget_enabled"
    private const val KEY_GAME2048_WIDGET_OPACITY_PERCENT = "game2048_widget_opacity_percent"
    private const val KEY_NEWS_WIDGET_ENABLED = "news_widget_enabled"
    private const val KEY_NEWS_WIDGET_OPACITY_PERCENT = "news_widget_opacity_percent"
    private const val KEY_EARTH_ONLY_MODE = "earth_only_mode"
    private const val KEY_EARTH_SHOW_CLOUDS = "earth_show_clouds"
    private const val KEY_EARTH_SHOW_TERMINATOR = "earth_show_terminator"
    private const val KEY_EARTH_FIXED_LOCATION_INDEX = "earth_fixed_location_index"
    private const val KEY_BANNER_TOGGLE_EARTH_VISIBLE = "banner_toggle_earth_visible"
    private const val KEY_BANNER_TOGGLE_CHESS_VISIBLE = "banner_toggle_chess_visible"
    private const val KEY_BANNER_TOGGLE_SUDOKU_VISIBLE = "banner_toggle_sudoku_visible"
    private const val KEY_BANNER_TOGGLE_2048_VISIBLE = "banner_toggle_2048_visible"
    private const val KEY_BANNER_TOGGLE_CRYPTO_VISIBLE = "banner_toggle_crypto_visible"
    private const val KEY_BANNER_TOGGLE_NEWS_VISIBLE = "banner_toggle_news_visible"
    private const val KEY_SOLITAIRE_WIDGET_ENABLED = "solitaire_widget_enabled"
    private const val KEY_SOLITAIRE_WIDGET_OPACITY_PERCENT = "solitaire_widget_opacity_percent"
    private const val KEY_BANNER_TOGGLE_SOLITAIRE_VISIBLE = "banner_toggle_solitaire_visible"
    private const val KEY_COLOR_STACK_WIDGET_ENABLED = "color_stack_widget_enabled"
    private const val KEY_COLOR_STACK_WIDGET_OPACITY_PERCENT = "color_stack_widget_opacity_percent"
    private const val KEY_BANNER_TOGGLE_COLOR_STACK_VISIBLE = "banner_toggle_color_stack_visible"
    private const val KEY_IMAGE_CENTER_ALIGNED = "image_center_aligned"
    private const val KEY_CRYPTO_WIDGET_MINIMAL = "crypto_widget_minimal"
    private const val KEY_CRYPTO_WIDGET_EUR = "crypto_widget_eur"
    private const val KEY_CRYPTO_EURUSD_ENABLED = "crypto_eurusd_enabled"
    private const val KEY_CRYPTO_COMPARISON_MINUTES = "crypto_comparison_minutes"
    private const val KEY_CLICKER_ENABLED = "clicker_enabled"
    private const val KEY_SHOP_PLUS_PREFIX = "shop_plus_enabled_"
    private const val KEY_CLICKER_OPACITY_PERCENT = "clicker_opacity_percent"
    private const val KEY_CLICKER_HEIGHT_DP = "clicker_height_dp"
    private const val KEY_CLICKER_DECIMAL_DIGITS = "clicker_decimal_digits"
    private const val KEY_CLICKER_ALPHA_FORMAT = "clicker_alpha_format"
    private const val KEY_ATOM_SPRING_ENABLED = "atom_spring_enabled"
    private const val KEY_ATOM_SPRING_INDEX = "atom_spring_index"
    private const val KEY_ATOM_LOW_ANIMATION = "atom_low_animation"
    private const val KEY_ATOM_BIGGER_IMAGE = "atom_bigger_image"
    private const val KEY_CUSTOM_ATOM_FOLDER_URI = "atom_custom_folder_uri"
    private const val KEY_BANNER_TOGGLE_CLICKER_VISIBLE = "banner_toggle_clicker_visible"
    private const val KEY_BACKGROUND_DISPLAY_ENABLED = "background_display_enabled"
    private const val KEY_BLACKJACK_WIDGET_ENABLED = "blackjack_widget_enabled"
    private const val KEY_BLACKJACK_WIDGET_OPACITY_PERCENT = "blackjack_widget_opacity_percent"
    private const val KEY_BANNER_TOGGLE_BLACKJACK_VISIBLE = "banner_toggle_blackjack_visible"
    private const val KEY_MUSIC_WIDGET_ENABLED = "music_widget_enabled"
    private const val KEY_MUSIC_WIDGET_OPACITY_PERCENT = "music_widget_opacity_percent"
    private const val KEY_BANNER_TOGGLE_MUSIC_VISIBLE = "banner_toggle_music_visible"
    private const val KEY_PIPETAP_WIDGET_ENABLED = "pipetap_widget_enabled"
    private const val KEY_PIPETAP_WIDGET_OPACITY_PERCENT = "pipetap_widget_opacity_percent"
    private const val KEY_BANNER_TOGGLE_PIPETAP_VISIBLE = "banner_toggle_pipetap_visible"
    private const val KEY_THE_LINE_WIDGET_ENABLED = "the_line_widget_enabled"
    private const val KEY_THE_LINE_WIDGET_OPACITY_PERCENT = "the_line_widget_opacity_percent"
    private const val KEY_BANNER_TOGGLE_THE_LINE_VISIBLE = "banner_toggle_the_line_visible"
    private const val KEY_BANNER_TOGGLE_ORDER = "banner_toggle_order"
    private const val KEY_FAVORITES_MODE = "favorites_mode"

    val ALL_WIDGET_KEYS = listOf(
        "clicker", "earth", "chess", "draughts", "sudoku", "2048", "crypto",
        "news", "solitaire", "blackjack", "color_stack", "music", "pipetap", "the_line"
    )

    private const val DEFAULT_WIDGET_OPACITY_PERCENT = 100

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFolderUri(context: Context): Uri? {
        val uriString = prefs(context).getString(KEY_FOLDER_URI, null)
        return uriString?.let(Uri::parse)
    }

    fun setFolderUri(context: Context, uri: Uri?) {
        prefs(context).edit { putString(KEY_FOLDER_URI, uri?.toString()) }
    }

    fun getInterval(context: Context): MainClickerBackgroundInterval {
        val stored = prefs(context).getString(KEY_INTERVAL, null)
        return MainClickerBackgroundInterval.fromPreference(stored)
    }

    fun setInterval(context: Context, interval: MainClickerBackgroundInterval) {
        prefs(context).edit { putString(KEY_INTERVAL, interval.preferenceValue) }
    }

    fun isClickerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLICKER_ENABLED, true)

    fun setClickerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_CLICKER_ENABLED, enabled) }
    }

    /**
     * Indique si l'amélioration [upgradeId] contribue au badge "+" du bouton boutique
     * (le "$+" s'allume quand on a assez d'atomes pour l'acheter). Activé par défaut.
     */
    fun isShopPlusEnabled(context: Context, upgradeId: String): Boolean =
        prefs(context).getBoolean(KEY_SHOP_PLUS_PREFIX + upgradeId, true)

    fun setShopPlusEnabled(context: Context, upgradeId: String, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOP_PLUS_PREFIX + upgradeId, enabled) }
    }

    fun getClickerOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_CLICKER_OPACITY_PERCENT, 100)

    fun setClickerOpacityPercent(context: Context, percent: Int) {
        prefs(context).edit { putInt(KEY_CLICKER_OPACITY_PERCENT, percent) }
    }

    fun getClickerHeightDp(context: Context): Int =
        prefs(context).getInt(KEY_CLICKER_HEIGHT_DP, 56)

    fun setClickerHeightDp(context: Context, dp: Int) {
        prefs(context).edit { putInt(KEY_CLICKER_HEIGHT_DP, dp) }
    }

    fun getClickerDecimalDigits(context: Context): Int =
        prefs(context).getInt(KEY_CLICKER_DECIMAL_DIGITS, 2)

    fun setClickerDecimalDigits(context: Context, digits: Int) {
        prefs(context).edit { putInt(KEY_CLICKER_DECIMAL_DIGITS, digits) }
    }

    fun isClickerAlphaFormat(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLICKER_ALPHA_FORMAT, false)

    fun setClickerAlphaFormat(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_CLICKER_ALPHA_FORMAT, enabled) }
    }

    fun isAtomSpringEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ATOM_SPRING_ENABLED, true)

    fun setAtomSpringEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ATOM_SPRING_ENABLED, enabled) }
    }

    fun getAtomSpringIndex(context: Context): Int =
        prefs(context).getInt(KEY_ATOM_SPRING_INDEX, 0)

    fun setAtomSpringIndex(context: Context, index: Int) {
        prefs(context).edit { putInt(KEY_ATOM_SPRING_INDEX, index) }
    }

    fun isAtomLowAnimation(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ATOM_LOW_ANIMATION, false)

    fun setAtomLowAnimation(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ATOM_LOW_ANIMATION, enabled) }
    }

    fun isAtomBiggerImage(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ATOM_BIGGER_IMAGE, false)

    fun setAtomBiggerImage(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ATOM_BIGGER_IMAGE, enabled) }
    }

    fun getCustomAtomFolderUri(context: Context): Uri? {
        val s = prefs(context).getString(KEY_CUSTOM_ATOM_FOLDER_URI, null)
        return s?.let(Uri::parse)
    }

    fun setCustomAtomFolderUri(context: Context, uri: Uri?) {
        prefs(context).edit { putString(KEY_CUSTOM_ATOM_FOLDER_URI, uri?.toString()) }
    }

    fun isKeepScreenOnEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, false)
    }

    fun setKeepScreenOnEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }
    }

    fun getWidgetOpacityPercent(context: Context): Int {
        val stored = prefs(context).getInt(KEY_WIDGET_OPACITY_PERCENT, DEFAULT_WIDGET_OPACITY_PERCENT)
        return stored.coerceIn(0, 100)
    }

    fun setWidgetOpacityPercent(context: Context, opacityPercent: Int) {
        val clamped = opacityPercent.coerceIn(0, 100)
        prefs(context).edit { putInt(KEY_WIDGET_OPACITY_PERCENT, clamped) }
    }

    fun setWidgetOpacityPercent(context: Context, opacityPercent: Float) {
        setWidgetOpacityPercent(context, opacityPercent.roundToInt())
    }

    fun isStatusBarVisible(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_STATUS_BAR, false)
    }

    fun setStatusBarVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_STATUS_BAR, visible) }
    }

    fun isNavBarVisible(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHOW_NAV_BAR, false)
    }

    fun setNavBarVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_NAV_BAR, visible) }
    }

    fun getRefreshInterval(context: Context): MainClickerRefreshInterval {
        val stored = prefs(context).getString(KEY_REFRESH_INTERVAL, null)
        return MainClickerRefreshInterval.fromPreference(stored)
    }

    fun setRefreshInterval(context: Context, interval: MainClickerRefreshInterval) {
        prefs(context).edit { putString(KEY_REFRESH_INTERVAL, interval.preferenceValue) }
    }

    fun isCryptoWidgetEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_CRYPTO_WIDGET_ENABLED, false)
    }

    fun setCryptoWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_CRYPTO_WIDGET_ENABLED, enabled) }
    }

    fun isEarthWidgetEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EARTH_WIDGET_ENABLED, false)
    }

    fun setEarthWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_EARTH_WIDGET_ENABLED, enabled) }
    }

    fun isChessWidgetEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_CHESS_WIDGET_ENABLED, false)
    }

    fun setChessWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_CHESS_WIDGET_ENABLED, enabled) }
    }

    fun isDraughtsWidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DRAUGHTS_WIDGET_ENABLED, false)

    fun setDraughtsWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_DRAUGHTS_WIDGET_ENABLED, enabled) }
    }

    fun getDraughtsWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_DRAUGHTS_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setDraughtsWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_DRAUGHTS_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isBannerToggleDraughtsVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_DRAUGHTS_VISIBLE, true)

    fun setBannerToggleDraughtsVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_DRAUGHTS_VISIBLE, visible) }
    }

    fun isSudokuWidgetEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SUDOKU_WIDGET_ENABLED, false)
    }

    fun setSudokuWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_SUDOKU_WIDGET_ENABLED, enabled) }
    }

    fun getEarthWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_EARTH_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setEarthWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_EARTH_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun getChessWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_CHESS_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setChessWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_CHESS_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun getSudokuWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_SUDOKU_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setSudokuWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_SUDOKU_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun getSudokuNumbersOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_SUDOKU_NUMBERS_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setSudokuNumbersOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_SUDOKU_NUMBERS_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isGame2048WidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GAME2048_WIDGET_ENABLED, false)

    fun setGame2048WidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_GAME2048_WIDGET_ENABLED, enabled) }
    }

    fun isNewsWidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NEWS_WIDGET_ENABLED, false)

    fun setNewsWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_NEWS_WIDGET_ENABLED, enabled) }
    }

    fun getNewsWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_NEWS_WIDGET_OPACITY_PERCENT, DEFAULT_WIDGET_OPACITY_PERCENT)

    fun setNewsWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_NEWS_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }


    fun getGame2048WidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_GAME2048_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setGame2048WidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_GAME2048_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isEarthOnlyMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EARTH_ONLY_MODE, false)
    }

    fun setEarthOnlyMode(context: Context, earthOnly: Boolean) {
        prefs(context).edit { putBoolean(KEY_EARTH_ONLY_MODE, earthOnly) }
    }

    fun isEarthShowClouds(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EARTH_SHOW_CLOUDS, true)
    }

    fun setEarthShowClouds(context: Context, showClouds: Boolean) {
        prefs(context).edit { putBoolean(KEY_EARTH_SHOW_CLOUDS, showClouds) }
    }

    fun isEarthShowTerminator(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EARTH_SHOW_TERMINATOR, true)
    }

    fun setEarthShowTerminator(context: Context, show: Boolean) {
        prefs(context).edit { putBoolean(KEY_EARTH_SHOW_TERMINATOR, show) }
    }

    fun getEarthFixedLocationIndex(context: Context): Int {
        val max = EarthMoonCanvasView.LOCATION_PRESETS.size - 1
        return prefs(context).getInt(KEY_EARTH_FIXED_LOCATION_INDEX, 0).coerceIn(0, max)
    }

    fun setEarthFixedLocationIndex(context: Context, index: Int) {
        prefs(context).edit { putInt(KEY_EARTH_FIXED_LOCATION_INDEX, index) }
    }

    fun isImageCenterAligned(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IMAGE_CENTER_ALIGNED, false)

    fun setImageCenterAligned(context: Context, center: Boolean) {
        prefs(context).edit { putBoolean(KEY_IMAGE_CENTER_ALIGNED, center) }
    }

    fun getSlideshowMinutes(context: Context): Int {
        val stored = prefs(context).getString(KEY_INTERVAL, null)
        if (stored == null) return 1
        if (stored == "off") return 0
        return stored.toIntOrNull()?.coerceIn(0, 60) ?: 1
    }

    fun setSlideshowMinutes(context: Context, minutes: Int) {
        val value = if (minutes == 0) "off" else minutes.toString()
        prefs(context).edit { putString(KEY_INTERVAL, value) }
    }

    fun isBannerToggleEarthVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_EARTH_VISIBLE, true)

    fun setBannerToggleEarthVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_EARTH_VISIBLE, visible) }
    }

    fun isBannerToggleChessVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_CHESS_VISIBLE, true)

    fun setBannerToggleChessVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_CHESS_VISIBLE, visible) }
    }

    fun isBannerToggleSudokuVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_SUDOKU_VISIBLE, true)

    fun setBannerToggleSudokuVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_SUDOKU_VISIBLE, visible) }
    }

    fun isBannerToggle2048Visible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_2048_VISIBLE, true)

    fun setBannerToggle2048Visible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_2048_VISIBLE, visible) }
    }

    fun isBannerToggleCryptoVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_CRYPTO_VISIBLE, true)

    fun setBannerToggleCryptoVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_CRYPTO_VISIBLE, visible) }
    }

    fun isBannerToggleNewsVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_NEWS_VISIBLE, true)

    fun setBannerToggleNewsVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_NEWS_VISIBLE, visible) }
    }

    fun isSolitaireWidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOLITAIRE_WIDGET_ENABLED, false)

    fun setSolitaireWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_SOLITAIRE_WIDGET_ENABLED, enabled) }
    }

    fun getSolitaireWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_SOLITAIRE_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setSolitaireWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_SOLITAIRE_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isBannerToggleSolitaireVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_SOLITAIRE_VISIBLE, true)

    fun setBannerToggleSolitaireVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_SOLITAIRE_VISIBLE, visible) }
    }

    fun isCryptoWidgetMinimal(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CRYPTO_WIDGET_MINIMAL, false)

    fun setCryptoWidgetMinimal(context: Context, minimal: Boolean) {
        prefs(context).edit { putBoolean(KEY_CRYPTO_WIDGET_MINIMAL, minimal) }
    }

    fun isCryptoWidgetEur(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CRYPTO_WIDGET_EUR, false)

    fun setCryptoWidgetEur(context: Context, eur: Boolean) {
        prefs(context).edit { putBoolean(KEY_CRYPTO_WIDGET_EUR, eur) }
    }

    fun isCryptoEurUsdEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CRYPTO_EURUSD_ENABLED, false)

    fun setCryptoEurUsdEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_CRYPTO_EURUSD_ENABLED, enabled) }
    }

    fun getCryptoComparisonWindowIndex(context: Context): Int =
        prefs(context).getInt(KEY_CRYPTO_COMPARISON_MINUTES, 0)
            .coerceIn(0, CryptoComparisonWindow.entries.size - 1)

    fun setCryptoComparisonWindowIndex(context: Context, index: Int) {
        prefs(context).edit { putInt(KEY_CRYPTO_COMPARISON_MINUTES, index) }
    }

    fun isColorStackWidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COLOR_STACK_WIDGET_ENABLED, false)

    fun setColorStackWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_COLOR_STACK_WIDGET_ENABLED, enabled) }
    }

    fun getColorStackWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_COLOR_STACK_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setColorStackWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_COLOR_STACK_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isBannerToggleColorStackVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_COLOR_STACK_VISIBLE, true)

    fun setBannerToggleColorStackVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_COLOR_STACK_VISIBLE, visible) }
    }

    fun isBannerToggleClickerVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_CLICKER_VISIBLE, true)

    fun setBannerToggleClickerVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_CLICKER_VISIBLE, visible) }
    }

    fun isBackgroundDisplayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKGROUND_DISPLAY_ENABLED, false)

    fun setBackgroundDisplayEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_BACKGROUND_DISPLAY_ENABLED, enabled) }
    }

    fun isBlackjackWidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLACKJACK_WIDGET_ENABLED, false)

    fun setBlackjackWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_BLACKJACK_WIDGET_ENABLED, enabled) }
    }

    fun getBlackjackWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_BLACKJACK_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setBlackjackWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_BLACKJACK_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isBannerToggleBlackjackVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_BLACKJACK_VISIBLE, true)

    fun setBannerToggleBlackjackVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_BLACKJACK_VISIBLE, visible) }
    }

    fun isMusicWidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MUSIC_WIDGET_ENABLED, false)

    fun setMusicWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_MUSIC_WIDGET_ENABLED, enabled) }
    }

    fun getMusicWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_MUSIC_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setMusicWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_MUSIC_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isBannerToggleMusicVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_MUSIC_VISIBLE, true)

    fun setBannerToggleMusicVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_MUSIC_VISIBLE, visible) }
    }

    fun isPipeTapWidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PIPETAP_WIDGET_ENABLED, false)

    fun setPipeTapWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_PIPETAP_WIDGET_ENABLED, enabled) }
    }

    fun getPipeTapWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_PIPETAP_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setPipeTapWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_PIPETAP_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isBannerTogglePipeTapVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_PIPETAP_VISIBLE, true)

    fun setBannerTogglePipeTapVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_PIPETAP_VISIBLE, visible) }
    }

    fun isTheLineWidgetEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_THE_LINE_WIDGET_ENABLED, false)

    fun setTheLineWidgetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_THE_LINE_WIDGET_ENABLED, enabled) }
    }

    fun getTheLineWidgetOpacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_THE_LINE_WIDGET_OPACITY_PERCENT, 100).coerceIn(0, 100)

    fun setTheLineWidgetOpacityPercent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_THE_LINE_WIDGET_OPACITY_PERCENT, value.coerceIn(0, 100)) }
    }

    fun isBannerToggleTheLineVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BANNER_TOGGLE_THE_LINE_VISIBLE, true)

    fun setBannerToggleTheLineVisible(context: Context, visible: Boolean) {
        prefs(context).edit { putBoolean(KEY_BANNER_TOGGLE_THE_LINE_VISIBLE, visible) }
    }

    fun getBannerToggleOrder(context: Context): List<String> {
        val stored = prefs(context).getString(KEY_BANNER_TOGGLE_ORDER, null)
        if (stored.isNullOrEmpty()) return ALL_WIDGET_KEYS
        val parsed = stored.split(",").filter { it in ALL_WIDGET_KEYS }
        val missing = ALL_WIDGET_KEYS.filter { it !in parsed }
        return parsed + missing
    }

    fun setBannerToggleOrder(context: Context, order: List<String>) {
        prefs(context).edit { putString(KEY_BANNER_TOGGLE_ORDER, order.joinToString(",")) }
    }

    fun isFavoritesModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FAVORITES_MODE, false)

    fun setFavoritesModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_FAVORITES_MODE, enabled) }
    }
}
