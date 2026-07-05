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
    private var sharedImageUri: Uri? = null
    private val REQ_CONTACTS = 101
    private val REQ_FILE = 201
    private val REQ_WRITE = 301
    private val REQ_AUDIO = 401
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingAudioRequest: android.webkit.PermissionRequest? = null
    private var nativeRec: android.media.MediaRecorder? = null
    private var nativeRecFile: java.io.File? = null
    // התנהגות ביציאה מפוקוס: close = נסגרת לחלוטין (ברירת מחדל) | home = חוזרת למסך אנשי הקשר | last = נשארת במסך האחרון.
    private var resumeMode = "close"
    private var userLeaving = false      // true רק כשהמשתמש עזב ביוזמתו (בית/החלפת אפליקציה), לא כשאנחנו פותחים מסך אחר.
    private var expectingResult = false  // true בזמן בורר קבצים וכד' — מונע סגירה בטעות.
    private val recLock = Any()

    /** קורא טקסט/תמונה משותפים מתוך אינטנט ACTION_SEND. */
    private fun readShareIntent(i: Intent?) {
        sharedText = ""
        sharedImageUri = null
        if (i?.action == Intent.ACTION_SEND) {
            val t = i.type ?: ""
            if (t == "text/plain") {
                sharedText = i.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            } else if (t.startsWith("image/")) {
                sharedImageUri = i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                sharedText = i.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            }
        }
    }

    /** שיתוף חדש כשהאפליקציה כבר פתוחה (singleTask): לקרוא את השיתוף ולטעון מחדש למצב שיתוף. */
    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        readShareIntent(newIntent)
        if (sharedText.isNotEmpty() || sharedImageUri != null) {
            try { web.loadUrl("file:///android_asset/myshare.html") } catch (e: Exception) {}
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readShareIntent(intent)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowContentAccess = true
        web.settings.allowFileAccess = true
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
                    expectingResult = true
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
        resumeMode = getSharedPreferences("sweep_prefs", MODE_PRIVATE).getString("resume_mode", "close") ?: "close"

        // מבקש את כל ההרשאות יחד בהתקנה הראשונה: אנשי קשר (קריאה+כתיבה) ומיקרופון (לקלט קולי).
        val needed = ArrayList<String>()
        val perms = ArrayList(listOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CALENDAR
        ))
        // POST_NOTIFICATIONS לא מבוקש — ההתראות מבוטלות; בקשתו גרמה ללולאת בקשה+reload כשהמשתמש דוחה.
        for (perm in perms) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) needed.add(perm)
        }
        // בקשה חד-פעמית בלבד: מונע לולאת reload אינסופית כשהמשתמש דוחה הרשאה כלשהי (SMS/יומן שיחות וכו').
        val _permPrefs = getSharedPreferences("sweep_prefs", MODE_PRIVATE)
        if (needed.isNotEmpty() && !_permPrefs.getBoolean("perms_asked", false)) {
            _permPrefs.edit().putBoolean("perms_asked", true).apply()
            requestPermissions(needed.toTypedArray(), REQ_CONTACTS)
        }
        // התראות "שמירה על קשר" מבוטלות לבקשת המשתמש — החיווי האדום בתוך האפליקציה מספיק.
        // ההרשאה והקוד נשארים לשימוש עתידי. כדי להחזיר: בטל הערה כאן וב-saveKitState.
        // try { KitAlarmReceiver.schedule(this) } catch (e: Exception) {}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CONTACTS && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) web.reload()
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
        expectingResult = false
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
        if (web.canGoBack()) web.goBack()
        else web.evaluateJavascript("window.__swExit && window.__swExit()", null)
    }

    /** נקרא רק כשהמשתמש עוזב ביוזמתו (בית/החלפת אפליקציה), לא כשאנחנו פותחים מחייגן/שיתוף/בורר. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeaving = true
    }

    /** במצב close: אם המשתמש עזב ביוזמתו (ולא באמצע בורר קבצים) — סגירה מלאה + הסרה מרשימת האחרונות. */
    override fun onStop() {
        super.onStop()
        if (resumeMode == "close" && userLeaving && !expectingResult) {
            finishAndRemoveTask()
        }
    }

    override fun onResume() {
        super.onResume()
        userLeaving = false
        expectingResult = false
    }

    /** חזרה מרקע: איפוס למסך אנשי הקשר, אלא אם המצב "נשארת במסך האחרון". לא נקרא בהפעלה ראשונה. */
    override fun onRestart() {
        super.onRestart()
        if (resumeMode != "last") web.evaluateJavascript("window.__swHome && window.__swHome()", null)
    }

    /** ספרות בלבד; מספר ישראלי שמתחיל ב-0 מקבל קידומת 972 (ל-wa.me). */
    private fun digits(p: String): String {
        var d = p.filter { it.isDigit() }
        if (d.startsWith("0")) d = "972" + d.substring(1)
        return d
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** נרמול שם/כותרת להשוואה: אותיות קטנות, בלי פיסוק/מקפים, רווחים מנורמלים. */
    private fun normNm(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** תווית חשבון ידידותית. ל-Google מחזיר את כתובת המייל (כדי להבחין בין חשבונות). */
    private fun accountLabel(name: String?, type: String?): String {
        if (type == null || type.isBlank()) return "מכשיר"
        if (type.contains("google", true)) return if (!name.isNullOrBlank()) name else "Google"
        if (type.contains("sim", true)) return "SIM"
        if (type.contains("whatsapp", true)) return "WhatsApp"
        return if (!name.isNullOrBlank()) name else "מכשיר"
    }

    private fun readContacts(): String {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return "[]"
        // מיפוי contactId -> חשבון (לפי ה-raw contact הראשון)
        val acctMap = HashMap<Long, String>()
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts.CONTACT_ID,
                    ContactsContract.RawContacts.ACCOUNT_NAME,
                    ContactsContract.RawContacts.ACCOUNT_TYPE
                ),
                null, null, null
            )?.use {
                val iC = it.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                val iN = it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val iT = it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                while (it.moveToNext()) {
                    val cid = if (iC >= 0) it.getLong(iC) else continue
                    if (acctMap.containsKey(cid)) continue
                    val an = if (iN >= 0) it.getString(iN) else null
                    val at = if (iT >= 0) it.getString(iT) else null
                    acctMap[cid] = accountLabel(an, at)
                }
            }
        } catch (e: Exception) {}
        val arr = JSONArray()
        val seen = HashSet<String>()
        val cur = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cur?.use {
            val iName = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val iNum = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val iCid = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val iThumb = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val iPhoto = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            while (it.moveToNext()) {
                val name = if (iName >= 0) it.getString(iName) ?: "" else ""
                val num = if (iNum >= 0) it.getString(iNum) ?: "" else ""
                if (name.isBlank()) continue
                if (!seen.add(name)) continue
                val cid = if (iCid >= 0) it.getLong(iCid) else -1L
                val acct = acctMap[cid] ?: "מכשיר"
                val photo = (if (iThumb >= 0) it.getString(iThumb) else null)
                    ?: (if (iPhoto >= 0) it.getString(iPhoto) else null) ?: ""
                arr.put(JSONObject().put("name", name).put("phone", num).put("account", acct).put("photo", photo))
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

    /** מזהה שורת data לפי raw_contact ו-mimetype (לשם/מייל). */
    private fun dataRowId(rawId: Long, mime: String): Long? {
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data._ID),
            ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(rawId.toString(), mime), null
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        return null
    }

    /** מזהה שורת טלפון שתואמת למספר המקורי (השוואת ספרות), אחרת הטלפון הראשון. */
    private fun phoneRowId(rawId: Long, origPhone: String): Long? {
        val target = origPhone.filter { it.isDigit() }
        var firstId: Long? = null
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(rawId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE), null
        )?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                if (firstId == null) firstId = id
                val num = (it.getString(1) ?: "").filter { c -> c.isDigit() }
                if (target.isNotEmpty() && (num == target || num.endsWith(target) || target.endsWith(num))) return id
            }
        }
        return firstId
    }

    /** מזהה שורת אירוע מסוג יום הולדת (Event TYPE_BIRTHDAY). */
    private fun birthdayRowId(rawId: Long): Long? {
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data._ID),
            ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=? AND " +
                ContactsContract.CommonDataKinds.Event.TYPE + "=?",
            arrayOf(rawId.toString(), ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()), null
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        return null
    }

    /**
     * כותב עריכת איש קשר חזרה למאגר הטלפון. מאתר לפי השם/טלפון המקוריים.
     * מעדכן שם, טלפון, מייל, כתובת ותאריך לידה; יוצר שורה אם חסרה. מחזיר true בהצלחה.
     */
    private fun applyContactUpdate(origName: String, origPhone: String, name: String, phone: String, email: String, address: String, birthday: String): Boolean {
        val lookupKey = findLookupKey(origName, origPhone) ?: return false
        val cUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        var contactId = -1L
        contentResolver.query(cUri, arrayOf(ContactsContract.Contacts._ID), null, null, null)
            ?.use { if (it.moveToFirst()) contactId = it.getLong(0) }
        if (contactId < 0) return false
        var rawId = -1L
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            ContactsContract.RawContacts.CONTACT_ID + "=?", arrayOf(contactId.toString()), null
        )?.use { if (it.moveToFirst()) rawId = it.getLong(0) }
        if (rawId < 0) return false

        val ops = ArrayList<android.content.ContentProviderOperation>()
        val DATA = ContactsContract.Data.CONTENT_URI

        if (name.isNotBlank()) {
            val mime = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            val rowId = dataRowId(rawId, mime)
            if (rowId != null) {
                ops.add(android.content.ContentProviderOperation.newUpdate(DATA)
                    .withSelection(ContactsContract.Data._ID + "=?", arrayOf(rowId.toString()))
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, null)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, null)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, null)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, null)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, null)
                    .build())
            } else {
                ops.add(android.content.ContentProviderOperation.newInsert(DATA)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, mime)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build())
            }
        }

        if (phone.isNotBlank()) {
            val mime = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            val rowId = phoneRowId(rawId, origPhone)
            if (rowId != null) {
                ops.add(android.content.ContentProviderOperation.newUpdate(DATA)
                    .withSelection(ContactsContract.Data._ID + "=?", arrayOf(rowId.toString()))
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .build())
            } else {
                ops.add(android.content.ContentProviderOperation.newInsert(DATA)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, mime)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build())
            }
        }

        if (email.isNotBlank()) {
            val mime = ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
            val rowId = dataRowId(rawId, mime)
            if (rowId != null) {
                ops.add(android.content.ContentProviderOperation.newUpdate(DATA)
                    .withSelection(ContactsContract.Data._ID + "=?", arrayOf(rowId.toString()))
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .build())
            } else {
                ops.add(android.content.ContentProviderOperation.newInsert(DATA)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, mime)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                    .build())
            }
        }

        if (address.isNotBlank()) {
            val mime = ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
            val rowId = dataRowId(rawId, mime)
            if (rowId != null) {
                ops.add(android.content.ContentProviderOperation.newUpdate(DATA)
                    .withSelection(ContactsContract.Data._ID + "=?", arrayOf(rowId.toString()))
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address)
                    .build())
            } else {
                ops.add(android.content.ContentProviderOperation.newInsert(DATA)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, mime)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME)
                    .build())
            }
        }

        if (birthday.isNotBlank()) {
            val mime = ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
            val rowId = birthdayRowId(rawId)
            if (rowId != null) {
                ops.add(android.content.ContentProviderOperation.newUpdate(DATA)
                    .withSelection(ContactsContract.Data._ID + "=?", arrayOf(rowId.toString()))
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, birthday)
                    .build())
            } else {
                ops.add(android.content.ContentProviderOperation.newInsert(DATA)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, mime)
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, birthday)
                    .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                    .build())
            }
        }

        if (ops.isEmpty()) return true
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return true
    }

    private fun openUri(uri: String) =
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

    /** חיוג ישיר ללא בורר: ACTION_CALL אם ניתנה הרשאת CALL_PHONE, אחרת נפילה ל-ACTION_DIAL. */
    private fun placeCall(phone: String) {
        val tel = Uri.parse("tel:$phone")
        try {
            if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(Intent.ACTION_CALL, tel))
            } else {
                startActivity(Intent(Intent.ACTION_DIAL, tel))
            }
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_DIAL, tel)) } catch (e2: Exception) {}
        }
    }

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

    /**
     * מעביר תמונה משותפת ליעד לפי appId.
     * למייל/SMS — הנמען (איש הקשר שנבחר בסוויפ) נשמר.
     * לוואטסאפ/טלגרם וכו' — בורר הצ'אט של אותה אפליקציה נפתח (מגבלת אנדרואיד למדיה).
     */
    private fun shareImageTo(appId: String, uri: Uri, phone: String, email: String, text: String) {
        val mime = try { contentResolver.getType(uri) } catch (e: Exception) { null } ?: "image/*"
        fun base(): Intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (text.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        when (appId) {
            "whatsapp"  -> sendImageToPackage("com.whatsapp", uri, text, mime)
            "telegram"  -> sendImageToPackage("org.telegram.messenger", uri, text, mime)
            "messenger" -> sendImageToPackage("com.facebook.orca", uri, text, mime)
            "instagram" -> sendImageToPackage("com.instagram.android", uri, text, mime)
            "signal"    -> sendImageToPackage("org.thoughtcrime.securesms", uri, text, mime)
            "discord"   -> sendImageToPackage("com.discord", uri, text, mime)
            "facebook"  -> sendImageToPackage("com.facebook.katana", uri, text, mime)
            "twitter"   -> sendImageToPackage("com.twitter.android", uri, text, mime)
            "linkedin"  -> sendImageToPackage("com.linkedin.android", uri, text, mime)
            "sms" -> {
                val i = base().apply {
                    putExtra("address", phone); putExtra("sms_body", text)
                    val pkg = try { android.provider.Telephony.Sms.getDefaultSmsPackage(this@ShareActivity) } catch (e: Exception) { null }
                    if (pkg != null) setPackage(pkg)
                }
                if (i.resolveActivity(packageManager) != null) startActivity(i)
                else startActivity(Intent.createChooser(base(), "שלח תמונה"))
            }
            "mail" -> {
                val i = base().apply { if (email.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email)) }
                startActivity(Intent.createChooser(i, "שלח תמונה"))
            }
            else -> startActivity(Intent.createChooser(base(), "שיתוף תמונה"))
        }
    }

    /** ACTION_SEND עם תמונה לחבילה ספציפית; אם לא מותקנת — בורר מערכת. */
    private fun sendImageToPackage(pkg: String, uri: Uri, text: String, mime: String) {
        fun make(withPkg: Boolean): Intent = Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_STREAM, uri)
            if (text.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, text)
            if (withPkg) setPackage(pkg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val i = make(true)
        if (i.resolveActivity(packageManager) != null) startActivity(i)
        else startActivity(Intent.createChooser(make(false), "שיתוף תמונה"))
    }

    private fun digitsOf(s: String): String = s.filter { it.isDigit() }

    /** האם שני מספרים מתייחסים לאותו אדם (השוואת סיומת ספרות). */
    private fun sameNumber(aDigits: String, lastDigits: String): Boolean {
        if (aDigits.isEmpty() || lastDigits.isEmpty()) return false
        val n = minOf(aDigits.length, lastDigits.length)
        return aDigits.takeLast(n) == lastDigits.takeLast(n)
    }

    /** יומן שיחות מול מספר: תאריך, משך, סוג. JSON ממוין יורד. */
    private fun callLogJson(phone: String): String {
        if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return "[]"
        val target = digitsOf(phone)
        if (target.length < 5) return "[]"
        val last = target.takeLast(9)
        val arr = JSONArray()
        try {
            contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(
                    android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.DATE,
                    android.provider.CallLog.Calls.DURATION, android.provider.CallLog.Calls.TYPE
                ),
                android.provider.CallLog.Calls.NUMBER + " LIKE ?", arrayOf("%$last"),
                android.provider.CallLog.Calls.DATE + " DESC"
            )?.use {
                val iNum = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                val iDate = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                val iDur = it.getColumnIndex(android.provider.CallLog.Calls.DURATION)
                val iType = it.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                var n = 0
                while (it.moveToNext() && n < 300) {
                    val num = digitsOf(if (iNum >= 0) it.getString(iNum) ?: "" else "")
                    if (!sameNumber(num, last)) continue
                    arr.put(JSONObject()
                        .put("date", if (iDate >= 0) it.getLong(iDate) else 0L)
                        .put("dur", if (iDur >= 0) it.getLong(iDur) else 0L)
                        .put("type", if (iType >= 0) it.getInt(iType) else 0))
                    n++
                }
            }
        } catch (e: Exception) {}
        return arr.toString()
    }

    /** יומן SMS מול מספר: תאריך, כיוון (1=נכנס,2=יוצא), תוכן. JSON ממוין יורד. */
    private fun smsLogJson(phone: String): String {
        if (checkSelfPermission(android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return "[]"
        val target = digitsOf(phone)
        if (target.length < 5) return "[]"
        val last = target.takeLast(9)
        val arr = JSONArray()
        try {
            contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("address", "date", "body", "type"),
                "address LIKE ?", arrayOf("%$last"), "date DESC"
            )?.use {
                var n = 0
                while (it.moveToNext() && n < 300) {
                    val addr = digitsOf(it.getString(0) ?: "")
                    if (!sameNumber(addr, last)) continue
                    arr.put(JSONObject()
                        .put("date", it.getLong(1))
                        .put("body", it.getString(2) ?: "")
                        .put("type", it.getInt(3)))
                    n++
                }
            }
        } catch (e: Exception) {}
        return arr.toString()
    }

    private fun callKind(type: Int): String = when (type) {
        1 -> "call_in"
        2 -> "call_out"
        3, 5 -> "call_miss"
        else -> "call"
    }

    /** המספרים שדיברת איתם לאחרונה (שיחות + SMS), distinct לפי 9 ספרות, ממוין יורד, עם סוג המגע. */
    private fun recentContactsJson(limit: Int): String {
        val dateMap = HashMap<String, Long>()
        val kindMap = HashMap<String, String>()
        try {
            if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.DATE, android.provider.CallLog.Calls.TYPE),
                    null, null,
                    android.provider.CallLog.Calls.DATE + " DESC"
                )?.use {
                    val iNum = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                    val iDate = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                    val iType = it.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                    var cnt = 0
                    while (it.moveToNext()) {
                        if (cnt++ > 800) break
                        val d = digitsOf(if (iNum >= 0) it.getString(iNum) ?: "" else "")
                        if (d.length < 5) continue
                        val k = d.takeLast(9)
                        val date = if (iDate >= 0) it.getLong(iDate) else 0L
                        if (date > (dateMap[k] ?: 0L)) { dateMap[k] = date; kindMap[k] = callKind(if (iType >= 0) it.getInt(iType) else 0) }
                    }
                }
            }
        } catch (e: Exception) {}
        try {
            if (checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                contentResolver.query(
                    Uri.parse("content://sms"),
                    arrayOf("address", "date"),
                    null, null, "date DESC"
                )?.use {
                    var cnt = 0
                    while (it.moveToNext()) {
                        if (cnt++ > 800) break
                        val d = digitsOf(it.getString(0) ?: "")
                        if (d.length < 5) continue
                        val k = d.takeLast(9)
                        val date = it.getLong(1)
                        if (date > (dateMap[k] ?: 0L)) { dateMap[k] = date; kindMap[k] = "sms" }
                    }
                }
            }
        } catch (e: Exception) {}
        val n = if (limit > 0) limit else 60
        val sorted = dateMap.entries.sortedByDescending { it.value }.take(n)
        val arr = JSONArray()
        sorted.forEach { arr.put(JSONObject().put("num", it.key).put("date", it.value).put("kind", kindMap[it.key] ?: "call")) }
        return arr.toString()
    }

    inner class Bridge {
        /** חיוג ישיר ללא בורר מתוך ה-HTML (כרטיס איש קשר / רשימה). */
        @JavascriptInterface fun callNumber(phone: String) { runOnUiThread { placeCall(phone) } }

        /** קריאה אחת: מחזירה את כל אירועי היומן (כותרת מנורמלת + יום/חודש), וה-JS ממפה מקומית וזריז — מונע סריקה סינכרונית לכל איש קשר. */
        @JavascriptInterface fun listCalendarBirthdays(): String {
            return try {
                if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return "[]"
                val proj = arrayOf("title", "dtstart")
                val cur = contentResolver.query(android.provider.CalendarContract.Events.CONTENT_URI, proj, null, null, null) ?: return "[]"
                val arr = org.json.JSONArray()
                cur.use {
                    val ti = it.getColumnIndex("title"); val di = it.getColumnIndex("dtstart")
                    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                    while (it.moveToNext()) {
                        val title = it.getString(ti) ?: continue
                        val nt = normNm(title); if (nt.isEmpty()) continue
                        val dt = it.getLong(di); if (dt <= 0L) continue
                        utc.timeInMillis = dt
                        arr.put(org.json.JSONObject().put("t", nt).put("mo", utc.get(java.util.Calendar.MONTH) + 1).put("da", utc.get(java.util.Calendar.DAY_OF_MONTH)))
                    }
                }
                arr.toString()
            } catch (e: Exception) { "[]" }
        }

        /** התנהגות ביציאה מפוקוס: close | home | last. נשמר גם ב-prefs כדי שיהיה זמין כבר ב-onStop. */
        @JavascriptInterface fun setResumeMode(mode: String) {
            val m = if (mode == "home" || mode == "last") mode else "close"
            resumeMode = m
            getSharedPreferences("sweep_prefs", MODE_PRIVATE).edit().putString("resume_mode", m).apply()
        }

        @JavascriptInterface fun getSharedText(): String = sharedText
        @JavascriptInterface fun getSharedImage(): String = if (sharedImageUri != null) "1" else ""
        @JavascriptInterface fun getContacts(): String = readContacts()

        /** פותח את אפליקציית היומן עם אירוע חדש (ליום הולדת/זימון). */
        @JavascriptInterface
        fun addEvent(json: String) {
            runOnUiThread {
                try {
                    val o = JSONObject(json)
                    val begin = o.optLong("begin", System.currentTimeMillis())
                    val end = o.optLong("end", begin + 3600000L)
                    val i = Intent(Intent.ACTION_INSERT).apply {
                        data = android.provider.CalendarContract.Events.CONTENT_URI
                        putExtra(android.provider.CalendarContract.Events.TITLE, o.optString("title", ""))
                        val loc = o.optString("location", ""); if (loc.isNotEmpty()) putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, loc)
                        val desc = o.optString("description", ""); if (desc.isNotEmpty()) putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, end)
                        if (o.optBoolean("allDay", false)) putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                        val rrule = o.optString("rrule", ""); if (rrule.isNotEmpty()) putExtra(android.provider.CalendarContract.Events.RRULE, rrule)
                        val emails = o.optJSONArray("emails")
                        if (emails != null && emails.length() > 0) {
                            val sb = StringBuilder()
                            for (k in 0 until emails.length()) { if (k > 0) sb.append(","); sb.append(emails.optString(k, "")) }
                            putExtra(Intent.EXTRA_EMAIL, sb.toString())
                        }
                    }
                    startActivity(i)
                } catch (e: Exception) {
                    Toast.makeText(this@ShareActivity, "לא נמצאה אפליקציית יומן", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
                    val _img = sharedImageUri
                    if (_img != null) {
                        shareImageTo(appId, _img, phone, email, text)
                        finish(); return@runOnUiThread
                    }
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
                        "call" -> placeCall(phone)
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

        /** עדכון איש קשר במאגר הטלפון (שם/טלפון/מייל). מחזיר true אם נכתב בהצלחה. */
        @JavascriptInterface
        fun updateContact(origName: String, origPhone: String, name: String, phone: String, email: String, address: String, birthday: String): Boolean {
            if (checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { requestPermissions(arrayOf(android.Manifest.permission.WRITE_CONTACTS), REQ_WRITE) }
                return false
            }
            return try { applyContactUpdate(origName, origPhone, name, phone, email, address, birthday) } catch (e: Exception) { false }
        }

        /** מחזיר את טקסט הלוח (להדבקה אוטומטית של מפתח). ריק אם אין. */
        @JavascriptInterface
        fun readClipboard(): String {
            return try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0)
                    (clip.getItemAt(0).coerceToText(this@ShareActivity)?.toString() ?: "")
                else ""
            } catch (e: Exception) { "" }
        }

        /** יומן שיחות מול מספר (JSON). */
        @JavascriptInterface fun getCallLog(phone: String): String = try { callLogJson(phone) } catch (e: Exception) { "[]" }

        /** יומן SMS מול מספר (JSON). */
        @JavascriptInterface fun getSmsLog(phone: String): String = try { smsLogJson(phone) } catch (e: Exception) { "[]" }

        /** מספרים שדיברת איתם לאחרונה (לתצוגת "שימוש אחרון"). */
        @JavascriptInterface fun getRecentContacts(limit: Int): String = try { recentContactsJson(limit) } catch (e: Exception) { "[]" }

        /** שומר את מצב "שמירה על קשר" (מערך JSON של {num,days,name}) לבדיקת הרקע. */
        @JavascriptInterface
        fun saveKitState(json: String) {
            try {
                getSharedPreferences("kit_state", Context.MODE_PRIVATE).edit().putString("members", json ?: "[]").apply()
                // התראות רקע מבוטלות לבקשת המשתמש: KitAlarmReceiver.schedule(this@ShareActivity)
            } catch (e: Exception) {}
        }

        /**
         * גיבוי: שומר את נתוני האפליקציה (JSON של localStorage) + מייצא את כל אנשי הקשר ל-VCF,
         * לתיקיית Android/data/com.sweep/files/backups. מחזיר "OK|נתיב|מספר_אנשי_קשר" או "ERR|הודעה".
         */
        @JavascriptInterface
        fun runBackup(cfgJson: String): String {
            return try {
                val dir = java.io.File(getExternalFilesDir(null), "backups")
                dir.mkdirs()
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
                java.io.File(dir, "sweep-data-$ts.json").writeText(cfgJson ?: "{}", Charsets.UTF_8)
                var nv = 0
                try {
                    val sb = StringBuilder()
                    contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY),
                        null, null, null
                    )?.use { c ->
                        val iL = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                        while (c.moveToNext()) {
                            val lk = if (iL >= 0) c.getString(iL) else null
                            if (lk.isNullOrEmpty()) continue
                            try {
                                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lk)
                                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                    afd.createInputStream().use { ins ->
                                        sb.append(String(ins.readBytes(), Charsets.UTF_8))
                                        nv++
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    if (sb.isNotEmpty()) java.io.File(dir, "contacts-$ts.vcf").writeText(sb.toString(), Charsets.UTF_8)
                } catch (e: Exception) {}
                pruneBackups(dir, "sweep-data-", 4)
                pruneBackups(dir, "contacts-", 4)
                "OK|" + dir.absolutePath + "|" + nv
            } catch (e: Exception) { "ERR|" + (e.message ?: "") }
        }

        /** שומר רק את [keep] הקבצים האחרונים עם הקידומת הנתונה. */
        private fun pruneBackups(dir: java.io.File, prefix: String, keep: Int) {
            try {
                val fs = dir.listFiles { f -> f.name.startsWith(prefix) }?.sortedByDescending { it.lastModified() } ?: return
                for (i in keep until fs.size) try { fs[i].delete() } catch (e: Exception) {}
            } catch (e: Exception) {}
        }

        /** התראות וואטסאפ שנתפסו לאיש קשר לפי שם (JSON). */
        @JavascriptInterface fun getNotifLog(name: String): String = try { NotifListener.read(this@ShareActivity, name) } catch (e: Exception) { "[]" }

        /** האם גישת קריאת ההתראות מופעלת. */
        @JavascriptInterface
        fun isNotifAccess(): Boolean {
            return try {
                val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
                flat.contains("$packageName/")
            } catch (e: Exception) { false }
        }

        /** פותח את מסך הגדרות גישת ההתראות. */
        @JavascriptInterface
        fun openNotifAccess() {
            runOnUiThread {
                try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
                catch (e: Exception) { try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
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
