package com.Atom2Universe.app.crypto

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import org.robolectric.android.controller.ContentProviderController
import android.graphics.Bitmap
import android.os.Environment
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CivitaiSlideshowTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences("clicker_civitai", Context.MODE_PRIVATE)
    private val requests = Collections.synchronizedList(mutableListOf<String>())
    private val png = ByteArrayOutputStream().also {
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val url = request.url
        requests.add(url.toString())
        val red = url.host == "civitai.red"
        val body = if (red || url.host == "civitai.com") {
            val start = url.queryParameter("cursor")?.toInt() ?: if (red) 1001 else 1
            val next = url.newBuilder().setQueryParameter("cursor", (start + 20).toString()).build()
            val items = JSONArray().put(JSONObject().put("id", 9000).put("type", "image")
                .put("browsingLevel", if (red) 1 else 16).put("url", "https://image.civitai.com/9000.png"))
            for (id in start until start + 20) {
                items.put(JSONObject().put("id", id).put("type", "image").put("browsingLevel", if (red) 16 else 1)
                    .put("createdAt", Instant.parse("2026-11-12T10:00:00Z").toString())
                    .put("title", if (id == 1) "Northern lights" else "")
                    .put("url", "https://image.civitai.com/$id.png"))
            }
            JSONObject().put("items", items).put("metadata", JSONObject().put("nextPage", next.toString()))
                .toString().toResponseBody("application/json".toMediaType())
        } else png.toResponseBody("image/png".toMediaType())
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
    }.build()
    private var slideshow = CivitaiSlideshow(context, client)
    private val recent = ClickerBackgroundSource.RECENT
    private val week = ClickerBackgroundSource.WEEK

    private fun display(source: ClickerBackgroundSource, direction: Int, id: Long? = null): File = runBlocking {
        val file = requireNotNull(slideshow.select(source, direction, id)) {
            "No image for $source, direction $direction, id $id after requests: $requests"
        }
        slideshow.remember(source, file)
        file
    }

    private fun imageRequests() = requests.count { it.startsWith("https://image.") }

    @Test fun countsReadyFilesAndKeepsTheSameImageWhenAppendingAndReopening() = runBlocking {
        val first = display(recent, 0)
        assertEquals(CivitaiSlideshow.Position(1, 20), slideshow.position(recent, first))
        val second = display(recent, 1)
        assertEquals(CivitaiSlideshow.Position(2, 20), slideshow.position(recent, second))
        assertEquals(20, imageRequests())
        assertEquals(20, slideshow.addBatch(recent) {})
        assertEquals(CivitaiSlideshow.Position(2, 40), slideshow.position(recent, second))
        assertTrue(requests.any { "cursor=21" in it })
        slideshow = CivitaiSlideshow(context, client)
        assertEquals(second, display(recent, 0))
        assertEquals(first, display(recent, -1))
        assertEquals("40", display(recent, -1).nameWithoutExtension)
        assertEquals(first, display(recent, 1))
        assertEquals(40, imageRequests())
    }

    @Test fun switchingSitesKeepsEachReserveAndItsOwnPagination() = runBlocking {
        val red = ClickerBackgroundSource.RED_RECENT
        assertEquals("1", display(recent, 0).nameWithoutExtension)
        assertEquals("1001", display(red, 0).nameWithoutExtension)
        assertEquals(20, slideshow.addBatch(red) {})
        assertEquals(40, slideshow.position(red, null).total)
        assertEquals(20, slideshow.position(recent, null).total)
        assertEquals("1002", display(red, 1).nameWithoutExtension)
        assertEquals("1001", display(red, -1).nameWithoutExtension)
        slideshow = CivitaiSlideshow(context, client)
        assertEquals("1", display(recent, 0).nameWithoutExtension)
        assertEquals("1001", display(red, 0).nameWithoutExtension)
        assertTrue(requests.any { it.startsWith("https://civitai.red/api/") && "cursor=1021" in it })
        assertFalse(requests.any { it.endsWith("/9000.png") })
    }

    @Test fun bothSortsUseTheSelectedHostAndPersist() {
        for (feed in ClickerBackgroundSource.feeds) {
            slideshow.source = feed
            slideshow = CivitaiSlideshow(context, client)
            assertEquals(feed, slideshow.source)
            assertEquals(feed.weekly, slideshow.weeklySort)
            val site = requireNotNull(feed.site)
            display(feed, 0)
            val uri = requests.last { it.startsWith("https://${site.host}/api/") }
            val query = android.net.Uri.parse(uri)
            assertEquals(if (feed.weekly) "Most Reactions" else "Newest", query.getQueryParameter("sort"))
            assertEquals(if (feed.weekly) "Week" else null, query.getQueryParameter("period"))
            assertEquals(if (site == CivitaiSite.RED) "28" else "1", query.getQueryParameter("browsingLevel"))
        }
        slideshow.source = ClickerBackgroundSource.FOLDER
        assertTrue(CivitaiSlideshow(context, client).weeklySort)
    }

    @Test fun widgetArrowsMoveBackwardAndForward() {
        val themed = android.view.ContextThemeWrapper(context, com.Atom2Universe.app.R.style.Theme_A2U)
        val view = android.view.LayoutInflater.from(themed).inflate(com.Atom2Universe.app.R.layout.view_civitai_widget, null)
        val steps = mutableListOf<Int>()
        val widget = CivitaiWidget(view, { steps.add(it) }, {}, {}, {})
        widget.render(true, "Civitai", CivitaiSlideshow.Position(2, 20), null, true, false, 35)
        assertEquals(.65f, view.alpha, .001f)
        assertEquals("2/20", view.findViewById<android.widget.TextView>(com.Atom2Universe.app.R.id.civitai_count).text.toString())
        assertFalse(view.findViewById<android.view.View>(com.Atom2Universe.app.R.id.civitai_reject).isEnabled)
        assertTrue(view.findViewById<android.view.View>(com.Atom2Universe.app.R.id.civitai_accept).isEnabled)
        view.findViewById<android.view.View>(com.Atom2Universe.app.R.id.civitai_back).performClick()
        view.findViewById<android.view.View>(com.Atom2Universe.app.R.id.civitai_next).performClick()
        assertEquals(listOf(-1, 1), steps)
    }

    @Test fun compactSettingsKeepSourceAndSortIndependentOnSmallScreens() {
        val themed = android.view.ContextThemeWrapper(context, com.Atom2Universe.app.R.style.Theme_A2U)
        val view = android.view.LayoutInflater.from(themed).inflate(com.Atom2Universe.app.R.layout.dialog_crypto_settings, null)
        val sources = view.findViewById<com.google.android.material.chip.ChipGroup>(com.Atom2Universe.app.R.id.settings_image_source)
        val sorts = view.findViewById<com.google.android.material.chip.ChipGroup>(com.Atom2Universe.app.R.id.settings_civitai_sort)
        sorts.visibility = android.view.View.VISIBLE
        sources.check(com.Atom2Universe.app.R.id.source_red)
        sorts.check(com.Atom2Universe.app.R.id.source_week)
        val width = (320 * context.resources.displayMetrics.density).toInt()
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), 0)
        view.layout(0, 0, width, view.measuredHeight)
        assertEquals(com.Atom2Universe.app.R.id.source_red, sources.checkedChipId)
        assertEquals(com.Atom2Universe.app.R.id.source_week, sorts.checkedChipId)
        assertTrue(sources.width in 1..width)
        assertTrue(sorts.width in 1..width)
    }

    @Test fun hiddenImageStaysHiddenAcrossFeedsAndRestart() {
        val first = display(recent, 0)
        assertEquals("1", first.nameWithoutExtension)
        assertTrue("Trash at ${slideshow.trashDirectory()}, cache exists: ${first.isFile}", runBlocking { slideshow.hide(first) })
        assertFalse(first.exists())
        slideshow = CivitaiSlideshow(context, client)
        assertEquals("2", display(week, 0).nameWithoutExtension)
        assertTrue(requests.any { "period=Week" in it && "Most" in it })
        assertEquals("2", display(recent, 1).nameWithoutExtension)
        assertEquals(21, imageRequests())
        assertNotEquals("1", display(recent, -1).nameWithoutExtension)
    }

    @Test fun dailyLimitIsSharedAndReopeningResumesAPartialFirstBatch() = runBlocking {
        prefs.edit().putLong("day", LocalDate.now().toEpochDay()).putInt("downloads", 39).commit()
        val first = display(recent, 0)
        assertEquals(CivitaiSlideshow.Position(1, 1), slideshow.position(recent, first))
        assertEquals(first, display(week, 0))
        assertEquals(1, imageRequests())
        prefs.edit().putLong("day", LocalDate.now().minusDays(1).toEpochDay()).commit()
        slideshow = CivitaiSlideshow(context, client)
        assertEquals(first, display(recent, 0))
        assertEquals(CivitaiSlideshow.Position(1, 20), slideshow.position(recent, first))
        assertEquals(20, imageRequests())
    }

    @Test fun manualBatchAddsTwentyRealImagesBeyondTheAutomaticLimit() = runBlocking {
        val first = display(recent, 0)
        prefs.edit().putLong("day", LocalDate.now().toEpochDay()).putInt("downloads", 40).commit()
        val counts = mutableListOf<Int>()
        assertEquals(20, slideshow.addBatch(recent) { counts += it })
        assertEquals((1..20).toList(), counts)
        assertEquals(40, slideshow.position(recent, first).total)
        slideshow = CivitaiSlideshow(context, client)
        for (id in 2..40) assertEquals(id.toString(), display(recent, 1).nameWithoutExtension)
        assertEquals(first, display(recent, 1))
        assertEquals(40, imageRequests())
    }

    @Test @Config(sdk = [28]) fun savedFilesStayInTodaysLoopAndRejectionsExpireAfterTwoMonths() = runBlocking {
        var day = LocalDate.of(2026, 1, 31)
        slideshow = CivitaiSlideshow(context, client) { day }
        val kept = display(recent, 0)
        assertTrue(slideshow.save(kept))
        val rejected = display(recent, 1)
        slideshow.hide(rejected)
        assertTrue(slideshow.allowed(kept))
        assertFalse(slideshow.allowed(rejected))
        assertEquals(kept, display(recent, 1, 20L))
        day = day.plusDays(1)
        slideshow = CivitaiSlideshow(context, client) { day }
        assertFalse(slideshow.allowed(kept))
        assertNotEquals(kept, display(week, 0))
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        assertTrue(File(pictures, "Atom2Universe/2026-11-12-Civitai-1.webp").isFile)
        day = LocalDate.of(2026, 3, 30)
        assertFalse(slideshow.allowed(rejected))
        day = day.plusDays(1)
        assertTrue(slideshow.allowed(rejected))
        assertFalse(slideshow.allowed(kept))
    }

    @Test fun exportingToSelectedDocumentTreeUsesWebpAndUpdatesTheLocalFolder() = runBlocking {
        val root = File(context.cacheDir, "external-card").apply { mkdirs() }
        val authority = "test.civitai.documents"
        val provider = object : DocumentsProvider() {
            override fun onCreate() = true
            override fun queryRoots(projection: Array<out String>?) = MatrixCursor(emptyArray())
            override fun queryDocument(documentId: String, projection: Array<out String>?) = MatrixCursor(emptyArray())
            override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
                MatrixCursor(emptyArray())
            override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")
            override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
                assertEquals("library", parentDocumentId)
                assertEquals("image/webp", mimeType)
                return "$parentDocumentId/$displayName"
            }
            override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
                ParcelFileDescriptor.open(File(root, documentId.substringAfterLast('/')), ParcelFileDescriptor.parseMode(mode))
        }
        val info = ProviderInfo().apply {
            this.authority = authority
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        }
        ContentProviderController.of(provider).create(info)
        val folder = DocumentsContract.buildTreeDocumentUri(authority, "library")
        MainClickerPreferences.setFolderUri(context, folder)
        slideshow.exportFolder = folder
        slideshow = CivitaiSlideshow(context, client)
        val file = display(recent, 0)
        assertTrue(slideshow.save(file))
        assertTrue(slideshow.folderNeedsRefresh(folder))
        assertArrayEquals(file.readBytes(), File(root, "2026-11-12-Civitai-1.webp").readBytes())
        assertTrue(slideshow.unvalidate(file))
        assertTrue(slideshow.save(file))
        assertFalse(slideshow.hide(file))
        assertTrue(slideshow.unvalidate(file))
        assertTrue(slideshow.hide(file))
        assertTrue(File(root, "2026-11-12-Civitai-1.webp").length() > 0)
        assertEquals(1, root.listFiles()!!.size)
    }

    @Test @Config(sdk = [28]) fun savingCopiesTheCachedImageWithoutAnotherRequest() {
        val file = display(recent, 0)
        val name = "2026-11-12-Civitai-1.webp"
        assertEquals("webp", file.extension)
        assertEquals("WEBP", file.readBytes().copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals("Northern lights", slideshow.title(file))
        assertTrue(runBlocking { slideshow.save(file) })
        assertTrue(runBlocking { slideshow.save(file) })
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        assertArrayEquals(file.readBytes(), File(pictures, "Atom2Universe/$name").readBytes())
        assertEquals(20, imageRequests())
    }

    @Test @Config(sdk = [28]) fun uncheckingOrRejectingNeverRemovesTheExport() = runBlocking {
        val cached = display(recent, 0)
        val bytes = cached.readBytes()
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val export = File(pictures, "Atom2Universe/2026-11-12-Civitai-1.webp")
        assertTrue(slideshow.save(cached))
        assertFalse(slideshow.hide(export))
        assertFalse(slideshow.hide(cached))
        assertTrue(slideshow.unvalidate(cached))
        assertArrayEquals(bytes, export.readBytes())
        assertTrue(slideshow.save(cached))
        assertTrue(slideshow.unvalidate(cached))
        slideshow = CivitaiSlideshow(context, client)
        assertTrue("Trash at ${slideshow.trashDirectory()}", slideshow.hide(cached))
        assertFalse(cached.exists())
        assertArrayEquals(bytes, export.readBytes())
        assertArrayEquals(bytes, slideshow.trashDirectory().listFiles()!!
            .single { it.name.startsWith("2026-11-12-Civitai-1-trash-") }.readBytes())
        assertEquals(19, slideshow.position(recent, null).total)
        assertEquals(20, imageRequests())
    }

    @Test @Config(sdk = [28]) fun uncheckingRestartsTheFull24HoursAndExpiryKeepsTheCopy() = runBlocking {
        var now = System.currentTimeMillis()
        slideshow = CivitaiSlideshow(context, client, nowMillis = { now })
        val cached = display(recent, 0)
        val bytes = cached.readBytes()
        assertTrue(slideshow.save(cached))
        now += 23 * 3_600_000L
        assertTrue(slideshow.unvalidate(cached))
        now += 24 * 3_600_000L - 1
        slideshow.cleanExpired()
        assertEquals(CivitaiSlideshow.Position(1, 1), slideshow.position(recent, cached))
        now++
        slideshow.cleanExpired()
        assertFalse(cached.exists())
        assertEquals(0, slideshow.position(recent, null).total)
        val export = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Atom2Universe/2026-11-12-Civitai-1.webp")
        assertArrayEquals(bytes, export.readBytes())
        assertArrayEquals(bytes, slideshow.trashDirectory().listFiles()!!
            .single { it.name.startsWith("2026-11-12-Civitai-1-trash-") }.readBytes())
    }
}
