# Sweep — המשך עבודה (Handoff, סוף "Sweep 12")

קרא את הקובץ הזה בתחילת כל שיחת Sweep. גרסה נוכחית: **`2026.07.07.1700`** (⚠️ **עדיין לא נפרסה** — צריך להריץ `deploy-apps.bat` + "עדכן אפליקציה").

## קבצים ובנייה
- מקור ה-UI: `apps/MyShare.html` (קובץ ענק יחיד). נטען ב-WebView מ-GitHub Pages / מהאסט המובנה `Sweep/android/app/src/main/assets/myshare.html`.
- נייטיב: `apps/Sweep/android/app/src/main/java/com/sweep/ShareActivity.kt` (מחלקת `Bridge` = ממשק JS בשם `AndroidShare`; שדות `web:WebView`, `runOnUiThread`).
- **דיפלוי = דאבל-קליק על `apps/deploy-apps.bat`** (הצג אותו כקרד לחיץ כשמבקשים מאלעד לפרוס). דוחף את תיקיית `apps` ל-repo `elad-cmd/elad-apps` → GitHub Actions "Build Sweep APK" בונה APK + מפרסם Release (tag `sweep-latest`) + GitHub Pages.
- **שינויי HTML/CSS** → מגיעים למכשיר דרך "עדכן אפליקציה" (מושך מ-GitHub Pages; עדיין צריך דיפלוי כדי לפרסם). **שינויי Kotlin/נייטיב** → מצריכים בניית APK והתקנה ידנית.
- בכל שינוי: לעדכן `const APP_VERSION=` ב-MyShare.html **וגם** `version.json` יחד.
- חתימת APK = `debug.keystore` מחויב בריפו (יציב → עדכונים לא מוחקים נתונים).
- אפשר לקרוא לוגים של GitHub Actions דרך Claude-in-Chrome (אלעד מחובר) לאבחון כשלי בנייה.
- תיקון CI שהוחל: הוספת `env: ACTIONS_ALLOW_USE_UNSECURE_NODE_VERSION: 'true'` ל-build-sweep.yml (גיטהאב כפו Node 24 ששבר את `android-actions/setup-android@v3`). בנייה טובה אחרונה = #319.
- אימות JS אחרי עריכות: לחלץ בלוקי `<script>` → `node --check`. מטמון ה-Dropbox ב-bash לרוב **מיושן/חתוך** — לסמוך על כלי ה-host (Read/Grep/Edit).

## תיקונים גדולים שנעשו ב-Sweep 12
- `fKey`=`statKey`=מספר טלפון גולמי (**לא יציב**). חברי רשימות נשמרים לפי fKey. `healKeys()` (רץ בטעינה) ממפה מפתחות שמורים→אנשי קשר נוכחיים. **תוקן**: מרפא גם `members` וגם `manual` (קודם רק members → recomputeAllRules בנה מחדש מ-manual ישן ומחק שוב), ומתאים לפי **כל** הטלפונים + שם. זה היה הבאג "חברי רשימות נעלמים בעדכון".
- ניקוי מספרים כותב דרך `applyContactUpdate` (ShareActivity.kt) — **תוקן** לעדכן את שורת הטלפון בכל ה-raw contacts (כל החשבונות כולל גוגל) כדי שסנכרון גוגל לא ידרוס. נוסף `allRawIds()`.
- גיבוי: `backupToDownloads` (נייטיבי, Thread אסינכרוני + callbacks `__bkProg`/`__bkDone`) → `Download/Sweep` (MediaStore, API29+). JS מציג בר התקדמות + חוסם לחיצה כפולה.
- `navLock` נעילת מחוות גלובלית; `openScreen` מחזיר מסך קיים אם נעול (מונע מסכים כפולים). `.mpovl` z-index הועלה 85→100 (היה מתחת ל-`#botbar` z94 → לחיצות "נפלו דרך" הגיליון לכפתור החייגן). לא להסתמך על CSS `:has()`.
- סרגל בחירה משותף `showSelBar(count,actions,onCancel)`/`hideSelBar` — עוצב מחדש לסגנון אנדרואיד: **אייקון למעלה + תווית קטנה**, על גלולה כהה. פעולות = `{icon,label,cls,fn}`. משמש מולטי-סלקט של אחרונים, סינון, וארכיון.
- מולטי-סלקט באחרונים: `recSel`/`recSelSet`, `enterRecSel` (לחיצה ארוכה באחרונים), `updRecSelBar`; צ'קבוקס = `.pk-chk` הקיים. פעולות: בחר-הכל/נעץ/העתק/הסר.
- "טען עוד" באחרונים: `RECENT_SHOWN`/`RECENT_TOTAL` + כפתור `.rec-loadmore` (RECENT_LIMIT=30 גודל עמוד).
- אנימציות חולקות מסגרת `.nz-prog-*`: ניקוי=מטאטא (ללא הילת זוהר), גיבוי=`nzDupHTML()` כספת (כרטיסים ימין→שמאל לתוך כספת). `animTabSlide`=קסקדת אקורדיון. אייקון אפליקציה נוצר מחדש מלוגו 3-הנקודות הפנימי.
- גיליונות `.mp-sheet` רקע `#bfe8db` (טורקיז בהיר), ריפוד תחתון 42. נקודת KIT באיחור `.late-dot` ב-left:72. הוסרה בדיחת "אף פעם"(99999) + ניקוי הנתונים שלה.

## TODO פתוחים (להמשך)
1. לוודא במכשיר אחרי דיפלוי v1700: חברי הרשימות חזרו? סרגל המולטי-סלקט תקין?
2. **ה"אחרונים" כמעט ריק / "האפליקציה לא מקבלת את כל המידע"** — הרשאות מאושרות, אז זו בעיית טעינת נתונים אמיתית (getContacts/getCallLog מחזירים חלקית?). **לא פתור — לחקור.**
3. **מסך אפור/ריק בחזרה לאפליקציה מהרקע** — נוסף נודניק ריענון JS ב-`visibilitychange` (אולי צריך גם `onResume` נייטיבי שמרענן את ה-WebView).
4. **ניסוי תזמון המניפה 45° (נדחה):** רק בבית — שהמניפה 45° בסרגל התחתון תיפתח **במקביל** לסגירת הסרגל (כרגע רציף/איטי). לשמור את המקור להשוואה. (הערה: `startPick` הוא מניפת השיתוף בהחזקת איש קשר; ה"45° 3 נקודות" הוא flip נפרד של הסרגל התחתון.)
5. **היסטוריית שיחות בפרופיל → אותו סרגל מולטי-סלקט:** שורות ההיסטוריה ב-`openContact` (`#ctHistory`, בערך שורה 2282 יש לחיצה ארוכה→`miniConfirm` להסרה) צריכות לתמוך באותו סרגל בחירה כמו האחרונים.
