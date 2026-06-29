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
    private val REQ_WRITE = 301
    private val REQ_AUDIO = 401
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingAudioRequest: android.webkit.PermissionRequest? = null
    private var nativeRec: android.media.MediaRecorder? = null
    private var nativeRecFile: java.io.File? = null
    private val recLock = Any()

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
            // מתן גישת מיקרופון ל-WebView (לתמלול הקלט הקולי)
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request ?: return
                runOnUiThread {
                    val wants = request.resources.any { it == android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    if (!wants) { try { request.deny() } catch (e: Exception) {}; return@runOnUiThread }
                    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        // ההרשאה כבר קיימת — לאשר מיד את בקשת ה-WebView
                        try { request.grant(request.resources) } catch (e: Exception) {}
                    } else {
                        // אין עדיין הרשאה — לשמור את הבקשה, לבקש מהמערכת, ולאשר/לדחות בתוצאה (onRequestPermissionsResult)
                        pendingAudioRequest = request
                        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
                    }
                }
            }
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

        // מבקש את כל ההרשאות יחד בהתקנה הראשונה: אנשי קשר (קריאה+כתיבה) ומיקרופון (לקלט קולי).
        val needed = ArrayList<String>()
        for (perm in listOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.RECORD_AUDIO
        )) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) needed.add(perm)
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), REQ_CONTACTS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CONTACTS) web.reload()
        if (requestCode == REQ_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val req = pendingAudioRequest
            pendingAudioRequest = null
            try {
                if (granted && req != null) req.grant(req.resources) else req?.deny()
            } catch (e: Exception) {}
        }
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

    /** מאתר LOOKUP_KEY של איש קשר — קודם לפי טלפון (אמין), אחרת לפי שם תצוגה. */
    private fun findLookupKey(name: String, phone: String): String? {
        if (phone.isNotBlank()) {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone)
            )
            contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null
            )?.use { if (it.moveToFirst()) return it.getString(0) }
        }
        if (name.isNotBlank()) {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                ContactsContract.Contacts.DISPLAY_NAME + "=?", arrayOf(name), null
            )?.use { if (it.moveToFirst()) return it.getString(0) }
        }
        return null
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

        /** מבקש הרשאת קריאת אנשי קשר; בעת אישור — טעינה מחדש (onRequestPermissionsResult). */
        @JavascriptInterface
        fun requestContacts() {
            runOnUiThread {
                if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
                } else {
                    web.reload()
                }
            }
        }

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

        /** מחיקה אמיתית של איש קשר מהמכשיר (מסונכרן ל-Google). מאתר לפי טלפון, אחרת לפי שם. */
        @JavascriptInterface
        fun deleteContact(name: String, phone: String) {
            runOnUiThread {
                try {
                    if (checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.WRITE_CONTACTS), REQ_WRITE)
                        Toast.makeText(this@ShareActivity, "אשר/י הרשאת עריכת אנשי קשר ונסה/י שוב", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    val lookupKey = findLookupKey(name, phone) ?: return@runOnUiThread
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
                    contentResolver.delete(uri, null, null)
                } catch (e: Exception) {}
            }
        }

        /**
         * הקלטה נייטיב של המיקרופון (עוקף את לכידת האודיו של ה-WebView שאינה אמינה).
         * מקליט ל-AAC/MPEG_4 בקובץ זמני. מחזיר true אם ההקלטה התחילה.
         */
        @JavascriptInterface
        fun startRecording(): Boolean {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_AUDIO) }
                return false
            }
            return try {
                synchronized(recLock) {
                    stopAndReleaseRec()
                    val f = java.io.File(cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
                    val r = if (android.os.Build.VERSION.SDK_INT >= 31)
                                android.media.MediaRecorder(this@ShareActivity)
                            else
                                @Suppress("DEPRECATION") android.media.MediaRecorder()
                    r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                    r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                    r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                    r.setAudioEncodingBitRate(128000)
                    r.setAudioSamplingRate(44100)
                    r.setOutputFile(f.absolutePath)
                    r.prepare()
                    r.start()
                    nativeRec = r
                    nativeRecFile = f
                }
                true
            } catch (e: Exception) {
                synchronized(recLock) { stopAndReleaseRec() }
                false
            }
        }

        /** עוצר את ההקלטה ומחזיר את האודיו כ-Base64 (ריק אם נכשל/קצר מדי). */
        @JavascriptInterface
        fun stopRecording(): String {
            return try {
                synchronized(recLock) {
                    val r = nativeRec ?: return ""
                    try { r.stop() } catch (e: Exception) {}
                    try { r.release() } catch (e: Exception) {}
                    nativeRec = null
                    val f = nativeRecFile
                    nativeRecFile = null
                    if (f == null || !f.exists() || f.length() <= 0L) { try { f?.delete() } catch (e: Exception) {}; return "" }
                    val bytes = f.readBytes()
                    try { f.delete() } catch (e: Exception) {}
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
            } catch (e: Exception) { "" }
        }

        /** מבטל הקלטה פעילה ומשחרר את המיקרופון. */
        @JavascriptInterface
        fun cancelRecording() { synchronized(recLock) { stopAndReleaseRec() } }

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

    /** עוצר ומשחרר את מקליט המיקרופון הנייטיב (אם פעיל) ומוחק את הקובץ הזמני. */
    private fun stopAndReleaseRec() {
        try {
            nativeRec?.let {
                try { it.stop() } catch (e: Exception) {}
                try { it.release() } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        nativeRec = null
        try { nativeRecFile?.delete() } catch (e: Exception) {}
        nativeRecFile = null
    }

    override fun onDestroy() {
        super.onDestroy()
        synchronized(recLock) { stopAndReleaseRec() }
    }
}
