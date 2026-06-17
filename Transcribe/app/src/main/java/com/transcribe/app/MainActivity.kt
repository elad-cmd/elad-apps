package com.transcribe.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Launcher screen. Explains the flow and lets the user store their OpenAI key once.
 * UI is built in code (the project keeps no XML layouts).
 */
class MainActivity : Activity() {

    private val accent = 0xFF5A40D8.toInt()
    private val ink = 0xFF1A1830.toInt()
    private val muted = 0xFF6B6880.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.WHITE) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }

        val title = TextView(this).apply {
            text = getString(R.string.intro_title)
            textSize = 23f
            setTextColor(ink)
            gravity = Gravity.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val body = TextView(this).apply {
            text = getString(R.string.intro_body)
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.END
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(12), 0, dp(22))
        }

        val keyLabel = TextView(this).apply {
            text = getString(R.string.key_label)
            textSize = 13f
            setTextColor(accent)
            gravity = Gravity.END
            setPadding(dp(2), 0, dp(2), dp(6))
        }

        val keyInput = EditText(this).apply {
            setText(Prefs.getKey(this@MainActivity))
            hint = getString(R.string.key_hint)
            textSize = 15f
            setTextColor(ink)
            gravity = Gravity.START
            textDirection = View.TEXT_DIRECTION_LTR
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFFF3F2FA.toInt())
                setStroke(dp(1), 0xFFDDDAF0.toInt())
            }
        }

        val keyHelp = TextView(this).apply {
            text = getString(R.string.key_help)
            textSize = 12.5f
            setTextColor(muted)
            gravity = Gravity.END
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(2), dp(8), dp(2), dp(18))
        }

        val status = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
            setPadding(0, dp(16), 0, 0)
        }
        fun refreshStatus() {
            if (Prefs.hasKey(this@MainActivity)) {
                status.text = getString(R.string.ready)
                status.setTextColor(0xFF2E7D32.toInt())
            } else {
                status.text = getString(R.string.key_missing)
                status.setTextColor(0xFFB00020.toInt())
            }
        }
        refreshStatus()

        val saveBtn = Button(this).apply {
            text = getString(R.string.save)
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            stateListAnimator = null
            minimumHeight = dp(52)
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(accent)
            }
            setOnClickListener {
                val v = keyInput.text.toString().trim()
                if (v.length < 10 || !v.startsWith("sk")) {
                    Toast.makeText(this@MainActivity, R.string.key_empty_error, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                Prefs.setKey(this@MainActivity, v)
                Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
        }

        root.addView(title)
        root.addView(body)
        root.addView(keyLabel)
        root.addView(keyInput, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(keyHelp)
        root.addView(saveBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
        root.addView(status)

        scroll.addView(root)
        setContentView(scroll)
    }
}
