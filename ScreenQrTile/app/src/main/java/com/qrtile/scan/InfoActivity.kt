package com.qrtile.scan

import android.app.Activity
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Launcher screen. Explains the tool and, on Android 13+, offers to add the
 * Quick Settings tile with one tap via requestAddTileService.
 */
class InfoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (resources.displayMetrics.density * 24).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
        }
        val body = TextView(this).apply {
            text = getString(R.string.info_body)
            textSize = 16f
            setPadding(0, pad / 2, 0, pad)
        }

        root.addView(title)
        root.addView(body)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val addTile = Button(this).apply {
                text = getString(R.string.add_tile)
                setOnClickListener { requestTile() }
            }
            root.addView(addTile)
        }

        setContentView(root)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestTile() {
        val sm = getSystemService(android.app.StatusBarManager::class.java)
        sm.requestAddTileService(
            ComponentName(this, ScanTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile_scan),
            { it.run() },
            { /* result code; ignore */ }
        )
        Toast.makeText(this, R.string.add_tile_hint, Toast.LENGTH_LONG).show()
    }
}
