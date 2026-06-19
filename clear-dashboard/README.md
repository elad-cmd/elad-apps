# דשבורד משימות — PWA

אפליקציית דשבורד משימות בסגנון Clear עם מחוות החלקה, שמקורה בארטיפקט של Claude.
הומרה לפרויקט Vite + React + Tailwind, ארוזה כ‑PWA להתקנה בטלפון.

## דרישות מוקדמות

Node.js גרסה 18 ומעלה (מומלץ 20+). בדיקה: `node -v`.
אם אין — להתקין מ‑https://nodejs.org (גרסת LTS).

## הרצה מקומית (במחשב)

```powershell
cd "C:\Users\elad\010_קלוד\Claude_C\claude_elad\apps\clear-dashboard"
npm install
npm run dev
```

לאחר מכן לפתוח בדפדפן את הכתובת שמופיעה (בדרך כלל http://localhost:5173).

## בנייה לפרודקשן

```powershell
npm run build
npm run preview
```

הקבצים הבנויים נוצרים בתיקיית `dist`. שם נמצא גם ה‑service worker וה‑manifest של ה‑PWA.

## התקנה כאפליקציה בטלפון (PWA)

חשוב: התקנת PWA דורשת אתר מאובטח (HTTPS). הרצה מקומית רגילה (http) לא תאפשר התקנה אמיתית בטלפון.
המסלול הפשוט והחינמי:

1. להריץ `npm run build` (נוצרת תיקיית `dist`).
2. להעלות את תוכן `dist` לאירוח סטטי עם HTTPS — למשל Netlify (גרירת התיקייה ל‑app.netlify.com/drop), Vercel, Cloudflare Pages או GitHub Pages.
3. לפתוח את כתובת ה‑HTTPS שקיבלת בדפדפן של הטלפון.
4. אנדרואיד (Chrome): תפריט ⋮ → "התקן אפליקציה" / "הוסף למסך הבית".
   אייפון (Safari): כפתור שיתוף → "הוסף למסך הבית".

## מה שונה מהארטיפקט המקורי

- האחסון: `window.storage` (API של Claude) הוחלף ב‑`localStorage` דרך shim ב‑`src/main.jsx`. הקוד ב‑`Dashboard.jsx` לא שונה.
- נוספה סביבת build (Vite), Tailwind, ופונטים Heebo/Assistant.
- נוספה תצורת PWA: manifest, service worker, ואייקונים.

## מה עדיין דמו / פתוח (לפי הצ'ק־ליסט הפנימי בקוד)

- מסך "מיילים" הוא placeholder — אין חיבור אמיתי ל‑Gmail.
- כרטיס "דחוף" ונתוני חידושים הם דוגמה מקודדת בקוד.
- תזכורות מציגות ספירה לאחור אך לא שולחות התראה אמיתית (דורש Web Push / שירות התראות).

## מבנה הפרויקט

```
clear-dashboard/
├─ index.html
├─ package.json
├─ vite.config.js        # תצורת Vite + vite-plugin-pwa
├─ tailwind.config.js
├─ postcss.config.js
├─ public/               # אייקונים ל-PWA
│  ├─ icon-192.png
│  ├─ icon-512.png
│  └─ apple-touch-icon.png
└─ src/
   ├─ main.jsx           # נקודת כניסה + shim של האחסון
   ├─ Dashboard.jsx      # הרכיב המקורי (ללא שינוי)
   └─ index.css          # Tailwind
```
