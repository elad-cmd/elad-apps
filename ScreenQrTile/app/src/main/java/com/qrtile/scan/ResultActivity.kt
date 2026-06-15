package com.qrtile.scan

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Result card shown after every successful decode. Nothing opens automatically:
 * it shows the decoded content and lets the user choose — open (links only),
 * copy, or share. Styled programmatically (the project has no XML layouts).
 */
class ResultActivity : Activity() {

    private val accent = 0xFF0E7C7B.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val openIntent = QrDecoder.asOpenableIntent(text)

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.WHITE)
            }
        }

        val header = TextView(this).apply {
            this.text = getString(if (openIntent != null) R.string.type_link else R.string.type_text)
            textSize = 12.5f
            setTextColor(accent)
            letterSpacing = 0.02f
            setPadding(dp(2), 0, dp(2), dp(10))
        }

        val value = TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(0xFF1A2024.toInt())
            setLineSpacing(dp(2).toFloat(), 1f)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFFF1F3F5.toInt())
            }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(18), 0, 0)
        }

        fun style(btn: Button, filled: Boolean, onClick: () -> Unit): Button = btn.apply {
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
            ).apply { marginStart = dp(6) }
        }

        if (openIntent != null) {
            val openBtn = style(Button(this), true) {
                try {
                    openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(openIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            openBtn.text = getString(R.string.open)
            buttons.addView(openBtn)
        }

        val copyBtn = style(Button(this), false) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("QR", text))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            finish()
        }
        copyBtn.text = getString(R.string.copy)

        val shareBtn = style(Button(this), false) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, null))
            finish()
        }
        shareBtn.text = getString(R.string.share)

        buttons.addView(copyBtn)
        buttons.addView(shareBtn)

        root.addView(header)
        root.addView(value)
        root.addView(buttons)
        setContentView(root)
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
