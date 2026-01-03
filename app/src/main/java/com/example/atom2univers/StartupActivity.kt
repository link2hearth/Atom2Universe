package com.example.atom2univers

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class StartupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_startup)

        findViewById<android.view.View>(R.id.start_button).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
