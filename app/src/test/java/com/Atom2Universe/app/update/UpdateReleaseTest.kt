package com.Atom2Universe.app.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    private fun release(tag: String = "v0.2.2", vararg names: String = arrayOf("Atom2Universe.apk")) = JSONObject()
        .put("tag_name", tag).put("body", "Release notes").put("draft", false).put("prerelease", false)
        .put("assets", JSONArray(names.map { name -> JSONObject().put("name", name)
            .put("state", "uploaded").put("size", 1234)
            .put("browser_download_url", "https://github.com/link2hearth/Atom2Universe/releases/download/$tag/$name") }))

    @Test fun comparesNumericVersionsRatherThanText() {
        assertTrue(UpdateRelease.isNewer("v0.10.0", "0.9.9"))
        assertTrue(UpdateRelease.isNewer("1.0", "0.99.99"))
        assertFalse(UpdateRelease.isNewer("0.2.1.0", "0.2.1"))
        assertFalse(UpdateRelease.isNewer("0.2.0", "0.2.1"))
        assertFalse(UpdateRelease.isNewer("v0.3.0-beta", "0.2.1"))
        assertFalse(UpdateRelease.isNewer("999999999999999999999.0", "0.2.1"))
    }

    @Test fun acceptsNewStableReleaseWithApk() {
        val next = UpdateRelease.fromGitHub(release(), "0.2.1")!!
        assertEquals("v0.2.2", next.tag)
        assertEquals(1234L, next.size)
    }

    @Test fun ignoresOldDraftAndPrerelease() {
        assertNull(UpdateRelease.fromGitHub(release("v0.2.1"), "0.2.1"))
        assertNull(UpdateRelease.fromGitHub(release().put("draft", true), "0.2.1"))
        assertNull(UpdateRelease.fromGitHub(release().put("prerelease", true), "0.2.1"))
    }

    @Test fun ignoresDebugAndOtherArchitectures() {
        val next = UpdateRelease.fromGitHub(release("v0.2.2", "app-debug.apk", "app-x86.apk", "app-arm64.apk"), "0.2.1")!!
        assertTrue(next.url.endsWith("app-arm64.apk"))
        assertNull(UpdateRelease.fromGitHub(release("v0.2.2", "app-debug.apk"), "0.2.1"))
    }

    @Test fun refusesMissingOrAmbiguousApks() {
        assertNull(UpdateRelease.fromGitHub(release("v0.2.2", "source.zip"), "0.2.1"))
        assertNull(UpdateRelease.fromGitHub(release("v0.2.2", "first.apk", "second.apk"), "0.2.1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesDownloadFromAnotherRepository() {
        val json = release()
        json.getJSONArray("assets").getJSONObject(0)
            .put("browser_download_url", "https://github.com/other/project/releases/download/v1/app.apk")
        UpdateRelease.fromGitHub(json, "0.2.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesInsecureDownload() {
        val json = release()
        val asset = json.getJSONArray("assets").getJSONObject(0)
        asset.put("browser_download_url", asset.getString("browser_download_url").replace("https:", "http:"))
        UpdateRelease.fromGitHub(json, "0.2.1")
    }
}
