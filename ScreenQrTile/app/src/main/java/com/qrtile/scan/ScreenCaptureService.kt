package com.qrtile.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Foreground service that owns the MediaProjection session, grabs a single
 * frame of the screen, decodes a QR code from it, acts on the result, then
 * tears everything down. Nothing stays resident after a scan.
 */
class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                it.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else
                @Suppress("DEPRECATION") it.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultData == null) {
            stopEverything()
            return START_NOT_STICKY
        }

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, resultData).also {
            it.registerCallback(projectionCallback, main)
        }

        // Let the transparent CaptureActivity finish animating out before we grab
        // the frame, otherwise it can occlude the QR underneath.
        main.postDelayed({ captureFrame() }, FRAME_DELAY_MS)
        return START_NOT_STICKY
    }

    private fun captureFrame() {
        val metrics = screenMetrics()
        val width = metrics.first
        val height = metrics.second
        val density = metrics.third

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = projection?.createVirtualDisplay(
            "qr-capture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, main
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val bitmap = try {
                imageToBitmap(image, width, height)
            } finally {
                image.close()
            }
            // We only need one frame.
            reader.setOnImageAvailableListener(null, null)

            val text = QrDecoder.decode(bitmap)
            bitmap.recycle()
            main.post { handleResult(text) }
        }, main)
    }

    private fun handleResult(text: String?) {
        if (text.isNullOrBlank()) {
            Toast.makeText(this, R.string.no_qr_found, Toast.LENGTH_LONG).show()
        } else {
            // Never open automatically — always show the content and let the
            // user choose to open, copy or share it.
            showResultScreen(text)
        }
        stopEverything()
    }

    private fun showResultScreen(text: String) {
        val i = Intent(this, ResultActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(ResultActivity.EXTRA_TEXT, text)
        }
        startActivity(i)
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(buffer)
        if (padded.width == width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }

    private fun screenMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), dm.densityDpi)
        } else {
            val legacy = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(legacy)
            Triple(legacy.widthPixels, legacy.heightPixels, legacy.densityDpi)
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopEverything() }
    }

    private fun stopEverything() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        projection?.unregisterCallback(projectionCallback)
        projection?.stop(); projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val channelId = "qr_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId, getString(R.string.channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.scanning))
            .setSmallIcon(R.drawable.ic_tile_scan)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val NOTIF_ID = 42
        private const val FRAME_DELAY_MS = 350L
    }
}
