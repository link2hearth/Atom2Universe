package com.Atom2Universe.app.crypto

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Écran plein écran affichant le graphique en chandeliers interactif d'un actif (BTC ou ETH),
 * ouvert via un double-tap sur le widget crypto. Les données proviennent de l'endpoint klines
 * de Binance. Deux rangées de boutons sélectionnent l'intervalle des bougies (1m → mensuel) ;
 * la navigation dans l'historique se fait au glisser/zoom. La devise (USD/EUR) suit le widget.
 */
class CryptoChartActivity : ThemedActivity() {

    companion object {
        const val EXTRA_EUR = "extra_eur"
        const val EXTRA_ASSET = "extra_asset"
        const val ASSET_BTC = "BTC"
        const val ASSET_ETH = "ETH"

        private const val BASE_URL = "https://api.binance.com"
    }

    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    private lateinit var chartView: CandleChartView
    private lateinit var priceView: TextView
    private lateinit var changeView: TextView
    private lateinit var statusView: TextView

    private var useEur = false
    private var asset = ASSET_BTC
    private var interval = CryptoCandleInterval.M15

    private var loadJob: Job? = null
    private var syncingGroups = false

    /** Cache mémoire des bougies déjà téléchargées, valable le temps de l'écran. */
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

    private val topIntervalIds = mapOf(
        R.id.crypto_chart_int_1m to CryptoCandleInterval.M1,
        R.id.crypto_chart_int_5m to CryptoCandleInterval.M5,
        R.id.crypto_chart_int_15m to CryptoCandleInterval.M15,
        R.id.crypto_chart_int_1h to CryptoCandleInterval.H1,
        R.id.crypto_chart_int_4h to CryptoCandleInterval.H4
    )
    private val bottomIntervalIds = mapOf(
        R.id.crypto_chart_int_1d to CryptoCandleInterval.D1,
        R.id.crypto_chart_int_1w to CryptoCandleInterval.W1,
        R.id.crypto_chart_int_1mo to CryptoCandleInterval.MN1
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crypto_chart)
        enableImmersiveMode()

        useEur = intent.getBooleanExtra(EXTRA_EUR, false)
        asset = intent.getStringExtra(EXTRA_ASSET) ?: ASSET_BTC

        findViewById<View>(R.id.crypto_chart_back).setOnClickListener { finish() }

        chartView = findViewById(R.id.crypto_chart_candles)
        priceView = findViewById(R.id.crypto_chart_price)
        changeView = findViewById(R.id.crypto_chart_change)
        statusView = findViewById(R.id.crypto_chart_status)

        chartView.onViewportChanged = { price, change -> updateHeader(price, change) }

        val assetGroup = findViewById<MaterialButtonToggleGroup>(R.id.crypto_chart_asset_group)
        val topGroup = findViewById<MaterialButtonToggleGroup>(R.id.crypto_chart_interval_group_top)
        val bottomGroup = findViewById<MaterialButtonToggleGroup>(R.id.crypto_chart_interval_group_bottom)

        // État initial avant d'attacher les listeners pour ne pas déclencher de reload prématuré.
        assetGroup.check(if (asset == ASSET_ETH) R.id.crypto_chart_asset_eth else R.id.crypto_chart_asset_btc)
        topGroup.check(R.id.crypto_chart_int_15m)

        assetGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            asset = if (checkedId == R.id.crypto_chart_asset_eth) ASSET_ETH else ASSET_BTC
            reload()
        }

        // Les deux rangées d'intervalles forment une seule sélection logique : cocher dans l'une
        // vide l'autre.
        topGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (syncingGroups || !isChecked) return@addOnButtonCheckedListener
            val selected = topIntervalIds[checkedId] ?: return@addOnButtonCheckedListener
            syncingGroups = true
            bottomGroup.clearChecked()
            syncingGroups = false
            interval = selected
            reload()
        }
        bottomGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (syncingGroups || !isChecked) return@addOnButtonCheckedListener
            val selected = bottomIntervalIds[checkedId] ?: return@addOnButtonCheckedListener
            syncingGroups = true
            topGroup.clearChecked()
            syncingGroups = false
            interval = selected
            reload()
        }

        reload()
    }

    private fun currencySuffix(): String =
        if (useEur) getString(R.string.crypto_widget_currency_eur)
        else getString(R.string.crypto_widget_currency_usd)

    private fun symbol(): String {
        val quote = if (useEur) "EUR" else "USDT"
        return asset + quote
    }

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

    private fun reload() {
        val sym = symbol()
        val i = interval
        val cacheKey = "${sym}_${i.name}"

        val cached = candleCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < cacheTtlMs(i)) {
            loadJob?.cancel()
            hideStatus()
            chartView.setData(cached.candles, i)
            return
        }

        loadJob?.cancel()
        showStatus(getString(R.string.crypto_widget_loading))
        loadJob = lifecycleScope.launch {
            try {
                val candles = withContext(Dispatchers.IO) { fetchCandles(sym, i) }
                if (candles.isEmpty()) {
                    showStatus(getString(R.string.crypto_widget_error))
                    return@launch
                }
                candleCache[cacheKey] = CachedCandles(candles, System.currentTimeMillis())
                hideStatus()
                chartView.setData(candles, i)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showStatus(getString(R.string.crypto_widget_error))
            }
        }
    }

    /** Met à jour l'en-tête à partir de la fenêtre visible du graphe (prix + variation %). */
    private fun updateHeader(lastPrice: Double, changePercent: Double) {
        priceView.text = "${priceFormat.format(lastPrice)} ${currencySuffix()}"
        val sign = if (changePercent >= 0) "+" else "−"
        changeView.text = "$sign${percentFormat.format(abs(changePercent))} %"
        val color = if (changePercent >= 0) R.color.crypto_widget_up else R.color.crypto_widget_down
        changeView.setTextColor(ContextCompat.getColor(this, color))
    }

    private suspend fun fetchCandles(symbol: String, interval: CryptoCandleInterval): List<Candle> {
        val url = "$BASE_URL/api/v3/klines?symbol=$symbol" +
            "&interval=${interval.binanceInterval}&limit=${CryptoCandleInterval.FETCH_LIMIT}"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
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
                    close = k.getString(4).toDouble()
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
