package com.transcribe.app

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

/** Shows the bundled Hebrew guide (assets/help.html) for obtaining an API key. */
class HelpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.builtInZoomControls = false
            setBackgroundColor(0xFFFFFFFF.toInt())
            loadUrl("file:///android_asset/help.html")
        }
        setContentView(web)
    }
}
