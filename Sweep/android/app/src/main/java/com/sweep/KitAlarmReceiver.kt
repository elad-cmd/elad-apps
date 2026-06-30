package com.sweep

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray

/**
 * בדיקה יומית של "שמירה על קשר": קורא את מצב הרשימה ש-JS שמר (SharedPreferences),
 * מחשב מי באיחור לפי יומן השיחות + SMS, ושולח התראה. נקרא ע"י AlarmManager וגם ב-BOOT.
 */
class KitAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        try {
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED) { schedule(ctx); return }
            checkAndNotify(ctx)
        } catch (e: Exception) {}
    }

    private fun digits(s: String): String = s.filter { it.isDigit() }

    /** מיפוי 9-ספרות -> תאריך מגע אחרון (שיחות + SMS, עד 800 רשומות אחרונות). */
    private fun lastContactMap(ctx: Context): HashMap<String, Long> {
        val map = HashMap<String, Long>()
        try {
            if (ctx.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                ctx.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.DATE),
                    null, null, android.provider.CallLog.Calls.DATE + " DESC"
                )?.use {
                    val iN = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                    val iD = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                    var c = 0
                    while (it.moveToNext()) {
                        if (c++ > 800) break
                        val d = digits(if (iN >= 0) it.getString(iN) ?: "" else "")
                        if (d.length < 5) continue
                        val k = d.takeLast(9); val date = if (iD >= 0) it.getLong(iD) else 0L
                        if (date > (map[k] ?: 0L)) map[k] = date
                    }
                }
            }
        } catch (e: Exception) {}
        try {
            if (ctx.checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                ctx.contentResolver.query(Uri.parse("content://sms"), arrayOf("address", "date"), null, null, "date DESC")?.use {
                    var c = 0
                    while (it.moveToNext()) {
                        if (c++ > 800) break
                        val d = digits(it.getString(0) ?: "")
                        if (d.length < 5) continue
                        val k = d.takeLast(9); val date = it.getLong(1)
                        if (date > (map[k] ?: 0L)) map[k] = date
                    }
                }
            }
        } catch (e: Exception) {}
        return map
    }

    private fun checkAndNotify(ctx: Context) {
        val sp = ctx.getSharedPreferences("kit_state", Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString("members", "[]") ?: "[]")
        if (arr.length() == 0) return
        val last = lastContactMap(ctx)
        val overdue = ArrayList<String>()
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val num = digits(o.optString("num", "")).takeLast(9)
            val days = o.optInt("days", 30)
            val name = o.optString("name", "")
            if (num.length < 5 || name.isBlank()) continue
            val lt = last[num] ?: 0L
            val due = days.toLong() * 86400000L
            val over = if (lt > 0L) (now - lt > due) else true
            if (over) overdue.add(name)
        }
        if (overdue.isEmpty()) return
        notify(ctx, overdue)
    }

    private fun notify(ctx: Context, names: List<String>) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chId = "kit_reminder"
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(chId, "שמירה על קשר", NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = "תזכורות ליצירת קשר"
                nm.createNotificationChannel(ch)
            }
            val title = if (names.size == 1) "כדאי ליצור קשר עם " + names[0]
                        else "כדאי ליצור קשר עם " + names.size + " אנשים"
            val body = names.take(6).joinToString(", ") + (if (names.size > 6) " ועוד…" else "")
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pi = PendingIntent.getActivity(ctx, 0, launch ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
            val b = if (android.os.Build.VERSION.SDK_INT >= 26)
                        android.app.Notification.Builder(ctx, chId)
                    else
                        @Suppress("DEPRECATION") android.app.Notification.Builder(ctx)
            b.setSmallIcon(ctx.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(android.app.Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
            nm.notify(7001, b.build())
        } catch (e: Exception) {}
    }

    companion object {
        /** מתזמן בדיקה יומית משוערת (לא מדויקת — לא דורש הרשאה מיוחדת). */
        fun schedule(ctx: Context) {
            try {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val i = Intent(ctx, KitAlarmReceiver::class.java)
                val pi = PendingIntent.getBroadcast(ctx, 700, i,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
                // הפעלה ראשונה בעוד ~3 שעות, ואז כל יום
                val first = System.currentTimeMillis() + 3L * 60 * 60 * 1000
                am.setInexactRepeating(AlarmManager.RTC, first, AlarmManager.INTERVAL_DAY, pi)
            } catch (e: Exception) {}
        }
    }
}
