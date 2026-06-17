package com.sweep

import android.app.Activity
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * "השיתוף שלי" — יעד שיתוף מותאם אישית.
 * מארח את apps/MyShare.html (assets/myshare.html) ב-WebView, והצד הילידי:
 *   - נרשם כיעד שיתוף (ACTION_SEND ב-manifest)
 *   - קורא אנשי קשר מהמאגר (ContentResolver)
 *   - מפעיל את אינטנט השיתוף האמיתי לפי מזהה אפליקציה (shareTo)
 */
class ShareActivity : Activity() {

    private lateinit var web: WebView
    private var sharedText: String = ""
    private val REQ_CONTACTS = 101

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        }
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "AndroidShare")
        setContentView(web)
        web.loadUrl("file:///android_asset/myshare.html")

        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CONTACTS) web.reload()
    }

    /** ספרות בלבד; מספר ישראלי שמתחיל ב-0 מקבל קידומת 972 (ל-wa.me). */
    private fun digits(p: String): String {
        var d = p.filter { it.isDigit() }
        if (d.startsWith("0")) d = "972" + d.substring(1)
        return d
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun readContacts(): String {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return "[]"
        val arr = JSONArray()
        val seen = HashSet<String>()
        val cur = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cur?.use {
            val iName = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val iNum = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = if (iName >= 0) it.getString(iName) ?: "" else ""
                val num = if (iNum >= 0) it.getString(iNum) ?: "" else ""
                if (name.isBlank()) continue
                if (!seen.add(name)) continue
                arr.put(JSONObject().put("name", name).put("phone", num))
            }
        }
        return arr.toString()
    }

    private fun openUri(uri: String) =
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

    /** ACTION_SEND לחבילה ספציפית; אם לא מותקנת — בורר מערכת. */
    private fun sendToPackage(pkg: String, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); setPackage(pkg)
        }
        if (i.resolveActivity(packageManager) != null) startActivity(i) else systemChooser(text)
    }

    private fun systemChooser(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "שיתוף"))
    }

    inner class Bridge {
        @JavascriptInterface fun getSharedText(): String = sharedText
        @JavascriptInterface fun getContacts(): String = readContacts()

        /** appId: whatsapp | mail | sms | telegram | messenger | instagram | copy | more */
        @JavascriptInterface
        fun shareTo(appId: String, contactJson: String, text: String) {
            val c = try { JSONObject(contactJson) } catch (e: Exception) { JSONObject() }
            val phone = c.optString("phone", "")
            val email = c.optString("email", "")
            runOnUiThread {
                try {
                    when (appId) {
                        "whatsapp" -> openUri("https://wa.me/${digits(phone)}?text=${enc(text)}")
                        "telegram" -> openUri("https://t.me/share/url?url=${enc(text)}")
                        "sms" -> {
                            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                            i.putExtra("sms_body", text); startActivity(i)
                        }
                        "mail" -> {
                            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                            i.putExtra(Intent.EXTRA_TEXT, text); startActivity(i)
                        }
                        "call" -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        "messenger" -> sendToPackage("com.facebook.orca", text)
                        "instagram" -> sendToPackage("com.instagram.android", text)
                        "signal" -> sendToPackage("org.thoughtcrime.securesms", text)
                        "discord" -> sendToPackage("com.discord", text)
                        "facebook" -> sendToPackage("com.facebook.katana", text)
                        "twitter" -> sendToPackage("com.twitter.android", text)
                        "linkedin" -> sendToPackage("com.linkedin.android", text)
                        "savecontact" -> {
                            val name = c.optString("name", "")
                            val i = Intent(Intent.ACTION_INSERT).apply {
                                type = ContactsContract.Contacts.CONTENT_TYPE
                                if (name.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.NAME, name)
                                if (phone.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                                if (email.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                            }
                            startActivity(i)
                        }
                        "copy" -> {
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("שיתוף", text))
                            Toast.makeText(this@ShareActivity, "הקישור הועתק", Toast.LENGTH_SHORT).show()
                        }
                        else -> systemChooser(text)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ShareActivity, "לא נמצאה אפליקציה מתאימה", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }

        @JavascriptInterface fun close() { runOnUiThread { finish() } }
    }
}
