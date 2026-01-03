package com.example.atom2univers

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.atom2univers.pixelart.PixelArtEditorActivity
import com.example.atom2univers.radio.RadioActivity

class StartupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_startup)

        findViewById<android.view.View>(R.id.start_button).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<android.view.View>(R.id.pixel_art_button).setOnClickListener {
            startActivity(Intent(this, PixelArtEditorActivity::class.java))
        }

        findViewById<android.view.View>(R.id.radio_button).setOnClickListener {
            startActivity(Intent(this, RadioActivity::class.java))
        }
    }
}
