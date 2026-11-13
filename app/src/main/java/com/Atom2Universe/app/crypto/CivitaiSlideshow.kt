package com.Atom2Universe.app.crypto

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class CivitaiSite(val host: String, val browsingLevel: Int) {
    STANDARD("civitai.com", 1),
    RED("civitai.red", 4 or 8 or 16),
    WALLHAVEN("wallhaven.cc", 0)
}

internal enum class ClickerBackgroundSource(val site: CivitaiSite? = null, val weekly: Boolean = false) {
    FOLDER,
    RECENT(CivitaiSite.STANDARD),
    WEEK(CivitaiSite.STANDARD, true),
    RED_RECENT(CivitaiSite.RED),
    RED_WEEK(CivitaiSite.RED, true),
    WALLHAVEN_RECENT(CivitaiSite.WALLHAVEN),
    WALLHAVEN_WEEK(CivitaiSite.WALLHAVEN, true);

    val pageSize get() = if (site == CivitaiSite.WALLHAVEN) 24 else 20
    val batchSize get() = 200

    companion object {
        val feeds = entries.filter { it.site != null }
        fun feed(site: CivitaiSite, weekly: Boolean) = feeds.first { it.site == site && it.weekly == weekly }
    }
}

internal class CivitaiSlideshow(
    private val context: Context,
    private val client: OkHttpClient,
    private val today: () -> LocalDate = LocalDate::now
) {
    companion object {
        private const val HOUR = 3_600_000L
        private const val RETRY_DELAY = 60_000L
        private const val MAX_PAGES_PER_IMAGE = 40
        private const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
        private const val SITE_FILES = 1000
        // Safety net only: the file count of each site is the real limit.
        private const val CACHE_BYTES = 3L * 1024 * 1024 * 1024
        private val mutex = Mutex()
        const val RED_VISIBLE = "red_visible"

        fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences("clicker_civitai", Context.MODE_PRIVATE)

        private fun storedSource(prefs: SharedPreferences): ClickerBackgroundSource {
            val name = prefs.getString("source", null)
            return ClickerBackgroundSource.entries.firstOrNull { it.name == name } ?: ClickerBackgroundSource.FOLDER
        }

        fun toggleRedVisibility(context: Context) {
            val prefs = preferences(context)
            val selected = storedSource(prefs)
            prefs.edit {
                putBoolean(RED_VISIBLE, !prefs.getBoolean(RED_VISIBLE, false))
                if (selected.site == CivitaiSite.RED) {
                    putString("source", ClickerBackgroundSource.feed(CivitaiSite.STANDARD, selected.weekly).name)
                }
            }
        }
    }

    private val prefs = preferences(context)

    // Hidden images used to be kept a month in a folder nobody could browse: remove what is left of it.
    suspend fun removeOldTrash() = withContext(Dispatchers.IO) {
        if (prefs.getBoolean("old_trash_removed", false)) return@withContext
        runCatching {
            File(android.os.Environment.getExternalStorageDirectory(),
                "Android/media/${context.packageName}/Civitai-Trash").deleteRecursively()
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("clicker-civitai-cleanup")
        }
        prefs.edit { putBoolean("old_trash_removed", true) }
    }
    val redVisible: Boolean get() = prefs.getBoolean(RED_VISIBLE, false)
    private val directory = File(context.cacheDir, "clicker-civitai")
    private data class Entry(val id: String, val url: String, val title: String, val date: String)
    private fun file(id: String) = File(directory, "$id.webp")
    private fun ids(key: String) = prefs.getString(key, "").orEmpty().split(',').filter { it.isNotEmpty() }
    private fun isCacheFile(file: File) = file.parentFile == directory && file.extension == "webp" &&
        cacheId.matches(file.nameWithoutExtension)
    private val cacheId = Regex("(?:[0-9]+|wallhaven-[a-z0-9]+)")
    private fun id(file: File) = file.nameWithoutExtension
    fun displayId(file: File) = id(file).removePrefix("wallhaven-")
    private fun imageName(file: File): String {
        val provider = if (id(file).startsWith("wallhaven-")) "Wallhaven" else "Civitai"
        return "${date(file)}-$provider-${displayId(file)}"
    }
    private fun lastId(feed: ClickerBackgroundSource): String? = prefs.getString("last_image_$feed", null)
        ?: prefs.getLong("last_$feed", -1).takeIf { it > 0 }?.toString()

    private fun folderKey(site: CivitaiSite) = if (site == CivitaiSite.STANDARD) "export_folder" else "export_folder_$site"
    fun folder(site: CivitaiSite): Uri? = prefs.getString(folderKey(site), null)?.let(Uri::parse)
    fun setFolder(site: CivitaiSite, uri: Uri?) = prefs.edit { putString(folderKey(site), uri?.toString()) }
    var exportFolder: Uri?
        get() = folder(source.site ?: CivitaiSite.STANDARD)
        set(value) { setFolder(source.site ?: CivitaiSite.STANDARD, value) }

    var transparency: Int
        get() = prefs.getInt("widget_transparency", 0)
        set(value) { prefs.edit { putInt("widget_transparency", value.coerceIn(0, 90)) } }

    fun date(file: File): String = prefs.getString("date_" + id(file), null) ?: today().toString()
    fun title(file: File): String? = prefs.getString("title_" + id(file), null)?.takeIf(String::isNotBlank)
    fun saved(file: File) = prefs.contains("saved_" + id(file))
    fun allowed(file: File) = allowed(id(file))
    private fun allowed(id: String) = prefs.getLong("reject_$id", -1) <= today().toEpochDay()

    private fun pool(feed: ClickerBackgroundSource): List<String> =
        ids("pool_$feed").filter { file(it).isFile && allowed(it) }

    data class Position(val current: Int = 0, val total: Int = 0)

    suspend fun position(feed: ClickerBackgroundSource, displayed: File?): Position = withContext(Dispatchers.IO) {
        val ready = pool(feed)
        Position(displayed?.let { ready.indexOf(id(it)) + 1 } ?: 0, ready.size)
    }

    init {
        val day = today()
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            if (key.startsWith("hidden_") && value == true) {
                editor.remove(key).putLong("reject_" + key.removePrefix("hidden_"), day.plusMonths(2).toEpochDay())
            } else if (key.startsWith("reject_") && value is Long && value <= day.toEpochDay()) {
                editor.remove(key)
            } else if (key.startsWith("expires_")) {
                editor.remove(key)
            }
        }
        editor.apply()
        directory.listFiles()?.forEach { if (it.extension == "undo") it.delete() }
    }
    private data class Page(val entries: List<Entry>, val next: String)
    private class InvalidImage : IOException()
    private class TooLarge : IOException()
    private class CacheFull : IOException()
    private val unavailable = mutableMapOf<String, Long>()
    private val retryImagesAfter = mutableMapOf<CivitaiSite, Long>()

    private fun available(feed: ClickerBackgroundSource) = feed.site != CivitaiSite.RED || redVisible

    private fun visibleSource(feed: ClickerBackgroundSource) =
        if (available(feed)) feed else ClickerBackgroundSource.feed(CivitaiSite.STANDARD, feed.weekly)

    var source: ClickerBackgroundSource
        get() = visibleSource(storedSource(prefs))
        set(value) {
            val selected = visibleSource(value)
            prefs.edit {
                putString("source", selected.name)
                if (selected.site != null) putBoolean("weekly_sort", selected.weekly)
            }
        }

    val weeklySort: Boolean get() = prefs.getBoolean("weekly_sort", source.weekly)

    private class Hidden(val id: String, val backup: File, val places: Map<String, Int>, val seen: Set<String>)
    private var hidden: Hidden? = null

    // Called under the writer lock: the last hidden image can no longer come back.
    private fun forgetHidden() {
        val last = hidden ?: return
        hidden = null
        last.backup.delete()
        prefs.edit {
            remove("title_" + last.id)
            remove("date_" + last.id)
            remove("saved_" + last.id)
        }
    }

    suspend fun confirmHide() = withContext(Dispatchers.IO) { mutex.withLock { forgetHidden() } }

    // The file is only set aside: undoHide() brings it back until the next hide or confirmHide().
    suspend fun hide(file: File) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isCacheFile(file) || !file.isFile) return@withLock false
            forgetHidden()
            val id = id(file)
            val backup = File(directory, "$id.undo")
            if (!file.renameTo(backup)) return@withLock false
            val places = mutableMapOf<String, Int>()
            val seen = mutableSetOf<String>()
            prefs.edit {
                putLong("reject_$id", today().plusMonths(2).toEpochDay())
                for (feed in ClickerBackgroundSource.feeds) {
                    val order = ids("pool_$feed")
                    val place = order.indexOf(id)
                    if (lastId(feed) == id) {
                        remove("last_$feed")
                        putString("last_image_$feed", order.getOrNull(place - 1))
                    }
                    if (place >= 0) {
                        places["pool_$feed"] = place
                        putString("pool_$feed", (order - id).joinToString(","))
                    }
                    val history = ids("seen_$feed")
                    if (id in history) {
                        seen += "seen_$feed"
                        putString("seen_$feed", (history - id).joinToString(","))
                    }
                }
            }
            hidden = Hidden(id, backup, places, seen)
            true
        }
    }

    suspend fun undoHide(): File? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val last = hidden ?: return@withLock null
            val target = file(last.id)
            if (!last.backup.renameTo(target)) return@withLock null
            hidden = null
            prefs.edit {
                remove("reject_" + last.id)
                for ((key, place) in last.places) {
                    val order = ids(key).filterNot { it == last.id }.toMutableList()
                    order.add(place.coerceAtMost(order.size), last.id)
                    putString(key, order.joinToString(","))
                }
                for (key in last.seen) putString(key, (ids(key) + last.id).distinct().joinToString(","))
            }
            target
        }
    }

    suspend fun unvalidate(file: File): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isCacheFile(file) || !file.isFile || !allowed(file) || !saved(file)) return@withLock false
            prefs.edit { remove("saved_" + id(file)) }
            true
        }
    }

    suspend fun save(file: File, site: CivitaiSite = source.site ?: CivitaiSite.STANDARD): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isCacheFile(file)) return@withLock false
            if (saved(file)) return@withLock true
            if (!allowed(file) || !file.isFile) return@withLock false
            val name = "${imageName(file)}.webp"
            val destination = folder(site)
            val copyKey = "copy_${id(file)}_${destination ?: "Pictures"}"
            val previous = prefs.getString(copyKey, null)?.let(Uri::parse)
            val copy = previous?.takeIf { uri ->
                runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true }
                    .getOrDefault(false)
            } ?: runCatching {
                file.inputStream().use { input ->
                    if (destination == null) copyToAtom2Universe(context, input, name, "image/webp")
                    else saveClickerImageToTree(context, destination, input, name)
                }
            }.getOrNull()
            if (copy != null) prefs.edit {
                putString(copyKey, copy.toString())
                putLong("saved_" + id(file), today().toEpochDay())
                val local = MainClickerPreferences.getFolderUri(context)
                if (local != null && includesDestination(local, destination)) putString("refresh_folder", local.toString())
            }
            copy != null
        }
    }

    private fun includesDestination(folder: Uri, destination: Uri?): Boolean {
        if (folder == destination) return true
        if (folder.authority != "com.android.externalstorage.documents") return false
        if (destination != null && destination.authority != folder.authority) return false
        val tree = android.provider.DocumentsContract.getTreeDocumentId(folder).trimEnd('/')
        val export = destination?.let(android.provider.DocumentsContract::getTreeDocumentId) ?: "primary:Pictures/Atom2Universe"
        return export == tree || export.startsWith(if (tree.endsWith(':')) tree else "$tree/")
    }

    fun folderNeedsRefresh(folder: Uri?) = folder != null && prefs.getString("refresh_folder", null) == folder.toString()
    fun folderRefreshed(folder: Uri?) {
        if (folderNeedsRefresh(folder)) prefs.edit { remove("refresh_folder") }
    }

    private fun selectCached(feed: ClickerBackgroundSource, direction: Int, displayedId: String?): File? {
        val last = displayedId ?: lastId(feed)
        if (direction == 0 && last != null && last in ids("pool_$feed")) {
            val cached = file(last)
            if (cached.isFile && allowed(last)) return cached
        }
        val ready = pool(feed)
        if (ready.isEmpty()) return null
        val position = ready.indexOf(last)
        val index = if (position >= 0) Math.floorMod(position + direction, ready.size)
                    else if (direction < 0) ready.lastIndex else 0
        return file(ready[index])
    }

    suspend fun select(feed: ClickerBackgroundSource, direction: Int, displayedId: String?): File? =
        withContext(Dispatchers.IO) {
            if (!available(feed)) return@withContext null
            // Published WebPs can be read while a download or a trash sweep holds the writer lock.
            selectCached(feed, direction, displayedId) ?: mutex.withLock {
                selectCached(feed, direction, displayedId)?.let { return@withLock it }
                if (ids("pool_$feed").any { !file(it).isFile }) {
                    prefs.edit { remove("pool_$feed"); remove("initial_day_$feed") }
                }
                addInitialImage(feed)
            }
        }

    fun needsInitialBatch(feed: ClickerBackgroundSource) =
        prefs.getLong("initial_day_$feed", -1) != today().toEpochDay() || prefs.getInt("initial_left_$feed", 0) > 0

    // Called under the writer lock. Only completed WebPs count towards the initial twenty.
    private suspend fun addInitialImage(feed: ClickerBackgroundSource): File? {
        val day = today().toEpochDay()
        if (prefs.getLong("initial_day_$feed", -1) != day) {
            prefs.edit {
                putLong("initial_day_$feed", day)
                putInt("initial_left_$feed", (feed.pageSize - pool(feed).size).coerceAtLeast(0))
            }
        }
        val left = prefs.getInt("initial_left_$feed", 0)
        if (left <= 0) return null
        val image = addOne(feed) ?: return null
        prefs.edit { putInt("initial_left_$feed", left - 1) }
        return image
    }

    suspend fun completeInitialBatch(feed: ClickerBackgroundSource, progress: (Int) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            while (needsInitialBatch(feed)) {
                ensureActive()
                val done = mutex.withLock {
                    addInitialImage(feed) ?: return@withContext
                    feed.pageSize - prefs.getInt("initial_left_$feed", 0)
                }
                progress(done)
            }
        }

    // One lock per image: browsing, keeping and hiding stay possible while a long batch downloads.
    // stop() is read between two images: the one being fetched is finished and kept, then nothing more is requested.
    suspend fun addBatch(feed: ClickerBackgroundSource, size: Int = feed.batchSize, stop: () -> Boolean = { false },
                         progress: (Int) -> Unit): Int =
        withContext(Dispatchers.IO) {
            var added = 0
            while (added < size && !stop()) {
                ensureActive()
                mutex.withLock { addOne(feed) } ?: break
                added++
                progress(added)
            }
            if (added > 0) prefs.edit {
                putLong("initial_day_$feed", today().toEpochDay())
                putInt("initial_left_$feed", 0)
            }
            added
        }

    // Each step commits its own file and cursor so cancelling a batch keeps its completed images.
    private suspend fun addOne(feed: ClickerBackgroundSource): File? {
        if (!available(feed)) return null
        val site = requireNotNull(feed.site)
        val key = feed.name
        val now = System.currentTimeMillis()
        val already = (pool(feed) + history(key)).toHashSet()
        var page = readPage(key)
        val mayFetch = now >= prefs.getLong("retry_$key", 0)
        suspend fun fetch(url: String, append: Boolean): Boolean {
            return try {
                val newPage = fetchPage(url, site)
                val head = newPage.entries.joinToString(",") { it.id.toString() }
                if (append || head != prefs.getString("head_$key", null)) {
                    page = newPage.copy(next = newPage.next.takeUnless { it == url }.orEmpty())
                }
                if (!append) prefs.edit { putString("head_$key", head) }
                writePage(key, page, now, append)
                true
            } catch (_: IOException) {
                prefs.edit { putLong("retry_$key", now + RETRY_DELAY) }
                false
            } catch (_: JSONException) {
                prefs.edit { putLong("retry_$key", now + RETRY_DELAY) }
                false
            }
        }
        var online = mayFetch
        if (mayFetch && now - prefs.getLong("updated_$key", 0) >= HOUR) {
            online = fetch(firstUrl(site, feed.weekly), false)
        }
        var attempted = 0
        // Pages holding only known images are crossed freely: after the first page moved, the walk
        // restarts from the top and must get past everything already taken.
        var freshPages = 0
        repeat(MAX_PAGES_PER_IMAGE) {
            var fresh = false
            for (entry in page.entries) {
                if (entry.id in already || !allowed(entry.id)) continue
                fresh = true
                if (now < (unavailable[entry.id] ?: 0)) continue
                val target = file(entry.id)
                if (!target.isFile) {
                    if (now < (retryImagesAfter[site] ?: 0L)) continue
                    if (ClickerBackgroundSource.feeds.filter { it.site == site }.flatMap { ids("pool_$it") }
                            .distinct().count { file(it).isFile } >= SITE_FILES) return null
                    if (attempted >= 3) return null
                    attempted++
                    try {
                        cacheImage(entry)
                    } catch (_: InvalidImage) {
                        unavailable[entry.id] = now + HOUR
                        continue
                    } catch (_: CacheFull) {
                        return null
                    } catch (_: TooLarge) {
                        unavailable[entry.id] = now + HOUR
                        continue
                    } catch (_: IOException) {
                        retryImagesAfter[site] = now + RETRY_DELAY
                        return null
                    }
                }
                prefs.edit {
                    putString("pool_$key", (pool(feed) + entry.id).distinct().joinToString(","))
                    putString("title_" + entry.id, entry.title)
                    putString("date_" + entry.id, entry.date)
                }
                return target
            }
            if (fresh) freshPages++
            if (freshPages >= 3 || !online || page.next.isEmpty() || !fetch(page.next, true)) return null
        }
        return null
    }

    suspend fun rejectUnreadable(file: File) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isCacheFile(file)) return@withLock
            val id = id(file)
            unavailable[id] = System.currentTimeMillis() + HOUR
            file.delete()
        }
    }

    private fun history(key: String): List<String> = ids("seen_$key")

    fun remember(feed: ClickerBackgroundSource, file: File) {
        if (!isCacheFile(file)) return
        val id = id(file)
        val key = feed.name
        val previous = history(key)
        prefs.edit {
            putString("last_image_$key", id)
            if (id !in previous) putString("seen_$key", (previous + id).takeLast(500).joinToString(","))
        }
    }


    private fun firstUrl(site: CivitaiSite, weekly: Boolean): String = if (site == CivitaiSite.WALLHAVEN) {
        "https://wallhaven.cc/api/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("purity", "100")
            .addQueryParameter("sorting", if (weekly) "toplist" else "date_added")
            .apply { if (weekly) addQueryParameter("topRange", "1w") }
            .build().toString()
    } else
        "https://${site.host}/api/v1/images".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "20")
            .addQueryParameter("type", "image")
            .addQueryParameter("browsingLevel", site.browsingLevel.toString())
            .addQueryParameter("sort", if (weekly) "Most Reactions" else "Newest")
            .apply { if (weekly) addQueryParameter("period", "Week") }
            .build().toString()

    private suspend fun fetchPage(url: String, site: CivitaiSite): Page {
        val json = JSONObject(requestBytes(url, 2L * 1024 * 1024).toString(Charsets.UTF_8))
        if (site == CivitaiSite.WALLHAVEN) return wallhavenPage(url, json)
        val items = json.getJSONArray("items")
        val entries = buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optLong("id", -1)
                if (id <= 0 || item.optString("type", "image") != "image") continue
                val rating = item.optInt("browsingLevel", 0)
                if (rating <= 0 || (rating and site.browsingLevel) != rating) continue
                val imageUrl = item.optString("url").toHttpUrlOrNull() ?: continue
                if (imageUrl.isHttps && (imageUrl.host.endsWith(".civitai.com") || imageUrl.host.endsWith(".${site.host}"))) {
                    val title = item.optString("title").takeUnless { it.isBlank() || it == "null" }.orEmpty()
                    val date = runCatching { LocalDate.parse(item.optString("createdAt").take(10)) }.getOrNull() ?: today()
                    add(Entry(id.toString(), imageUrl.toString(), title.take(200), date.toString()))
                }
            }
        }
        val next = json.optJSONObject("metadata")?.optString("nextPage")?.toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.host == site.host && it.encodedPath == "/api/v1/images" }
        return Page(entries.distinctBy { it.id }, next?.toString().orEmpty())
    }

    private fun wallhavenPage(url: String, json: JSONObject): Page {
        val items = json.getJSONArray("data")
        val entries = buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val key = "wallhaven-" + item.optString("id")
                if (!cacheId.matches(key) || item.optString("purity") != "sfw") continue
                val oversized = item.optLong("file_size") > MAX_IMAGE_BYTES
                val path = if (oversized) item.optJSONObject("thumbs")?.optString("large") else item.optString("path")
                val image = path?.toHttpUrlOrNull() ?: continue
                if (!image.isHttps || image.host !in setOf("w.wallhaven.cc", "th.wallhaven.cc")) continue
                val date = runCatching { LocalDate.parse(item.optString("created_at").take(10)) }.getOrElse { today() }
                add(Entry(key, image.toString(), "", date.toString()))
            }
        }
        val meta = json.getJSONObject("meta")
        val current = meta.getInt("current_page")
        val next = if (current in 1 until meta.getInt("last_page")) url.toHttpUrl().newBuilder()
            .setQueryParameter("page", (current + 1).toString()).build().toString() else ""
        return Page(entries.distinctBy { it.id }, next)
    }

    private fun readPage(key: String): Page {
        val entries = JSONArray(prefs.getString("entries_$key", "[]"))
        return Page(List(entries.length()) { i ->
            val row = entries.getJSONArray(i)
            Entry(row.getString(0), row.getString(1), row.optString(2), row.optString(3, today().toString()))
        }, prefs.getString("next_$key", "").orEmpty())
    }

    private fun writePage(key: String, page: Page, now: Long, append: Boolean) {
        val rows = JSONArray()
        page.entries.forEach { rows.put(JSONArray().put(it.id).put(it.url).put(it.title).put(it.date)) }
        prefs.edit {
            putString("entries_$key", rows.toString())
            putString("next_$key", page.next)
            if (!append) putLong("updated_$key", now)
            remove("retry_$key")
        }
    }

    private suspend fun cacheImage(entry: Entry): File {
        val existing = directory.listFiles().orEmpty().filter { it.extension == "webp" }
        val bytes = requestBytes(entry.url, MAX_IMAGE_BYTES)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Image cache unavailable")
        val target = file(entry.id)
        val part = File.createTempFile("image-", ".part", directory)
        try {
            try {
                CivitaiWebp.write(bytes, part)
            } catch (_: IOException) {
                throw InvalidImage()
            } catch (_: IllegalArgumentException) {
                throw InvalidImage()
            }
            if (existing.sumOf { it.length() } + part.length() > CACHE_BYTES) throw CacheFull()
            if (!part.renameTo(target)) throw IOException("Cannot publish WebP")
        } finally {
            part.delete()
        }
        return target
    }

    private suspend fun requestBytes(url: String, limit: Long): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (it.code == 404 || it.code == 410) throw InvalidImage()
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            val body = it.body ?: throw IOException("Empty response")
                            val source = body.source()
                            if (body.contentLength() > limit || source.request(limit + 1)) throw TooLarge()
                            source.readByteArray()
                        }
                        continuation.resume(bytes)
                    } catch (e: IOException) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
}
