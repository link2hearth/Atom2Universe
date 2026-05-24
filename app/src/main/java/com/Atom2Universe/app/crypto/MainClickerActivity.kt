package com.Atom2Universe.app.crypto

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.graphics.BitmapFactory
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.app.Dialog
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.data.MainClickerDatabase
import com.Atom2Universe.app.news.NewsWidgetView
import com.Atom2Universe.app.crypto.data.MainClickerRepository
import com.Atom2Universe.app.util.applySystemBarsVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import android.provider.DocumentsContract
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.crypto.clicker.ClickerBannerView
import com.Atom2Universe.app.crypto.clicker.ClickerGameState
import com.Atom2Universe.app.crypto.clicker.ClickerViewModel
import androidx.core.content.edit

class MainClickerActivity : ThemedActivity() {

    companion object {
        private val SLIDESHOW_STEPS = intArrayOf(0, 1, 2, 3, 4, 5, 10, 15, 30, 45, 60)
        private const val CRYPTO_WIDGET_BASE_URL = "https://api.binance.com"
        private const val BTC_ENDPOINT_USD = "/api/v3/ticker/price?symbol=BTCUSDT"
        private const val ETH_ENDPOINT_USD = "/api/v3/ticker/price?symbol=ETHUSDT"
        private const val BTC_ENDPOINT_EUR = "/api/v3/ticker/price?symbol=BTCEUR"
        private const val ETH_ENDPOINT_EUR = "/api/v3/ticker/price?symbol=ETHEUR"
        private const val EURUSD_URL = "https://api.frankfurter.app/latest?from=EUR&to=USD"
        private const val EURUSD_REFRESH_MS = 60 * 60 * 1000L // 1 heure
    }

    private var useEurCurrency = false
    private var comparisonWindow = CryptoComparisonWindow.LAST_UPDATE
    private val btcEndpoint get() = if (useEurCurrency) BTC_ENDPOINT_EUR else BTC_ENDPOINT_USD
    private val ethEndpoint get() = if (useEurCurrency) ETH_ENDPOINT_EUR else ETH_ENDPOINT_USD
    private val btcSymbol get() = if (useEurCurrency) "BTCEUR" else "BTCUSDT"
    private val ethSymbol get() = if (useEurCurrency) "ETHEUR" else "ETHUSDT"

    private var refreshInterval: MainClickerRefreshInterval = MainClickerRefreshInterval.ONE_MIN

    private val httpClient: OkHttpClient by lazy { OkHttpClient() }
    private val backgroundRepository: MainClickerRepository by lazy {
        val database = MainClickerDatabase.getInstance(applicationContext)
        MainClickerRepository.getInstance(database)
    }
    private val shuffleManager by lazy { MainClickerShuffleManager(backgroundRepository) }

    private var bannerExpanded = false
    private lateinit var bannerControls: List<View>
    private lateinit var bannerTapZone: View
    private val hideBannerRunnable = Runnable { setBannerExpanded(false) }
    private val bannerHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private lateinit var cryptoWidgetView: CryptoWidgetView
    private lateinit var backgroundImageView: BottomCropImageView
    private lateinit var starfieldView: StarfieldView
    private lateinit var earthMoonWidgetView: EarthMoonWidgetView
    private lateinit var chessWidgetView: ChessWidgetView
    private lateinit var draughtsWidgetView: DraughtsWidgetView
    private lateinit var sudokuWidgetView: SudokuWidgetView
    private lateinit var game2048WidgetView: Game2048WidgetView
    private lateinit var cryptoToggle: SwitchCompat
    private lateinit var earthToggle: SwitchCompat
    private lateinit var chessToggle: SwitchCompat
    private lateinit var draughtsToggle: SwitchCompat
    private lateinit var sudokuToggle: SwitchCompat
    private lateinit var game2048Toggle: SwitchCompat
        private lateinit var newsWidgetView: NewsWidgetView
        private lateinit var newsToggle: SwitchCompat
    private lateinit var solitaireWidgetView: SolitaireWidgetView
    private lateinit var solitaireToggle: SwitchCompat
    private lateinit var blackjackWidgetView: BlackjackWidgetView
    private lateinit var blackjackToggle: SwitchCompat
    private lateinit var colorStackWidgetView: ColorStackWidgetView
    private lateinit var colorStackToggle: SwitchCompat
    private lateinit var pipeTapWidgetView: PipeTapWidgetView
    private lateinit var pipeTapToggle: SwitchCompat
    private lateinit var theLineWidgetView: TheLineWidgetView
    private lateinit var theLineToggle: SwitchCompat
    private lateinit var musicPlayerWidgetView: com.Atom2Universe.app.music.MusicPlayerWidgetView
    private lateinit var musicToggle: SwitchCompat
    private lateinit var floatingWebWidget: FloatingWebWidget
    private lateinit var clickerBannerView: ClickerBannerView
    private lateinit var clickerToggle: SwitchCompat
    private lateinit var atomSpringView: com.Atom2Universe.app.crypto.clicker.AtomSpringView
    private val clickerViewModel: ClickerViewModel by lazy {
        ViewModelProvider(this)[ClickerViewModel::class.java]
    }

    private lateinit var bannerViewCache: Map<String, Pair<View, View>>

    private val widgetLabelRes = mapOf(
        "earth" to R.string.crypto_banner_toggle_earth_label,
        "chess" to R.string.crypto_banner_toggle_chess_label,
        "draughts" to R.string.crypto_banner_toggle_draughts_label,
        "sudoku" to R.string.crypto_banner_toggle_sudoku_label,
        "2048" to R.string.crypto_banner_toggle_2048_label,
        "crypto" to R.string.crypto_banner_toggle_crypto_label,
        "news" to R.string.crypto_banner_toggle_news_label,
        "solitaire" to R.string.crypto_banner_toggle_solitaire_label,
        "blackjack" to R.string.crypto_banner_toggle_blackjack_label,
        "color_stack" to R.string.crypto_banner_toggle_color_stack_label,
        "clicker" to R.string.crypto_banner_toggle_clicker_label,
        "music" to R.string.crypto_banner_toggle_music_label,
        "pipetap" to R.string.crypto_banner_toggle_pipetap_label,
        "the_line" to R.string.crypto_banner_toggle_the_line_label,
    )

    private val widgetBannerVisibleGetter: Map<String, () -> Boolean> by lazy {
        mapOf(
            "earth" to { MainClickerPreferences.isBannerToggleEarthVisible(this) },
            "chess" to { MainClickerPreferences.isBannerToggleChessVisible(this) },
            "draughts" to { MainClickerPreferences.isBannerToggleDraughtsVisible(this) },
            "sudoku" to { MainClickerPreferences.isBannerToggleSudokuVisible(this) },
            "2048" to { MainClickerPreferences.isBannerToggle2048Visible(this) },
            "crypto" to { MainClickerPreferences.isBannerToggleCryptoVisible(this) },
            "news" to { MainClickerPreferences.isBannerToggleNewsVisible(this) },
            "solitaire" to { MainClickerPreferences.isBannerToggleSolitaireVisible(this) },
            "blackjack" to { MainClickerPreferences.isBannerToggleBlackjackVisible(this) },
            "color_stack" to { MainClickerPreferences.isBannerToggleColorStackVisible(this) },
            "clicker" to { MainClickerPreferences.isBannerToggleClickerVisible(this) },
            "music" to { MainClickerPreferences.isBannerToggleMusicVisible(this) },
            "pipetap" to { MainClickerPreferences.isBannerTogglePipeTapVisible(this) },
            "the_line" to { MainClickerPreferences.isBannerToggleTheLineVisible(this) },
        )
    }

    private val widgetBannerVisibleSetter: Map<String, (Boolean) -> Unit> by lazy {
        mapOf(
            "earth" to { v -> MainClickerPreferences.setBannerToggleEarthVisible(this, v) },
            "chess" to { v -> MainClickerPreferences.setBannerToggleChessVisible(this, v) },
            "draughts" to { v -> MainClickerPreferences.setBannerToggleDraughtsVisible(this, v) },
            "sudoku" to { v -> MainClickerPreferences.setBannerToggleSudokuVisible(this, v) },
            "2048" to { v -> MainClickerPreferences.setBannerToggle2048Visible(this, v) },
            "crypto" to { v -> MainClickerPreferences.setBannerToggleCryptoVisible(this, v) },
            "news" to { v -> MainClickerPreferences.setBannerToggleNewsVisible(this, v) },
            "solitaire" to { v -> MainClickerPreferences.setBannerToggleSolitaireVisible(this, v) },
            "blackjack" to { v -> MainClickerPreferences.setBannerToggleBlackjackVisible(this, v) },
            "color_stack" to { v -> MainClickerPreferences.setBannerToggleColorStackVisible(this, v) },
            "clicker" to { v -> MainClickerPreferences.setBannerToggleClickerVisible(this, v) },
            "music" to { v -> MainClickerPreferences.setBannerToggleMusicVisible(this, v) },
            "pipetap" to { v -> MainClickerPreferences.setBannerTogglePipeTapVisible(this, v) },
            "the_line" to { v -> MainClickerPreferences.setBannerToggleTheLineVisible(this, v) },
        )
    }

    private var shopDialog: Dialog? = null
    private var shopGodFingerMult = 1
    private var shopStarCoreMult = 1
    private var shopGodFingerLevelView: TextView? = null
    private var shopGodFingerPriceView: TextView? = null
    private var shopGodFingerMultBtn: Button? = null
    private var shopStarCoreLevelView: TextView? = null
    private var shopStarCorePriceView: TextView? = null
    private var shopStarCoreMultBtn: Button? = null

    // Neutrinos dans le shop
    private var shopNeutrinoBalance: TextView? = null
    private var shopApcToApsLevelView: TextView? = null
    private var shopApcToApsEffectView: TextView? = null
    private var shopApcToApsCostView: TextView? = null
    private var shopApsToApcLevelView: TextView? = null
    private var shopApsToApcEffectView: TextView? = null
    private var shopApsToApcCostView: TextView? = null
    private var shopElemToNeutrinoStock: TextView? = null
    private var shopElemToNeutrinoMult: Button? = null
    private var shopElemToNeutrinoBuy: Button? = null
    private var shopElemToNeutrinoMult_value = 1

    // Stats dans le shop
    private var shopStatLifetime: TextView? = null
    private var shopStatApc: TextView? = null
    private var shopStatAps: TextView? = null
    private var shopStatElemApc: TextView? = null
    private var shopStatElemAps: TextView? = null
    private var shopStatTotalClicks: TextView? = null
    private var shopStatFrenzies: TextView? = null
    private var shopStatMaxApcClicks: TextView? = null
    private var shopStatMaxCps: TextView? = null
    private var shopStatPlayTime: TextView? = null
    private var shopStatProdApc: TextView? = null
    private var shopStatProdAps: TextView? = null
    private var shopProdBar: android.widget.LinearLayout? = null

    // Stats jeux dans le shop

    // Usines dans le shop
    private val shopFactoryCountViews  = mutableMapOf<com.Atom2Universe.app.crypto.clicker.FactoryType, TextView>()
    private val shopFactoryEffectViews = mutableMapOf<com.Atom2Universe.app.crypto.clicker.FactoryType, TextView>()
    private val shopFactoryPriceViews  = mutableMapOf<com.Atom2Universe.app.crypto.clicker.FactoryType, TextView>()
    private val shopFactoryBuyBtns     = mutableMapOf<com.Atom2Universe.app.crypto.clicker.FactoryType, Button>()

    // Succès dans le shop
    private var shopAchievementProgress: TextView? = null
    private var shopAchievementsContainer: LinearLayout? = null

    // Toast succès débloqué
    private lateinit var achievementToastContainer: View
    private var achievementToastJob: Job? = null

    private val clickerStatsRepo by lazy { com.Atom2Universe.app.crypto.clicker.ClickerStatsRepository(this) }
    private val periodicStore by lazy { com.Atom2Universe.app.periodic.PeriodicCollectionStore(this) }

    private var refreshJob: Job? = null
    private var backgroundJob: Job? = null
    private var astronomyJob: Job? = null
    private var eurUsdJob: Job? = null
    private var activeBtcCall: Call? = null
    private var activeEthCall: Call? = null

    private var btcPriceUsd: Double? = null
    private var previousBtcPriceUsd: Double? = null
    private var ethPriceUsd: Double? = null
    private var previousEthPriceUsd: Double? = null
    private var eurUsdRate: Double? = null
    private var previousEurUsdRate: Double? = null
    private var isLoading = false

    // Cache klines : le prix de référence historique + timestamp du dernier fetch
    private var btcKlineRef: Double? = null
    private var ethKlineRef: Double? = null
    private var lastKlineRefFetchMs: Long = 0L

    private fun klineRefStale(): Boolean {
        if (!comparisonWindow.usesKlines) return false
        val ttlMs = if (comparisonWindow.klineInterval == "1h") 3_600_000L else 60_000L
        return System.currentTimeMillis() - lastKlineRefFetchMs >= ttlMs
    }

    private fun invalidateKlineCache() {
        btcKlineRef = null
        ethKlineRef = null
        lastKlineRefFetchMs = 0L
    }

    private var backgroundFolderUri: Uri? = null
    private var slideshowIntervalMs: Long = 60_000L
    private var settingsDialogFolderView: TextView? = null
    private var lastReloadReason: MainClickerShuffleManager.ReloadReason = MainClickerShuffleManager.ReloadReason.NO_FOLDER
    private var backgroundRequestToken = 0
    private var backgroundDisplayEnabled = true
    private var keepScreenOnEnabled = false
    private var widgetOpacityPercent = 100
    private var statusBarVisible = false
    private var navBarVisible = false
    private var favoritesMode = false

    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val swipeDistanceThreshold = 72f * resources.displayMetrics.density
            private val swipeVelocityThreshold = 200f * resources.displayMetrics.density

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (abs(dx) < abs(dy)) return false
                if (abs(dx) < swipeDistanceThreshold) return false
                if (abs(velocityX) < swipeVelocityThreshold) return false

                if (dx < 0) {
                    showNextBackgroundImage(restartTimer = true, notifyIfMissing = true)
                } else {
                    showPreviousBackgroundImage(restartTimer = true, notifyIfMissing = true)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val uri = shuffleManager.currentUri() ?: return
                showImageOptionsDialog(uri)
            }
        })
    }

    private val openFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            persistFolderPermission(uri, result.data?.flags ?: 0)
            MainClickerPreferences.setFolderUri(this, uri)
            settingsDialogFolderView?.let { updateFolderValueView(it, uri) }
            reloadBackground(force = true)
            showToast(getString(R.string.crypto_background_folder_saved))
        }

    private val openAtomFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            persistFolderPermission(uri, result.data?.flags ?: 0)
            MainClickerPreferences.setCustomAtomFolderUri(this, uri)
            applyCustomAtomFolder(uri)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (floatingWebWidget.visibility == View.VISIBLE) {
                    if (floatingWebWidget.canGoBack()) floatingWebWidget.goBack()
                    else floatingWebWidget.dismiss()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        statusBarVisible = MainClickerPreferences.isStatusBarVisible(this)
        navBarVisible = MainClickerPreferences.isNavBarVisible(this)
        refreshInterval = MainClickerPreferences.getRefreshInterval(this)
        applySystemBarsPreference()
        setContentView(R.layout.activity_main_clicker)

        cryptoWidgetView = findViewById(R.id.crypto_widget)
        backgroundImageView = findViewById(R.id.main_clicker_background_image)
        starfieldView = findViewById(R.id.main_clicker_starfield)
        earthMoonWidgetView = findViewById(R.id.earth_moon_widget)
        chessWidgetView = findViewById(R.id.chess_widget)
        draughtsWidgetView = findViewById(R.id.draughts_widget)
        sudokuWidgetView = findViewById(R.id.sudoku_widget)
        game2048WidgetView = findViewById(R.id.game2048_widget)
        cryptoToggle = findViewById(R.id.main_clicker_crypto_toggle)
        earthToggle = findViewById(R.id.main_clicker_earth_toggle)
        chessToggle = findViewById(R.id.main_clicker_chess_toggle)
        draughtsToggle = findViewById(R.id.main_clicker_draughts_toggle)
        sudokuToggle = findViewById(R.id.main_clicker_sudoku_toggle)
        game2048Toggle = findViewById(R.id.main_clicker_2048_toggle)
        newsWidgetView = findViewById(R.id.news_widget)
        newsToggle = findViewById(R.id.main_clicker_news_toggle)
        solitaireWidgetView = findViewById(R.id.solitaire_widget)
        solitaireToggle = findViewById(R.id.main_clicker_solitaire_toggle)
        blackjackWidgetView = findViewById(R.id.blackjack_widget)
        blackjackToggle = findViewById(R.id.main_clicker_blackjack_toggle)
        colorStackWidgetView = findViewById(R.id.color_stack_widget)
        colorStackToggle = findViewById(R.id.main_clicker_color_stack_toggle)
        floatingWebWidget = findViewById(R.id.floating_web_widget)

        val backButton = findViewById<ImageButton>(R.id.main_clicker_back_button)
        val settingsBtn = findViewById<ImageButton>(R.id.main_clicker_settings_button)
        val togglesContainer = findViewById<View>(R.id.main_clicker_toggles)
        val topBanner = findViewById<View>(R.id.main_clicker_top_banner)
        bannerControls = listOf(backButton, settingsBtn, togglesContainer, topBanner)

        backButton.setOnClickListener { finish() }
        settingsBtn.setOnClickListener { showSettingsDialog() }

        bannerTapZone = findViewById(R.id.main_clicker_banner_tap_zone)
        bannerTapZone.setOnClickListener { setBannerExpanded(true) }

        cryptoToggle.isChecked = MainClickerPreferences.isCryptoWidgetEnabled(this)
        cryptoWidgetView.visibility = if (cryptoToggle.isChecked) View.VISIBLE else View.GONE
        cryptoToggle.setOnCheckedChangeListener { _, isChecked -> onCryptoToggleChanged(isChecked) }

        earthToggle.isChecked = MainClickerPreferences.isEarthWidgetEnabled(this)
        earthToggle.setOnCheckedChangeListener { _, isChecked -> onEarthToggleChanged(isChecked) }

        chessToggle.isChecked = MainClickerPreferences.isChessWidgetEnabled(this)
        chessWidgetView.visibility = if (chessToggle.isChecked) View.VISIBLE else View.GONE
        chessToggle.setOnCheckedChangeListener { _, isChecked -> onChessToggleChanged(isChecked) }

        draughtsToggle.isChecked = MainClickerPreferences.isDraughtsWidgetEnabled(this)
        draughtsWidgetView.visibility = if (draughtsToggle.isChecked) View.VISIBLE else View.GONE
        draughtsToggle.setOnCheckedChangeListener { _, isChecked -> onDraughtsToggleChanged(isChecked) }

        sudokuToggle.isChecked = MainClickerPreferences.isSudokuWidgetEnabled(this)
        sudokuWidgetView.visibility = if (sudokuToggle.isChecked) View.VISIBLE else View.GONE
        sudokuToggle.setOnCheckedChangeListener { _, isChecked -> onSudokuToggleChanged(isChecked) }

        game2048Toggle.isChecked = MainClickerPreferences.isGame2048WidgetEnabled(this)
        game2048WidgetView.visibility = if (game2048Toggle.isChecked) View.VISIBLE else View.GONE
        game2048Toggle.setOnCheckedChangeListener { _, isChecked -> onGame2048ToggleChanged(isChecked) }

        newsToggle.isChecked = MainClickerPreferences.isNewsWidgetEnabled(this)
        newsWidgetView.setToggleEnabled(newsToggle.isChecked)
        newsToggle.setOnCheckedChangeListener { _, isChecked -> onNewsToggleChanged(isChecked) }
        newsWidgetView.onOpenUrl = { url -> floatingWebWidget.load(url) }

        solitaireToggle.isChecked = MainClickerPreferences.isSolitaireWidgetEnabled(this)
        solitaireWidgetView.visibility = if (solitaireToggle.isChecked) View.VISIBLE else View.GONE
        solitaireToggle.setOnCheckedChangeListener { _, isChecked -> onSolitaireToggleChanged(isChecked) }

        blackjackToggle.isChecked = MainClickerPreferences.isBlackjackWidgetEnabled(this)
        blackjackWidgetView.visibility = if (blackjackToggle.isChecked) View.VISIBLE else View.GONE
        blackjackToggle.setOnCheckedChangeListener { _, isChecked -> onBlackjackToggleChanged(isChecked) }

        colorStackToggle.isChecked = MainClickerPreferences.isColorStackWidgetEnabled(this)
        colorStackWidgetView.visibility = if (colorStackToggle.isChecked) View.VISIBLE else View.GONE
        colorStackToggle.setOnCheckedChangeListener { _, isChecked -> onColorStackToggleChanged(isChecked) }

        musicPlayerWidgetView = findViewById(R.id.music_player_widget)
        musicToggle = findViewById(R.id.main_clicker_music_toggle)
        musicToggle.isChecked = MainClickerPreferences.isMusicWidgetEnabled(this)
        musicPlayerWidgetView.visibility = if (musicToggle.isChecked) View.VISIBLE else View.GONE
        musicToggle.setOnCheckedChangeListener { _, isChecked -> onMusicToggleChanged(isChecked) }

        atomSpringView = findViewById(R.id.atom_spring_view)
        atomSpringView.setLowAnimation(MainClickerPreferences.isAtomLowAnimation(this))
        atomSpringView.setBiggerImage(MainClickerPreferences.isAtomBiggerImage(this))
        MainClickerPreferences.getCustomAtomFolderUri(this)?.let { applyCustomAtomFolder(it) }
        atomSpringView.setAtomIndex(MainClickerPreferences.getAtomSpringIndex(this))
        atomSpringView.visibility = if (MainClickerPreferences.isAtomSpringEnabled(this)) View.VISIBLE else View.GONE

        clickerBannerView = findViewById(R.id.clicker_banner)
        clickerBannerView.onShopClick = { showShopDialog() }
        clickerBannerView.onGachaClick = {
            startActivity(android.content.Intent(this, com.Atom2Universe.app.crypto.gacha.GachaActivity::class.java))
        }
        clickerBannerView.onAtomsClick = {
            val atomVisualActive = clickerToggle.isChecked && MainClickerPreferences.isAtomSpringEnabled(this)
            if (atomVisualActive) {
                val newIndex = atomSpringView.cycleToNext()
                MainClickerPreferences.setAtomSpringIndex(this, newIndex)
            } else {
                openCurrentBackgroundImageOptions()
            }
        }
        clickerBannerView.onAtomsLongClick = {
            val enabled = !MainClickerPreferences.isAtomSpringEnabled(this)
            MainClickerPreferences.setAtomSpringEnabled(this, enabled)
            atomSpringView.visibility = if (clickerToggle.isChecked && enabled) View.VISIBLE else View.GONE
        }
        clickerToggle = findViewById(R.id.main_clicker_clicker_toggle)
        clickerToggle.isChecked = MainClickerPreferences.isClickerEnabled(this)
        clickerBannerView.visibility = if (clickerToggle.isChecked) View.VISIBLE else View.GONE
        atomSpringView.visibility = if (clickerToggle.isChecked && MainClickerPreferences.isAtomSpringEnabled(this)) View.VISIBLE else View.GONE
        pipeTapWidgetView = findViewById(R.id.pipetap_widget)
        pipeTapToggle = findViewById(R.id.main_clicker_pipetap_toggle)
        pipeTapToggle.isChecked = MainClickerPreferences.isPipeTapWidgetEnabled(this)
        pipeTapWidgetView.visibility = if (pipeTapToggle.isChecked) View.VISIBLE else View.GONE
        pipeTapWidgetView.applyBackgroundOpacity(MainClickerPreferences.getPipeTapWidgetOpacityPercent(this))
        pipeTapToggle.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setPipeTapWidgetEnabled(this, isChecked)
            pipeTapWidgetView.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) pipeTapWidgetView.reload()
        }
        theLineWidgetView = findViewById(R.id.the_line_widget)
        theLineToggle = findViewById(R.id.main_clicker_the_line_toggle)
        theLineToggle.isChecked = MainClickerPreferences.isTheLineWidgetEnabled(this)
        theLineWidgetView.visibility = if (theLineToggle.isChecked) View.VISIBLE else View.GONE
        theLineWidgetView.applyBackgroundOpacity(MainClickerPreferences.getTheLineWidgetOpacityPercent(this))
        theLineToggle.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setTheLineWidgetEnabled(this, isChecked)
            theLineWidgetView.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) theLineWidgetView.reload()
        }

        bannerViewCache = mapOf(
            "earth" to Pair(findViewById(R.id.main_clicker_label_earth), earthToggle),
            "chess" to Pair(findViewById(R.id.main_clicker_label_chess), chessToggle),
            "draughts" to Pair(findViewById(R.id.main_clicker_label_draughts), draughtsToggle),
            "sudoku" to Pair(findViewById(R.id.main_clicker_label_sudoku), sudokuToggle),
            "2048" to Pair(findViewById(R.id.main_clicker_label_2048), game2048Toggle),
            "crypto" to Pair(findViewById(R.id.main_clicker_label_crypto), cryptoToggle),
            "news" to Pair(findViewById(R.id.main_clicker_label_news), newsToggle),
            "solitaire" to Pair(findViewById(R.id.main_clicker_label_solitaire), solitaireToggle),
            "blackjack" to Pair(findViewById(R.id.main_clicker_label_blackjack), blackjackToggle),
            "color_stack" to Pair(findViewById(R.id.main_clicker_label_color_stack), colorStackToggle),
            "clicker" to Pair(findViewById(R.id.main_clicker_label_clicker), clickerToggle),
            "music" to Pair(findViewById(R.id.main_clicker_label_music), musicToggle),
            "pipetap" to Pair(findViewById(R.id.main_clicker_label_pipetap), pipeTapToggle),
            "the_line" to Pair(findViewById(R.id.main_clicker_label_the_line), theLineToggle),
        )

        clickerBannerView.applyOpacity(MainClickerPreferences.getClickerOpacityPercent(this))
        clickerBannerView.post {
            clickerBannerView.applySize(MainClickerPreferences.getClickerHeightDp(this))
        }
        com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber.mantissaFractionDigits =
            MainClickerPreferences.getClickerDecimalDigits(this)
        clickerToggle.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setClickerEnabled(this, isChecked)
            clickerBannerView.visibility = if (isChecked) View.VISIBLE else View.GONE
            atomSpringView.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) clickerViewModel.startGameLoop() else clickerViewModel.stopGameLoop()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                clickerViewModel.state.collect { state ->
                    if (clickerBannerView.visibility == View.VISIBLE) {
                        clickerBannerView.update(state)
                        val godCost  = clickerViewModel.shopCost("godFinger", 1)
                        val starCost = clickerViewModel.shopCost("starCore", 1)
                        val affordableAtoms = state.atoms.greaterOrEqual(godCost) || state.atoms.greaterOrEqual(starCost)
                        clickerBannerView.setShopAffordable(affordableAtoms)
                    }
                    if (shopDialog?.isShowing == true) {
                        updateShopViews(state)
                    }
                    if (blackjackWidgetView.visibility == View.VISIBLE) {
                        blackjackWidgetView.setNeutrinoBalance(state.neutrinos)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                clickerViewModel.frenzyUiState.collect { frenzyState ->
                    if (clickerBannerView.visibility == View.VISIBLE) {
                        clickerBannerView.bindFrenzy(frenzyState)
                    }
                }
            }
        }

        achievementToastContainer = findViewById(R.id.achievement_toast_container)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                clickerViewModel.achievementUnlocked.collect { achievement ->
                    showAchievementToast(achievement)
                }
            }
        }

        setupOpacityLongPress()
        setBannerExpanded(false)

        cryptoWidgetView.updatePrices(null, null, null, null)
        cryptoWidgetView.showLoading()
        reloadPreferences()
    }

    override fun onStart() {
        super.onStart()
        applyKeepScreenOnPreference()
        if (clickerToggle.isChecked) clickerViewModel.startGameLoop()
        startRefreshLoop()
        startAstronomyLoop()
        if (MainClickerPreferences.isCryptoEurUsdEnabled(this)) startEurUsdLoop()
        if (bannerExpanded) scheduleHideBanner()
    }

    override fun onResume() {
        super.onResume()
        val resetFlags = getSharedPreferences("pending_reset_flags", MODE_PRIVATE)
        val resetStats   = resetFlags.getBoolean("reset_stats",   false)
        val resetClicker = resetFlags.getBoolean("reset_clicker", false)
        if (resetStats || resetClicker) {
            resetFlags.edit { clear() }
            clickerViewModel.applyPendingReset(resetStats, resetClicker)
        }
        clickerViewModel.resumeOfflineGains()
        clickerViewModel.refreshElementBonuses()
        clickerViewModel.awardGachaTickets()
        clickerViewModel.refreshNeutrinoBalance()
        reloadPreferences()
        reloadBackground(force = false)
        if (MainClickerPreferences.isChessWidgetEnabled(this)) {
            chessWidgetView.reload()
        }
        if (MainClickerPreferences.isDraughtsWidgetEnabled(this)) {
            draughtsWidgetView.reload()
        }
        if (MainClickerPreferences.isSudokuWidgetEnabled(this)) {
            sudokuWidgetView.reload(lifecycleScope)
        }
        if (MainClickerPreferences.isGame2048WidgetEnabled(this)) {
            game2048WidgetView.reload()
        }
        if (MainClickerPreferences.isSolitaireWidgetEnabled(this)) {
            solitaireWidgetView.reload(lifecycleScope)
        }
        if (MainClickerPreferences.isColorStackWidgetEnabled(this)) {
            colorStackWidgetView.reload()
        }
        if (MainClickerPreferences.isTheLineWidgetEnabled(this)) {
            theLineWidgetView.reload()
        }
        if (MainClickerPreferences.isBlackjackWidgetEnabled(this)) {
            blackjackWidgetView.reload()
        }
        floatingWebWidget.onResume()
    }

    override fun onPause() {
        floatingWebWidget.onPause()
        colorStackWidgetView.cancelTimer()
        super.onPause()
    }

    override fun onDestroy() {
        floatingWebWidget.onDestroy()
        super.onDestroy()
    }


    override fun onStop() {
        bannerHandler.removeCallbacks(hideBannerRunnable)
        clickerViewModel.stopGameLoop()
        clickerViewModel.save()
        stopBackgroundLoop()
        stopRefreshLoop()
        stopAstronomyLoop()
        stopEurUsdLoop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cryptoWidgetView.keepScreenOn = false
        backgroundImageView.keepScreenOn = false
        super.onStop()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Appelé seulement si aucune vue enfant n'a consommé l'event.
        if (clickerBannerView.visibility == View.VISIBLE) {
            // Chaque nouveau doigt posé = 1 clic. On retourne true pour continuer
            // à recevoir ACTION_POINTER_DOWN (multi-touch).
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    clickerViewModel.registerClick()
                    clickerBannerView.onClickRegistered()
                    if (atomSpringView.visibility == View.VISIBLE) atomSpringView.registerClick()
                }
            }
            return true
        }
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    // ── Shop du clicker ───────────────────────────────────────────────────────

    private fun showShopDialog() {
        if (shopDialog?.isShowing == true) return
        clickerViewModel.refreshNeutrinoBalance()
        val view = layoutInflater.inflate(R.layout.view_clicker_shop, null)

        shopGodFingerLevelView  = view.findViewById(R.id.shop_god_finger_level)
        shopGodFingerPriceView  = view.findViewById(R.id.shop_god_finger_price)
        shopGodFingerMultBtn    = view.findViewById(R.id.shop_god_finger_mult)
        shopStarCoreLevelView   = view.findViewById(R.id.shop_star_core_level)
        shopStarCorePriceView   = view.findViewById(R.id.shop_star_core_price)
        shopStarCoreMultBtn     = view.findViewById(R.id.shop_star_core_mult)
        shopNeutrinoBalance     = view.findViewById(R.id.shop_neutrino_balance)
        shopApcToApsLevelView   = view.findViewById(R.id.shop_apc_to_aps_level)
        shopApcToApsEffectView  = view.findViewById(R.id.shop_apc_to_aps_effect)
        shopApcToApsCostView    = view.findViewById(R.id.shop_apc_to_aps_cost)
        shopApsToApcLevelView   = view.findViewById(R.id.shop_aps_to_apc_level)
        shopApsToApcEffectView  = view.findViewById(R.id.shop_aps_to_apc_effect)
        shopApsToApcCostView    = view.findViewById(R.id.shop_aps_to_apc_cost)
        shopElemToNeutrinoStock = view.findViewById(R.id.shop_elem_to_neutrino_stock)
        shopElemToNeutrinoMult  = view.findViewById(R.id.shop_elem_to_neutrino_mult)
        shopElemToNeutrinoBuy   = view.findViewById(R.id.shop_elem_to_neutrino_buy)
        shopStatLifetime        = view.findViewById(R.id.shop_stat_lifetime)
        shopStatApc            = view.findViewById(R.id.shop_stat_apc)
        shopStatAps            = view.findViewById(R.id.shop_stat_aps)
        shopStatElemApc        = view.findViewById(R.id.shop_stat_elem_apc)
        shopStatElemAps        = view.findViewById(R.id.shop_stat_elem_aps)
        shopStatTotalClicks    = view.findViewById(R.id.shop_stat_total_clicks)
        shopStatFrenzies       = view.findViewById(R.id.shop_stat_frenzies)
        shopStatMaxApcClicks   = view.findViewById(R.id.shop_stat_max_apc_clicks)
        shopStatMaxCps         = view.findViewById(R.id.shop_stat_max_cps)
        shopStatPlayTime       = view.findViewById(R.id.shop_stat_play_time)
        shopStatProdApc        = view.findViewById(R.id.shop_stat_prod_apc)
        shopStatProdAps        = view.findViewById(R.id.shop_stat_prod_aps)
        shopProdBar            = view.findViewById(R.id.shop_prod_bar)
        shopAchievementProgress   = view.findViewById(R.id.shop_achievement_progress)
        shopAchievementsContainer = view.findViewById(R.id.shop_achievements_container)
        buildAchievementShopItems()
        buildFactoryShopItems(view.findViewById(R.id.shop_factories_container))

        shopGodFingerMultBtn?.setOnClickListener {
            shopGodFingerMult = cycleShopMultiplier(shopGodFingerMult)
            shopGodFingerMultBtn?.text = "×$shopGodFingerMult"
            updateShopViews(clickerViewModel.state.value)
        }
        shopStarCoreMultBtn?.setOnClickListener {
            shopStarCoreMult = cycleShopMultiplier(shopStarCoreMult)
            shopStarCoreMultBtn?.text = "×$shopStarCoreMult"
            updateShopViews(clickerViewModel.state.value)
        }
        view.findViewById<Button>(R.id.shop_god_finger_buy).setOnClickListener {
            clickerViewModel.buyUpgrade("godFinger", shopGodFingerMult)
        }
        view.findViewById<Button>(R.id.shop_star_core_buy).setOnClickListener {
            clickerViewModel.buyUpgrade("starCore", shopStarCoreMult)
        }
        view.findViewById<Button>(R.id.shop_apc_to_aps_buy).setOnClickListener {
            clickerViewModel.buyApcToAps()
        }
        view.findViewById<Button>(R.id.shop_aps_to_apc_buy).setOnClickListener {
            clickerViewModel.buyApsToApc()
        }
        shopElemToNeutrinoMult?.setOnClickListener {
            shopElemToNeutrinoMult_value = cycleShopMultiplier(shopElemToNeutrinoMult_value)
            shopElemToNeutrinoMult?.text = "×$shopElemToNeutrinoMult_value"
            updateShopViews(clickerViewModel.state.value)
        }
        shopElemToNeutrinoBuy?.setOnClickListener {
            clickerViewModel.buyElementsToNeutrinos(shopElemToNeutrinoMult_value)
            updateShopViews(clickerViewModel.state.value)
        }

        updateShopViews(clickerViewModel.state.value)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.80).toInt()
        )
        dialog.setOnDismissListener {
            shopGodFingerLevelView = null
            shopGodFingerPriceView = null
            shopGodFingerMultBtn   = null
            shopStarCoreLevelView  = null
            shopStarCorePriceView  = null
            shopStarCoreMultBtn    = null
            shopNeutrinoBalance    = null
            shopApcToApsLevelView  = null
            shopApcToApsEffectView = null
            shopApcToApsCostView   = null
            shopApsToApcLevelView  = null
            shopApsToApcEffectView = null
            shopApsToApcCostView   = null
            shopElemToNeutrinoStock = null
            shopElemToNeutrinoMult  = null
            shopElemToNeutrinoBuy   = null
            shopStatLifetime       = null
            shopStatApc            = null
            shopStatAps            = null
            shopStatElemApc        = null
            shopStatElemAps        = null
            shopStatTotalClicks    = null
            shopStatFrenzies       = null
            shopStatMaxApcClicks   = null
            shopStatMaxCps         = null
            shopStatPlayTime       = null
            shopStatProdApc        = null
            shopStatProdAps        = null
            shopProdBar            = null
            shopAchievementProgress   = null
            shopAchievementsContainer = null
            shopFactoryCountViews.clear()
            shopFactoryEffectViews.clear()
            shopFactoryPriceViews.clear()
            shopFactoryBuyBtns.clear()
            shopDialog = null
        }
        shopDialog = dialog
        dialog.show()
    }

    private fun showAchievementToast(achievement: com.Atom2Universe.app.crypto.clicker.ClickerAchievement) {
        achievementToastJob?.cancel()
        val toast = achievementToastContainer
        toast.animate().cancel()

        toast.findViewById<TextView>(R.id.achievement_toast_name)?.text = getString(achievement.nameRes)
        toast.findViewById<TextView>(R.id.achievement_toast_flavor)?.text = getString(achievement.flavorRes)
        toast.findViewById<TextView>(R.id.achievement_toast_target)?.text = achievement.targetText

        toast.visibility = View.VISIBLE
        toast.alpha = 0f
        toast.translationY = -200f
        toast.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        achievementToastJob = lifecycleScope.launch {
            delay(3_500L)
            toast.animate()
                .alpha(0f)
                .translationY(-120f)
                .setDuration(300)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction { toast.visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun buildFactoryShopItems(container: LinearLayout) {
        shopFactoryCountViews.clear()
        shopFactoryEffectViews.clear()
        shopFactoryPriceViews.clear()
        shopFactoryBuyBtns.clear()
        container.removeAllViews()

        val dp      = resources.displayMetrics.density
        val dp1     = dp.toInt()
        val dp6     = (6 * dp).toInt()
        val dp8     = (8 * dp).toInt()
        val dp14    = (14 * dp).toInt()
        val dp28    = (28 * dp).toInt()
        val dp36    = (36 * dp).toInt()

        val orderedByPair = listOf(
            com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_ACCELERATOR,
            com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_INJECTOR,
            com.Atom2Universe.app.crypto.clicker.FactoryType.FUSION_REACTOR,
            com.Atom2Universe.app.crypto.clicker.FactoryType.PLASMA_CATALYST,
            com.Atom2Universe.app.crypto.clicker.FactoryType.HADRON_COLLIDER,
            com.Atom2Universe.app.crypto.clicker.FactoryType.SYNCHROTRON
        )
        for ((index, type) in orderedByPair.withIndex()) {
            if (index > 0) {
                val isPairBoundary = index % 2 == 0
                val sep = View(this)
                sep.setBackgroundColor(if (isPairBoundary) (0x2D3748 or 0xFF000000.toInt()) else (0x1E293B or 0xFF000000.toInt()))
                val sepP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp1)
                sepP.topMargin    = if (isPairBoundary) dp8 else 0
                sepP.bottomMargin = if (isPairBoundary) dp14 else dp8
                container.addView(sep, sepP)
            }

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity     = android.view.Gravity.CENTER_VERTICAL
            val rowP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rowP.bottomMargin = if (index % 2 == 0) dp6 else dp14
            row.layoutParams = rowP

            val icon = TextView(this)
            icon.text     = factoryIcon(type)
            icon.textSize = 22f
            icon.gravity  = android.view.Gravity.CENTER
            icon.layoutParams = LinearLayout.LayoutParams(dp28, LinearLayout.LayoutParams.WRAP_CONTENT)
            row.addView(icon)

            val textCol = LinearLayout(this)
            textCol.orientation = LinearLayout.VERTICAL
            textCol.setPadding(dp8, 0, 0, 0)
            textCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val nameView = TextView(this)
            nameView.text     = getString(factoryNameRes(type))
            nameView.setTextColor(0xFFE2E8F0.toInt())
            nameView.textSize = 16f
            nameView.typeface = android.graphics.Typeface.MONOSPACE
            textCol.addView(nameView)

            val effectView = TextView(this)
            effectView.text     = ""
            effectView.setTextColor(0xFF94A3B8.toInt())
            effectView.textSize = 12f
            effectView.typeface = android.graphics.Typeface.MONOSPACE
            shopFactoryEffectViews[type] = effectView
            textCol.addView(effectView)

            row.addView(textCol)

            val countView = TextView(this)
            countView.text     = "×0"
            countView.setTextColor(0xFF94A3B8.toInt())
            countView.textSize = 15f
            countView.typeface = android.graphics.Typeface.MONOSPACE
            countView.setPadding(0, 0, dp8, 0)
            shopFactoryCountViews[type] = countView
            row.addView(countView)

            val priceView = TextView(this)
            priceView.text     = "—"
            priceView.setTextColor(0xFF94A3B8.toInt())
            priceView.textSize = 15f
            priceView.typeface = android.graphics.Typeface.MONOSPACE
            priceView.setPadding(0, 0, dp6, 0)
            shopFactoryPriceViews[type] = priceView
            row.addView(priceView)

            val buyBtn = Button(this)
            buyBtn.text     = getString(R.string.clicker_shop_buy)
            buyBtn.textSize = 13f
            buyBtn.setTextColor(0xFFFFFFFF.toInt())
            buyBtn.setPadding(dp14, 0, dp14, 0)
            buyBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp36)
            buyBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(factoryButtonColor(type))
            buyBtn.setOnClickListener { clickerViewModel.buyFactory(type) }
            shopFactoryBuyBtns[type] = buyBtn
            row.addView(buyBtn)

            container.addView(row)
        }
    }

    private fun factoryIcon(type: com.Atom2Universe.app.crypto.clicker.FactoryType) = when (type) {
        com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_ACCELERATOR -> "⚡"
        com.Atom2Universe.app.crypto.clicker.FactoryType.FUSION_REACTOR     -> "♨"
        com.Atom2Universe.app.crypto.clicker.FactoryType.HADRON_COLLIDER    -> "⊕"
        com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_INJECTOR    -> "⇑"
        com.Atom2Universe.app.crypto.clicker.FactoryType.PLASMA_CATALYST    -> "≈"
        com.Atom2Universe.app.crypto.clicker.FactoryType.SYNCHROTRON        -> "↺"
    }

    private fun factoryNameRes(type: com.Atom2Universe.app.crypto.clicker.FactoryType) = when (type) {
        com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_ACCELERATOR -> R.string.clicker_factory_proton_accel_name
        com.Atom2Universe.app.crypto.clicker.FactoryType.FUSION_REACTOR     -> R.string.clicker_factory_fusion_reactor_name
        com.Atom2Universe.app.crypto.clicker.FactoryType.HADRON_COLLIDER    -> R.string.clicker_factory_hadron_collider_name
        com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_INJECTOR    -> R.string.clicker_factory_proton_injector_name
        com.Atom2Universe.app.crypto.clicker.FactoryType.PLASMA_CATALYST    -> R.string.clicker_factory_plasma_catalyst_name
        com.Atom2Universe.app.crypto.clicker.FactoryType.SYNCHROTRON        -> R.string.clicker_factory_synchrotron_name
    }

    private fun factoryButtonColor(type: com.Atom2Universe.app.crypto.clicker.FactoryType) = when (type) {
        com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_ACCELERATOR -> 0xFFD97706.toInt()
        com.Atom2Universe.app.crypto.clicker.FactoryType.FUSION_REACTOR     -> 0xFF1D4ED8.toInt()
        com.Atom2Universe.app.crypto.clicker.FactoryType.HADRON_COLLIDER    -> 0xFF7C3AED.toInt()
        com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_INJECTOR    -> 0xFF92400E.toInt()
        com.Atom2Universe.app.crypto.clicker.FactoryType.PLASMA_CATALYST    -> 0xFF1E3A8A.toInt()
        com.Atom2Universe.app.crypto.clicker.FactoryType.SYNCHROTRON        -> 0xFF4C1D95.toInt()
    }

    private fun factoryEffectText(
        type: com.Atom2Universe.app.crypto.clicker.FactoryType,
        counts: Map<com.Atom2Universe.app.crypto.clicker.FactoryType, Int>
    ): String {
        val count = counts[type] ?: 0
        return when (type) {
            com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_ACCELERATOR ->
                getString(R.string.clicker_factory_effect_apc, count * 10)
            com.Atom2Universe.app.crypto.clicker.FactoryType.FUSION_REACTOR ->
                getString(R.string.clicker_factory_effect_aps, count * 10)
            com.Atom2Universe.app.crypto.clicker.FactoryType.HADRON_COLLIDER ->
                getString(R.string.clicker_factory_effect_mixed, count * 15, count * 5)
            com.Atom2Universe.app.crypto.clicker.FactoryType.PROTON_INJECTOR,
            com.Atom2Universe.app.crypto.clicker.FactoryType.PLASMA_CATALYST,
            com.Atom2Universe.app.crypto.clicker.FactoryType.SYNCHROTRON ->
                String.format(java.util.Locale.US, "×%.2f", 1.0 + count * 0.20)
        }
    }

    private fun buildAchievementShopItems() {
        val container = shopAchievementsContainer ?: return
        container.removeAllViews()
        val unlockedIds = clickerViewModel.getUnlockedAchievementIds()
        val all = com.Atom2Universe.app.crypto.clicker.ClickerAchievements.all
        val unlockedCount = all.count { it.id in unlockedIds }
        shopAchievementProgress?.text = "$unlockedCount/${all.size}"

        val dp1 = resources.displayMetrics.density.toInt()
        val dp8 = dp1 * 8
        val dp12 = dp1 * 12

        for (achievement in all) {
            val unlocked = achievement.id in unlockedIds

            // Séparateur
            val divider = View(this)
            divider.setBackgroundColor(0x1A_FFFFFF)
            val divParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp1)
            divParams.topMargin = dp8
            divParams.bottomMargin = dp8
            container.addView(divider, divParams)

            // Ligne principale
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL

            val icon = TextView(this)
            icon.text = if (unlocked) "🏆" else "🔒"
            icon.textSize = 18f
            icon.setPadding(0, 0, dp12, 0)
            row.addView(icon)

            val textBlock = LinearLayout(this)
            textBlock.orientation = LinearLayout.VERTICAL
            val blockParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textBlock.layoutParams = blockParams

            val nameView = TextView(this)
            nameView.text = getString(achievement.nameRes)
            nameView.textSize = 13f
            nameView.setTextColor(if (unlocked) 0xFFE2E8F0.toInt() else 0xFF64748B.toInt())
            nameView.typeface = android.graphics.Typeface.MONOSPACE
            textBlock.addView(nameView)

            val flavorView = TextView(this)
            flavorView.text = getString(achievement.flavorRes)
            flavorView.textSize = 11f
            flavorView.setTextColor(if (unlocked) 0xFF94A3B8.toInt() else 0xFF475569.toInt())
            flavorView.typeface = android.graphics.Typeface.MONOSPACE
            textBlock.addView(flavorView)

            row.addView(textBlock)

            val targetView = TextView(this)
            targetView.text = achievement.targetText
            targetView.textSize = 11f
            targetView.setTextColor(if (unlocked) 0xFFF59E0B.toInt() else 0xFF475569.toInt())
            targetView.typeface = android.graphics.Typeface.MONOSPACE
            targetView.setPadding(dp8, 0, 0, 0)
            row.addView(targetView)

            container.addView(row)
        }
    }

    private fun updateShopViews(state: ClickerGameState) {
        val fmt = java.text.NumberFormat.getNumberInstance(java.util.Locale.FRENCH)

        shopGodFingerLevelView?.text = getString(R.string.clicker_shop_level, state.godFingerLevel)
        shopGodFingerPriceView?.text = clickerViewModel.shopCost("godFinger", shopGodFingerMult).toString()
        shopStarCoreLevelView?.text  = getString(R.string.clicker_shop_level, state.starCoreLevel)
        shopStarCorePriceView?.text  = clickerViewModel.shopCost("starCore", shopStarCoreMult).toString()

        shopNeutrinoBalance?.text    = state.neutrinos.toString()
        shopApcToApsLevelView?.text  = getString(R.string.clicker_shop_level, state.apcToApsLevel)
        shopApcToApsEffectView?.text = getString(R.string.clicker_shop_apc_to_aps_effect, state.apcToApsLevel)
        shopApcToApsCostView?.text   = "${clickerViewModel.apcToApsCost()} ⚛"
        shopApsToApcLevelView?.text  = getString(R.string.clicker_shop_level, state.apsToApcLevel)
        shopApsToApcEffectView?.text = getString(R.string.clicker_shop_aps_to_apc_effect, state.apsToApcLevel)
        shopApsToApcCostView?.text   = "${clickerViewModel.apsToApcCost()} ⚛"

        val tokenBalance = clickerViewModel.getElementTokens()
        shopElemToNeutrinoStock?.text = getString(R.string.clicker_shop_elem_stock, tokenBalance)
        shopElemToNeutrinoBuy?.isEnabled = tokenBalance >= shopElemToNeutrinoMult_value

        shopStatLifetime?.text    = state.lifetime.toString()
        shopStatApc?.text         = state.perClick.toString()
        shopStatAps?.text         = state.perSecond.toString()

        val elem = com.Atom2Universe.app.crypto.clicker.ElementBonusEngine.compute(periodicStore)
        shopStatElemApc?.text     = formatElemBonus(elem.flatApc, elem.multApc)
        shopStatElemAps?.text     = formatElemBonus(elem.flatAps, elem.multAps)

        val stats = clickerViewModel.getStatsSnapshot()
        shopStatTotalClicks?.text  = fmt.format(stats.totalClicks)
        shopStatFrenzies?.text     = "${stats.apcFrenzyCount} / ${stats.apsFrenzyCount}"
        shopStatMaxApcClicks?.text = if (stats.maxClicksPerApcFrenzy > 0) "${stats.maxClicksPerApcFrenzy}" else "—"
        shopStatMaxCps?.text       = if (stats.maxCps > 0) "${stats.maxCps.toInt()} /s" else "—"
        shopStatPlayTime?.text     = formatShopPlayTime(stats.totalPlayTimeMs)

        updateProductionBar(stats)

        for (type in com.Atom2Universe.app.crypto.clicker.FactoryType.values()) {
            val count = state.factoryCounts[type] ?: 0
            val affordable = !clickerViewModel.factoryCost(type).greaterThan(state.atoms)
            shopFactoryCountViews[type]?.text  = "×$count"
            shopFactoryEffectViews[type]?.text = factoryEffectText(type, state.factoryCounts)
            shopFactoryPriceViews[type]?.text  = clickerViewModel.factoryCost(type).toString()
            shopFactoryBuyBtns[type]?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (affordable) 0xFF16A34A.toInt() else 0xFF475569.toInt()
            )
        }
    }

    private fun snapToStep(value: Int, step: Int = 5): Float =
        (Math.round(value.toFloat() / step) * step).toFloat().coerceIn(0f, 100f)

    private fun formatGameStat(played: Int, won: Int): String =
        if (played == 0) "—" else "$won / $played"

    private fun formatBestTime(ms: Long): String {
        if (ms <= 0L) return "—"
        val totalSec = ms / 1000L
        val minutes  = totalSec / 60
        val secs     = totalSec % 60
        return if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"
    }

    private fun formatElemBonus(flat: Int, mult: Double): String {
        val parts = mutableListOf<String>()
        if (flat > 0) parts.add("+$flat flat")
        if (mult > 0.0) parts.add("+${"%.2f".format(mult * 100)}%")
        return if (parts.isEmpty()) "—" else parts.joinToString("  ")
    }

    private fun formatShopPlayTime(ms: Long): String {
        if (ms <= 0L) return "—"
        val totalSec = ms / 1000L
        val hours    = totalSec / 3600
        val minutes  = (totalSec % 3600) / 60
        val secs     = totalSec % 60
        return when {
            hours > 0   -> "${hours}h ${minutes}min"
            minutes > 0 -> "${minutes}min ${secs}s"
            else        -> "${secs}s"
        }
    }

    private fun updateProductionBar(stats: com.Atom2Universe.app.crypto.clicker.ClickerStats) {
        fun pct(a: com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber,
                b: com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber): Pair<Double, Double> {
            val total = a.add(b)
            if (total.isZero()) return 50.0 to 50.0
            val ap = a.divide(total).toNumber() * 100.0
            return ap to (100.0 - ap)
        }
        fun fmtPct(v: Double, hasData: Boolean) = if (!hasData) "—" else "${"%.1f".format(v)} %"
        fun applyBar(bar: android.widget.LinearLayout?, wA: Float, wB: Float) {
            bar ?: return
            val vA = bar.getChildAt(0)
            val vB = bar.getChildAt(1)
            (vA.layoutParams as android.widget.LinearLayout.LayoutParams).weight = wA
            (vB.layoutParams as android.widget.LinearLayout.LayoutParams).weight = wB
            vA.layoutParams = vA.layoutParams
            vB.layoutParams = vB.layoutParams
        }

        val (prodApcPct, prodApsPct) = pct(stats.lifetimeApcAtoms, stats.lifetimeApsAtoms)
        val hasProd = !stats.lifetimeApcAtoms.add(stats.lifetimeApsAtoms).isZero()
        shopStatProdApc?.text = fmtPct(prodApcPct, hasProd)
        shopStatProdAps?.text = fmtPct(prodApsPct, hasProd)
        applyBar(shopProdBar,
            if (hasProd) prodApcPct.toFloat().coerceIn(0f, 100f) else 1f,
            if (hasProd) prodApsPct.toFloat().coerceIn(0f, 100f) else 1f)

    }

    private fun cycleShopMultiplier(current: Int): Int = when (current) {
        1    -> 10
        10   -> 100
        else -> 1
    }

    private fun startRefreshLoop() {
        if (refreshJob?.isActive == true) return
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                fetchCryptoPrices()
                delay(refreshInterval.intervalMs)
            }
        }
    }

    private fun stopRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = null
        activeBtcCall?.cancel()
        activeEthCall?.cancel()
        activeBtcCall = null
        activeEthCall = null
        isLoading = false
    }

    private fun setBannerExpanded(expanded: Boolean) {
        bannerExpanded = expanded
        bannerHandler.removeCallbacks(hideBannerRunnable)
        val vis = if (expanded) View.VISIBLE else View.GONE
        bannerControls.forEach { it.visibility = vis }
        // Tap zone cliquable seulement quand la bannière est fermée
        bannerTapZone.isClickable = !expanded
        bannerTapZone.isFocusable = !expanded
        if (expanded) {
            applyBannerTogglesVisibility()
            scheduleHideBanner()
        }
    }

    private fun scheduleHideBanner() {
        bannerHandler.removeCallbacks(hideBannerRunnable)
        bannerHandler.postDelayed(hideBannerRunnable, 10_000L)
    }

    private fun applyBannerTogglesVisibility() {
        val container = findViewById<LinearLayout>(R.id.main_clicker_toggles_container)
        val order = MainClickerPreferences.getBannerToggleOrder(this)
        container.removeAllViews()
        for (key in order) {
            if (widgetBannerVisibleGetter[key]?.invoke() != true) continue
            val (label, switch) = bannerViewCache[key] ?: continue
            container.addView(label)
            container.addView(switch)
        }
    }

    private fun reloadPreferences() {
        applyBannerTogglesVisibility()
        keepScreenOnEnabled = MainClickerPreferences.isKeepScreenOnEnabled(this)
        backgroundDisplayEnabled = MainClickerPreferences.isBackgroundDisplayEnabled(this)
        applyBackgroundDisplayPreference()
        widgetOpacityPercent = MainClickerPreferences.getWidgetOpacityPercent(this)
        cryptoWidgetView.applyBackgroundOpacity(widgetOpacityPercent)
        applyCryptoWidgetMinimal(MainClickerPreferences.isCryptoWidgetMinimal(this))
        comparisonWindow = CryptoComparisonWindow.fromIndex(
            MainClickerPreferences.getCryptoComparisonWindowIndex(this))

        val newEur = MainClickerPreferences.isCryptoWidgetEur(this)
        if (newEur != useEurCurrency) applyEurCurrency(newEur) else cryptoWidgetView.setCurrencyEur(newEur)

        val eurUsdEnabled = MainClickerPreferences.isCryptoEurUsdEnabled(this)
        cryptoWidgetView.setEurUsdEnabled(eurUsdEnabled)
        cryptoWidgetView.updateEurUsdRate(eurUsdRate, previousEurUsdRate)
        if (eurUsdEnabled) startEurUsdLoop() else stopEurUsdLoop()
        earthMoonWidgetView.alpha = MainClickerPreferences.getEarthWidgetOpacityPercent(this) / 100f
        chessWidgetView.alpha = MainClickerPreferences.getChessWidgetOpacityPercent(this) / 100f
        draughtsWidgetView.alpha = MainClickerPreferences.getDraughtsWidgetOpacityPercent(this) / 100f
        sudokuWidgetView.applyBackgroundOpacity(MainClickerPreferences.getSudokuWidgetOpacityPercent(this))
        sudokuWidgetView.applyNumbersOpacity(MainClickerPreferences.getSudokuNumbersOpacityPercent(this))
        game2048WidgetView.applyBackgroundOpacity(MainClickerPreferences.getGame2048WidgetOpacityPercent(this))
        newsWidgetView.applyBackgroundOpacity(MainClickerPreferences.getNewsWidgetOpacityPercent(this))
        solitaireWidgetView.applyBackgroundOpacity(MainClickerPreferences.getSolitaireWidgetOpacityPercent(this))
        blackjackWidgetView.applyBackgroundOpacity(MainClickerPreferences.getBlackjackWidgetOpacityPercent(this))
        colorStackWidgetView.applyBackgroundOpacity(MainClickerPreferences.getColorStackWidgetOpacityPercent(this))
        musicPlayerWidgetView.applyBackgroundOpacity(MainClickerPreferences.getMusicWidgetOpacityPercent(this))
        backgroundImageView.setAlignCenter(MainClickerPreferences.isImageCenterAligned(this))
        applyKeepScreenOnPreference()

        val newStatusBarVisible = MainClickerPreferences.isStatusBarVisible(this)
        val newNavBarVisible = MainClickerPreferences.isNavBarVisible(this)
        if (newStatusBarVisible != statusBarVisible || newNavBarVisible != navBarVisible) {
            statusBarVisible = newStatusBarVisible
            navBarVisible = newNavBarVisible
            applySystemBarsPreference()
        }

        val newRefreshInterval = MainClickerPreferences.getRefreshInterval(this)
        if (newRefreshInterval != refreshInterval) {
            refreshInterval = newRefreshInterval
            stopRefreshLoop()
            startRefreshLoop()
        }

        val cryptoWidgetEnabled = MainClickerPreferences.isCryptoWidgetEnabled(this)
        if (cryptoToggle.isChecked != cryptoWidgetEnabled) {
            cryptoToggle.setOnCheckedChangeListener(null)
            cryptoToggle.isChecked = cryptoWidgetEnabled
            cryptoToggle.setOnCheckedChangeListener { _, isChecked -> onCryptoToggleChanged(isChecked) }
        }
        cryptoWidgetView.visibility = if (cryptoWidgetEnabled) View.VISIBLE else View.GONE

        val game2048WidgetEnabled = MainClickerPreferences.isGame2048WidgetEnabled(this)
        if (game2048Toggle.isChecked != game2048WidgetEnabled) {
            game2048Toggle.setOnCheckedChangeListener(null)
            game2048Toggle.isChecked = game2048WidgetEnabled
            game2048Toggle.setOnCheckedChangeListener { _, isChecked -> onGame2048ToggleChanged(isChecked) }
        }
        game2048WidgetView.visibility = if (game2048WidgetEnabled) View.VISIBLE else View.GONE

        val newsWidgetEnabled = MainClickerPreferences.isNewsWidgetEnabled(this)
        if (newsToggle.isChecked != newsWidgetEnabled) {
            newsToggle.setOnCheckedChangeListener(null)
            newsToggle.isChecked = newsWidgetEnabled
            newsToggle.setOnCheckedChangeListener { _, isChecked -> onNewsToggleChanged(isChecked) }
        }
        newsWidgetView.setToggleEnabled(newsWidgetEnabled)

        val solitaireWidgetEnabled = MainClickerPreferences.isSolitaireWidgetEnabled(this)
        if (solitaireToggle.isChecked != solitaireWidgetEnabled) {
            solitaireToggle.setOnCheckedChangeListener(null)
            solitaireToggle.isChecked = solitaireWidgetEnabled
            solitaireToggle.setOnCheckedChangeListener { _, isChecked -> onSolitaireToggleChanged(isChecked) }
        }
        solitaireWidgetView.visibility = if (solitaireWidgetEnabled) View.VISIBLE else View.GONE

        val blackjackWidgetEnabled = MainClickerPreferences.isBlackjackWidgetEnabled(this)
        if (blackjackToggle.isChecked != blackjackWidgetEnabled) {
            blackjackToggle.setOnCheckedChangeListener(null)
            blackjackToggle.isChecked = blackjackWidgetEnabled
            blackjackToggle.setOnCheckedChangeListener { _, isChecked -> onBlackjackToggleChanged(isChecked) }
        }
        blackjackWidgetView.visibility = if (blackjackWidgetEnabled) View.VISIBLE else View.GONE

        val colorStackWidgetEnabled = MainClickerPreferences.isColorStackWidgetEnabled(this)
        if (colorStackToggle.isChecked != colorStackWidgetEnabled) {
            colorStackToggle.setOnCheckedChangeListener(null)
            colorStackToggle.isChecked = colorStackWidgetEnabled
            colorStackToggle.setOnCheckedChangeListener { _, isChecked -> onColorStackToggleChanged(isChecked) }
        }
        colorStackWidgetView.visibility = if (colorStackWidgetEnabled) View.VISIBLE else View.GONE

        val theLineWidgetEnabled = MainClickerPreferences.isTheLineWidgetEnabled(this)
        if (theLineToggle.isChecked != theLineWidgetEnabled) {
            theLineToggle.setOnCheckedChangeListener(null)
            theLineToggle.isChecked = theLineWidgetEnabled
            theLineToggle.setOnCheckedChangeListener { _, isChecked ->
                MainClickerPreferences.setTheLineWidgetEnabled(this, isChecked)
                theLineWidgetView.visibility = if (isChecked) View.VISIBLE else View.GONE
                if (isChecked) theLineWidgetView.reload()
            }
        }
        theLineWidgetView.visibility = if (theLineWidgetEnabled) View.VISIBLE else View.GONE

        val musicWidgetEnabled = MainClickerPreferences.isMusicWidgetEnabled(this)
        if (musicToggle.isChecked != musicWidgetEnabled) {
            musicToggle.setOnCheckedChangeListener(null)
            musicToggle.isChecked = musicWidgetEnabled
            musicToggle.setOnCheckedChangeListener { _, isChecked -> onMusicToggleChanged(isChecked) }
        }
        musicPlayerWidgetView.visibility = if (musicWidgetEnabled) View.VISIBLE else View.GONE

        val earthWidgetEnabled = MainClickerPreferences.isEarthWidgetEnabled(this)
        if (earthToggle.isChecked != earthWidgetEnabled) {
            earthToggle.setOnCheckedChangeListener(null)
            earthToggle.isChecked = earthWidgetEnabled
            earthToggle.setOnCheckedChangeListener { _, isChecked -> onEarthToggleChanged(isChecked) }
        }
        earthMoonWidgetView.visibility = if (earthWidgetEnabled) View.VISIBLE else View.GONE
        earthMoonWidgetView.setEarthOnlyMode(MainClickerPreferences.isEarthOnlyMode(this))
        earthMoonWidgetView.setShowClouds(MainClickerPreferences.isEarthShowClouds(this))
        earthMoonWidgetView.setShowTerminator(MainClickerPreferences.isEarthShowTerminator(this))
        val fixedLocIndex = MainClickerPreferences.getEarthFixedLocationIndex(this)
        val fixedPreset = EarthMoonCanvasView.LOCATION_PRESETS.getOrElse(fixedLocIndex) { EarthMoonCanvasView.LOCATION_PRESETS[0] }
        earthMoonWidgetView.setFixedLocation(fixedPreset.latDeg, fixedPreset.lonDeg, fixedPreset.name)
        if (earthWidgetEnabled) startAstronomyLoop() else stopAstronomyLoop()
    }

    private fun applyBackgroundDisplayPreference() {
        backgroundImageView.visibility = if (backgroundDisplayEnabled) View.VISIBLE else View.INVISIBLE
        starfieldView.visibility = if (backgroundDisplayEnabled) View.GONE else View.VISIBLE
        if (backgroundDisplayEnabled) {
            updateAutoBackgroundLoop()
        } else {
            stopBackgroundLoop()
        }
    }

    private fun applyKeepScreenOnPreference() {
        if (keepScreenOnEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        cryptoWidgetView.keepScreenOn = keepScreenOnEnabled
        backgroundImageView.keepScreenOn = keepScreenOnEnabled
    }

    private fun applySystemBarsPreference() {
        applySystemBarsVisibility(showStatusBar = statusBarVisible, showNavBar = navBarVisible)
    }

    private fun reloadBackground(force: Boolean) {
        backgroundFolderUri = MainClickerPreferences.getFolderUri(this)
        slideshowIntervalMs = MainClickerPreferences.getSlideshowMinutes(this) * 60_000L
        favoritesMode = MainClickerPreferences.isFavoritesModeEnabled(this)

        if (favoritesMode) {
            lifecycleScope.launch {
                val favUris = withContext(Dispatchers.IO) { backgroundRepository.getAllFavoriteUris() }
                if (favUris.isEmpty()) {
                    showToast(getString(R.string.crypto_favorites_empty_toast))
                    favoritesMode = false
                    MainClickerPreferences.setFavoritesModeEnabled(this@MainClickerActivity, false)
                    reloadBackground(force)
                    return@launch
                }
                shuffleManager.loadFromFavorites(favUris)
                showNextBackgroundImage(restartTimer = false, notifyIfMissing = false)
                startBackgroundLoop()
            }
            return
        }

        lifecycleScope.launch {
            val result = shuffleManager.reloadFromFolder(this@MainClickerActivity, backgroundFolderUri, force)
            lastReloadReason = result.reason

            when (result.reason) {
                MainClickerShuffleManager.ReloadReason.LOADED -> {
                    showNextBackgroundImage(restartTimer = false, notifyIfMissing = false)
                    if (force) {
                        showToast(getString(R.string.crypto_background_loaded_toast, result.imageCount))
                    }
                }

                MainClickerShuffleManager.ReloadReason.UNCHANGED -> {
                    val currentUri = shuffleManager.currentUri()
                    if (currentUri == null) {
                        showNextBackgroundImage(restartTimer = false, notifyIfMissing = false)
                    } else {
                        if (force || backgroundImageView.drawable == null) {
                            setBackgroundImage(
                                uri = currentUri,
                                advanceOnFailure = true
                            )
                        }
                        updateAutoBackgroundLoop()
                    }
                }

                MainClickerShuffleManager.ReloadReason.NO_FOLDER -> {
                    backgroundImageView.setImageDrawable(null)
                    updateAutoBackgroundLoop()
                    if (force) {
                        showToast(getString(R.string.crypto_background_missing_folder_toast))
                    }
                }

                MainClickerShuffleManager.ReloadReason.NO_IMAGES -> {
                    backgroundImageView.setImageDrawable(null)
                    updateAutoBackgroundLoop()
                    if (force) {
                        showToast(getString(R.string.crypto_background_no_images_toast))
                    }
                }

                MainClickerShuffleManager.ReloadReason.ERROR -> {
                    backgroundImageView.setImageDrawable(null)
                    updateAutoBackgroundLoop()
                    if (force) {
                        showToast(getString(R.string.crypto_background_load_error_toast))
                    }
                }
            }
        }
    }

    private fun updateAutoBackgroundLoop() {
        stopBackgroundLoop()
        startBackgroundLoop()
    }

    private fun startBackgroundLoop() {
        if (!backgroundDisplayEnabled) return
        if (slideshowIntervalMs <= 0L) return
        if (shuffleManager.imageCount() == 0) return
        if (backgroundJob?.isActive == true) return

        backgroundJob = lifecycleScope.launch {
            while (isActive) {
                delay(slideshowIntervalMs)
                showNextBackgroundImage(restartTimer = false, notifyIfMissing = false)
            }
        }
    }

    private fun stopBackgroundLoop() {
        backgroundJob?.cancel()
        backgroundJob = null
    }

    private fun showNextBackgroundImage(restartTimer: Boolean, notifyIfMissing: Boolean) {
        val nextUri = shuffleManager.nextUri()
        if (nextUri == null) {
            if (notifyIfMissing) {
                notifyMissingBackgroundSource()
            }
            updateAutoBackgroundLoop()
            return
        }
        setBackgroundImage(
            uri = nextUri,
            advanceOnFailure = true
        )
        if (restartTimer) {
            updateAutoBackgroundLoop()
        } else {
            startBackgroundLoop()
        }
    }

    private fun showPreviousBackgroundImage(restartTimer: Boolean, notifyIfMissing: Boolean) {
        val previousUri = shuffleManager.previousUri()
        if (previousUri == null) {
            if (notifyIfMissing) {
                notifyMissingBackgroundSource()
            }
            updateAutoBackgroundLoop()
            return
        }
        setBackgroundImage(
            uri = previousUri,
            advanceOnFailure = true
        )
        if (restartTimer) {
            updateAutoBackgroundLoop()
        } else {
            startBackgroundLoop()
        }
    }

    private fun notifyMissingBackgroundSource() {
        val messageRes = when (lastReloadReason) {
            MainClickerShuffleManager.ReloadReason.NO_FOLDER -> R.string.crypto_background_missing_folder_toast
            MainClickerShuffleManager.ReloadReason.NO_IMAGES -> R.string.crypto_background_no_images_toast
            MainClickerShuffleManager.ReloadReason.ERROR -> R.string.crypto_background_load_error_toast
            else -> R.string.crypto_background_missing_folder_toast
        }
        showToast(getString(messageRes))
    }

    private fun setBackgroundImage(
        uri: Uri,
        advanceOnFailure: Boolean = true
    ) {
        val requestToken = ++backgroundRequestToken
        lifecycleScope.launch {
            val bitmap = decodeBackgroundBitmap(uri)
            if (requestToken != backgroundRequestToken) return@launch
            if (bitmap != null) {
                backgroundImageView.setImageBitmap(bitmap)
                shuffleManager.persistPlaybackState()
            } else {
                handleBackgroundDecodeFailure(uri, advanceOnFailure, requestToken)
            }
        }
    }

    private fun handleBackgroundDecodeFailure(
        uri: Uri,
        advanceOnFailure: Boolean,
        requestToken: Int
    ) {
        if (!advanceOnFailure) {
            showToast(getString(R.string.crypto_background_load_error_toast))
            return
        }

        lifecycleScope.launch {
            val removed = shuffleManager.removeInvalidUri(uri)
            if (requestToken != backgroundRequestToken) return@launch

            if (!removed) {
                showToast(getString(R.string.crypto_background_load_error_toast))
                return@launch
            }

            if (shuffleManager.imageCount() == 0) {
                backgroundImageView.setImageDrawable(null)
                updateAutoBackgroundLoop()
                notifyMissingBackgroundSource()
                return@launch
            }

            showNextBackgroundImage(restartTimer = false, notifyIfMissing = false)
        }
    }

    private suspend fun decodeBackgroundBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val metrics = resources.displayMetrics
            val targetWidth = metrics.widthPixels.coerceAtLeast(1)
            val targetHeight = metrics.heightPixels.coerceAtLeast(1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(uri, targetWidth, targetHeight)
            } else {
                decodeWithBitmapFactory(uri, targetWidth, targetHeight)
            }
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val sampleSize = calculateSampleSize(info.size.width, info.size.height, targetWidth, targetHeight)
            decoder.setTargetSampleSize(sampleSize)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    private fun decodeWithBitmapFactory(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) return null

        val sampleSize = calculateSampleSize(width, height, targetWidth, targetHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        val safeTargetWidth = targetWidth.coerceAtLeast(1)
        val safeTargetHeight = targetHeight.coerceAtLeast(1)
        val widthRatio = ceil(width.toDouble() / safeTargetWidth.toDouble()).toInt()
        val heightRatio = ceil(height.toDouble() / safeTargetHeight.toDouble()).toInt()
        return max(widthRatio, heightRatio).coerceAtLeast(1)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private suspend fun fetchCryptoPrices() {
        if (isLoading) return
        isLoading = true
        cryptoWidgetView.showLoading()

        val controllerBtc = activeBtcCall
        val controllerEth = activeEthCall
        controllerBtc?.cancel()
        controllerEth?.cancel()

        try {
            val window = comparisonWindow
            val fetchKlines = window.usesKlines && klineRefStale()
            val (btcPrice, ethPrice, freshBtcRef, freshEthRef) = withContext(Dispatchers.IO) {
                val btcDeferred = async { fetchCryptoWidgetPrice(btcEndpoint) }
                val ethDeferred = async { fetchCryptoWidgetPrice(ethEndpoint) }
                val btcRefDeferred = if (fetchKlines) async { fetchKlineOpenPrice(btcSymbol, window) } else null
                val ethRefDeferred = if (fetchKlines) async { fetchKlineOpenPrice(ethSymbol, window) } else null
                FourDoubles(btcDeferred.await(), ethDeferred.await(), btcRefDeferred?.await(), ethRefDeferred?.await())
            }

            if (fetchKlines) {
                btcKlineRef = freshBtcRef
                ethKlineRef = freshEthRef
                lastKlineRefFetchMs = System.currentTimeMillis()
            }

            val prevBtc = if (window.usesKlines) btcKlineRef else btcPriceUsd?.takeIf { it.isFinite() }
            val prevEth = if (window.usesKlines) ethKlineRef else ethPriceUsd?.takeIf { it.isFinite() }

            previousBtcPriceUsd = prevBtc
            previousEthPriceUsd = prevEth
            btcPriceUsd = btcPrice
            ethPriceUsd = ethPrice

            cryptoWidgetView.updatePrices(btcPriceUsd, previousBtcPriceUsd, ethPriceUsd, previousEthPriceUsd)
            cryptoWidgetView.clearStatus()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cryptoWidgetView.showError()
        } finally {
            activeBtcCall = null
            activeEthCall = null
            isLoading = false
        }
    }

    private data class FourDoubles(val a: Double, val b: Double, val c: Double?, val d: Double?)

    private suspend fun fetchKlineOpenPrice(symbol: String, window: CryptoComparisonWindow): Double? {
        val url = "$CRYPTO_WIDGET_BASE_URL/api/v3/klines?symbol=$symbol&interval=${window.klineInterval}&limit=${window.klineLimit}"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) { response.close(); return null }
        val body = response.body?.string().orEmpty()
        response.close()
        return runCatching {
            org.json.JSONArray(body).getJSONArray(0).getString(1).toDoubleOrNull()
        }.getOrNull()
    }

    private suspend fun fetchCryptoWidgetPrice(endpoint: String): Double {
        val url = buildCryptoWidgetUrl(endpoint)
        val request = Request.Builder().url(url).build()
        val call = httpClient.newCall(request)
        if (endpoint == btcEndpoint) {
            activeBtcCall = call
        } else if (endpoint == ethEndpoint) {
            activeEthCall = call
        }

        val response = call.await()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        response.close()

        val price = JSONObject(body).optString("price").toDoubleOrNull()
        return price?.takeIf { it.isFinite() } ?: throw IllegalStateException("Invalid price payload")
    }

    private fun buildCryptoWidgetUrl(endpoint: String): String {
        val normalized = endpoint.trim()
        if (normalized.startsWith("http://", ignoreCase = true) || normalized.startsWith("https://", ignoreCase = true)) {
            return normalized
        }
        val base = CRYPTO_WIDGET_BASE_URL.trimEnd('/')
        if (normalized.isEmpty()) {
            return base
        }
        return if (normalized.startsWith('/')) {
            base + normalized
        } else {
            "$base/$normalized"
        }
    }

    private fun onCryptoToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setCryptoWidgetEnabled(this, enabled)
        cryptoWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun onEarthToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setEarthWidgetEnabled(this, enabled)
        earthMoonWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) startAstronomyLoop() else stopAstronomyLoop()
    }

    private fun onChessToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setChessWidgetEnabled(this, enabled)
        chessWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) chessWidgetView.reload()
    }

    private fun onDraughtsToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setDraughtsWidgetEnabled(this, enabled)
        draughtsWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) draughtsWidgetView.reload()
    }

    private fun onSudokuToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setSudokuWidgetEnabled(this, enabled)
        sudokuWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) sudokuWidgetView.reload(lifecycleScope)
    }

    private fun onGame2048ToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setGame2048WidgetEnabled(this, enabled)
        game2048WidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) game2048WidgetView.reload()
    }

    private fun onNewsToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setNewsWidgetEnabled(this, enabled)
        newsWidgetView.setToggleEnabled(enabled)
        if (enabled) newsWidgetView.refresh()
    }

    private fun onSolitaireToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setSolitaireWidgetEnabled(this, enabled)
        solitaireWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) solitaireWidgetView.reload(lifecycleScope)
    }

    private fun onBlackjackToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setBlackjackWidgetEnabled(this, enabled)
        blackjackWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) blackjackWidgetView.reload()
    }

    private fun onColorStackToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setColorStackWidgetEnabled(this, enabled)
        colorStackWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) colorStackWidgetView.reload()
    }

    private fun onMusicToggleChanged(enabled: Boolean) {
        MainClickerPreferences.setMusicWidgetEnabled(this, enabled)
        musicPlayerWidgetView.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun startAstronomyLoop() {
        if (astronomyJob?.isActive == true) return
        astronomyJob = lifecycleScope.launch {
            while (isActive) {
                val snap = withContext(Dispatchers.Default) {
                    AstronomyCalculator.compute(System.currentTimeMillis())
                }
                earthMoonWidgetView.updateSnapshot(snap)
                delay(10 * 60 * 1000L)  // mise à jour toutes les 10 minutes
            }
        }
    }

    private fun stopAstronomyLoop() {
        astronomyJob?.cancel()
        astronomyJob = null
    }

    private fun startEurUsdLoop() {
        if (eurUsdJob?.isActive == true) return
        eurUsdJob = lifecycleScope.launch {
            while (isActive) {
                fetchEurUsdRate()
                delay(EURUSD_REFRESH_MS)
            }
        }
    }

    private fun stopEurUsdLoop() {
        eurUsdJob?.cancel()
        eurUsdJob = null
    }

    private suspend fun fetchEurUsdRate() {
        try {
            val rate = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(EURUSD_URL).build()
                val response = httpClient.newCall(request).await()
                if (!response.isSuccessful) { response.close(); return@withContext null }
                val body = response.body?.string().orEmpty()
                response.close()
                JSONObject(body).optJSONObject("rates")?.optString("USD")?.toDoubleOrNull()
            }
            previousEurUsdRate = eurUsdRate?.takeIf { it.isFinite() }
            eurUsdRate = rate
            cryptoWidgetView.updateEurUsdRate(eurUsdRate, previousEurUsdRate)
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Exception) {
            // silencieux : le taux garde sa dernière valeur connue
        }
    }

    // ── Popups d'opacité sur long-press des labels de la bannière ────────────

    private fun setupOpacityLongPress() {
        findViewById<TextView>(R.id.main_clicker_label_earth).setOnClickListener { anchor ->
            showEarthWidgetPopup(anchor)
        }
        findViewById<TextView>(R.id.main_clicker_label_chess).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_chess_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getChessWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setChessWidgetOpacityPercent(this, it) },
                applyValue1 = { chessWidgetView.alpha = it / 100f }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_draughts).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_draughts_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getDraughtsWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setDraughtsWidgetOpacityPercent(this, it) },
                applyValue1 = { draughtsWidgetView.alpha = it / 100f }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_sudoku).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_sudoku_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getSudokuWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setSudokuWidgetOpacityPercent(this, it) },
                applyValue1 = { sudokuWidgetView.applyBackgroundOpacity(it) },
                label2 = getString(R.string.crypto_sudoku_numbers_opacity_label),
                getValue2 = { MainClickerPreferences.getSudokuNumbersOpacityPercent(this) },
                setValue2 = { MainClickerPreferences.setSudokuNumbersOpacityPercent(this, it) },
                applyValue2 = { sudokuWidgetView.applyNumbersOpacity(it) }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_2048).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_game2048_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getGame2048WidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setGame2048WidgetOpacityPercent(this, it) },
                applyValue1 = { game2048WidgetView.applyBackgroundOpacity(it) }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_crypto).setOnClickListener { anchor ->
            showCryptoWidgetPopup(anchor)
        }
        findViewById<TextView>(R.id.main_clicker_label_news).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_news_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getNewsWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setNewsWidgetOpacityPercent(this, it) },
                applyValue1 = { newsWidgetView.applyBackgroundOpacity(it) }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_solitaire).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_solitaire_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getSolitaireWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setSolitaireWidgetOpacityPercent(this, it) },
                applyValue1 = { solitaireWidgetView.applyBackgroundOpacity(it) }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_blackjack).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_blackjack_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getBlackjackWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setBlackjackWidgetOpacityPercent(this, it) },
                applyValue1 = { blackjackWidgetView.applyBackgroundOpacity(it) }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_color_stack).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_color_stack_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getColorStackWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setColorStackWidgetOpacityPercent(this, it) },
                applyValue1 = { colorStackWidgetView.applyBackgroundOpacity(it) }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_clicker).setOnClickListener { anchor ->
            showClickerPopup(anchor)
        }
        findViewById<TextView>(R.id.main_clicker_label_music).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_music_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getMusicWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setMusicWidgetOpacityPercent(this, it) },
                applyValue1 = { musicPlayerWidgetView.applyBackgroundOpacity(it) }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_pipetap).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_pipetap_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getPipeTapWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setPipeTapWidgetOpacityPercent(this, it) },
                applyValue1 = { pipeTapWidgetView.applyBackgroundOpacity(it) }
            )
        }
        findViewById<TextView>(R.id.main_clicker_label_the_line).setOnClickListener { anchor ->
            showOpacityPopup(
                anchor = anchor,
                label1 = getString(R.string.crypto_the_line_widget_opacity_label),
                getValue1 = { MainClickerPreferences.getTheLineWidgetOpacityPercent(this) },
                setValue1 = { MainClickerPreferences.setTheLineWidgetOpacityPercent(this, it) },
                applyValue1 = { theLineWidgetView.applyBackgroundOpacity(it) }
            )
        }
    }

    private fun showClickerPopup(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_opacity_sliders, null)

        val lbl1 = popupView.findViewById<TextView>(R.id.popup_label_1)
        val val1 = popupView.findViewById<TextView>(R.id.popup_value_1)
        val slider1 = popupView.findViewById<Slider>(R.id.popup_slider_1)
        val container2 = popupView.findViewById<View>(R.id.popup_slider2_container)
        val lbl2 = popupView.findViewById<TextView>(R.id.popup_label_2)
        val val2 = popupView.findViewById<TextView>(R.id.popup_value_2)
        val slider2 = popupView.findViewById<Slider>(R.id.popup_slider_2)
        val container3 = popupView.findViewById<View>(R.id.popup_slider3_container)
        val lbl3 = popupView.findViewById<TextView>(R.id.popup_label_3)
        val val3 = popupView.findViewById<TextView>(R.id.popup_value_3)
        val slider3 = popupView.findViewById<Slider>(R.id.popup_slider_3)

        // Slider opacité (0–100)
        lbl1.text = getString(R.string.crypto_label_banner_opacity)
        val currentOpacity = MainClickerPreferences.getClickerOpacityPercent(this)
        slider1.value = currentOpacity.toFloat()
        val1.text = getString(R.string.crypto_widget_opacity_value, currentOpacity)
        slider1.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val pct = value.roundToInt()
            MainClickerPreferences.setClickerOpacityPercent(this, pct)
            clickerBannerView.applyOpacity(pct)
            val1.text = getString(R.string.crypto_widget_opacity_value, pct)
        }

        // Slider taille (36–200 dp)
        container2.visibility = View.VISIBLE
        slider2.visibility = View.VISIBLE
        slider2.valueFrom = 36f
        slider2.valueTo = 200f
        slider2.stepSize = 4f
        lbl2.text = getString(R.string.atom_banner_height)
        val currentHeight = MainClickerPreferences.getClickerHeightDp(this)
        slider2.value = currentHeight.toFloat()
        val2.text = getString(R.string.atom_banner_height_value, currentHeight)
        slider2.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val dp = value.roundToInt()
            MainClickerPreferences.setClickerHeightDp(this, dp)
            clickerBannerView.applySize(dp)
            val2.text = getString(R.string.atom_banner_height_value, dp)
        }

        // Slider décimales (0–5)
        container3.visibility = View.VISIBLE
        slider3.visibility = View.VISIBLE
        lbl3.text = getString(R.string.crypto_label_decimals)
        val currentDigits = MainClickerPreferences.getClickerDecimalDigits(this)
        slider3.value = currentDigits.toFloat()
        val3.text = "$currentDigits"
        slider3.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val d = value.roundToInt()
            MainClickerPreferences.setClickerDecimalDigits(this, d)
            com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber.mantissaFractionDigits = d
            val3.text = "$d"
        }

        // Toggle atome animé
        val toggleContainer = popupView.findViewById<View>(R.id.popup_toggle_container)
        val toggleLabel = popupView.findViewById<android.widget.TextView>(R.id.popup_toggle_label)
        val toggleSwitch = popupView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.popup_toggle_switch)
        toggleContainer.visibility = View.VISIBLE
        toggleLabel.text = getString(R.string.atom_animated)
        toggleSwitch.isChecked = MainClickerPreferences.isAtomSpringEnabled(this)
        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setAtomSpringEnabled(this, isChecked)
            atomSpringView.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Toggle animation réduite
        val toggle2Container = popupView.findViewById<View>(R.id.popup_toggle2_container)
        val toggle2Label = popupView.findViewById<android.widget.TextView>(R.id.popup_toggle2_label)
        val toggle2Switch = popupView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.popup_toggle2_switch)
        toggle2Container.visibility = View.VISIBLE
        toggle2Label.text = getString(R.string.atom_low_animation)
        toggle2Switch.isChecked = MainClickerPreferences.isAtomLowAnimation(this)
        toggle2Switch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setAtomLowAnimation(this, isChecked)
            atomSpringView.setLowAnimation(isChecked)
        }

        // Toggle image agrandie
        val toggle3Container = popupView.findViewById<View>(R.id.popup_toggle3_container)
        val toggle3Label = popupView.findViewById<android.widget.TextView>(R.id.popup_toggle3_label)
        val toggle3Switch = popupView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.popup_toggle3_switch)
        toggle3Container.visibility = View.VISIBLE
        toggle3Label.text = getString(R.string.atom_bigger_image)
        toggle3Switch.isChecked = MainClickerPreferences.isAtomBiggerImage(this)
        toggle3Switch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setAtomBiggerImage(this, isChecked)
            atomSpringView.setBiggerImage(isChecked)
        }

        // Bouton sélection dossier images personnalisées pour l'atome
        val folderBtn = popupView.findViewById<Button>(R.id.popup_folder_btn)
        folderBtn.visibility = View.VISIBLE
        val hasCustomFolder = MainClickerPreferences.getCustomAtomFolderUri(this) != null
        folderBtn.text = getString(
            if (hasCustomFolder) R.string.atom_custom_folder_change else R.string.atom_custom_folder_pick
        )
        folderBtn.setOnClickListener {
            if (MainClickerPreferences.getCustomAtomFolderUri(this) != null) {
                AlertDialog.Builder(this)
                    .setItems(arrayOf(
                        getString(R.string.atom_custom_folder_clear),
                        getString(R.string.atom_custom_folder_pick_new)
                    )) { _, which ->
                        if (which == 0) {
                            MainClickerPreferences.setCustomAtomFolderUri(this, null)
                            atomSpringView.setCustomImageUris(emptyList())
                            folderBtn.text = getString(R.string.atom_custom_folder_pick)
                        } else {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            }
                            openAtomFolderLauncher.launch(intent)
                        }
                    }
                    .show()
            } else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                openAtomFolderLauncher.launch(intent)
            }
        }

        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isOutsideTouchable = true
        popup.elevation = 16f
        popup.showAsDropDown(anchor, 0, 4)
    }

    // ── Dossier d'images ──────────────────────────────────────────────────────

    private fun openFolderPicker(currentFolder: Uri?) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && currentFolder != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, currentFolder)
            }
        }
        openFolderLauncher.launch(intent)
    }

    private fun persistFolderPermission(uri: Uri, flags: Int) {
        val persistedFlags = flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (persistedFlags == 0) return
        try {
            contentResolver.takePersistableUriPermission(uri, persistedFlags)
        } catch (_: SecurityException) {}
    }

    private fun updateFolderValueView(textView: TextView, folderUri: Uri?) {
        if (folderUri == null) {
            textView.text = getString(R.string.crypto_background_no_folder)
            return
        }
        lifecycleScope.launch {
            val displayName = withContext(Dispatchers.IO) { resolveFolderName(folderUri) }
            textView.text = getString(R.string.crypto_background_folder_selected, displayName)
        }
    }

    private fun resolveFolderName(uri: Uri): String {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        val name = documentFile?.name
        if (!name.isNullOrBlank()) return name
        val lastSegment = uri.lastPathSegment.orEmpty()
        return lastSegment.substringAfterLast(":").substringAfterLast("/").ifBlank { uri.toString() }
    }

    private fun uriToDisplayPath(uri: Uri): String {
        val lastSegment = uri.lastPathSegment ?: return uri.toString()
        // ExternalStorage URIs: "primary:DCIM/Camera/image.jpg"
        val path = if (lastSegment.contains(':')) lastSegment.substringAfter(':') else lastSegment
        return path
    }

    private fun applyCustomAtomFolder(folderUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val uris = listImageUrisFromFolder(folderUri)
            withContext(Dispatchers.Main) {
                atomSpringView.setCustomImageUris(uris)
            }
        }
    }

    private fun listImageUrisFromFolder(folderUri: Uri): List<Uri> {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocId)
            val uris = mutableListOf<Uri>()
            contentResolver.query(
                childUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeCol) ?: continue
                    if (mime.startsWith("image/")) {
                        val docId = cursor.getString(idCol)
                        uris.add(DocumentsContract.buildDocumentUriUsingTree(folderUri, docId))
                    }
                }
            }
            uris.sortedBy { it.lastPathSegment }
        } catch (_: Exception) { emptyList() }
    }

    private fun openCurrentBackgroundImageOptions() {
        val uri = shuffleManager.currentUri() ?: return
        showImageOptionsDialog(uri)
    }

    // ── Dialog options image (long press) ────────────────────────────────────

    private fun showImageOptionsDialog(uri: Uri) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_image_options, null)

        view.findViewById<TextView>(R.id.image_options_path).text = uriToDisplayPath(uri)

        val btnFavorite = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_favorite)
        val btnRemove = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_remove_slideshow)
        val btnDelete = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_device)

        lifecycleScope.launch {
            val isFav = withContext(Dispatchers.IO) { backgroundRepository.isFavorite(uri) }
            btnFavorite.text = getString(
                if (isFav) R.string.crypto_image_remove_favorite else R.string.crypto_image_add_favorite
            )
        }

        btnFavorite.setOnClickListener {
            lifecycleScope.launch {
                val isFav = withContext(Dispatchers.IO) { backgroundRepository.isFavorite(uri) }
                withContext(Dispatchers.IO) {
                    if (isFav) backgroundRepository.removeFavorite(uri)
                    else backgroundRepository.addFavorite(uri)
                }
                showToast(getString(
                    if (isFav) R.string.crypto_image_favorite_removed else R.string.crypto_image_favorite_added
                ))
                dialog.dismiss()
            }
        }

        btnRemove.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                shuffleManager.removeInvalidUri(uri)
                if (favoritesMode) {
                    withContext(Dispatchers.IO) { backgroundRepository.removeFavorite(uri) }
                }
                showNextBackgroundImage(restartTimer = true, notifyIfMissing = false)
            }
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle(R.string.crypto_image_delete_confirm_title)
                .setMessage(R.string.crypto_image_delete_confirm_msg)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            try {
                                DocumentsContract.deleteDocument(contentResolver, uri)
                            } catch (e: Exception) {
                                false
                            }
                        }
                        if (deleted) {
                            shuffleManager.removeInvalidUri(uri)
                            if (favoritesMode) {
                                withContext(Dispatchers.IO) { backgroundRepository.removeFavorite(uri) }
                            }
                            showNextBackgroundImage(restartTimer = true, notifyIfMissing = false)
                            showToast(getString(R.string.crypto_image_deleted_toast))
                        } else {
                            showToast(getString(R.string.crypto_image_delete_failed_toast))
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ── Dialog Réglages ───────────────────────────────────────────────────────

    private fun showSettingsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_crypto_settings, null)

        val folderView = view.findViewById<TextView>(R.id.settings_folder_value)
        settingsDialogFolderView = folderView
        updateFolderValueView(folderView, MainClickerPreferences.getFolderUri(this))

        view.findViewById<View>(R.id.settings_choose_folder_btn).setOnClickListener {
            dialog.dismiss()
            openFolderPicker(MainClickerPreferences.getFolderUri(this))
        }

        fun setupSwitch(id: Int, getter: () -> Boolean, setter: (Boolean) -> Unit, onChanged: (() -> Unit)? = null) {
            val sw = view.findViewById<SwitchMaterial>(id)
            sw.isChecked = getter()
            sw.setOnCheckedChangeListener { _, isChecked -> setter(isChecked); onChanged?.invoke() }
        }

        setupSwitch(R.id.settings_background_enabled,
            { MainClickerPreferences.isBackgroundDisplayEnabled(this) },
            { MainClickerPreferences.setBackgroundDisplayEnabled(this, it) },
            { backgroundDisplayEnabled = MainClickerPreferences.isBackgroundDisplayEnabled(this); applyBackgroundDisplayPreference() }
        )

        val currentImageNameView = view.findViewById<TextView>(R.id.settings_current_image_name)
        val currentUri = shuffleManager.currentUri()
        if (currentUri != null) {
            val displayPath = uriToDisplayPath(currentUri)
            if (displayPath.isNotBlank()) {
                currentImageNameView.text = displayPath
                currentImageNameView.visibility = View.VISIBLE
            }
        }

        val slideshowValueView = view.findViewById<TextView>(R.id.settings_slideshow_value)
        val slideshowSlider = view.findViewById<Slider>(R.id.settings_slideshow_slider)
        val currentMinutes = MainClickerPreferences.getSlideshowMinutes(this)
        slideshowSlider.value = minutesToSliderIndex(currentMinutes).toFloat()
        slideshowValueView.text = slideshowDurationLabel(currentMinutes)
        slideshowSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val minutes = sliderIndexToMinutes(value.toInt())
            MainClickerPreferences.setSlideshowMinutes(this, minutes)
            slideshowIntervalMs = minutes * 60_000L
            slideshowValueView.text = slideshowDurationLabel(minutes)
            updateAutoBackgroundLoop()
        }

        val imageCenterSwitch = view.findViewById<SwitchMaterial>(R.id.settings_image_center)
        imageCenterSwitch.isChecked = MainClickerPreferences.isImageCenterAligned(this)
        imageCenterSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setImageCenterAligned(this, isChecked)
            backgroundImageView.setAlignCenter(isChecked)
        }

        val favoritesModeSwitch = view.findViewById<SwitchMaterial>(R.id.settings_favorites_mode)
        favoritesModeSwitch.isChecked = MainClickerPreferences.isFavoritesModeEnabled(this)
        favoritesModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setFavoritesModeEnabled(this, isChecked)
            favoritesMode = isChecked
            reloadBackground(force = true)
        }

        setupSwitch(R.id.settings_keep_screen_on,
            { MainClickerPreferences.isKeepScreenOnEnabled(this) },
            { MainClickerPreferences.setKeepScreenOnEnabled(this, it) },
            { keepScreenOnEnabled = MainClickerPreferences.isKeepScreenOnEnabled(this); applyKeepScreenOnPreference() }
        )
        setupSwitch(R.id.settings_status_bar,
            { MainClickerPreferences.isStatusBarVisible(this) },
            { MainClickerPreferences.setStatusBarVisible(this, it) },
            { statusBarVisible = MainClickerPreferences.isStatusBarVisible(this); applySystemBarsPreference() }
        )
        setupSwitch(R.id.settings_nav_bar,
            { MainClickerPreferences.isNavBarVisible(this) },
            { MainClickerPreferences.setNavBarVisible(this, it) },
            { navBarVisible = MainClickerPreferences.isNavBarVisible(this); applySystemBarsPreference() }
        )
        val bannerList = view.findViewById<RecyclerView>(R.id.settings_banner_list)
        bannerList.layoutManager = LinearLayoutManager(this)
        val bannerItems = MainClickerPreferences.getBannerToggleOrder(this).mapNotNull { key ->
            val labelRes = widgetLabelRes[key] ?: return@mapNotNull null
            val visible = widgetBannerVisibleGetter[key]?.invoke() ?: true
            BannerToggleItem(key, labelRes, visible)
        }.toMutableList()
        val bannerAdapter = BannerToggleAdapter(bannerItems)
        val touchHelper = ItemTouchHelper(BannerDragCallback(bannerAdapter))
        bannerAdapter.startDrag = { touchHelper.startDrag(it) }
        bannerList.adapter = bannerAdapter
        touchHelper.attachToRecyclerView(bannerList)

        // ─── Sync Google Drive ───────────────────────────────────────────────
        val syncBtn    = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.settings_sync_games_btn)
        val syncStatus = view.findViewById<android.widget.TextView>(R.id.settings_sync_status)
        syncBtn.setOnClickListener {
            syncBtn.isEnabled = false
            syncStatus.visibility = android.view.View.VISIBLE
            syncStatus.text = getString(R.string.games_sync_in_progress)
            lifecycleScope.launch {
                when (val result = com.Atom2Universe.app.crypto.sync.GamesSyncManager.syncGames()) {
                    is com.Atom2Universe.app.crypto.sync.GamesSyncManager.SyncResult.Success -> {
                        syncBtn.isEnabled = true
                        syncStatus.text = getString(R.string.games_sync_success)
                    }
                    is com.Atom2Universe.app.crypto.sync.GamesSyncManager.SyncResult.Error -> {
                        syncBtn.isEnabled = true
                        syncStatus.text = getString(R.string.games_sync_error, result.message)
                    }
                    is com.Atom2Universe.app.crypto.sync.GamesSyncManager.SyncResult.Conflict -> {
                        syncBtn.isEnabled = true
                        syncStatus.text = ""
                        dialog.dismiss()
                        showSyncConflictDialog(result.local, result.remote)
                    }
                }
            }
        }

        dialog.setOnDismissListener { settingsDialogFolderView = null }
        dialog.setContentView(view)
        dialog.show()
    }

    // ─── Dialog de conflit de synchronisation ────────────────────────────────

    private fun showSyncConflictDialog(
        local: com.Atom2Universe.app.crypto.sync.GamesSyncFile,
        remote: com.Atom2Universe.app.crypto.sync.GamesSyncFile
    ) {
        val conflictDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_games_sync_conflict, null)

        fun fillCard(
            dateId: Int, atomsId: Int, godId: Int, starId: Int, gachaId: Int,
            file: com.Atom2Universe.app.crypto.sync.GamesSyncFile
        ) {
            view.findViewById<TextView>(dateId).text =
                java.text.SimpleDateFormat("d MMM yyyy · HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(file.lastModified))

            val clicker = file.clicker
            view.findViewById<TextView>(atomsId).text = if (clicker != null)
                formatLayeredNumber(clicker.atoms) else "—"
            view.findViewById<TextView>(godId).text  = clicker?.godFingerLevel?.toString() ?: "—"
            view.findViewById<TextView>(starId).text = clicker?.starCoreLevel?.toString()  ?: "—"

            val elementCount = file.gacha?.copies?.count { it.value > 0 } ?: 0
            view.findViewById<TextView>(gachaId).text = "$elementCount / 118"
        }

        fillCard(
            R.id.conflict_local_date, R.id.conflict_local_atoms,
            R.id.conflict_local_god_finger, R.id.conflict_local_star_core, R.id.conflict_local_gacha,
            local
        )
        fillCard(
            R.id.conflict_remote_date, R.id.conflict_remote_atoms,
            R.id.conflict_remote_god_finger, R.id.conflict_remote_star_core, R.id.conflict_remote_gacha,
            remote
        )

        fun resolve(chosen: com.Atom2Universe.app.crypto.sync.GamesSyncFile) {
            conflictDialog.dismiss()
            lifecycleScope.launch {
                val result = com.Atom2Universe.app.crypto.sync.GamesSyncManager.resolveConflict(chosen)
                val msg = if (result is com.Atom2Universe.app.crypto.sync.GamesSyncManager.SyncResult.Success)
                    getString(R.string.games_sync_success)
                else
                    getString(R.string.games_sync_error,
                        (result as com.Atom2Universe.app.crypto.sync.GamesSyncManager.SyncResult.Error).message)
                showToast(msg)
            }
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.conflict_btn_choose_local)
            .setOnClickListener { resolve(local) }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.conflict_btn_choose_remote)
            .setOnClickListener { resolve(remote) }

        conflictDialog.setContentView(view)
        conflictDialog.show()
    }

    private fun formatLayeredNumber(n: com.Atom2Universe.app.crypto.sync.LayeredNumberData): String {
        if (n.sign == 0) return "0"
        val prefix = if (n.sign < 0) "-" else ""
        return when (n.layer) {
            0 -> {
                val exp = n.exponent.toLong()
                when {
                    exp < 3  -> String.format("%.0f", n.mantissa * Math.pow(10.0, n.exponent))
                    exp < 6  -> String.format("%.2fk", n.mantissa * Math.pow(10.0, n.exponent - 3))
                    exp < 9  -> String.format("%.2fM", n.mantissa * Math.pow(10.0, n.exponent - 6))
                    exp < 12 -> String.format("%.2fG", n.mantissa * Math.pow(10.0, n.exponent - 9))
                    else     -> String.format("%.3fe%d", n.mantissa, exp)
                }.let { prefix + it }
            }
            else -> "${prefix}e(e${String.format("%.1f", n.value)})"
        }
    }

    // ── Popup durée de défilement (long-press roue crantée) ───────────────────

    private fun showSlideshowPopup(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_slideshow_duration, null)
        val valueView = popupView.findViewById<TextView>(R.id.popup_slideshow_value)
        val slider = popupView.findViewById<Slider>(R.id.popup_slideshow_slider)
        val centerSwitch = popupView.findViewById<SwitchCompat>(R.id.popup_image_center)

        val currentMinutes = MainClickerPreferences.getSlideshowMinutes(this)
        slider.value = currentMinutes.toFloat()
        valueView.text = slideshowDurationLabel(currentMinutes)

        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val minutes = value.toInt()
            MainClickerPreferences.setSlideshowMinutes(this, minutes)
            slideshowIntervalMs = minutes * 60_000L
            valueView.text = slideshowDurationLabel(minutes)
            updateAutoBackgroundLoop()
        }

        centerSwitch.isChecked = MainClickerPreferences.isImageCenterAligned(this)
        centerSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setImageCenterAligned(this, isChecked)
            backgroundImageView.setAlignCenter(isChecked)
        }

        val popup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.elevation = 16f
        popup.showAsDropDown(anchor, 0, 4)
    }

    private fun slideshowDurationLabel(minutes: Int): String = when (minutes) {
        0 -> getString(R.string.crypto_slideshow_duration_off)
        60 -> getString(R.string.crypto_slideshow_duration_1h)
        else -> getString(R.string.crypto_slideshow_duration_value, minutes)
    }

    private fun minutesToSliderIndex(minutes: Int): Int =
        SLIDESHOW_STEPS.indexOfFirst { it >= minutes }.takeIf { it >= 0 } ?: (SLIDESHOW_STEPS.size - 1)

    private fun sliderIndexToMinutes(index: Int): Int =
        SLIDESHOW_STEPS.getOrElse(index) { SLIDESHOW_STEPS.last() }

    // ── Popup widget Crypto (long-press label) ────────────────────────────────

    private fun showCryptoWidgetPopup(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_crypto_widget, null)

        val opacityValue = popupView.findViewById<TextView>(R.id.popup_crypto_opacity_value)
        val opacitySlider = popupView.findViewById<Slider>(R.id.popup_crypto_opacity_slider)
        val currentOpacity = MainClickerPreferences.getWidgetOpacityPercent(this)
        opacitySlider.value = currentOpacity.toFloat()
        opacityValue.text = getString(R.string.crypto_widget_opacity_value, currentOpacity)
        opacitySlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val pct = value.roundToInt()
            MainClickerPreferences.setWidgetOpacityPercent(this, pct)
            cryptoWidgetView.applyBackgroundOpacity(pct)
            opacityValue.text = getString(R.string.crypto_widget_opacity_value, pct)
        }

        val refreshValue = popupView.findViewById<TextView>(R.id.popup_crypto_refresh_value)
        val refreshSlider = popupView.findViewById<Slider>(R.id.popup_crypto_refresh_slider)
        val currentInterval = MainClickerPreferences.getRefreshInterval(this)
        refreshSlider.value = currentInterval.ordinal.toFloat()
        refreshValue.text = refreshIntervalShortLabel(currentInterval)
        refreshSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val interval = MainClickerRefreshInterval.fromIndex(value.toInt())
            MainClickerPreferences.setRefreshInterval(this, interval)
            if (interval != refreshInterval) {
                refreshInterval = interval
                stopRefreshLoop()
                startRefreshLoop()
            }
            refreshValue.text = refreshIntervalShortLabel(interval)
        }

        val minimalSwitch = popupView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.popup_crypto_minimal_switch)
        minimalSwitch.isChecked = MainClickerPreferences.isCryptoWidgetMinimal(this)
        minimalSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setCryptoWidgetMinimal(this, isChecked)
            applyCryptoWidgetMinimal(isChecked)
        }

        val eurSwitch = popupView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.popup_crypto_eur_switch)
        eurSwitch.isChecked = MainClickerPreferences.isCryptoWidgetEur(this)
        eurSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setCryptoWidgetEur(this, isChecked)
            applyEurCurrency(isChecked)
        }

        val comparisonValue = popupView.findViewById<TextView>(R.id.popup_crypto_comparison_value)
        val comparisonSlider = popupView.findViewById<com.google.android.material.slider.Slider>(R.id.popup_crypto_comparison_slider)
        val currentWindowIndex = MainClickerPreferences.getCryptoComparisonWindowIndex(this)
        comparisonSlider.value = currentWindowIndex.toFloat()
        comparisonValue.text = CryptoComparisonWindow.fromIndex(currentWindowIndex).label
        comparisonSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val idx = value.toInt()
            MainClickerPreferences.setCryptoComparisonWindowIndex(this, idx)
            comparisonWindow = CryptoComparisonWindow.fromIndex(idx)
            invalidateKlineCache()
            comparisonValue.text = comparisonWindow.label
        }

        val eurUsdSwitch = popupView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.popup_crypto_eurusd_switch)
        eurUsdSwitch.isChecked = MainClickerPreferences.isCryptoEurUsdEnabled(this)
        eurUsdSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setCryptoEurUsdEnabled(this, isChecked)
            cryptoWidgetView.setEurUsdEnabled(isChecked)
            if (isChecked) {
                startEurUsdLoop()
                lifecycleScope.launch { fetchEurUsdRate() }
            } else {
                stopEurUsdLoop()
            }
        }

        val popup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.elevation = 16f
        popup.showAsDropDown(anchor, 0, 4)
    }

    private fun applyCryptoWidgetMinimal(minimal: Boolean) {
        cryptoWidgetView.setMinimalMode(minimal)
        cryptoWidgetView.updatePrices(btcPriceUsd, previousBtcPriceUsd, ethPriceUsd, previousEthPriceUsd)
    }

    private fun applyEurCurrency(isEur: Boolean) {
        useEurCurrency = isEur
        cryptoWidgetView.setCurrencyEur(isEur)
        invalidateKlineCache()
        btcPriceUsd = null
        previousBtcPriceUsd = null
        ethPriceUsd = null
        previousEthPriceUsd = null
        cryptoWidgetView.updatePrices(null, null, null, null)
        stopRefreshLoop()
        startRefreshLoop()
    }

    private fun refreshIntervalShortLabel(interval: MainClickerRefreshInterval): String = when (interval) {
        MainClickerRefreshInterval.FIVE_SEC -> "5s"
        MainClickerRefreshInterval.TEN_SEC -> "10s"
        MainClickerRefreshInterval.THIRTY_SEC -> "30s"
        MainClickerRefreshInterval.ONE_MIN -> "1 min"
        MainClickerRefreshInterval.TWO_MIN -> "2 min"
        MainClickerRefreshInterval.FIVE_MIN -> "5 min"
    }

    // ── Popup widget Terre-Lune (long-press label) ────────────────────────────

    private fun showEarthWidgetPopup(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_earth_widget, null)

        val opacityValue = popupView.findViewById<TextView>(R.id.popup_earth_opacity_value)
        val opacitySlider = popupView.findViewById<Slider>(R.id.popup_earth_opacity_slider)
        val currentOpacity = MainClickerPreferences.getEarthWidgetOpacityPercent(this)
        opacitySlider.value = currentOpacity.toFloat()
        opacityValue.text = getString(R.string.crypto_widget_opacity_value, currentOpacity)
        opacitySlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val pct = value.roundToInt()
            MainClickerPreferences.setEarthWidgetOpacityPercent(this, pct)
            earthMoonWidgetView.alpha = pct / 100f
            opacityValue.text = getString(R.string.crypto_widget_opacity_value, pct)
        }

        val onlyModeSwitch = popupView.findViewById<SwitchCompat>(R.id.popup_earth_only_mode)
        onlyModeSwitch.isChecked = MainClickerPreferences.isEarthOnlyMode(this)
        onlyModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setEarthOnlyMode(this, isChecked)
            earthMoonWidgetView.setEarthOnlyMode(isChecked)
        }

        val cloudsSwitch = popupView.findViewById<SwitchCompat>(R.id.popup_earth_clouds)
        cloudsSwitch.isChecked = MainClickerPreferences.isEarthShowClouds(this)
        cloudsSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setEarthShowClouds(this, isChecked)
            earthMoonWidgetView.setShowClouds(isChecked)
        }

        val terminatorSwitch = popupView.findViewById<SwitchCompat>(R.id.popup_earth_terminator)
        terminatorSwitch.isChecked = MainClickerPreferences.isEarthShowTerminator(this)
        terminatorSwitch.setOnCheckedChangeListener { _, isChecked ->
            MainClickerPreferences.setEarthShowTerminator(this, isChecked)
            earthMoonWidgetView.setShowTerminator(isChecked)
        }

        val popup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.elevation = 16f
        popup.showAsDropDown(anchor, 0, 4)
    }

    private fun showOpacityPopup(
        anchor: View,
        label1: String,
        getValue1: () -> Int,
        setValue1: (Int) -> Unit,
        applyValue1: (Int) -> Unit,
        label2: String? = null,
        getValue2: (() -> Int)? = null,
        setValue2: ((Int) -> Unit)? = null,
        applyValue2: ((Int) -> Unit)? = null
    ) {
        val popupView = layoutInflater.inflate(R.layout.popup_opacity_sliders, null)

        val lbl1 = popupView.findViewById<TextView>(R.id.popup_label_1)
        val val1 = popupView.findViewById<TextView>(R.id.popup_value_1)
        val slider1 = popupView.findViewById<Slider>(R.id.popup_slider_1)
        val container2 = popupView.findViewById<View>(R.id.popup_slider2_container)
        val lbl2 = popupView.findViewById<TextView>(R.id.popup_label_2)
        val val2 = popupView.findViewById<TextView>(R.id.popup_value_2)
        val slider2 = popupView.findViewById<Slider>(R.id.popup_slider_2)

        lbl1.text = label1
        val current1 = getValue1()
        slider1.value = current1.toFloat()
        val1.text = getString(R.string.crypto_widget_opacity_value, current1)
        slider1.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val pct = value.roundToInt()
            setValue1(pct)
            applyValue1(pct)
            val1.text = getString(R.string.crypto_widget_opacity_value, pct)
        }

        if (label2 != null && getValue2 != null && setValue2 != null && applyValue2 != null) {
            container2.visibility = View.VISIBLE
            slider2.visibility = View.VISIBLE
            lbl2.text = label2
            val current2 = getValue2()
            slider2.value = current2.toFloat()
            val2.text = getString(R.string.crypto_widget_opacity_value, current2)
            slider2.addOnChangeListener { _, value, fromUser ->
                if (!fromUser) return@addOnChangeListener
                val pct = value.roundToInt()
                setValue2(pct)
                applyValue2(pct)
                val2.text = getString(R.string.crypto_widget_opacity_value, pct)
            }
        }

        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isOutsideTouchable = true
        popup.elevation = 16f
        popup.showAsDropDown(anchor, 0, 4)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (error: Throwable) {
                // Ignore cancellation failures.
            }
        }
    }

    // ── Drag-and-drop pour les toggles de bannière ────────────────────────────

    private data class BannerToggleItem(val key: String, val labelRes: Int, var visible: Boolean)

    private inner class BannerToggleAdapter(
        private val items: MutableList<BannerToggleItem>
    ) : RecyclerView.Adapter<BannerToggleAdapter.VH>() {

        var startDrag: ((RecyclerView.ViewHolder) -> Unit)? = null

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val dragHandle: ImageView = v.findViewById(R.id.banner_item_drag_handle)
            val label: android.widget.TextView = v.findViewById(R.id.banner_item_label)
            val switch: SwitchMaterial = v.findViewById(R.id.banner_item_switch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(layoutInflater.inflate(R.layout.item_banner_toggle, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.label.setText(item.labelRes)
            holder.switch.setOnCheckedChangeListener(null)
            holder.switch.isChecked = item.visible
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                item.visible = isChecked
                widgetBannerVisibleSetter[item.key]?.invoke(isChecked)
                applyBannerTogglesVisibility()
            }
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) startDrag?.invoke(holder)
                false
            }
        }

        override fun getItemCount() = items.size

        fun onItemMoved(from: Int, to: Int) {
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
            MainClickerPreferences.setBannerToggleOrder(this@MainClickerActivity, items.map { it.key })
            applyBannerTogglesVisibility()
        }
    }

    private inner class BannerDragCallback(
        private val adapter: BannerToggleAdapter
    ) : ItemTouchHelper.Callback() {

        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
            makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            adapter.onItemMoved(vh.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

        override fun isLongPressDragEnabled() = false
    }
}
