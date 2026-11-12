package com.Atom2Universe.app.crypto

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
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
    RED("civitai.red", 4 or 8 or 16)
}

internal enum class ClickerBackgroundSource(val site: CivitaiSite? = null, val weekly: Boolean = false) {
    FOLDER,
    RECENT(CivitaiSite.STANDARD),
    WEEK(CivitaiSite.STANDARD, true),
    RED_RECENT(CivitaiSite.RED),
    RED_WEEK(CivitaiSite.RED, true);

    companion object {
        val feeds = entries.filter { it.site != null }
        fun feed(site: CivitaiSite, weekly: Boolean) = feeds.first { it.site == site && it.weekly == weekly }
    }
}

internal class CivitaiSlideshow(
    private val context: Context,
    private val client: OkHttpClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now
) {
    companion object {
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR
        private const val RETRY_DELAY = 60_000L
        private const val MAX_DOWNLOADS = 40
        private const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
        private const val CACHE_BYTES = 64L * 1024 * 1024
        private val mutex = Mutex()
    }

    private val prefs = context.getSharedPreferences("clicker_civitai", Context.MODE_PRIVATE)
    private val directory = File(context.cacheDir, "clicker-civitai")
    private data class Entry(val id: Long, val url: String, val title: String, val date: String)
    private fun file(id: Long) = File(directory, "$id.webp")
    private fun ids(key: String) = prefs.getString(key, "").orEmpty().split(',').mapNotNull(String::toLongOrNull)
    private fun isCacheFile(file: File) = file.parentFile == directory && file.extension == "webp" &&
        file.nameWithoutExtension.toLongOrNull() != null
    private fun id(file: File) = file.nameWithoutExtension.toLong()
    var exportFolder: Uri?
        get() = prefs.getString("export_folder", null)?.let(Uri::parse)
        set(value) { prefs.edit { putString("export_folder", value?.toString()) } }

    var transparency: Int
        get() = prefs.getInt("widget_transparency", 0)
        set(value) { prefs.edit { putInt("widget_transparency", value.coerceIn(0, 90)) } }

    fun date(file: File): String = prefs.getString("date_" + id(file), null) ?: today().toString()
    fun title(file: File): String? = prefs.getString("title_" + id(file), null)?.takeIf(String::isNotBlank)
    fun saved(file: File) = prefs.contains("saved_" + id(file))
    fun allowed(file: File) = allowed(id(file))
    private fun allowed(id: Long): Boolean {
        val day = today().toEpochDay()
        if (prefs.getLong("reject_$id", -1) > day) return false
        if (prefs.contains("saved_$id")) return prefs.getLong("saved_$id", -1) >= day
        val cached = file(id)
        return !cached.isFile || expiresAt(cached) > nowMillis()
    }

    private fun expiresAt(file: File) = prefs.getLong("expires_" + id(file), file.lastModified() + DAY)

    private fun pool(feed: ClickerBackgroundSource): List<Long> =
        ids("pool_$feed").filter { file(it).isFile && allowed(it) }

    data class Position(val current: Int = 0, val total: Int = 0)

    suspend fun position(feed: ClickerBackgroundSource, displayed: File?): Position = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ready = pool(feed)
            Position(displayed?.let { ready.indexOf(id(it)) + 1 } ?: 0, ready.size)
        }
    }

    init {
        val day = today()
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            if (key.startsWith("hidden_") && value == true) {
                editor.remove(key).putLong("reject_" + key.removePrefix("hidden_"), day.plusMonths(2).toEpochDay())
            } else if (key.startsWith("reject_") && value is Long && value <= day.toEpochDay()) {
                editor.remove(key)
            }
        }
        editor.apply()
    }
    private data class Page(val entries: List<Entry>, val next: String)
    private class InvalidImage : IOException()
    private class TooLarge : IOException()
    private class CacheFull : IOException()
    private val unavailable = mutableMapOf<Long, Long>()
    private val retryImagesAfter = mutableMapOf<CivitaiSite, Long>()

    var source: ClickerBackgroundSource
        get() = ClickerBackgroundSource.entries.firstOrNull { it.name == prefs.getString("source", null) }
            ?: ClickerBackgroundSource.FOLDER
        set(value) {
            prefs.edit {
                putString("source", value.name)
                if (value.site != null) putBoolean("weekly_sort", value.weekly)
            }
        }

    val weeklySort: Boolean get() = prefs.getBoolean("weekly_sort", source.weekly)

    suspend fun hide(file: File) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isCacheFile(file) || saved(file) || !moveToTrash(file)) return@withLock false
            val id = id(file)
            prefs.edit {
                remove("expires_$id")
                putLong("reject_$id", today().plusMonths(2).toEpochDay())
                for (feed in ClickerBackgroundSource.feeds) {
                    if (prefs.getLong("last_$feed", -1) == id) {
                        val order = ids("pool_$feed")
                        putLong("last_$feed", order.getOrNull(order.indexOf(id) - 1) ?: -1L)
                    }
                    for (key in listOf("pool_$feed", "seen_$feed")) {
                        putString(key, ids(key).filterNot { it == id }.joinToString(","))
                    }
                }
            }
            true
        }
    }

    suspend fun unvalidate(file: File): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isCacheFile(file) || !file.isFile || !allowed(file) || !saved(file)) return@withLock false
            prefs.edit {
                remove("saved_" + id(file))
                putLong("expires_" + id(file), nowMillis() + DAY)
            }
            true
        }
    }

    suspend fun save(file: File): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isCacheFile(file)) return@withLock false
            if (saved(file)) return@withLock true
            if (!allowed(file) || !file.isFile) return@withLock false
            val name = "${date(file)}-Civitai-${id(file)}.webp"
            val destination = exportFolder
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

    // Android/media is accessible in Files. .nomedia keeps rejected images out of galleries.
    internal fun trashDirectory(): File = File(android.os.Environment.getExternalStorageDirectory(),
        "Android/media/${context.packageName}/Civitai-Trash")

    private fun moveToTrash(source: File): Boolean {
        if (!source.isFile) return false
        val trash = trashDirectory()
        return try {
            if (!trash.isDirectory && !trash.mkdirs()) return false
            File(trash, ".nomedia").createNewFile()
            val part = File.createTempFile("${date(source)}-Civitai-${id(source)}-trash-", ".part", trash)
            val target = File(trash, part.nameWithoutExtension + ".webp")
            try {
                source.copyTo(part, overwrite = true)
                if (!part.renameTo(target)) return false
                target.setLastModified(nowMillis())
                source.delete()
            } finally {
                part.delete()
            }
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun cleanExpiredLocked() {
        val removed = mutableSetOf<Long>()
        for (cached in directory.listFiles().orEmpty()) {
            val id = cached.nameWithoutExtension.toLongOrNull() ?: continue
            if (allowed(id)) continue
            val cleared = if (saved(cached)) cached.delete() else moveToTrash(cached)
            if (cleared) removed.add(id)
        }
        if (removed.isNotEmpty()) prefs.edit {
            for (id in removed) {
                remove("expires_$id")
                remove("title_$id")
                remove("date_$id")
            }
            for (feed in ClickerBackgroundSource.feeds) {
                val order = ids("pool_$feed")
                val expired = order.filter { it in removed }
                if (expired.isEmpty()) continue
                val remaining = order - removed
                putString("pool_$feed", remaining.joinToString(","))
                if (remaining.none { file(it).isFile && allowed(it) }) remove("initial_day_$feed")
                putString("seen_$feed", (history(feed.name) + expired).distinct().takeLast(500).joinToString(","))
            }
        }
        val now = nowMillis()
        if (now - prefs.getLong("last_trash_sweep", 0) >= HOUR) {
            val trash = trashDirectory().listFiles() ?: return
            for (item in trash) {
                if ("-trash-" in item.name && item.extension in listOf("webp", "part") &&
                    item.lastModified() <= now - 30 * DAY) item.delete()
            }
            prefs.edit { putLong("last_trash_sweep", now) }
        }
    }

    suspend fun cleanExpired() = withContext(Dispatchers.IO) { mutex.withLock { cleanExpiredLocked() } }

    suspend fun nextExpiryDelay(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = nowMillis()
            directory.listFiles().orEmpty().asSequence().filter { it.extension == "webp" && !saved(it) }
                .map { expiresAt(it) - now }.filter { it > 0 }.minOrNull() ?: DAY
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

    private fun remainingDownloads(day: Long = today().toEpochDay()): Int =
        if (prefs.getLong("day", -1) == day)
            MAX_DOWNLOADS - prefs.getInt("downloads", 0) else MAX_DOWNLOADS

    private fun reserveDownload(): Boolean {
        val day = today().toEpochDay()
        val remaining = remainingDownloads(day)
        return remaining > 0 && prefs.edit().putLong("day", day)
            .putInt("downloads", MAX_DOWNLOADS - remaining + 1).commit()
    }

    suspend fun select(feed: ClickerBackgroundSource, direction: Int, displayedId: Long?,
                       progress: (Int) -> Unit = {}): File? = withContext(Dispatchers.IO) {
        mutex.withLock {
            cleanExpiredLocked()
            val day = today().toEpochDay()
            if (prefs.getLong("initial_day_$feed", -1) != day) {
                prefs.edit {
                    putLong("initial_day_$feed", day)
                    putInt("initial_left_$feed", (20 - pool(feed).size).coerceAtLeast(0))
                }
            }
            var left = prefs.getInt("initial_left_$feed", 0)
            if (left > 0 && (direction == 0 || pool(feed).isEmpty())) {
                while (left > 0) {
                    progress(20 - left)
                    if (addOne(feed, manual = false) == null) break
                    left--
                    prefs.edit { putInt("initial_left_$feed", left) }
                }
            }
            val ready = pool(feed)
            if (ready.isEmpty()) return@withLock null
            val last = displayedId ?: prefs.getLong("last_$feed", -1)
            val position = ready.indexOf(last)
            val index = when {
                position < 0 -> if (direction < 0) ready.lastIndex else 0
                else -> Math.floorMod(position + direction, ready.size)
            }
            file(ready[index])
        }
    }

    suspend fun addBatch(feed: ClickerBackgroundSource, progress: (Int) -> Unit): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            cleanExpiredLocked()
            var added = 0
            while (added < 20 && addOne(feed, manual = true) != null) {
                added++
                progress(added)
            }
            if (added > 0) prefs.edit {
                putLong("initial_day_$feed", today().toEpochDay())
                putInt("initial_left_$feed", 0)
            }
            added
        }
    }

    // Each step commits its own file and cursor so cancelling a batch keeps its completed images.
    private suspend fun addOne(feed: ClickerBackgroundSource, manual: Boolean): File? {
        val site = requireNotNull(feed.site)
        val key = feed.name
        val now = System.currentTimeMillis()
        val already = (pool(feed) + history(key)).toHashSet()
        val hasBudget = manual || remainingDownloads() > 0
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
        repeat(3) { pageIndex ->
            for (entry in page.entries) {
                if (entry.id in already || !allowed(entry.id) || now < (unavailable[entry.id] ?: 0)) continue
                val target = file(entry.id)
                if (!target.isFile) {
                    if (!hasBudget || now < (retryImagesAfter[site] ?: 0L)) continue
                    if (directory.listFiles().orEmpty().count { it.extension == "webp" } >= 80) return null
                    if (attempted >= 3 || (!manual && !reserveDownload())) return null
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
            if (pageIndex == 2 || !online || !hasBudget || page.next.isEmpty() || !fetch(page.next, true)) return null
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

    private fun history(key: String): List<Long> = ids("seen_$key")

    fun remember(feed: ClickerBackgroundSource, file: File) {
        val id = file.nameWithoutExtension.toLongOrNull() ?: return
        val key = feed.name
        val previous = history(key)
        prefs.edit {
            putLong("last_$key", id)
            if (id !in previous) putString("seen_$key", (previous + id).takeLast(500).joinToString(","))
        }
    }


    private fun firstUrl(site: CivitaiSite, weekly: Boolean): String =
        "https://${site.host}/api/v1/images".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "20")
            .addQueryParameter("type", "image")
            .addQueryParameter("browsingLevel", site.browsingLevel.toString())
            .addQueryParameter("sort", if (weekly) "Most Reactions" else "Newest")
            .apply { if (weekly) addQueryParameter("period", "Week") }
            .build().toString()

    private suspend fun fetchPage(url: String, site: CivitaiSite): Page {
        val json = JSONObject(requestBytes(url, 2L * 1024 * 1024).toString(Charsets.UTF_8))
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
                    add(Entry(id, imageUrl.toString(), title.take(200), date.toString()))
                }
            }
        }
        val next = json.optJSONObject("metadata")?.optString("nextPage")?.toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.host == site.host && it.encodedPath == "/api/v1/images" }
        return Page(entries.distinctBy { it.id }, next?.toString().orEmpty())
    }

    private fun readPage(key: String): Page {
        val entries = JSONArray(prefs.getString("entries_$key", "[]"))
        return Page(List(entries.length()) { i ->
            val row = entries.getJSONArray(i)
            Entry(row.getLong(0), row.getString(1), row.optString(2), row.optString(3, today().toString()))
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
            prefs.edit { putLong("expires_" + entry.id, nowMillis() + DAY) }
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

class CivitaiCacheWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        CivitaiSlideshow(applicationContext, OkHttpClient()).cleanExpired()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CivitaiCacheWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("clicker-civitai-cleanup",
                ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
