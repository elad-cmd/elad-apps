package com.transcribe.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** Shows the bundled Hebrew guide (assets/help.html) for obtaining an API key. */
class HelpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.builtInZoomControls = false
            setBackgroundColor(0xFFFFFFFF.toInt())
            // Open external (http/https) links in the phone's browser, not inside the guide.
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val url = request.url
                    return if (url.scheme == "http" || url.scheme == "https") {
                        try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, url)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                        }
                        true
                    } else {
                        false
                    }
                }
            }
            loadUrl("file:///android_asset/help.html")
        }
        setContentView(web)
    }
}
