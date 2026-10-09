package com.Atom2Universe.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.science.biology.AnatomyCatalog
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.followImmersiveMode

/**
 * About page showing app information and credits for open source libraries.
 */
class AboutActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_about)

        setupBackButton()
        displayVersion()
        setupBiologyCredits()
        findViewById<View>(R.id.about_biology_journeys).setOnClickListener {
            com.Atom2Universe.app.science.biology.AnatomyJourneyCredits.show(this)
        }
        setupBilliardCredits()
        setupFontCredits()
        findViewById<View>(R.id.about_timeline_sources).setOnClickListener {
            com.Atom2Universe.app.science.timeline.TimelineCredits.show(this)
        }
        findViewById<View>(R.id.about_parentes_sources).setOnClickListener {
            com.Atom2Universe.app.science.parentes.ParentesCredits.show(this)
        }
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            navigateBackToHub()
        }
    }

    private fun setupBilliardCredits() {
        listOf(R.id.about_billiard_source to "https://github.com/ekiefl/pooltool",
            R.id.about_billiard_publication to "https://doi.org/10.21105/joss.07301").forEach { (id,url) ->
            findViewById<View>(id).setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
                catch(_: ActivityNotFoundException) { Toast.makeText(this,R.string.bio_link_error,Toast.LENGTH_SHORT).show() }
            }
        }
        findViewById<View>(R.id.about_billiard_license).setOnClickListener {
            val license=listOf("NOTICE.txt","POOLTOOL-LICENSE.txt").joinToString("\n\n") { file ->
                assets.open("billiards/$file").bufferedReader().use { it.readText() }
            }
            val padding=(20*resources.displayMetrics.density).toInt()
            val content=TextView(this).apply { text=license; textSize=14f; setTextIsSelectable(true); setPadding(padding,padding,padding,padding) }
            val dialog=AlertDialog.Builder(this).setTitle(R.string.billiard_license)
                .setView(ScrollView(this).apply { addView(content) }).setPositiveButton(R.string.billiard_close,null).create()
            dialog.followImmersiveMode(); dialog.show()
        }
    }

    private fun setupFontCredits() {
        val hasFonts = runCatching {
            assets.list("fonts").orEmpty().any { it.endsWith(".ttf") || it.endsWith(".otf") }
        }.getOrDefault(false)
        val files = runCatching { assets.list("fonts/licenses").orEmpty().toList() }
            .getOrDefault(emptyList()).filter { it.endsWith("-LICENSE.txt") }.sorted()
        val button = findViewById<View>(R.id.about_font_licenses)
        button.visibility = if (hasFonts && files.isNotEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.about_fonts_body).setText(
            if (hasFonts) R.string.about_fonts_body else R.string.about_fonts_system_only
        )
        button.setOnClickListener {
            val names = files.map { it.removeSuffix("-LICENSE.txt") }.toTypedArray()
            val picker = AlertDialog.Builder(this)
                .setTitle(R.string.about_font_licenses)
                .setItems(names) { _, index ->
                    val license = runCatching {
                        assets.open("fonts/licenses/${files[index]}").bufferedReader().use { it.readText() }
                    }.getOrElse {
                        Toast.makeText(this, R.string.about_font_license_unavailable, Toast.LENGTH_SHORT).show()
                        return@setItems
                    }
                    val padding = (20 * resources.displayMetrics.density).toInt()
                    val content = TextView(this).apply {
                        text = license
                        textSize = 14f
                        setTextIsSelectable(true)
                        setPadding(padding, padding, padding, padding)
                        android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS)
                    }
                    val dialog = AlertDialog.Builder(this)
                        .setTitle(names[index])
                        .setView(ScrollView(this).apply { addView(content) })
                        .setPositiveButton(R.string.about_font_close, null).create()
                    dialog.followImmersiveMode()
                    dialog.show()
                }
                .setNegativeButton(R.string.about_font_close, null).create()
            picker.followImmersiveMode()
            picker.show()
        }
    }

    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }

    private fun setupBiologyCredits() {
        mapOf(
            R.id.about_biology_source to AnatomyCatalog.MODEL_SOURCE,
            R.id.about_biology_license to AnatomyCatalog.LICENSE_SOURCE,
            R.id.about_biology_reference to AnatomyCatalog.SKELETON_REFERENCE,
            R.id.about_muscle_reference to AnatomyCatalog.MUSCLE_REFERENCE,
            R.id.about_cartilage_reference to AnatomyCatalog.CARTILAGE_REFERENCE,
            R.id.about_knee_reference to AnatomyCatalog.KNEE_REFERENCE,
            R.id.about_organ_reference to AnatomyCatalog.ORGAN_REFERENCE,
            R.id.about_senses_reference to "https://openstax.org/books/anatomy-and-physiology-2e/pages/14-1-sensory-perception",
            R.id.about_nervous_reference to "https://openstax.org/books/anatomy-and-physiology-2e/pages/13-2-the-central-nervous-system",
            R.id.about_vascular_reference to "https://openstax.org/books/anatomy-and-physiology-2e/pages/20-1-structure-and-function-of-blood-vessels",
            R.id.about_skin_reference to "https://openstax.org/books/anatomy-and-physiology-2e/pages/5-1-layers-of-the-skin",
            R.id.about_female_source to "https://purl.humanatlas.io/ref-organ/united-female/v1.5",
            R.id.about_ear_source to "https://zenodo.org/records/1473724",
            R.id.about_facial_source to AnatomyCatalog.FACIAL_MODEL_SOURCE,
            R.id.about_facial_license to AnatomyCatalog.FACIAL_LICENSE_SOURCE,
        ).forEach { (id, url) ->
            findViewById<View>(id).setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.bio_link_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
        mapOf(
            R.id.about_filament_license to ("FILAMENT-LICENSE.txt" to R.string.bio_license_offline),
            R.id.about_facial_license_offline to ("Z-ANATOMY-LICENSE.txt" to R.string.bio_facial_license_offline),
            R.id.about_detail_license_offline to ("DETAIL-ATLASES-LICENSE.txt" to R.string.bio_detail_license_offline),
        ).forEach { (id, document) ->
            findViewById<View>(id).setOnClickListener { showBiologyLicense(document.first, document.second) }
        }
    }

    private fun showBiologyLicense(file: String, title: Int) {
        val license = assets.open("science/biology/$file").bufferedReader().use { it.readText() }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = TextView(this).apply {
            text = license
            textSize = 14f
            setTextIsSelectable(true)
            android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS)
            setPadding(padding, padding, padding, padding)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.bio_close, null)
            .create()
        dialog.followImmersiveMode()
        dialog.show()
    }

    private fun displayVersion() {
        val versionText = findViewById<TextView>(R.id.version_text)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            versionText.text = getString(R.string.about_version_format, versionName, versionCode)
        } catch (e: Exception) {
            versionText.text = getString(R.string.about_version_unknown)
        }
    }
}
