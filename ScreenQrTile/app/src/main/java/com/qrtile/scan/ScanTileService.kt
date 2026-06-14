package com.qrtile.scan

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * The Quick Settings tile. A tile click runs in a restricted background
 * context, so it cannot request the MediaProjection consent directly.
 * It collapses the panel and launches a transparent activity that asks
 * for the screen-capture permission.
 */
class ScanTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, CaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // startActivityAndCollapse closes the QS panel so the screenshot we
        // take captures the app underneath (e.g. WhatsApp), not the panel.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
