# מפרט ייצור שאלות — מבדק תנ"ך כיתה ו'

אתה מייצר שאלות תרגול לאתר הכנה למבדק תנ"ך של ראמ"ה, כיתה ו'. רמת התלמידים: בית ספר יסודי.

## כללי זהב
1. **דיוק מוחלט** — כל שאלה חייבת להיות נכונה עובדתית לפי הטקסט המקראי. אם אינך בטוח בפרט — אל תכתוב אותו. עדיף שאלה פשוטה ונכונה מאשר מתוחכמת ושגויה.
2. כל שאלה עם **הסבר מלא** (data-explain) — משפט-שניים שמלמדים את התשובה ומפנים לפסוק/פרק כשאפשר.
3. **בלי כפילויות** — אל תחזור על שאלה שכבר קיימת (תקבל רשימה).
4. רמה מתאימה לכיתה ו' — לא קל מדי ולא אקדמי. שאלות ידע, הבנה, הסקה וערכים.
5. **גיוון**: ערבב סוגי שאלות וזוויות (דמויות, אירועים, סדר, ערכים, פסוקים, הסקה).

## פורמט HTML — שלושה סוגים בלבד

### א. רב-ברירה (4 מסיחים) — הסוג העיקרי
```
<div class="quiz is-added" data-type="mc" data-explain="ההסבר כאן">
  <div class="tags"><span class="quiz__tag quiz__tag--added">✎ שאלה לתרגול</span><span class="quiz__tag">רב-ברירה · דמויות</span></div>
  <p class="quiz__q">נוסח השאלה?</p>
  <div class="opts">
    <div class="opt" data-correct="false">מסיח שגוי</div>
    <div class="opt" data-correct="true">התשובה הנכונה</div>
    <div class="opt" data-correct="false">מסיח שגוי</div>
    <div class="opt" data-correct="false">מסיח שגוי</div>
  </div>
  <div class="feedback"></div>
</div>
```
- **בדיוק מסיח אחד** עם data-correct="true". ערבב את מיקום הנכונה.
- תווית שנייה אפשרית: `רב-ברירה · דמויות` / `· אירועים` / `· ערכים` / `· הסקה` / `· ידע` / `· סדר` / `· פסוק`.

### ב. נכון / לא נכון (רב-ברירה עם 2 מסיחים)
```
<div class="quiz is-added" data-type="mc" data-explain="ההסבר כאן">
  <div class="tags"><span class="quiz__tag quiz__tag--added">✎ שאלה לתרגול</span><span class="quiz__tag">נכון / לא נכון</span></div>
  <p class="quiz__q">היגד: “המשפט שיש לשפוט.” נכון או לא נכון?</p>
  <div class="opts">
    <div class="opt" data-correct="true">נכון</div>
    <div class="opt" data-correct="false">לא נכון</div>
  </div>
  <div class="feedback"></div>
</div>
```
- שים את data-correct="true" על התשובה הנכונה (נכון או לא נכון לפי ההיגד).

### ג. השלמה (input)
```
<div class="quiz is-added" data-type="fill" data-answer="תשובה|מילה נרדפת|וריאציה" data-explain="ההסבר כאן">
  <div class="tags"><span class="quiz__tag quiz__tag--added">✎ שאלה לתרגול</span><span class="quiz__tag">השלמה</span></div>
  <p class="quiz__q">המשפט עם ______ להשלמה.</p>
  <input type="text" placeholder="כתבו את התשובה...">
  <button class="btn checkbtn"><i class="fa-solid fa-check"></i> בדיקה</button>
  <div class="feedback"></div>
</div>
```
- data-answer = רשימת תשובות מקובלות מופרדות ב-`|`. הבדיקה מתעלמת מרווחים/גרשיים/אותיות סופיות, אבל תן וריאציות (מספר במילים ובספרה, צורות כתיב). תשובה רצויה: מילה/מספר קצר.

## תמהיל מומלץ
בערך 70% רב-ברירה (4 מסיחים), 15% נכון/לא נכון, 15% השלמה.

## חוקי פורמט קריטיים (אחרת הקובץ נשבר)
- **אסור גרש כפול ישר (") בתוך ערך attribute** (data-explain / data-answer). לציטוטים מקראיים השתמש בגרשיים מסולסלים `“ ”` או בגרש בודד. דוגמה תקינה: data-explain="רחב הסתירה את המרגלים (יהושע ב')."
- כתוב ראשי תיבות של ספרים/פרקים בעברית: בראשית ל״ז, יהושע ו', ויקרא י״ט, שופטים ז'.
- כל בלוק שאלה מופרד בשורה ריקה אחת.
- אל תוסיף שום עטיפה — רק בלוקי שאלה גולמיים, אחד אחרי השני.
- כתוב את הקובץ שלך עם הכלי Write לנתיב המבוקש בלבד. אל תיגע ב-tirgul.html.
