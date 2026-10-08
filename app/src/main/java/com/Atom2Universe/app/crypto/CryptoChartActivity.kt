package com.Atom2Universe.app.crypto

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min

/**
 * Écran plein écran affichant le graphique en chandeliers interactif d'un actif crypto,
 * ouvert via un double-tap sur le widget crypto. Les données proviennent de l'endpoint klines
 * de Binance.
 *
 * En-tête : liste des actifs suivis (watchlist, BTC/ETH par défaut) — bouton loupe pour
 * rechercher et ajouter n'importe quelle paire Binance, appui long sur un actif pour le
 * retirer. Ligne d'outils : menu déroulant du timeframe (1m → mensuel), bouton Log
 * (échelle logarithmique), bouton MA (moyenne mobile), et sélecteur de devise USD/EUR —
 * le bouton surligné reflète la paire réellement affichée ; si la paire EUR n'existe pas
 * sur Binance, le clic est sans effet et l'affichage reste sur la paire USDT.
 * Timeframe, devise, échelle et MA sont mémorisés d'une ouverture à l'autre.
 * Le graphique se rafraîchit automatiquement sans perdre la position de lecture.
 */
class CryptoChartActivity : ThemedActivity() {

    companion object {
        const val EXTRA_EUR = "extra_eur"
        const val EXTRA_ASSET = "extra_asset"
        const val ASSET_BTC = "BTC"
        const val ASSET_ETH = "ETH"

        private const val BASE_URL = "https://api.binance.com"
        private const val PREFS_NAME = "crypto_chart_prefs"
        private const val KEY_WATCHLIST = "watchlist"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_LOG_SCALE = "log_scale"
        private const val KEY_SHOW_MA = "show_ma"
        private const val KEY_MA_PERIOD = "ma_period"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_DRAWINGS_PREFIX = "drawings_"

        /** Plafond de bougies accumulées en mémoire par paire+intervalle. */
        private const val MAX_CANDLES = 20_000
        private val DEFAULT_WATCHLIST = listOf(ASSET_BTC, ASSET_ETH)

        /** Périodes de moyenne mobile proposées dans le menu, façon TradingView. */
        private val MA_PERIOD_OPTIONS = listOf(5, 7, 9, 10, 20, 25, 30, 50, 99, 100, 200)

        /** Suggestions affichées dans la recherche tant que le champ est vide. */
        private val POPULAR_ASSETS = listOf(
            "BTC", "ETH", "SOL", "XRP", "BNB", "DOGE", "ADA", "AVAX", "LINK", "DOT",
            "TON", "TRX", "LTC", "SHIB", "PEPE", "NEAR", "UNI", "ATOM", "XLM", "HBAR"
        )
    }

    /** Paire inexistante sur Binance (HTTP 4xx ou historique vide). */
    private class InvalidSymbolException : IOException("invalid symbol")

    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    private lateinit var chartView: CandleChartView
    private lateinit var priceView: TextView
    private lateinit var changeView: TextView
    private lateinit var statusView: TextView
    private lateinit var assetGroup: MaterialButtonToggleGroup
    private lateinit var timeframeButton: MaterialButton
    private lateinit var logButton: MaterialButton
    private lateinit var maButton: MaterialButton
    private lateinit var currencyGroup: MaterialButtonToggleGroup

    private var asset = ASSET_BTC
    private var interval = CryptoCandleInterval.M15

    /** Devise préférée par l'utilisateur (persistée). */
    private var userEur = false

    /** La paire réellement affichée est-elle cotée en EUR ? (repli USDT possible) */
    private var displayEur = false

    private val watchlist = mutableListOf<String>()
    private val assetButtonIds = LinkedHashMap<String, Int>()
    private var syncingAssets = false
    private var syncingCurrency = false

    /** Bases (BTC, SOL…) disposant d'une paire USDT sur Binance, chargées à la demande. */
    private var symbolBases: List<String>? = null

    /** Paires dont Binance a déjà signalé l'inexistence, pour ne pas réessayer en boucle. */
    private val missingSymbols = HashSet<String>()

    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    /** Clé (paire + intervalle) des données actuellement affichées par le graphe. */
    private var displayedKey: String? = null

    /** Paire dont les lignes de tendance sont actuellement chargées dans le graphe. */
    private var drawingsSymbol: String? = null

    /** Paire actuellement affichée (sert à la pagination vers le passé). */
    private var displayedSymbol: String? = null

    /** Téléchargement de bougies plus anciennes en cours. */
    private var olderJob: Job? = null

    /** Clés (paire+intervalle) dont tout l'historique disponible a déjà été chargé. */
    private val exhaustedHistory = HashSet<String>()

    /** Cache mémoire des bougies déjà téléchargées, clé = paire + intervalle. */
    private data class CachedCandles(val candles: List<Candle>, val fetchedAt: Long)
    private val candleCache = HashMap<String, CachedCandles>()

    private val priceFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }
    private val percentFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crypto_chart)
        enableImmersiveMode()

        asset = intent.getStringExtra(EXTRA_ASSET) ?: ASSET_BTC

        findViewById<View>(R.id.crypto_chart_back).setOnClickListener { finish() }
        findViewById<View>(R.id.crypto_chart_search).setOnClickListener { openSearchDialog() }
        findViewById<View>(R.id.crypto_chart_add_line).setOnClickListener { chartView.addTrendLine() }

        chartView = findViewById(R.id.crypto_chart_candles)
        priceView = findViewById(R.id.crypto_chart_price)
        changeView = findViewById(R.id.crypto_chart_change)
        statusView = findViewById(R.id.crypto_chart_status)
        assetGroup = findViewById(R.id.crypto_chart_asset_group)
        timeframeButton = findViewById(R.id.crypto_chart_timeframe)
        logButton = findViewById(R.id.crypto_chart_scale_log)
        maButton = findViewById(R.id.crypto_chart_ma_toggle)
        currencyGroup = findViewById(R.id.crypto_chart_currency_group)

        chartView.onViewportChanged = { price, change -> updateHeader(price, change) }
        chartView.onTrendLinesChanged = { saveTrendLines() }
        chartView.onNeedOlderCandles = { loadOlderCandles() }

        loadPreferences()

        if (!watchlist.contains(asset)) {
            watchlist.add(asset)
            persistWatchlist()
        }
        rebuildAssetButtons(asset)

        assetGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (syncingAssets || !isChecked) return@addOnButtonCheckedListener
            val selected = assetButtonIds.entries.firstOrNull { it.value == checkedId }?.key
                ?: return@addOnButtonCheckedListener
            asset = selected
            reload()
        }

        setupToolbar()
        reload()
    }

    override fun onStart() {
        super.onStart()
        startAutoRefresh()
    }

    override fun onStop() {
        refreshJob?.cancel()
        refreshJob = null
        super.onStop()
    }

    // ---------------------------------------------------------------------------------
    // Préférences
    // ---------------------------------------------------------------------------------

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val stored = prefs.getString(KEY_WATCHLIST, null)
            ?.split(',')
            ?.map { it.trim().uppercase(Locale.ROOT) }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
        watchlist.clear()
        watchlist.addAll(if (stored.isEmpty()) DEFAULT_WATCHLIST else stored)

        // Devise : le choix fait dans cet écran prime ; à la première ouverture on part
        // de la préférence du widget (extra), sinon dollar.
        userEur = when (prefs.getString(KEY_CURRENCY, null)) {
            "EUR" -> true
            "USD" -> false
            else -> intent.getBooleanExtra(EXTRA_EUR, false)
        }

        interval = prefs.getString(KEY_INTERVAL, null)
            ?.let { name -> CryptoCandleInterval.entries.firstOrNull { it.name == name } }
            ?: CryptoCandleInterval.M15

        chartView.logScale = prefs.getBoolean(KEY_LOG_SCALE, false)
        // Migration : l'ancienne préférence booléenne (MA on/off) devient une période de 20.
        chartView.movingAveragePeriod = prefs.getInt(
            KEY_MA_PERIOD,
            if (prefs.getBoolean(KEY_SHOW_MA, false)) 20 else 0
        )
    }

    private fun persistWatchlist() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCHLIST, watchlist.joinToString(","))
            .apply()
    }

    private fun persistDisplayPrefs() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CURRENCY, if (userEur) "EUR" else "USD")
            .putBoolean(KEY_LOG_SCALE, chartView.logScale)
            .putInt(KEY_MA_PERIOD, chartView.movingAveragePeriod)
            .putString(KEY_INTERVAL, interval.name)
            .apply()
    }

    // ---------------------------------------------------------------------------------
    // Ligne d'outils : timeframe, échelle, MA, devise
    // ---------------------------------------------------------------------------------

    private fun setupToolbar() {
        updateTimeframeLabel()
        timeframeButton.setOnClickListener { showTimeframeMenu() }

        logButton.isChecked = chartView.logScale
        updateLogButtonLabel()
        logButton.addOnCheckedChangeListener { _, isChecked ->
            chartView.logScale = isChecked
            updateLogButtonLabel()
            persistDisplayPrefs()
        }

        updateMaButtonLabel()
        maButton.setOnClickListener {
            // Le clic sur un MaterialButton checkable inverse son état tout seul : on le
            // remet en phase avec la MA réelle, le menu décidera du nouvel état.
            maButton.isChecked = chartView.movingAveragePeriod > 0
            showMaMenu()
        }

        syncCurrencyToggle(userEur)
        currencyGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (syncingCurrency || !isChecked) return@addOnButtonCheckedListener
            onCurrencyClicked(checkedId == R.id.crypto_chart_currency_eur)
        }
    }

    private fun updateTimeframeLabel() {
        timeframeButton.text = "${getString(interval.labelRes)} ▾"
    }

    private fun showTimeframeMenu() {
        val popup = PopupMenu(this, timeframeButton)
        CryptoCandleInterval.entries.forEachIndexed { index, entry ->
            popup.menu.add(0, index, index, getString(entry.labelRes))
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = CryptoCandleInterval.entries[item.itemId]
            if (selected != interval) {
                interval = selected
                persistDisplayPrefs()
                updateTimeframeLabel()
                reload()
            }
            true
        }
        popup.show()
    }

    /** Le bouton d'échelle affiche l'état courant : « Lin » (linéaire) ou « Log » (surligné). */
    private fun updateLogButtonLabel() {
        logButton.setText(
            if (chartView.logScale) R.string.crypto_chart_scale_log else R.string.crypto_chart_scale_lin
        )
    }

    /** Le bouton MA affiche la période active (« MA 20 ▾ ») et se surligne quand elle l'est. */
    private fun updateMaButtonLabel() {
        val period = chartView.movingAveragePeriod
        maButton.text = if (period > 0) "MA $period ▾" else "MA ▾"
        maButton.isChecked = period > 0
    }

    private fun showMaMenu() {
        val popup = PopupMenu(this, maButton)
        popup.menu.add(0, 0, 0, getString(R.string.crypto_chart_ma_off))
        MA_PERIOD_OPTIONS.forEachIndexed { index, period ->
            popup.menu.add(0, period, index + 1, "MA $period")
        }
        popup.setOnMenuItemClickListener { item ->
            chartView.movingAveragePeriod = item.itemId
            persistDisplayPrefs()
            updateMaButtonLabel()
            true
        }
        popup.show()
    }

    /** Surligne la devise réellement affichée, sans déclencher le listener. */
    private fun syncCurrencyToggle(eur: Boolean) {
        syncingCurrency = true
        currencyGroup.check(if (eur) R.id.crypto_chart_currency_eur else R.id.crypto_chart_currency_usd)
        syncingCurrency = false
    }

    /**
     * Clic explicite sur USD ou EUR : on tente la paire correspondante, strictement.
     * Si elle n'existe pas (ou en cas d'erreur réseau), rien ne change et le surlignage
     * revient sur la devise affichée.
     */
    private fun onCurrencyClicked(wantEur: Boolean) {
        val symbol = symbolFor(asset, wantEur)
        if (missingSymbols.contains(symbol)) {
            syncCurrencyToggle(displayEur)
            return
        }
        if (wantEur == displayEur) {
            userEur = wantEur
            persistDisplayPrefs()
            return
        }
        loadJob?.cancel()
        showStatus(getString(R.string.crypto_widget_loading))
        loadJob = lifecycleScope.launch {
            try {
                val candles = fetchCandlesCached(symbol, interval)
                userEur = wantEur
                persistDisplayPrefs()
                display(symbol, candles, interval, preserveViewport = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: InvalidSymbolException) {
                missingSymbols.add(symbol)
                hideStatus()
                syncCurrencyToggle(displayEur)
            } catch (error: Exception) {
                hideStatus()
                syncCurrencyToggle(displayEur)
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Watchlist
    // ---------------------------------------------------------------------------------

    /** Recrée les boutons d'actifs à partir de la watchlist et coche [checkedAsset], sans reload. */
    private fun rebuildAssetButtons(checkedAsset: String) {
        syncingAssets = true
        assetGroup.removeAllViews()
        assetButtonIds.clear()
        for (entry in watchlist) {
            val button = layoutInflater.inflate(
                R.layout.item_crypto_chart_asset_button, assetGroup, false
            ) as MaterialButton
            button.id = View.generateViewId()
            button.text = entry
            button.setOnLongClickListener {
                promptRemoveAsset(entry)
                true
            }
            assetGroup.addView(button)
            assetButtonIds[entry] = button.id
        }
        assetButtonIds[checkedAsset]?.let { assetGroup.check(it) }
        syncingAssets = false
    }

    private fun promptRemoveAsset(entry: String) {
        if (watchlist.size <= 1) return
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.crypto_chart_remove_asset, entry))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                watchlist.remove(entry)
                persistWatchlist()
                val changed = !watchlist.contains(asset)
                if (changed) asset = watchlist.first()
                rebuildAssetButtons(asset)
                if (changed) reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectSearchedAsset(base: String) {
        val cleaned = base.trim().uppercase(Locale.ROOT)
        if (cleaned.isEmpty()) return
        if (!watchlist.contains(cleaned)) {
            watchlist.add(cleaned)
            persistWatchlist()
        }
        val changed = asset != cleaned
        asset = cleaned
        rebuildAssetButtons(asset)
        if (changed) reload()
    }

    // ---------------------------------------------------------------------------------
    // Recherche d'actifs
    // ---------------------------------------------------------------------------------

    private fun openSearchDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_crypto_search, null)
        val input = view.findViewById<EditText>(R.id.crypto_search_input)
        val status = view.findViewById<TextView>(R.id.crypto_search_status)
        val list = view.findViewById<ListView>(R.id.crypto_search_list)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        list.adapter = adapter

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crypto_chart_search_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        fun refreshResults() {
            val bases = symbolBases ?: return
            val query = input.text.toString().trim().uppercase(Locale.ROOT)
            val results = if (query.isEmpty()) {
                POPULAR_ASSETS.filter { bases.contains(it) }
            } else {
                bases.asSequence()
                    .filter { it.contains(query) }
                    .sortedWith(compareBy({ !it.startsWith(query) }, { it.length }, { it }))
                    .take(50)
                    .toList()
            }
            adapter.clear()
            adapter.addAll(results)
            status.isVisible = results.isEmpty()
            if (results.isEmpty()) status.setText(R.string.crypto_chart_search_empty)
        }

        input.doAfterTextChanged { refreshResults() }
        list.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { selectSearchedAsset(it) }
            dialog.dismiss()
        }

        if (symbolBases == null) {
            status.setText(R.string.crypto_chart_search_loading)
            status.isVisible = true
            lifecycleScope.launch {
                try {
                    symbolBases = withContext(Dispatchers.IO) { fetchSymbolBases() }
                    status.isVisible = false
                    refreshResults()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    status.setText(R.string.crypto_chart_search_error)
                    status.isVisible = true
                }
            }
        } else {
            refreshResults()
        }

        dialog.show()
    }

    /** Liste des actifs de base disposant d'une paire USDT sur Binance, triée alphabétiquement. */
    private suspend fun fetchSymbolBases(): List<String> {
        val url = "$BASE_URL/api/v3/ticker/price"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        response.close()

        val array = JSONArray(body)
        val bases = sortedSetOf<String>()
        for (i in 0 until array.length()) {
            val symbol = array.getJSONObject(i).getString("symbol")
            if (symbol.length > 4 && symbol.endsWith("USDT")) {
                bases.add(symbol.dropLast(4))
            }
        }
        return bases.toList()
    }

    // ---------------------------------------------------------------------------------
    // Chargement des bougies
    // ---------------------------------------------------------------------------------

    private fun symbolFor(asset: String, eur: Boolean): String =
        asset + if (eur) "EUR" else "USDT"

    private fun cacheKeyOf(symbol: String, i: CryptoCandleInterval): String = "${symbol}_${i.name}"

    private fun currencySuffix(): String =
        if (displayEur) getString(R.string.crypto_widget_currency_eur)
        else getString(R.string.crypto_widget_currency_usd)

    /**
     * Durée de fraîcheur du cache, calée sur la granularité des bougies : plus l'intervalle
     * est court, plus vite la dernière bougie évolue, donc plus court le TTL.
     */
    private fun cacheTtlMs(i: CryptoCandleInterval): Long = when (i) {
        CryptoCandleInterval.M1 -> 30_000L
        CryptoCandleInterval.M5, CryptoCandleInterval.M15 -> 60_000L
        CryptoCandleInterval.H1, CryptoCandleInterval.H4 -> 5 * 60_000L
        CryptoCandleInterval.D1, CryptoCandleInterval.W1, CryptoCandleInterval.MN1 -> 15 * 60_000L
    }

    /** Rafraîchit périodiquement les données affichées (le TTL du cache limite les requêtes). */
    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(cacheTtlMs(interval) + 1_000L)
                reload(silent = true)
            }
        }
    }

    /**
     * Charge et affiche les bougies de l'actif/intervalle courants dans la devise préférée,
     * avec repli automatique sur USDT si la paire EUR n'existe pas. En mode [silent]
     * (rafraîchissement automatique), aucun indicateur de chargement n'est montré, les
     * erreurs sont ignorées et la position de lecture du graphe est conservée.
     */
    private fun reload(silent: Boolean = false) {
        val i = interval
        val preferEur = userEur && !missingSymbols.contains(symbolFor(asset, true))
        val primary = symbolFor(asset, preferEur)
        val fallback = if (preferEur) symbolFor(asset, false) else null

        val cached = candleCache[cacheKeyOf(primary, i)]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < cacheTtlMs(i)) {
            if (silent && displayedKey == cacheKeyOf(primary, i)) return
            loadJob?.cancel()
            display(primary, cached.candles, i, preserveViewport = false)
            return
        }

        loadJob?.cancel()
        if (!silent) showStatus(getString(R.string.crypto_widget_loading))
        loadJob = lifecycleScope.launch {
            try {
                var symbol = primary
                val candles = try {
                    fetchCandlesCached(primary, i)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (fallback == null) throw error
                    if (error is InvalidSymbolException) missingSymbols.add(primary)
                    symbol = fallback
                    fetchCandlesCached(fallback, i)
                }
                display(symbol, candles, i, preserveViewport = silent && displayedKey == cacheKeyOf(symbol, i))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!silent) showStatus(getString(R.string.crypto_widget_error))
            }
        }
    }

    /** Affiche des bougies fraîchement obtenues et aligne l'état (devise, clé, surlignage). */
    private fun display(
        symbol: String,
        candles: List<Candle>,
        i: CryptoCandleInterval,
        preserveViewport: Boolean
    ) {
        hideStatus()
        displayEur = symbol.endsWith("EUR")
        chartView.setData(candles, i, preserveViewport)
        displayedKey = cacheKeyOf(symbol, i)
        displayedSymbol = symbol
        syncCurrencyToggle(displayEur)
        if (drawingsSymbol != symbol) {
            drawingsSymbol = symbol
            chartView.setTrendLines(loadTrendLines(symbol))
        }
    }

    // ---------------------------------------------------------------------------------
    // Lignes de tendance : persistance par paire
    // ---------------------------------------------------------------------------------

    private fun saveTrendLines() {
        val symbol = drawingsSymbol ?: return
        val array = JSONArray()
        for (line in chartView.trendLinesSnapshot()) {
            array.put(
                JSONObject()
                    .put("t1", line.time1)
                    .put("p1", line.price1)
                    .put("t2", line.time2)
                    .put("p2", line.price2)
                    .put("c", line.colorIndex)
            )
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DRAWINGS_PREFIX + symbol, array.toString())
            .apply()
    }

    private fun loadTrendLines(symbol: String): List<TrendLine> {
        val raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DRAWINGS_PREFIX + symbol, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                TrendLine(
                    time1 = o.getLong("t1"),
                    price1 = o.getDouble("p1"),
                    time2 = o.getLong("t2"),
                    price2 = o.getDouble("p2"),
                    colorIndex = o.optInt("c")
                )
            }
        } catch (error: Exception) {
            emptyList()
        }
    }

    /**
     * Bougies pour une paire donnée, depuis le cache si frais, sinon via le réseau.
     * Au rafraîchissement, la fenêtre récente téléchargée est fusionnée avec l'historique
     * plus ancien déjà accumulé (les bougies passées ne changent jamais) au lieu de l'écraser.
     */
    private suspend fun fetchCandlesCached(symbol: String, i: CryptoCandleInterval): List<Candle> {
        val key = cacheKeyOf(symbol, i)
        val cached = candleCache[key]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < cacheTtlMs(i)) {
            return cached.candles
        }
        val fresh = withContext(Dispatchers.IO) { fetchCandles(symbol, i) }
        if (fresh.isEmpty()) throw InvalidSymbolException()
        val merged = if (cached != null && cached.candles.isNotEmpty()) {
            val cutoff = fresh.first().openTime
            cached.candles.filter { it.openTime < cutoff } + fresh
        } else {
            fresh
        }
        candleCache[key] = CachedCandles(merged, System.currentTimeMillis())
        return merged
    }

    /**
     * Charge 1000 bougies plus anciennes que le début de l'historique affiché (endpoint
     * klines avec `endTime`) et les préfixe au cache, sans bouger la vue. Déclenché par le
     * graphe quand on approche du bord gauche ; silencieux en cas d'échec (retenté au
     * prochain passage au bord).
     */
    private fun loadOlderCandles() {
        val symbol = displayedSymbol ?: return
        val i = interval
        val key = cacheKeyOf(symbol, i)
        if (key != displayedKey) return
        if (exhaustedHistory.contains(key)) return
        if (olderJob?.isActive == true) return
        val current = candleCache[key]?.candles ?: return
        if (current.isEmpty()) return
        if (current.size >= MAX_CANDLES) {
            exhaustedHistory.add(key)
            return
        }
        val firstTime = current.first().openTime
        olderJob = lifecycleScope.launch {
            try {
                val older = withContext(Dispatchers.IO) {
                    fetchCandles(symbol, i, endTimeMs = firstTime - 1)
                }
                val filtered = older.filter { it.openTime < firstTime }
                if (filtered.isEmpty()) {
                    // Début de l'historique Binance atteint pour cette paire.
                    exhaustedHistory.add(key)
                    return@launch
                }
                val cached = candleCache[key] ?: return@launch
                val merged = filtered.filter { it.openTime < cached.candles.first().openTime } +
                    cached.candles
                candleCache[key] = CachedCandles(merged, cached.fetchedAt)
                if (displayedKey == key) {
                    chartView.setData(merged, i, preserveViewport = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Échec réseau ponctuel : on laisse la main, nouvel essai au prochain bord.
            }
        }
    }

    /** Met à jour l'en-tête à partir de la fenêtre visible du graphe (prix + variation %). */
    private fun updateHeader(lastPrice: Double, changePercent: Double) {
        val decimals = cryptoPriceDecimals(lastPrice)
        priceFormat.minimumFractionDigits = min(2, decimals)
        priceFormat.maximumFractionDigits = decimals
        priceView.text = "${priceFormat.format(lastPrice)} ${currencySuffix()}"
        val sign = if (changePercent >= 0) "+" else "−"
        changeView.text = "$sign${percentFormat.format(abs(changePercent))} %"
        val color = if (changePercent >= 0) R.color.crypto_widget_up else R.color.crypto_widget_down
        changeView.setTextColor(ContextCompat.getColor(this, color))
    }

    private suspend fun fetchCandles(
        symbol: String,
        interval: CryptoCandleInterval,
        endTimeMs: Long? = null
    ): List<Candle> {
        var url = "$BASE_URL/api/v3/klines?symbol=$symbol" +
            "&interval=${interval.binanceInterval}&limit=${CryptoCandleInterval.FETCH_LIMIT}"
        if (endTimeMs != null) url += "&endTime=$endTimeMs"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            if (code in 400..499) throw InvalidSymbolException()
            throw IOException("HTTP $code")
        }
        val body = response.body?.string().orEmpty()
        response.close()

        val array = JSONArray(body)
        val result = ArrayList<Candle>(array.length())
        for (i in 0 until array.length()) {
            val k = array.getJSONArray(i)
            // Format kline : [openTime, open, high, low, close, volume, closeTime, ...]
            result.add(
                Candle(
                    openTime = k.getLong(0),
                    open = k.getString(1).toDouble(),
                    high = k.getString(2).toDouble(),
                    low = k.getString(3).toDouble(),
                    close = k.getString(4).toDouble(),
                    volume = k.getString(5).toDouble()
                )
            )
        }
        return result
    }

    private fun showStatus(text: String) {
        statusView.text = text
        statusView.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        statusView.visibility = View.GONE
    }

    override fun onDestroy() {
        loadJob?.cancel()
        olderJob?.cancel()
        super.onDestroy()
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
}
