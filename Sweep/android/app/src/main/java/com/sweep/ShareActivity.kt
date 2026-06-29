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
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
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
    private val REQ_FILE = 201
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

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
        // מאפשר ל-<input type=file> בתוך ה-WebView לפתוח בורר קבצים (לשחזור גיבוי)
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = (try { params?.createIntent() } catch (e: Exception) { null })
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE)
                    }
                return try {
                    startActivityForResult(Intent.createChooser(intent, "בחר קובץ גיבוי"), REQ_FILE)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE) {
            val cb = filePathCallback
            filePathCallback = null
            val uris = if (resultCode == Activity.RESULT_OK)
                WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            cb?.onReceiveValue(uris)
        }
    }

    /**
     * Back של הטלפון מנותב ל-WebView: ה-JS (History API trap) שומר תמיד מצב היסטוריה,
     * כך ש-goBack() מפעיל popstate והלוגיקה הפנימית מטפלת (מסך קודם / דיאלוג יציאה).
     * יציאה אמיתית קורית רק כשה-JS קורא ל-AndroidShare.close().
     */
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
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

        /** פותח צ'אט וואטסאפ מבלי לנווט את ה-WebView ומבלי לסגור את ה-Activity (לתור שליחה מרובה). */
        @JavascriptInterface
        fun openWhatsApp(phone: String, text: String) {
            runOnUiThread {
                try {
                    openUri("https://wa.me/${digits(phone)}" + if (text.isNotEmpty()) "?text=${enc(text)}" else "")
                } catch (e: Exception) {
                    Toast.makeText(this@ShareActivity, "לא נמצאה אפליקציית וואטסאפ", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface fun close() { doExitApp() }
        /** יציאה אמיתית מהאפליקציה — נקרא מכפתור "יציאה" ב-JS. */
        @JavascriptInterface fun exitApp() { doExitApp() }
    }

    /** סוגר את ה-Activity וכל ה-task (יציאה אמיתית), עם נפילה ל-moveTaskToBack. */
    private fun doExitApp() {
        runOnUiThread {
            try {
                finishAffinity()
            } catch (e: Exception) {
                try { finish() } catch (_: Exception) {}
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask()
                }
            } catch (_: Exception) {}
            try { moveTaskToBack(true) } catch (_: Exception) {}
        }
    }
}
