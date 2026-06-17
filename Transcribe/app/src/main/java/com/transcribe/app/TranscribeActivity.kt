package com.transcribe.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Share target. Receives a shared audio file, sends it to OpenAI for Hebrew
 * transcription, and shows the result with copy / share actions.
 */
class TranscribeActivity : Activity() {

    private val accent = 0xFF5A40D8.toInt()
    private val ink = 0xFF1A1830.toInt()
    private lateinit var container: FrameLayout
    private var density = 1f

    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        density = resources.displayMetrics.density
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        container = FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(container)

        val uri = extractAudioUri(intent)
        when {
            !Prefs.hasKey(this) -> showError(getString(R.string.err_no_key), offerSettings = true)
            uri == null -> showError(getString(R.string.err_no_file))
            else -> startTranscription(uri)
        }
    }

    private fun extractAudioUri(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun startTranscription(uri: Uri) {
        showProgress()
        Thread {
            val result = try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) OpenAiTranscriber.Result.Err(R.string.err_no_file)
                else {
                    val mime = resolveMime(uri)
                    val name = "audio." + extensionFor(mime, uri)
                    OpenAiTranscriber.transcribe(Prefs.getKey(this), bytes, name, mime)
                }
            } catch (e: Exception) {
                OpenAiTranscriber.Result.Err(R.string.err_generic)
            }
            runOnUiThread {
                when (result) {
                    is OpenAiTranscriber.Result.Ok -> showResult(result.text)
                    is OpenAiTranscriber.Result.Err -> showError(getString(result.errorRes))
                }
            }
        }.start()
    }

    private fun resolveMime(uri: Uri): String {
        return contentResolver.getType(uri) ?: intent?.type ?: "audio/ogg"
    }

    /** Maps the MIME / file name to an extension OpenAI accepts. */
    private fun extensionFor(mime: String, uri: Uri): String {
        val m = mime.lowercase()
        val accepted = setOf("flac", "m4a", "mp3", "mp4", "mpeg", "mpga", "oga", "ogg", "wav", "webm")
        when {
            m.contains("opus") || m.contains("ogg") || m.contains("oga") -> return "ogg"
            m.contains("m4a") || m.contains("mp4") || m.contains("aac") -> return "m4a"
            m.contains("mpeg") || m.contains("mp3") || m.contains("mpga") -> return "mp3"
            m.contains("wav") -> return "wav"
            m.contains("webm") -> return "webm"
            m.contains("flac") -> return "flac"
        }
        val fromName = displayName(uri).substringAfterLast('.', "").lowercase()
        return if (fromName in accepted) fromName else "ogg"
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0).orEmpty() else ""
                } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // ---- UI ----

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(24), dp(24), dp(24), dp(18))
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(Color.WHITE)
        }
    }

    private fun swap(view: View) {
        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun showProgress() {
        val c = card().apply { gravity = Gravity.CENTER_VERTICAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        val label = TextView(this).apply {
            text = getString(R.string.transcribing)
            textSize = 16f
            setTextColor(ink)
            setPadding(dp(16), 0, dp(16), 0)
        }
        row.addView(spinner, LinearLayout.LayoutParams(dp(34), dp(34)))
        row.addView(label)
        c.addView(row)
        swap(c)
        setFinishOnTouchOutside(false)
    }

    private fun showResult(text: String) {
        val c = card()

        val header = TextView(this).apply {
            this.text = getString(R.string.result_title)
            textSize = 12.5f
            setTextColor(accent)
            gravity = Gravity.END
            setPadding(dp(2), 0, dp(2), dp(10))
        }

        val value = TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(ink)
            gravity = Gravity.END
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFFF3F2FA.toInt())
            }
        }
        val maxH = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val capped = MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, capped)
            }
        }.apply {
            addView(value)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = true
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(18), 0, 0)
        }

        val copyBtn = styleButton(filled = true) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("transcript", text))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }.apply { this.text = getString(R.string.copy) }

        val shareBtn = styleButton(filled = false) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, null))
        }.apply { this.text = getString(R.string.share) }

        val closeBtn = styleButton(filled = false) { finish() }
            .apply { this.text = getString(R.string.close) }

        buttons.addView(copyBtn)
        buttons.addView(shareBtn)
        buttons.addView(closeBtn)

        c.addView(header)
        c.addView(scroll)
        c.addView(buttons)
        swap(c)
        setFinishOnTouchOutside(true)
    }

    private fun showError(message: String, offerSettings: Boolean = false) {
        val c = card()
        val msg = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(ink)
            gravity = Gravity.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(18), 0, 0)
        }
        if (offerSettings) {
            val open = styleButton(filled = true) {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }.apply { this.text = getString(R.string.open_app) }
            buttons.addView(open)
        }
        val closeBtn = styleButton(filled = false) { finish() }
            .apply { this.text = getString(R.string.close) }
        buttons.addView(closeBtn)

        c.addView(msg)
        c.addView(buttons)
        swap(c)
        setFinishOnTouchOutside(true)
    }

    private fun styleButton(filled: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            textSize = 15f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(44)
            minimumHeight = dp(44)
            setPadding(dp(20), 0, dp(20), 0)
            stateListAnimator = null
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                if (filled) setColor(accent) else setColor(Color.TRANSPARENT)
            }
            setTextColor(if (filled) Color.WHITE else accent)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        }
}
