package com.Atom2Universe.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.util.enableImmersiveMode

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
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            navigateBackToHub()
        }
    }

    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
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
