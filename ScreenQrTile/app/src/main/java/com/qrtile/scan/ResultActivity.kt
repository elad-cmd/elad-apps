package com.qrtile.scan

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Shown after every successful decode. Displays the decoded content and lets
 * the user decide what to do with it — nothing opens automatically.
 * Offers: open (only when the content is a link/openable), copy, and share.
 */
class ResultActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()

        val pad = (resources.displayMetrics.density * 20).toInt()
        val gap = (resources.displayMetrics.density * 8).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val label = TextView(this).apply {
            this.text = getString(R.string.decoded_content)
            textSize = 13f
            alpha = 0.6f
        }
        val value = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextIsSelectable(true)
            setPadding(0, gap, 0, pad)
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val openIntent = QrDecoder.asOpenableIntent(text)
        if (openIntent != null) {
            val openBtn = Button(this).apply {
                this.text = getString(R.string.open)
                setOnClickListener {
                    try {
                        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(openIntent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ResultActivity,
                            R.string.no_app_to_open,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    finish()
                }
            }
            buttons.addView(openBtn)
        }

        val copyBtn = Button(this).apply {
            this.text = getString(R.string.copy)
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("QR", text))
                Toast.makeText(this@ResultActivity, R.string.copied, Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        val shareBtn = Button(this).apply {
            this.text = getString(R.string.share)
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, null))
                finish()
            }
        }

        buttons.addView(copyBtn)
        buttons.addView(shareBtn)

        root.addView(label)
        root.addView(value)
        root.addView(buttons)
        setContentView(root)
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
