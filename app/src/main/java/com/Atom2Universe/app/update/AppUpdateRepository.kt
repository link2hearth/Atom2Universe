package com.Atom2Universe.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.Atom2Universe.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AppUpdateRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    // Dossier exclu des sauvegardes, exposé uniquement à l'installateur via FileProvider.
    private val directory get() = File(app.filesDir, "updates").apply { mkdirs() }
    private val apk get() = File(directory, "update.apk")

    companion object {
        const val RELEASES_URL = "https://github.com/link2hearth/Atom2Universe/releases"
        private const val API_URL = "https://api.github.com/repos/link2hearth/Atom2Universe/releases/latest"
        private const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
        private val checkMutex = Mutex()
        private val downloadMutex = Mutex()
        private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()
    }

    fun cachedRelease(): UpdateRelease? = if (BuildConfig.DEBUG) null else runCatching {
        UpdateRelease.fromGitHub(JSONObject(prefs.getString("release", "{}")!!), BuildConfig.VERSION_NAME)
    }.getOrNull()

    suspend fun check(force: Boolean = false): UpdateRelease? = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) return@withContext null
        checkMutex.withLock {
            val now = System.currentTimeMillis()
            val age = now - prefs.getLong("attempt", 0)
            if (!force && age in 0 until CHECK_INTERVAL) return@withLock cachedRelease()
            prefs.edit { putLong("attempt", now) }
            client.newCall(Request.Builder().url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Atom2Universe/${BuildConfig.VERSION_NAME}").build()).execute().use { response ->
                currentCoroutineContext().ensureActive()
                if (response.code == 404) {
                    prefs.edit { remove("release") }
                    return@withLock null
                }
                if (!response.isSuccessful) throw IOException("GitHub HTTP ${response.code}")
                val json = JSONObject(response.body?.string() ?: throw IOException("Empty release"))
                val release = UpdateRelease.fromGitHub(json, BuildConfig.VERSION_NAME)
                prefs.edit { putString("release", json.toString()) }
                release
            }
        }
    }

    suspend fun download(release: UpdateRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (apk.exists() && prefs.getString("apk_url", null) == release.url) {
                if (runCatching { validateApk(apk) }.isSuccess) return@withLock apk
            }
            apk.delete()
            prefs.edit { remove("apk_url") }
            val partial = File(directory, "update.part")
            try {
                client.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
                    if (!response.isSuccessful || response.request.url.scheme != "https")
                        throw IOException("Download HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty APK")
                    if (directory.usableSpace < release.size + 16L * 1024 * 1024)
                        throw IOException("Insufficient space")
                    val sha = MessageDigest.getInstance("SHA-256")
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            var lastProgress = -1
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > release.size) throw IOException("Unexpected APK size")
                                output.write(buffer, 0, count)
                                sha.update(buffer, 0, count)
                                val percent = (total * 100 / release.size).toInt()
                                if (percent != lastProgress) { progress(percent); lastProgress = percent }
                            }
                            if (total != release.size) throw IOException("Incomplete APK")
                        }
                    }
                    if (release.digest.startsWith("sha256:")) {
                        val actual = sha.digest().joinToString("") { "%02x".format(it) }
                        if (!actual.equals(release.digest.removePrefix("sha256:"), ignoreCase = true))
                            throw InvalidUpdateException()
                    }
                }
                validateApk(partial)
                if (!partial.renameTo(apk)) throw IOException("Cannot save APK")
                prefs.edit { putString("apk_url", release.url) }
                apk
            } finally {
                partial.delete()
            }
        }
    }

    /** Android reste le vérificateur final ; on écarte déjà les mauvais paquets/signatures. */
    @Suppress("DEPRECATION")
    fun validateApk(file: File) {
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw InvalidUpdateException()
        val installed = pm.getPackageInfo(app.packageName, flags)
        val newer = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode > installed.longVersionCode
            else candidate.versionCode > installed.versionCode
        fun signatures(info: PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
                ?.map { it.toCharsString() }?.toSet().orEmpty()
        val currentSigners = signatures(installed)
        if (candidate.packageName != app.packageName || !newer || currentSigners.isEmpty() ||
            signatures(candidate) != currentSigners) throw InvalidUpdateException()
    }

    class InvalidUpdateException : IOException("APK package, version or signature mismatch")
}
