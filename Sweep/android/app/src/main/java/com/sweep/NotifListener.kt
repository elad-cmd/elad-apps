package com.sweep

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * מאזין התראות: תופס התראות וואטסאפ נכנסות ושומר אותן ליומן מקומי (SharedPreferences),
 * כדי להציג בפרופיל איש הקשר "מתי קיבלת ממנו הודעה". דורש הפעלה ידנית של גישת ההתראות.
 *
 * מגבלות מובנות: נתפסות רק הודעות נכנסות (לא יוצאות), רק מרגע ההפעלה והלאה, והטקסט עשוי
 * להיות חלקי/מאוחד ("3 הודעות חדשות"). שורות סיכום כאלה מסוננות.
 */
class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            val pkg = sbn.packageName ?: return
            if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
            val ex = sbn.notification?.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            if (title.isBlank() || text.isBlank()) return
            // סינון שורות סיכום ("3 messages" / "5 הודעות חדשות" / "checking for new messages")
            val low = text.lowercase()
            if (Regex("^\\d+\\s+(new\\s+)?messages?").containsMatchIn(low)) return
            if (Regex("^\\d+\\s+הודעות").containsMatchIn(text)) return
            if (low.contains("checking for new messages") || low.contains("backup")) return
            store(this, title, text, sbn.postTime)
        } catch (e: Exception) {}
    }

    companion object {
        private const val PREF = "notif_log"
        private const val KEY = "wa"
        private const val MAX = 2000

        @Synchronized
        fun store(ctx: Context, name: String, text: String, ts: Long) {
            try {
                val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                val arr = JSONArray(sp.getString(KEY, "[]") ?: "[]")
                arr.put(JSONObject().put("name", name).put("text", text).put("date", ts))
                val trimmed = if (arr.length() > MAX) {
                    val t = JSONArray()
                    for (i in (arr.length() - MAX) until arr.length()) t.put(arr.get(i))
                    t
                } else arr
                sp.edit().putString(KEY, trimmed.toString()).apply()
            } catch (e: Exception) {}
        }

        /** מחזיר את ההתראות שתואמות לשם איש הקשר (JSON). */
        fun read(ctx: Context, name: String): String {
            return try {
                if (name.isBlank()) return "[]"
                val want = name.trim()
                val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                val arr = JSONArray(sp.getString(KEY, "[]") ?: "[]")
                val out = JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val nm = o.optString("name", "").trim()
                    if (nm.isNotEmpty() && (nm == want || nm.contains(want) || want.contains(nm))) out.put(o)
                }
                out.toString()
            } catch (e: Exception) { "[]" }
        }
    }
}
