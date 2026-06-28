package com.transcribe.app

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.File

/**
 * Native shell. UI lives in assets/app.html (WebView). Native handles key storage,
 * mic recording (with pause/resume), the transcription call, shared-audio intents
 * (single or multiple), and copy/share. JS uses the "Android" bridge + window.TX callbacks.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var pageReady = false
    private var pendingShared: List<Uri> = emptyList()
    private var pendingAuthPurpose: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(Bridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!pageReady) {
                        pageReady = true
                        if (pendingShared.isNotEmpty()) transcribeSharedList(pendingShared)
                    }
                }
            }
            loadUrl("file:///android_asset/app.html")
        }
        setContentView(web)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_AUTH) {
            val ok = resultCode == RESULT_OK
            val p = pendingAuthPurpose; pendingAuthPurpose = null
            if (p != null) callJs("onAuth", p, if (ok) "1" else "0")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.TX && window.TX.onBack && window.TX.onBack()", null)
    }

    private fun handleIntent(intent: Intent?) {
        val uris = sharedAudioUris(intent)
        if (uris.isEmpty()) return
        if (pageReady) transcribeSharedList(uris) else pendingShared = uris
    }

    private fun sharedAudioUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        @Suppress("DEPRECATION")
        return when (intent.action) {
            Intent.ACTION_SEND ->
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { listOf(it) } ?: emptyList()
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }
    }

    // ---- JS callbacks ----
    private fun callJs(fn: String, vararg args: String) {
        val joined = args.joinToString(",") { JSONObject.quote(it) }
        runOnUiThread { web.evaluateJavascript("window.TX && window.TX.$fn($joined)", null) }
    }
    private fun callJsRaw(fn: String, arg: String) {
        runOnUiThread { web.evaluateJavascript("window.TX && window.TX.$fn($arg)", null) }
    }

    // ---- transcription ----
    private fun transcribeSharedList(uris: List<Uri>) {
        callJsRaw("onSharedStart", uris.size.toString())
        Thread {
            for (uri in uris) {
                val res = try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) err(R.string.err_no_file)
                    else {
                        val mime = contentResolver.getType(uri) ?: "audio/ogg"
                        txAuto(bytes, "audio." + extFor(mime, uri), mime, Prefs.getLang(this))
                    }
                } catch (e: Exception) { err(R.string.err_generic) }
                when (res) {
                    is OpenAiTranscriber.Result.Ok -> callJs("onShared", res.text, "")
                    is OpenAiTranscriber.Result.Err -> callJs("onShared", "", getString(res.errorRes))
                }
            }
            callJs("onSharedDone")
        }.start()
    }

    private fun transcribeFile(f: File) {
        Thread {
            val res = try {
                txAuto(f.readBytes(), "audio.m4a", "audio/m4a", Prefs.getLang(this))
            } catch (e: Exception) { err(R.string.err_generic) }
            f.delete()
            when (res) {
                is OpenAiTranscriber.Result.Ok -> callJs("onResult", res.text, "")
                is OpenAiTranscriber.Result.Err -> callJs("onResult", "", getString(res.errorRes))
            }
        }.start()
    }

    private fun err(res: Int) = OpenAiTranscriber.Result.Err(res)

    /** Transcribe via the user's own key if set, otherwise via the server-side proxy. */
    private fun txAuto(bytes: ByteArray, name: String, mime: String?, lang: String): OpenAiTranscriber.Result {
        val key = Prefs.getKey(this)
        return when {
            key.isNotEmpty() -> OpenAiTranscriber.transcribe(key, bytes, name, mime, lang)
            OpenAiTranscriber.isProxyConfigured() -> OpenAiTranscriber.transcribeViaProxy(bytes, name, mime, lang)
            else -> OpenAiTranscriber.Result.Err(R.string.err_no_key)
        }
    }

    private fun extFor(mime: String, uri: Uri): String {
        val m = mime.lowercase()
        val ok = setOf("flac", "m4a", "mp3", "mp4", "mpeg", "mpga", "oga", "ogg", "wav", "webm")
        when {
            m.contains("opus") || m.contains("ogg") || m.contains("oga") -> return "ogg"
            m.contains("m4a") || m.contains("mp4") || m.contains("aac") -> return "m4a"
            m.contains("mpeg") || m.contains("mp3") || m.contains("mpga") -> return "mp3"
            m.contains("wav") -> return "wav"
            m.contains("webm") -> return "webm"
            m.contains("flac") -> return "flac"
        }
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" } ?: ""
        } catch (e: Exception) { "" }
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext in ok) ext else "ogg"
    }

    // ---- recording ----
    private fun beginRecorder(): Boolean {
        try {
            val f = File(cacheDir, "rec_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else legacyRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recFile = f
            return true
        } catch (e: Exception) {
            recorder = null
            return false
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyRecorder() = MediaRecorder()

    private fun stopRecorder(): File? {
        return try {
            recorder?.stop(); recorder?.release(); recorder = null; recFile
        } catch (e: Exception) {
            recorder?.release(); recorder = null; recFile?.delete(); null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            callJsRaw("onMicPermission", if (granted) "true" else "false")
        }
    }

    // ---- bridge ----
    inner class Bridge {
        @JavascriptInterface fun hasKey(): Boolean = Prefs.hasKey(this@MainActivity)
        @JavascriptInterface fun needsKey(): Boolean = Prefs.getKey(this@MainActivity).isEmpty() && !OpenAiTranscriber.isProxyConfigured()
        @JavascriptInterface fun getKey(): String = Prefs.getKey(this@MainActivity)
        @JavascriptInterface fun setKey(value: String) { Prefs.setKey(this@MainActivity, value) }
        @JavascriptInterface fun getLang(): String = Prefs.getLang(this@MainActivity)
        @JavascriptInterface fun setLang(value: String) { Prefs.setLang(this@MainActivity, value) }

        @JavascriptInterface fun requestAuth(purpose: String) {
            runOnUiThread {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!km.isDeviceSecure) { callJs("onAuth", purpose, "1"); return@runOnUiThread }
                @Suppress("DEPRECATION")
                val intent = km.createConfirmDeviceCredentialIntent("אימות זהות", "אשר/י כדי לראות או להעתיק את המפתח")
                if (intent == null) { callJs("onAuth", purpose, "1") }
                else { pendingAuthPurpose = purpose; startActivityForResult(intent, REQ_AUTH) }
            }
        }

        @JavascriptInterface fun startRecording(): String {
            val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC) }
                return "need_permission"
            }
            return if (beginRecorder()) "ok" else "error"
        }

        @JavascriptInterface fun pauseRecording(): Boolean {
            return try { recorder?.pause(); true } catch (e: Exception) { false }
        }
        @JavascriptInterface fun resumeRecording(): Boolean {
            return try { recorder?.resume(); true } catch (e: Exception) { false }
        }
        @JavascriptInterface fun cancelRecording() { stopRecorder()?.delete() }

        @JavascriptInterface fun stopAndTranscribe() {
            val f = stopRecorder()
            if (f == null) callJs("onResult", "", getString(R.string.err_generic))
            else transcribeFile(f)
        }

        @JavascriptInterface fun copy(text: String) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("transcript", text))
        }
        @JavascriptInterface fun share(text: String) {
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
            startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        @JavascriptInterface fun openUrl(url: String) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        }
        /** Async version check: fetches the server version.json off the UI thread and calls window.TX.onVersion. */
        @JavascriptInterface fun checkVersion(url: String) {
            Thread {
                val body = try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val req = okhttp3.Request.Builder().url(url).header("Cache-Control", "no-cache").build()
                    client.newCall(req).execute().use { resp -> if (resp.isSuccessful) resp.body?.string().orEmpty() else "" }
                } catch (e: Exception) { "" }
                callJs("onVersion", body)
            }.start()
        }

        @JavascriptInterface fun exitApp() { runOnUiThread { finishAffinity() } }

        /** Share text directly to a specific app package. Returns false if it could not. */
        @JavascriptInterface fun shareToApp(pkg: String, text: String): Boolean {
            return try {
                startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }); true
            } catch (e: Exception) {
                try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) {}
                false
            }
        }

        /** Open WhatsApp chat with a specific number, prefilled with the text. */
        @JavascriptInterface fun shareToWhatsApp(number: String, text: String) {
            val n = number.filter { it.isDigit() }
            val url = "https://wa.me/" + n + "?text=" + Uri.encode(text)
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        }

        /** JSON list of apps that can receive shared text, with icons (base64). */
        @JavascriptInterface fun listShareApps(): String {
            return try {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                val ris = pm.queryIntentActivities(intent, 0)
                val arr = JSONArray(); val seen = HashSet<String>()
                for (ri in ris) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName || !seen.add(pkg)) continue
                    val o = JSONObject()
                    o.put("pkg", pkg)
                    o.put("label", ri.loadLabel(pm).toString())
                    o.put("icon", drawableToB64(ri.loadIcon(pm)))
                    arr.put(o)
                }
                arr.toString()
            } catch (e: Exception) { "[]" }
        }
    }

    private fun drawableToB64(d: Drawable, size: Int = 88): String {
        return try {
            val bmp = if (d is BitmapDrawable && d.bitmap != null) {
                Bitmap.createScaledBitmap(d.bitmap, size, size, true)
            } else {
                val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(b); d.setBounds(0, 0, size, size); d.draw(c); b
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }

    companion object { private const val REQ_MIC = 101; private const val REQ_AUTH = 102 }
}
