package com.Atom2Universe.app.update

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], application = Application::class)
class AppUpdateCleanupTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val apk get() = File(context.filesDir, "updates/update.apk")
    private val prefs get() = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun info(name: String, code: Int) = PackageInfo().apply {
        packageName = name
        versionCode = code
        if (Build.VERSION.SDK_INT >= 28) setLongVersionCode(code.toLong())
    }

    @Before fun prepareInstalledApp() {
        shadowOf(context.packageManager).installPackage(info(context.packageName, 10))
        apk.parentFile!!.mkdirs()
        apk.writeText("Downloaded APK fixture")
        prefs.edit().putString("apk_url", "cached-download").commit()
    }

    private fun downloaded(name: String = context.packageName, code: Int) {
        shadowOf(context.packageManager).setPackageArchiveInfo(apk.absolutePath, info(name, code))
    }

    @Test fun removesApkAfterSuccessfulInstallation() = runBlocking {
        downloaded(code = 10)
        AppUpdateRepository(context).cleanupInstalledApk()
        assertFalse(apk.exists())
        assertFalse(prefs.contains("apk_url"))
    }

    @Test fun alsoRemovesApkSupersededByAnotherUpdate() = runBlocking {
        downloaded(code = 9)
        AppUpdateRepository(context).cleanupInstalledApk()
        assertFalse(apk.exists())
    }

    @Test fun keepsApkAndCacheMetadataWhenInstallationWasCancelled() = runBlocking {
        downloaded(code = 11)
        AppUpdateRepository(context).cleanupInstalledApk()
        assertEquals("Downloaded APK fixture", apk.readText())
        assertEquals("cached-download", prefs.getString("apk_url", null))
    }

    @Test fun doesNotAssumeAnotherPackageWasInstalled() = runBlocking {
        downloaded(name = "other.app", code = 9)
        AppUpdateRepository(context).cleanupInstalledApk()
        assertTrue(apk.isFile)
        assertTrue(prefs.contains("apk_url"))
    }

    @Test fun repeatedCleanupAfterDeletionIsHarmless() = runBlocking {
        downloaded(code = 10)
        val repository = AppUpdateRepository(context)
        repository.cleanupInstalledApk()
        repository.cleanupInstalledApk()
        assertFalse(apk.exists())
    }
}
