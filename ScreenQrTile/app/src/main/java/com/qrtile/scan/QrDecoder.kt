package com.qrtile.scan

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Stateless QR decoding helper built on ZXing core (pure JVM, no camera).
 */
object QrDecoder {

    fun decode(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )

        // Try the normal image, then its inverted luminance (white-on-black codes).
        for (sourceVariant in listOf(source, source.invert())) {
            try {
                val binary = BinaryBitmap(HybridBinarizer(sourceVariant))
                return MultiFormatReader().apply { setHints(hints) }.decodeWithState(binary).text
            } catch (_: Exception) {
                // try next variant
            }
        }
        return null
    }

    /**
     * Returns an Intent that opens the payload directly (URL, tel, mailto,
     * geo, etc.), or null when the content is plain text that should be shown.
     */
    fun asOpenableIntent(text: String): Intent? {
        val t = text.trim()
        val uri: Uri = when {
            t.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE)) -> Uri.parse(t)
            t.startsWith("www.", ignoreCase = true) -> Uri.parse("https://$t")
            t.startsWith("mailto:", ignoreCase = true) -> Uri.parse(t)
            t.startsWith("tel:", ignoreCase = true) -> Uri.parse(t)
            t.startsWith("geo:", ignoreCase = true) -> Uri.parse(t)
            t.startsWith("sms:", ignoreCase = true) ||
                t.startsWith("smsto:", ignoreCase = true) -> Uri.parse(t.replace("smsto:", "sms:", true))
            else -> return null
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }
}
