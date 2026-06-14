package com.qrtile.scan

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Lightweight dialog shown when the decoded content is plain text rather than
 * something openable. Offers copy-to-clipboard.
 */
class ResultActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()

        val pad = (resources.displayMetrics.density * 20).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val label = TextView(this).apply {
            text = getString(R.string.decoded_content)
            textSize = 13f
            alpha = 0.6f
        }
        val value = TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextIsSelectable(true)
            setPadding(0, pad / 2, 0, pad)
        }
        val copy = Button(this).apply {
            text = getString(R.string.copy)
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("QR", text))
                Toast.makeText(this@ResultActivity, R.string.copied, Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        root.addView(label)
        root.addView(value)
        root.addView(copy)
        setContentView(root)
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
