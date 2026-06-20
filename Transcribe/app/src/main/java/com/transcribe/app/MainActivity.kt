package com.transcribe.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * Single-activity native shell. The whole UI lives in assets/app.html (WebView).
 * Native side handles: API-key storage, mic recording, the transcription call,
 * shared-audio intents, and copy/share. JS talks to it through the "Android" bridge.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var pageReady = false
    private var pendingSharedUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true          // localStorage for history
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(Bridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!pageReady) {
                        pageReady = true
                        pendingSharedUri?.let { transcribeShared(it) }
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

    private fun handleIntent(intent: Intent?) {
        val uri = sharedAudioUri(intent) ?: return
        if (pageReady) transcribeShared(uri) else pendingSharedUri = uri
    }

    private fun sharedAudioUri(intent: Intent?): Uri? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM)
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

    private fun transcribeShared(uri: Uri) {
        callJs("onSharedStart")
        Thread {
            val res = try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) err(R.string.err_no_file)
                else {
                    val mime = contentResolver.getType(uri) ?: intent?.type ?: "audio/ogg"
                    OpenAiTranscriber.transcribe(Prefs.getKey(this), bytes, "audio." + extFor(mime, uri), mime)
                }
            } catch (e: Exception) { err(R.string.err_generic) }
            deliver(res, shared = true)
        }.start()
    }

    private fun transcribeFile(f: File) {
        Thread {
            val res = try {
                val bytes = f.readBytes()
                OpenAiTranscriber.transcribe(Prefs.getKey(this), bytes, "audio.m4a", "audio/m4a")
            } catch (e: Exception) { err(R.string.err_generic) }
            f.delete()
            deliver(res, shared = false)
        }.start()
    }

    private fun err(res: Int) = OpenAiTranscriber.Result.Err(res)

    private fun deliver(res: OpenAiTranscriber.Result, shared: Boolean) {
        val fn = if (shared) "onShared" else "onResult"
        when (res) {
            is OpenAiTranscriber.Result.Ok -> callJs(fn, res.text, "")
            is OpenAiTranscriber.Result.Err -> callJs(fn, "", getString(res.errorRes))
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
            recorder?.stop()
            recorder?.release()
            recorder = null
            recFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            recFile?.delete()
            null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            callJsRaw("onMicPermission", if (granted) "true" else "false")
        }
    }

    // ---- bridge exposed to JS ----

    inner class Bridge {
        @JavascriptInterface fun hasKey(): Boolean = Prefs.hasKey(this@MainActivity)
        @JavascriptInterface fun getKey(): String = Prefs.getKey(this@MainActivity)
        @JavascriptInterface fun setKey(value: String) { Prefs.setKey(this@MainActivity, value) }

        /** "ok" | "need_permission" | "error" */
        @JavascriptInterface fun startRecording(): String {
            val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC) }
                return "need_permission"
            }
            return if (beginRecorder()) "ok" else "error"
        }

        @JavascriptInterface fun cancelRecording() {
            stopRecorder()?.delete()
        }

        /** Stops recording and transcribes; result via TX.onResult(text, err). */
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
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        @JavascriptInterface fun openUrl(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        private const val REQ_MIC = 101
    }
}
