package com.Atom2Universe.app.quiz

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class QuizWebViewActivity : ThemedActivity() {

    companion object {
        const val EXTRA_QUESTION = "quiz_webview_question"
        const val EXTRA_ANSWER  = "quiz_webview_answer"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_webview)
        enableImmersiveMode()

        val questionText = intent.getStringExtra(EXTRA_QUESTION) ?: ""
        val answerText   = intent.getStringExtra(EXTRA_ANSWER)   ?: ""

        findViewById<TextView>(R.id.quiz_webview_question).text = questionText
        findViewById<TextView>(R.id.quiz_webview_answer).apply {
            text = answerText
            visibility = if (answerText.isNotEmpty()) View.VISIBLE else View.GONE
        }

        findViewById<ImageButton>(R.id.quiz_webview_back).setOnClickListener { finish() }

        progressBar = findViewById(R.id.quiz_webview_progress)
        webView = findViewById(R.id.quiz_webview)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }
        }

        val query = Uri.encode(questionText)
        webView.loadUrl("https://www.google.com/search?q=$query")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
