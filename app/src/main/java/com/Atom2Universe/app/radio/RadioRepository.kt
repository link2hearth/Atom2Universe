package com.Atom2Universe.app.radio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.Atom2Universe.app.radio.data.RadioDatabase
import com.Atom2Universe.app.radio.data.RadioFilterDao
import com.Atom2Universe.app.radio.data.RadioFilterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import androidx.core.net.toUri
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Bug 4.28: Résultat de recherche avec distinction des erreurs
 */
sealed class SearchResult {
    /** Recherche réussie avec liste de stations (peut être vide) */
    data class Success(val stations: List<RadioStation>) : SearchResult()

    /** Erreur: Appareil hors ligne */
    data object Offline : SearchResult()

    /** Erreur: Serveurs radio non joignables mais appareil en ligne */
    data class ServerError(val message: String) : SearchResult()

    /** Erreur: Timeout de la requête */
    data object Timeout : SearchResult()
}

class RadioRepository(private val config: RadioConfig, context: Context) {

    private val appContext: Context = context.applicationContext
    private val filterDao: RadioFilterDao = RadioDatabase.getInstance(context).radioFilterDao()

    // Bug 4.18: Stocker le call en cours pour pouvoir l'annuler
    @Volatile
    private var currentCall: okhttp3.Call? = null
    private val callLock = Any()

    /**
     * Bug 4.28: Vérifie si l'appareil est connecté à Internet
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "RadioRepository"
        private const val FILTER_TYPE_COUNTRY = "country"
        private const val FILTER_TYPE_LANGUAGE = "language"
        private const val CACHE_VALIDITY_DAYS = 7 // Cache valide pendant 7 jours

        // Bug 4.21: Limite de taille pour les réponses JSON (10 MB max)
        private const val MAX_RESPONSE_SIZE_BYTES = 10 * 1024 * 1024L

        // Bug 4.23: Limite du cache mémoire des filtres
        private const val MAX_FILTER_CACHE_ENTRIES = 500

        // Bug 4.17: Client HTTP partagé entre instances avec configuration correcte
        @Volatile
        private var sharedClient: OkHttpClient? = null
        @Volatile
        private var sharedClientTimeoutMs: Long = 0  // Pour détecter si la config a changé
        private val clientLock = Any()

        /**
         * Libere les ressources du client HTTP.
         * A appeler quand le module radio n'est plus utilise.
         */
        fun releaseClient() {
            synchronized(clientLock) {
                sharedClient?.let { client ->
                    try {
                        // Annuler toutes les requêtes en cours
                        client.dispatcher.cancelAll()
                        client.dispatcher.executorService.shutdown()
                        client.connectionPool.evictAll()
                        client.cache?.close()
                        Log.d(TAG, "OkHttpClient released")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error releasing OkHttpClient", e)
                    }
                }
                sharedClient = null
                sharedClientTimeoutMs = 0
            }
        }
    }

    // Bug 4.17: S'assurer que le client est configuré correctement pour chaque instance
    private val client: OkHttpClient
        get() = synchronized(clientLock) {
            val existingClient = sharedClient
            // Recréer le client si le timeout a changé
            if (existingClient != null && sharedClientTimeoutMs == config.requestTimeoutMs) {
                return@synchronized existingClient
            }
            // Libérer l'ancien client si la config a changé
            if (existingClient != null && sharedClientTimeoutMs != config.requestTimeoutMs) {
                Log.d(TAG, "Config changed, recreating OkHttpClient")
                try {
                    existingClient.dispatcher.cancelAll()
                    existingClient.connectionPool.evictAll()
                    existingClient.cache?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up old client", e)
                }
            }
            OkHttpClient.Builder()
                .callTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
                .also {
                    sharedClient = it
                    sharedClientTimeoutMs = config.requestTimeoutMs
                }
        }

    /**
     * Bug 4.18: Annule les requêtes en cours
     */
    @Suppress("unused")
    fun cancelPendingRequests() {
        synchronized(callLock) {
            currentCall?.cancel()
            currentCall = null
        }
    }

    suspend fun fetchFilters(): RadioFilters = withContext(Dispatchers.IO) {
        // Vérifier si le cache est valide pour les deux types
        val countriesValid = isCacheValid(FILTER_TYPE_COUNTRY)
        val languagesValid = isCacheValid(FILTER_TYPE_LANGUAGE)

        if (countriesValid && languagesValid) {
            val cachedCountries = getCachedFilters(FILTER_TYPE_COUNTRY)
            val cachedLanguages = getCachedFilters(FILTER_TYPE_LANGUAGE)
            if (cachedCountries.isNotEmpty() && cachedLanguages.isNotEmpty()) {
                return@withContext RadioFilters(cachedCountries, cachedLanguages)
            }
        }

        // Sinon, télécharger depuis l'API
        val countries = fetchDirectory("json/countries")
        val languages = fetchDirectory("json/languages")

        // Mettre en cache si les listes ne sont pas vides
        if (countries.isNotEmpty() && languages.isNotEmpty()) {
            cacheFilters(FILTER_TYPE_COUNTRY, countries)
            cacheFilters(FILTER_TYPE_LANGUAGE, languages)
        }

        RadioFilters(countries, languages)
    }

    private suspend fun isCacheValid(filterType: String): Boolean {
        val timestamp = filterDao.getCacheTimestamp(filterType) ?: return false
        val now = System.currentTimeMillis()
        val validityMs = CACHE_VALIDITY_DAYS * 24 * 60 * 60 * 1000L
        return (now - timestamp) < validityMs
    }

    private suspend fun getCachedFilters(filterType: String): List<String> {
        return filterDao.getFiltersByType(filterType).map { it.value }
    }

    private suspend fun cacheFilters(filterType: String, values: List<String>) {
        // Supprimer l'ancien cache
        filterDao.deleteFiltersByType(filterType)

        // Bug 4.23: Limiter le nombre d'entrées en cache pour éviter les problèmes de mémoire
        val limitedValues = if (values.size > MAX_FILTER_CACHE_ENTRIES) {
            Log.w(TAG, "Filter cache truncated from ${values.size} to $MAX_FILTER_CACHE_ENTRIES entries for type $filterType")
            values.take(MAX_FILTER_CACHE_ENTRIES)
        } else {
            values
        }

        // Insérer les nouvelles valeurs avec le timestamp actuel
        val timestamp = System.currentTimeMillis()
        val entities = limitedValues.map { value ->
            RadioFilterEntity(
                filterType = filterType,
                value = value,
                cachedAt = timestamp
            )
        }
        filterDao.insertFilters(entities)
    }

    /**
     * Bug 4.28: Recherche avec résultat typé distinguant offline vs pas de stations
     */
    suspend fun searchStationsWithResult(params: RadioSearchParams): SearchResult = withContext(Dispatchers.IO) {
        // Vérifier d'abord la connectivité
        if (!isNetworkAvailable()) {
            return@withContext SearchResult.Offline
        }

        val servers = config.servers
        if (servers.isEmpty()) {
            return@withContext SearchResult.ServerError("No radio servers configured")
        }

        val query = params.query.trim()
        val country = params.country.trim()
        val language = params.language.trim()
        val results = mutableListOf<RadioStation>()
        var lastException: Exception? = null
        var hadTimeout = false

        for (server in servers) {
            val url = server.toUri()
                .buildUpon()
                .appendEncodedPath("json/stations/search")
                .apply {
                    if (query.isNotEmpty()) {
                        appendQueryParameter("name", query)
                    }
                    if (country.isNotEmpty()) {
                        appendQueryParameter("country", country)
                    }
                    if (language.isNotEmpty()) {
                        appendQueryParameter("language", language)
                    }
                    if (config.hideBroken) {
                        appendQueryParameter("hidebroken", "true")
                    }
                    appendQueryParameter("limit", config.maxResults.toString())
                }
                .build()
                .toString()
            try {
                val payload = fetchJsonArray(url)
                val normalized = normalizeStations(payload)
                if (normalized.isNotEmpty()) {
                    results.addAll(normalized)
                    break
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout on server $server, trying next", e)
                hadTimeout = true
                lastException = e
            } catch (e: UnknownHostException) {
                Log.w(TAG, "DNS resolution failed for $server, trying next", e)
                lastException = e
            } catch (e: IOException) {
                Log.w(TAG, "IO error on server $server, trying next", e)
                lastException = e
            } catch (e: Exception) {
                Log.w(TAG, "Search failed on server $server, trying next", e)
                lastException = e
            }
        }

        // Déterminer le résultat
        when {
            results.isNotEmpty() -> SearchResult.Success(results.take(config.maxResults))
            hadTimeout -> SearchResult.Timeout
            lastException != null -> SearchResult.ServerError(lastException.message ?: "Unknown error")
            else -> SearchResult.Success(emptyList()) // Recherche réussie mais aucun résultat
        }
    }

    /**
     * Recherche de stations (méthode legacy pour compatibilité)
     * @throws Exception en cas d'erreur réseau
     */
    suspend fun searchStations(params: RadioSearchParams): List<RadioStation> = withContext(Dispatchers.IO) {
        when (val result = searchStationsWithResult(params)) {
            is SearchResult.Success -> result.stations
            is SearchResult.Offline -> throw IOException("Device is offline")
            is SearchResult.Timeout -> throw SocketTimeoutException("Request timed out")
            is SearchResult.ServerError -> throw IOException(result.message)
        }
    }

    private suspend fun fetchDirectory(path: String): List<String> {
        val servers = config.servers
        if (servers.isEmpty()) {
            return emptyList()
        }
        for (server in servers) {
            // Ajouter un paramètre limit très élevé pour obtenir tous les résultats
            val url = server.toUri()
                .buildUpon()
                .appendEncodedPath(path)
                .appendQueryParameter("limit", "10000")  // Demander jusqu'à 10000 éléments
                .appendQueryParameter("hidebroken", "false")  // Inclure tous les éléments
                .build()
                .toString()
            try {
                val payload = fetchJsonArray(url)
                return normalizeDirectory(payload)
            } catch (e: Exception) {
                Log.w(TAG, "Fetch directory failed on server $server, trying next", e)
            }
        }
        return emptyList()
    }

    private fun normalizeDirectory(payload: JSONArray): List<String> {
        val entries = mutableListOf<String>()
        for (index in 0 until payload.length()) {
            val item = payload.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isNotEmpty()) {
                entries.add(name)
            }
        }
        return entries.distinct().sorted()
    }

    private fun normalizeStations(payload: JSONArray): List<RadioStation> {
        val stations = mutableListOf<RadioStation>()
        for (index in 0 until payload.length()) {
            val entry = payload.optJSONObject(index) ?: continue
            normalizeStation(entry)?.let { stations.add(it) }
        }
        return stations
    }

    private fun normalizeStation(raw: JSONObject): RadioStation? {
        val id = raw.optString("stationuuid").trim().ifEmpty { raw.optString("id").trim() }
        val url = raw.optString("url").trim().ifEmpty { raw.optString("url_resolved").trim() }
        if (id.isEmpty() || url.isEmpty()) {
            return null
        }
        // Valider que l'URL a un protocole HTTP/HTTPS
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.w(TAG, "Station $id has invalid URL protocol: $url")
            return null
        }
        val name = raw.optString("name").trim().ifEmpty { "Station" }
        val country = raw.optString("country").trim()
        val language = raw.optString("language").trim()
        val favicon = raw.optString("favicon").trim()
        val bitrateValue = raw.optInt("bitrate", 0)
        val bitrate = if (bitrateValue > 0) bitrateValue else null
        return RadioStation(
            id = id,
            name = name,
            url = url,
            country = country,
            language = language,
            favicon = favicon,
            bitrate = bitrate
        )
    }

    private suspend fun fetchJsonArray(url: String): JSONArray {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", config.userAgent)
            .build()
        return withContext(Dispatchers.IO) {
            // Bug 4.18: Annuler l'appel précédent et stocker le nouveau
            val call = client.newCall(request)
            synchronized(callLock) {
                currentCall?.cancel()
                currentCall = call
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }

                    // Bug 4.21: Vérifier la taille de la réponse avant de la lire
                    val contentLength = response.body?.contentLength() ?: -1
                    if (contentLength > MAX_RESPONSE_SIZE_BYTES) {
                        throw IllegalStateException("Response too large: $contentLength bytes (max: $MAX_RESPONSE_SIZE_BYTES)")
                    }

                    val body = response.body ?: throw IllegalStateException("Empty response body")

                    // Si Content-Length est connu et valide, lire directement
                    // Sinon, lire par chunks avec vérification de taille
                    val raw = if (contentLength in 1..MAX_RESPONSE_SIZE_BYTES) {
                        body.string()
                    } else {
                        // Lire par chunks avec limite de taille
                        val reader = body.charStream()
                        val builder = StringBuilder()
                        val buffer = CharArray(8192)
                        var totalCharsRead = 0L
                        var charsRead: Int

                        while (reader.read(buffer).also { charsRead = it } != -1) {
                            totalCharsRead += charsRead
                            // Approximation: 1 char ~= 1-4 bytes UTF-8
                            if (totalCharsRead * 4 > MAX_RESPONSE_SIZE_BYTES) {
                                throw IllegalStateException("Response exceeded max size during streaming")
                            }
                            builder.append(buffer, 0, charsRead)
                        }
                        builder.toString()
                    }

                    JSONArray(raw)
                }
            } finally {
                synchronized(callLock) {
                    if (currentCall == call) {
                        currentCall = null
                    }
                }
            }
        }
    }
}
