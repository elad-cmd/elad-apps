import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence, Reorder, useMotionValue, useTransform, useDragControls } from 'framer-motion';
import { CheckCircle2, Lightbulb, Mail, RefreshCw, ArrowRight, ChevronRight, Plus, Undo2, Palette, Search, Trash2, Clock, Check } from 'lucide-react';

// ========== MAIL FEED ==========
// Read-only feed of mail subjects. A scheduled Claude task writes this JSON file
// into a Dropbox folder; the app reads it from Dropbox's direct-content host
// (cross-origin friendly). Overwriting the same file keeps the same link.
const MAIL_FEED_URL = 'https://dl.dropboxusercontent.com/scl/fi/qc0q25ar14ywjl5k1rg4i/mail-feed.json?rlkey=nz10mz5lhm5u4wruj0i0t0oci';

// ========== PALETTES (Clear-style color schemes for individual views) ==========
// Each palette is a (dark, light) gradient. List is cyclic for tap-to-cycle behavior.
// 'lsd' is a special palette that triggers the rotating colors mode instead.
const PALETTES = [
  // Each palette: dark color is what shows on the inner page background.
  // Must clearly read as the palette's name, not bleed into another color.
  { id: 'turquoise', name: 'טורקיז',  dark: { r: 30, g: 90, b: 100 },    light: { r: 150, g: 215, b: 215 } },
  { id: 'purple',    name: 'סגול',    dark: { r: 109, g: 78, b: 173 },   light: { r: 196, g: 181, b: 253 } },
  { id: 'yellow',    name: 'צהוב',    dark: { r: 180, g: 145, b: 30 },   light: { r: 253, g: 230, b: 138 } },
  { id: 'mint',      name: 'ירוק',    dark: { r: 34, g: 130, b: 95 },    light: { r: 167, g: 243, b: 208 } },
  { id: 'pink',      name: 'ורוד',    dark: { r: 200, g: 95, b: 130 },   light: { r: 254, g: 202, b: 222 } },
  { id: 'lsd',       name: 'LSD',     lsd: true },
];

// Default palette per view
const DEFAULT_PALETTES = {
  tasks: 'turquoise',
  ideas: 'purple',
  mail: 'mint',
  renewals: 'pink',
};

// Storage key for per-view palette selection
const PALETTES_KEY = 'dashboard:palettes';

function getPalette(id) {
  return PALETTES.find(p => p.id === id) || PALETTES[0];
}

// ========== STORAGE KEYS ==========
const TASKS_KEY = 'tasks:list';
const IDEAS_KEY = 'ideas:list';
const MAILS_KEY = 'mails:list';
const RENEWALS_KEY = 'renewals:list';
const CHECKLIST_KEY = 'checklist:list';
const TASKS_UNDO_KEY = 'tasks:undo';
const IDEAS_UNDO_KEY = 'ideas:undo';
const RENEWALS_UNDO_KEY = 'renewals:undo';
const CHECKLIST_UNDO_KEY = 'checklist:undo';
const THEME_KEY = 'dashboard:theme';
const SEEDED_KEY = 'dashboard:seeded';
const IDEAS_VERSION_KEY = 'dashboard:ideasVersion';
const IDEAS_VERSION = '2026-05-28-audit';
// Bump this to force a full reset of ALL lists to the tutorial seed on next load.
const SEED_VERSION_KEY = 'dashboard:seedVersion';
const SEED_VERSION = '2026-05-28-tutorial-2';

// ========== SEED DATA (tutorial - shown only on first entry) ==========
const SEED_TUTORIAL = [
  'גרור מטה (כמו רענון) כדי להוסיף פריט חדש',
  'החלק ימינה חצי הדרך כדי לסמן ביצוע (ושוב כדי לבטל)',
  'החלק ימינה עד הסוף כדי לקבוע תזכורת',
  'החלק שמאלה למחיקה — יופיע כפתור ביטול',
  'אחוז בפינה הימנית וגרור מעלה/מטה כדי לשנות סדר',
];
const SEED_TASKS = [...SEED_TUTORIAL];
const SEED_IDEAS = [...SEED_TUTORIAL];
const SEED_MAILS = SEED_TUTORIAL.map(text => ({ text, urgent: false }));
const SEED_RENEWALS = SEED_TUTORIAL.map(text => ({ text, urgent: false }));

// Development checklist - what we've built and what's left. done:true shows strikethrough.
const SEED_CHECKLIST = [
  // Done
  { text: 'עיצוב בסגנון Clear - מלבנים בגרדיאנט', done: true },
  { text: 'פלטות צבעים לכל כרטיסיה + בורר פלטות', done: true },
  { text: 'swipe ימינה=בוצע, שמאלה=מחיקה', done: true },
  { text: 'כפתור ביטול מחיקה מרחף בגובה השורה', done: true },
  { text: 'גרירה אנכית עם לחיצה ארוכה + רטט', done: true },
  { text: 'משיכה להוספה (משיכה ארוכה בלבד)', done: true },
  { text: 'גלילה אופקית לטקסט ארוך + swipe מהקצוות', done: true },
  { text: 'חידושים = רשימה ידנית מלאה', done: true },
  { text: 'חזרה לדשבורד גוללת לכרטיסיה שיצאת ממנה', done: true },
  { text: 'תזכורות: גלגלי בחירה + badge ספירה לאחור', done: true },
  // Open
  { text: 'מיילים = רשימה לצפייה בלבד (בלי מחוות)', done: false },
  { text: 'אפקט גומי בקצוות הגלילה', done: false },
  { text: 'כיוונון צבעי פלטת LSD', done: false },
  { text: 'שיפור נוסף לבורר הפלטות', done: false },
  { text: 'הוספת רשימות מותאמות (כפתור +)', done: false },
  { text: 'שיתוף משימות דרך דרופבוקס', done: false },
  { text: 'ניקוי קוד סופי', done: false },
  { text: 'הפיכה ל-PWA + התראות אמיתיות', done: false },
  { text: 'סנכרון עם Google Calendar', done: false },
];

// ========== THEMES ==========
const THEMES = {
  psycho: {
    name: 'פסיכו',
    pageBgGradient: 'linear-gradient(180deg, #8FCBCC 0%, #7BBFC0 100%)',
    pageBg: '#7BBFC0',
    headerText: '#1F2937',
    headerSubText: '#374151',
    cardBg: '#FFFFFF',
    cardBorder: 'transparent',
    cardShadow: '0 8px 20px rgba(0,0,0,0.10)',
    cardText: '#1F2937',
    cardSubText: '#6B7280',
    pill1: { bg: '#C4B5FD', icon: '#5B21B6' },
    pill2: { bg: '#FDE68A', icon: '#92400E' },
    pill3: { bg: '#A7F3D0', icon: '#065F46' },
    pill4: { bg: '#FECACA', icon: '#991B1B' },
    // Per-view accent colors for nav bar
    viewAccents: {
      home: '#7BBFC0',
      tasks: '#7BBFC0',
      ideas: '#C4B5FD',
      mail: '#A7F3D0',
      renewals: '#FECACA',
    },
    previewBg: '#F3F4F6',
    previewText: '#374151',
    taskRowMode: 'gradient',
    rowColors: { dark: { r: 30, g: 90, b: 100 }, light: { r: 150, g: 215, b: 215 } },
    rowTextColor: '#FFFFFF',
    // Per-view gradient colors (Clear-style: each view has its own color theme)
    viewRowColors: {
      tasks:    { dark: { r: 30, g: 90, b: 100 },   light: { r: 150, g: 215, b: 215 } }, // turquoise
      ideas:    { dark: { r: 91, g: 33, b: 182 },   light: { r: 196, g: 181, b: 253 } }, // lavender purple
      mail:     { dark: { r: 6, g: 95, b: 70 },     light: { r: 167, g: 243, b: 208 } }, // mint green
      renewals: { dark: { r: 153, g: 27, b: 27 },   light: { r: 254, g: 202, b: 202 } }, // pink coral
    },
    inputBg: '#FFFFFF',
    inputBorder: '#E5E7EB',
    inputText: '#1F2937',
    inputPlaceholder: '#9CA3AF',
    addBtn: '#7BBFC0',
    addBtnDisabled: '#E5E7EB',
    bodyText: '#1F2937',
    mutedText: '#6B7280',
    navBg: 'rgba(255,255,255,0.85)',
    navBtnBg: '#FFFFFF',
    navBtnIcon: '#1F2937',
    navAccent: '#7BBFC0',
    fontFamily: '"Heebo", "Assistant", -apple-system, sans-serif',
    cardRadius: '24px',
    pillRadius: '50%',
    rowRadius: '12px',
  },
  lsd: {
    name: 'LSD',
    pageBgGradient: 'linear-gradient(160deg, #FFB6D5 0%, #FFD89E 35%, #FFEFB3 70%, #B5F4D1 100%)',
    pageBg: '#FFD89E',
    headerText: '#3B0764',
    headerSubText: '#7C2D6F',
    cardBg: '#FFFFFF',
    cardBorder: 'transparent',
    cardShadow: '0 8px 28px rgba(255, 107, 203, 0.25), 0 2px 6px rgba(155,93,229,0.15)',
    cardText: '#3B0764',
    cardSubText: '#7C2D6F',
    pill1: { bg: '#FF6BCB', icon: '#FFFFFF' },
    pill2: { bg: '#FFD93D', icon: '#3B0764' },
    pill3: { bg: '#00C2A8', icon: '#FFFFFF' },
    pill4: { bg: '#9B5DE5', icon: '#FFFFFF' },
    viewAccents: {
      home: '#FF6BCB',
      tasks: '#FF6BCB',
      ideas: '#FFD93D',
      mail: '#00C2A8',
      renewals: '#9B5DE5',
    },
    previewBg: '#FFF5F9',
    previewText: '#3B0764',
    taskRowMode: 'rotating',
    rotatingColors: [
      { bg: '#FF2E97', text: '#FFFFFF' },
      { bg: '#FF6B35', text: '#FFFFFF' },
      { bg: '#FFD23F', text: '#3B0764' },
      { bg: '#06FFA5', text: '#0B3D2E' },
      { bg: '#1B9AAA', text: '#FFFFFF' },
      { bg: '#6A4C93', text: '#FFFFFF' },
      { bg: '#C724B1', text: '#FFFFFF' },
      { bg: '#3D5AFE', text: '#FFFFFF' },
    ],
    rowTextColor: '#FFFFFF',
    inputBg: '#FFFFFF',
    inputBorder: 'rgba(155,93,229,0.25)',
    inputText: '#3B0764',
    inputPlaceholder: '#9CA3AF',
    addBtn: '#FF6BCB',
    addBtnDisabled: 'rgba(155,93,229,0.15)',
    bodyText: '#3B0764',
    mutedText: '#7C2D6F',
    navBg: 'rgba(255,255,255,0.85)',
    navBtnBg: '#FFFFFF',
    navBtnIcon: '#3B0764',
    navAccent: '#FF6BCB',
    fontFamily: '"Heebo", "Assistant", -apple-system, sans-serif',
    cardRadius: '28px',
    pillRadius: '50%',
    rowRadius: '20px',
  },
};

// ========== ROOT ==========
export default function Dashboard() {
  const [view, setView] = useState('home');
  const [tasks, setTasks] = useState([]);
  const [ideas, setIdeas] = useState([]);
  const [mails, setMails] = useState([]);
  const [mailUpdatedAt, setMailUpdatedAt] = useState(null);
  const [renewals, setRenewals] = useState([]);
  const [checklist, setChecklist] = useState([]);
  const [loading, setLoading] = useState(true);
  const [themeName, setThemeName] = useState('psycho');
  const [palettes, setPalettes] = useState(DEFAULT_PALETTES);
  const [paletteModalOpen, setPaletteModalOpen] = useState(false);
  const lastViewRef = useRef(null);
  const theme = THEMES[themeName];

  // When leaving home, remember which list we opened.
  const navigateTo = (newView) => {
    if (view === 'home' && newView !== 'home') {
      lastViewRef.current = newView;
    }
    setView(newView);
    if (newView !== 'home') {
      requestAnimationFrame(() => window.scrollTo(0, 0));
    }
  };

  // When the home view becomes active, scroll so the card we came from is at the top.
  // AnimatePresence mode="wait" delays mounting home until the previous view's exit
  // animation finishes, so we retry over a window until the card element exists.
  useEffect(() => {
    if (view !== 'home') return;
    const cameFrom = lastViewRef.current;
    if (!cameFrom) return;
    let cancelled = false;
    let attempts = 0;
    const tryScroll = () => {
      if (cancelled) return;
      const el = document.getElementById(`card-${cameFrom}`);
      if (el) {
        const top = el.getBoundingClientRect().top + window.scrollY - 12;
        window.scrollTo({ top: Math.max(0, top), behavior: 'auto' });
        // confirm it landed; if not, try a couple more times
        attempts++;
        if (attempts < 5) setTimeout(tryScroll, 60);
        else lastViewRef.current = null;
      } else if (attempts < 30) {
        attempts++;
        setTimeout(tryScroll, 40);
      }
    };
    setTimeout(tryScroll, 60);
    return () => { cancelled = true; };
  }, [view]);

  useEffect(() => {
    let cancelled = false;
    const safety = setTimeout(() => { if (!cancelled) setLoading(false); }, 2000);
    (async () => {
      let loadedTasks = [];
      let loadedIdeas = [];
      let alreadySeeded = false;

      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(TASKS_KEY);
          if (!cancelled && r && r.value) { try { loadedTasks = JSON.parse(r.value); } catch (e) {} }
        }
      } catch (e) {}
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(IDEAS_KEY);
          if (!cancelled && r && r.value) { try { loadedIdeas = JSON.parse(r.value); } catch (e) {} }
        }
      } catch (e) {}
      let loadedMails = [];
      let loadedRenewals = [];
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(MAILS_KEY);
          if (!cancelled && r && r.value) { try { loadedMails = JSON.parse(r.value); } catch (e) {} }
        }
      } catch (e) {}
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(RENEWALS_KEY);
          if (!cancelled && r && r.value) { try { loadedRenewals = JSON.parse(r.value); } catch (e) {} }
        }
      } catch (e) {}
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(SEEDED_KEY);
          if (r && r.value === 'true') alreadySeeded = true;
        }
      } catch (e) {}
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(THEME_KEY);
          if (!cancelled && r && r.value && THEMES[r.value]) setThemeName(r.value);
        }
      } catch (e) {}
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(PALETTES_KEY);
          if (!cancelled && r && r.value) {
            try {
              const parsed = JSON.parse(r.value);
              if (parsed && typeof parsed === 'object') setPalettes({ ...DEFAULT_PALETTES, ...parsed });
            } catch (e) {}
          }
        }
      } catch (e) {}

      if (cancelled) return;

      // If the seed version changed (or first ever load), wipe everything and
      // re-seed all lists with the tutorial. This also resets existing users.
      let seedVersion = null;
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(SEED_VERSION_KEY);
          if (r && r.value) seedVersion = r.value;
        }
      } catch (e) {}
      const needsReset = seedVersion !== SEED_VERSION;

      // Seed on first load when empty, OR when the seed version changed (forced reset)
      if (needsReset || (!alreadySeeded && loadedTasks.length === 0 && loadedIdeas.length === 0 && loadedMails.length === 0 && loadedRenewals.length === 0)) {
        const seededTasks = SEED_TASKS.map((text, i) => ({
          id: `seed-t-${Date.now()}-${i}`, text, done: false,
        }));
        const seededIdeas = SEED_IDEAS.map((item, i) => {
          const text = typeof item === 'string' ? item : item.text;
          const done = typeof item === 'string' ? false : !!item.done;
          return { id: `seed-i-${Date.now()}-${i}`, text, done };
        });
        const seededMails = SEED_MAILS.map((m, i) => ({
          id: `seed-m-${Date.now()}-${i}`, text: m.text, urgent: m.urgent,
        }));
        const seededRenewals = SEED_RENEWALS.map((r, i) => ({
          id: `seed-r-${Date.now()}-${i}`, text: r.text, urgent: r.urgent,
        }));
        setTasks(seededTasks);
        setIdeas(seededIdeas);
        setMails(seededMails);
        setRenewals(seededRenewals);
        try {
          if (typeof window !== 'undefined' && window.storage) {
            await window.storage.set(SEEDED_KEY, 'true');
            await window.storage.set(IDEAS_VERSION_KEY, IDEAS_VERSION);
            await window.storage.set(SEED_VERSION_KEY, SEED_VERSION);
          }
        } catch (e) {}
      } else {
        setTasks(loadedTasks);
        setIdeas(loadedIdeas);
        setMails(loadedMails);
        setRenewals(loadedRenewals);
      }

      setLoading(false);
    })();
    return () => { cancelled = true; clearTimeout(safety); };
  }, []);

  useEffect(() => {
    if (!loading && typeof window !== 'undefined' && window.storage) {
      try { window.storage.set(TASKS_KEY, JSON.stringify(tasks)).catch(() => {}); } catch (e) {}
    }
  }, [tasks, loading]);
  useEffect(() => {
    if (!loading && typeof window !== 'undefined' && window.storage) {
      try { window.storage.set(IDEAS_KEY, JSON.stringify(ideas)).catch(() => {}); } catch (e) {}
    }
  }, [ideas, loading]);
  useEffect(() => {
    if (!loading && typeof window !== 'undefined' && window.storage) {
      try { window.storage.set(MAILS_KEY, JSON.stringify(mails)).catch(() => {}); } catch (e) {}
    }
  }, [mails, loading]);
  useEffect(() => {
    if (!loading && typeof window !== 'undefined' && window.storage) {
      try { window.storage.set(RENEWALS_KEY, JSON.stringify(renewals)).catch(() => {}); } catch (e) {}
    }
  }, [renewals, loading]);
  useEffect(() => {
    if (!loading && typeof window !== 'undefined' && window.storage) {
      try { window.storage.set(THEME_KEY, themeName).catch(() => {}); } catch (e) {}
    }
  }, [themeName, loading]);
  useEffect(() => {
    if (!loading && typeof window !== 'undefined' && window.storage) {
      try { window.storage.set(PALETTES_KEY, JSON.stringify(palettes)).catch(() => {}); } catch (e) {}
    }
  }, [palettes, loading]);

  // Pull the mail subjects feed from Dropbox once the app is ready, and again
  // every time the app regains focus (so opening the installed app refreshes it).
  useEffect(() => {
    if (loading) return;
    let cancelled = false;
    const loadFeed = async () => {
      try {
        const sep = MAIL_FEED_URL.includes('?') ? '&' : '?';
        const res = await fetch(`${MAIL_FEED_URL}${sep}t=${Date.now()}`, { cache: 'no-store' });
        if (!res.ok) return;
        const data = await res.json();
        if (cancelled || !data || !Array.isArray(data.mails)) return;
        const mapped = data.mails
          .map((m, i) => ({
            id: `feed-${i}`,
            text: typeof m === 'string' ? m : (m.subject || m.text || ''),
            urgent: !!(m && m.urgent),
          }))
          .filter((m) => m.text);
        setMails(mapped);
        setMailUpdatedAt(data.updatedAt || null);
      } catch (e) {}
    };
    loadFeed();
    const onVis = () => { if (document.visibilityState === 'visible') loadFeed(); };
    document.addEventListener('visibilitychange', onVis);
    return () => { cancelled = true; document.removeEventListener('visibilitychange', onVis); };
  }, [loading]);

  // Cycle the palette of a given view to the next one in PALETTES
  const cyclePalette = (viewName) => {
    const currentId = palettes[viewName] || DEFAULT_PALETTES[viewName];
    const idx = PALETTES.findIndex(p => p.id === currentId);
    const nextIdx = (idx + 1) % PALETTES.length;
    setPalettes({ ...palettes, [viewName]: PALETTES[nextIdx].id });
  };

  const toggleTheme = () => setThemeName(themeName === 'psycho' ? 'lsd' : 'psycho');

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: '#7BBFC0' }}>
        <div className="text-white/70 text-sm">טוען...</div>
      </div>
    );
  }

  return (
    <div
      dir="rtl"
      className="min-h-screen relative"
      style={{
        background: theme.pageBgGradient,
        fontFamily: theme.fontFamily,
        color: theme.bodyText,
      }}
    >
      <style>{`
        *::-webkit-scrollbar { width: 0px; height: 0px; background: transparent; }
        * { scrollbar-width: none; -ms-overflow-style: none; }
      `}</style>
      <AnimatePresence mode="wait">
        {view === 'home' && (
          <motion.div key="home" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.25 }}>
            <HomeView tasks={tasks} ideas={ideas} mails={mails} renewals={renewals} onOpen={navigateTo} theme={theme} palettes={palettes} onOpenPalettePicker={() => setPaletteModalOpen(true)} />
          </motion.div>
        )}
        {view === 'tasks' && (
          <motion.div key="tasks" initial={{ x: '-100%' }} animate={{ x: 0 }} exit={{ x: '-100%' }} transition={{ type: 'spring', damping: 30, stiffness: 300 }}>
            <TasksView tasks={tasks} setTasks={setTasks} theme={theme} paletteId={palettes.tasks} />
          </motion.div>
        )}
        {view === 'ideas' && (
          <motion.div key="ideas" initial={{ x: '-100%' }} animate={{ x: 0 }} exit={{ x: '-100%' }} transition={{ type: 'spring', damping: 30, stiffness: 300 }}>
            <IdeasView ideas={ideas} setIdeas={setIdeas} theme={theme} paletteId={palettes.ideas} />
          </motion.div>
        )}
        {view === 'mail' && (
          <motion.div key="mail" initial={{ x: '-100%' }} animate={{ x: 0 }} exit={{ x: '-100%' }} transition={{ type: 'spring', damping: 30, stiffness: 300 }}>
            <PlaceholderView title="סקירת מיילים" subtitle={mailUpdatedAt ? `עודכן ${new Date(mailUpdatedAt).toLocaleString('he-IL', { hour: '2-digit', minute: '2-digit' })}` : 'נושאי המיילים האחרונים'} theme={theme} pill={theme.pill3} items={mails} paletteId={palettes.mail} />
          </motion.div>
        )}
        {view === 'renewals' && (
          <motion.div key="renewals" initial={{ x: '-100%' }} animate={{ x: 0 }} exit={{ x: '-100%' }} transition={{ type: 'spring', damping: 30, stiffness: 300 }}>
            <IdeasView ideas={renewals} setIdeas={setRenewals} theme={theme} paletteId={palettes.renewals} title="חידושים תקופתיים" addPlaceholder="חידוש חדש..." undoKey={RENEWALS_UNDO_KEY} />
          </motion.div>
        )}
      </AnimatePresence>

      {/* Bottom nav - hidden on home, shows back on subviews */}
      <BottomNav
        view={view}
        onBack={() => navigateTo('home')}
        paletteId={palettes[view]}
      />

      {/* Palette Picker modal - opens from the palette button on the Home view */}
      <PalettePicker
        open={paletteModalOpen}
        onClose={() => setPaletteModalOpen(false)}
        palettes={palettes}
        onCycle={cyclePalette}
      />
    </div>
  );
}

// ========== PALETTE PICKER ==========
function PalettePicker({ open, onClose, palettes, onCycle }) {
  const categories = [
    { key: 'tasks',    label: 'משימות' },
    { key: 'mail',     label: 'מיילים' },
    { key: 'ideas',    label: 'רעיונות' },
    { key: 'renewals', label: 'חידושים' },
  ];

  // Card background: gradient that ends noticeably darker at the bottom so
  // white text placed there stays readable. For LSD, vivid diagonal.
  const renderCardBg = (paletteId) => {
    const p = getPalette(paletteId);
    if (p.lsd) {
      return 'linear-gradient(120deg, #FF6BCB 0%, #9B5DE5 50%, #5B21B6 100%)';
    }
    const { light, dark } = p;
    // Darken the dark end further for text contrast
    const darker = {
      r: Math.round(dark.r * 0.7),
      g: Math.round(dark.g * 0.7),
      b: Math.round(dark.b * 0.7),
    };
    return `linear-gradient(105deg, rgb(${light.r},${light.g},${light.b}) 0%, rgb(${dark.r},${dark.g},${dark.b}) 55%, rgb(${darker.r},${darker.g},${darker.b}) 100%)`;
  };

  // Small color dot to represent a palette in the indicator row
  const dotColor = (p) => {
    if (p.lsd) return 'linear-gradient(135deg, #FF6BCB, #9B5DE5)';
    return `rgb(${p.dark.r},${p.dark.g},${p.dark.b})`;
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-40"
            style={{ background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(6px)', WebkitBackdropFilter: 'blur(6px)' }}
          />
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 32, stiffness: 340 }}
            className="fixed left-0 right-0 bottom-0 z-50"
          >
            <div
              className="px-5 pt-3 pb-9"
              style={{
                background: '#FFFFFF',
                borderRadius: '32px 32px 0 0',
                boxShadow: '0 -16px 50px rgba(0,0,0,0.2)',
              }}
            >
              {/* Grab handle */}
              <div className="flex justify-center mb-4">
                <div style={{ width: 40, height: 5, borderRadius: 999, background: '#E5E7EB' }} />
              </div>

              <div className="flex items-center justify-between mb-1">
                <h2 className="text-lg" style={{ color: '#111827', fontWeight: 700 }}>עיצוב</h2>
                <button
                  onClick={onClose}
                  className="text-sm px-4 py-1.5 rounded-full active:scale-95 transition-transform"
                  style={{ color: '#374151', background: '#F3F4F6', fontWeight: 600 }}
                >
                  סיום
                </button>
              </div>
              <p className="text-xs mb-5" style={{ color: '#9CA3AF', fontWeight: 500 }}>
                הקש על כרטיסיה כדי לעבור בין הצבעים
              </p>

              <div className="space-y-2.5">
                {categories.map(cat => {
                  const palette = getPalette(palettes[cat.key]);
                  const currentIdx = PALETTES.findIndex(p => p.id === palette.id);
                  return (
                    <button
                      key={cat.key}
                      onClick={() => onCycle(cat.key)}
                      className="w-full text-right px-4 py-3.5 active:scale-[0.98] transition-transform relative overflow-hidden"
                      style={{
                        background: renderCardBg(palettes[cat.key]),
                        borderRadius: '18px',
                        boxShadow: '0 2px 10px rgba(0,0,0,0.08)',
                      }}
                    >
                      <div className="flex items-center justify-between">
                        <div className="text-right">
                          <div className="text-base" style={{ color: '#FFFFFF', fontWeight: 700, textShadow: '0 1px 3px rgba(0,0,0,0.35)' }}>
                            {cat.label}
                          </div>
                          <div className="text-xs mt-0.5" style={{ color: 'rgba(255,255,255,0.9)', fontWeight: 500, textShadow: '0 1px 3px rgba(0,0,0,0.35)' }}>
                            {palette.name}
                          </div>
                        </div>
                        {/* Palette dots showing all options, current one highlighted */}
                        <div className="flex items-center gap-1.5">
                          {PALETTES.map((p, i) => (
                            <div
                              key={p.id}
                              style={{
                                width: i === currentIdx ? 12 : 7,
                                height: i === currentIdx ? 12 : 7,
                                borderRadius: 999,
                                background: dotColor(p),
                                border: i === currentIdx ? '2px solid rgba(255,255,255,0.95)' : '1px solid rgba(255,255,255,0.5)',
                                boxShadow: i === currentIdx ? '0 0 0 1px rgba(0,0,0,0.1)' : 'none',
                                transition: 'all 0.2s',
                              }}
                            />
                          ))}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}

// ========== BOTTOM NAV (CAPSULE STYLE) ==========
function BottomNav({ view, onBack, paletteId }) {
  const isHome = view === 'home';
  const palette = getPalette(paletteId);

  // Hide on home
  if (isHome) return null;

  // Compute palette colors
  const darkRgb = palette.lsd
    ? { r: 155, g: 93, b: 229 }
    : palette.dark;
  const lightRgb = palette.lsd
    ? { r: 255, g: 107, b: 203 }
    : palette.light;

  const darkColor = `rgb(${darkRgb.r},${darkRgb.g},${darkRgb.b})`;
  const lightColor = `rgb(${lightRgb.r},${lightRgb.g},${lightRgb.b})`;

  return (
    <div className="fixed left-0 right-0 bottom-0 z-30 pb-4 pt-3 px-4 flex justify-center pointer-events-none">
      <button
        onClick={onBack}
        aria-label="חזור"
        className="pointer-events-auto w-14 h-14 rounded-full flex items-center justify-center active:scale-90 transition-transform"
        style={{
          background: lightColor,
          boxShadow: `0 6px 18px ${darkColor}55, 0 2px 4px rgba(0,0,0,0.1)`,
        }}
      >
        <ChevronRight size={26} strokeWidth={2.4} style={{ color: darkColor }} />
      </button>
    </div>
  );
}

function NavSlot({ show, children }) {
  return (
    <AnimatePresence initial={false}>
      {show && (
        <motion.div
          initial={{ scale: 0, width: 0, opacity: 0 }}
          animate={{ scale: 1, width: 'auto', opacity: 1 }}
          exit={{ scale: 0, width: 0, opacity: 0 }}
          transition={{ type: 'spring', damping: 22, stiffness: 350 }}
          style={{ overflow: 'hidden' }}
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function NavButton({ onClick, ariaLabel, children, variant = 'ghost', bgColor }) {
  const isPrimary = variant === 'primary';
  return (
    <button
      onClick={onClick}
      aria-label={ariaLabel}
      className="w-12 h-12 rounded-full flex items-center justify-center active:scale-90 transition-transform"
      style={{
        background: isPrimary ? (bgColor || '#FFFFFF') : 'transparent',
        boxShadow: isPrimary ? '0 4px 12px rgba(0,0,0,0.15)' : 'none',
      }}
    >
      {children}
    </button>
  );
}

// ========== ADD MODAL ==========
function AddModal({ open, onClose, onSubmit, placeholder, theme }) {
  const [text, setText] = useState('');
  const inputRef = useRef(null);

  useEffect(() => {
    if (open && inputRef.current) {
      // Slight delay so the animation completes before focus
      setTimeout(() => inputRef.current && inputRef.current.focus(), 50);
    }
    if (!open) setText('');
  }, [open]);

  const handleSubmit = () => {
    const t = text.trim();
    if (!t) return;
    onSubmit(t);
    setText('');
    onClose();
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-40"
            style={{ background: 'rgba(0,0,0,0.4)', backdropFilter: 'blur(2px)' }}
          />
          {/* Sheet */}
          <motion.div
            initial={{ y: 200, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 200, opacity: 0 }}
            transition={{ type: 'spring', damping: 28, stiffness: 320 }}
            className="fixed left-4 right-4 bottom-28 z-50"
          >
            <div
              className="p-4"
              style={{
                background: '#FFFFFF',
                borderRadius: '24px',
                boxShadow: '0 20px 40px rgba(0,0,0,0.25)',
              }}
            >
              <div className="flex items-center gap-2">
                <input
                  ref={inputRef}
                  type="text"
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleSubmit();
                    if (e.key === 'Escape') onClose();
                  }}
                  placeholder={placeholder}
                  dir="rtl"
                  className="flex-1 bg-transparent text-base outline-none px-2"
                  style={{ color: '#1F2937' }}
                />
                <button
                  onClick={handleSubmit}
                  disabled={!text.trim()}
                  className="flex items-center justify-center w-10 h-10 rounded-full text-white transition active:scale-95"
                  style={{
                    background: text.trim() ? theme.navAccent : '#E5E7EB',
                    boxShadow: text.trim() ? `0 4px 12px ${theme.navAccent}55` : 'none',
                  }}
                >
                  <Plus size={22} strokeWidth={2.8} />
                </button>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}

// ========== HOME ==========
function HomeView({ tasks, ideas, mails, renewals, onOpen, theme, palettes, onOpenPalettePicker }) {
  const now = new Date();
  const dayNames = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];
  const monthNames = ['ינואר', 'פברואר', 'מרץ', 'אפריל', 'מאי', 'יוני', 'יולי', 'אוגוסט', 'ספטמבר', 'אוקטובר', 'נובמבר', 'דצמבר'];
  const dateStr = `יום ${dayNames[now.getDay()]}, ${now.getDate()} ב${monthNames[now.getMonth()]}`;

  const activeTasks = tasks.filter(t => !t.done);

  // Urgent items - demo data for now (so the card shows it pulls from several
  // sources). TODO: when sharing the app, switch back to computing from real state.
  const urgentItems = [
    { text: 'לסיים את הצעת המחיר ללקוח', source: 'משימה' },
    { text: 'לחזור לרואה החשבון עד מחר', source: 'משימה' },
    { text: 'מייל דחוף מיוסי - ממתין לתשובה', source: 'מייל' },
    { text: 'אישור הזמנה - נדרשת חתימה', source: 'מייל' },
    { text: 'דרכון - חידוש בעוד שבועיים', source: 'חידוש' },
    { text: 'ביטוח רכב - מתחדש ב-15.7', source: 'חידוש' },
  ];

  const previewTasks = activeTasks.map(t => t.text);
  const previewIdeas = ideas.map(t => t.text);
  const previewMails = mails.map(m => m.text);
  const previewRenewals = renewals.map(r => r.text);

  return (
    <div className="min-h-screen flex flex-col pb-32" style={{ background: '#F8F9FA' }}>
      {/* Header with palette button on the left */}
      <div className="px-6 pt-10 pb-5 flex items-start justify-between">
        <div>
          <h1 className="text-3xl tracking-wide" style={{ color: '#1F2937', fontWeight: 700 }}>ברוך הבא</h1>
          <p className="text-sm mt-1" style={{ color: '#6B7280', fontWeight: 500 }}>{dateStr}</p>
        </div>
        <button
          onClick={onOpenPalettePicker}
          aria-label="עיצוב"
          className="w-11 h-11 rounded-full flex items-center justify-center active:scale-90 transition-transform"
          style={{
            background: '#FFFFFF',
            boxShadow: '0 4px 12px rgba(0,0,0,0.08), 0 1px 3px rgba(0,0,0,0.04)',
            border: '1px solid rgba(0,0,0,0.04)',
          }}
        >
          <Palette size={20} strokeWidth={2} style={{ color: '#1F2937' }} />
        </button>
      </div>

      {/* Cards - asymmetric padding: more on right for thumb-scroll area */}
      <div className="flex-1 space-y-4" style={{ paddingRight: '48px', paddingLeft: '16px' }}>
        {/* Urgent card */}
        <UrgentCard items={urgentItems} theme={theme} />

        <PreviewCard
          cardId="card-tasks"
          title="משימות"
          countLabel={`${activeTasks.length} פעילות`}
          icon={<CheckCircle2 size={22} strokeWidth={2} />}
          paletteId={palettes.tasks}
          items={previewTasks}
          emptyMessage="אין משימות פעילות"
          onClick={() => onOpen('tasks')}
        />
        <PreviewCard
          cardId="card-mail"
          title="מיילים"
          countLabel={`${previewMails.length} נושאים`}
          icon={<Mail size={22} strokeWidth={2} />}
          paletteId={palettes.mail}
          items={previewMails}
          onClick={() => onOpen('mail')}
        />
        <PreviewCard
          cardId="card-ideas"
          title="רעיונות"
          countLabel={`${ideas.length} פרויקטים`}
          icon={<Lightbulb size={22} strokeWidth={2} />}
          paletteId={palettes.ideas}
          items={previewIdeas}
          emptyMessage="אין רעיונות"
          onClick={() => onOpen('ideas')}
        />
        <PreviewCard
          cardId="card-renewals"
          title="חידושים"
          countLabel={`${previewRenewals.length} קרובים (דוגמה)`}
          icon={<RefreshCw size={22} strokeWidth={2} />}
          paletteId={palettes.renewals}
          items={previewRenewals}
          isDemo
          onClick={() => onOpen('renewals')}
        />
      </div>
    </div>
  );
}

// ========== URGENT CARD (display-only, not clickable) ==========
function UrgentCard({ items, theme }) {
  // Urgent uses a distinctive red/orange accent for visual priority
  const urgentAccent = theme === THEMES.psycho ? '#EF4444' : '#FF3D7F';
  const urgentBg = theme === THEMES.psycho ? '#FEE2E2' : '#FFE0EC';

  return (
    <div
      className="w-full text-right p-5 block select-none"
      style={{
        background: theme.cardBg,
        border: `2px solid ${urgentAccent}`,
        borderRadius: theme.cardRadius,
        boxShadow: `0 8px 24px ${urgentAccent}33, 0 2px 6px rgba(0,0,0,0.08)`,
      }}
    >
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <div
            className="w-11 h-11 flex items-center justify-center flex-shrink-0"
            style={{
              background: urgentAccent,
              color: '#FFFFFF',
              borderRadius: theme.pillRadius,
              boxShadow: `0 4px 12px ${urgentAccent}66`,
            }}
          >
            <span style={{ fontSize: '22px', fontWeight: 700 }}>!</span>
          </div>
          <div>
            <div className="text-lg" style={{ color: urgentAccent, fontWeight: 800 }}>דחוף</div>
            <div className="text-xs mt-0.5" style={{ color: theme.cardSubText, fontWeight: 500 }}>
              {items.length === 0 ? 'אין פריטים דחופים' : `${items.length} פריטים זקוקים לתשומת לב`}
            </div>
          </div>
        </div>
      </div>

      <div
        className="space-y-2 overflow-y-auto"
        style={{
          maxHeight: '180px',
          touchAction: 'pan-y',
          WebkitOverflowScrolling: 'touch',
          scrollbarWidth: 'none',
        }}
      >
        {items.length === 0 ? (
          <div
            className="text-sm text-center py-4"
            style={{ color: theme.cardSubText, background: urgentBg, borderRadius: '12px', opacity: 0.8 }}
          >
            הכל בסדר! אין פריטים דחופים כרגע
          </div>
        ) : (
          items.map((item, idx) => (
            <div
              key={idx}
              className="text-sm px-3 py-2.5 flex items-center gap-2"
              style={{
                background: urgentBg,
                color: theme.previewText,
                borderRadius: '10px',
                fontWeight: 500,
              }}
            >
              <span style={{ color: urgentAccent, fontWeight: 700, fontSize: '11px' }}>● {item.source}</span>
              <span className="truncate flex-1 mr-1">{item.text}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function PreviewCard({ cardId, title, countLabel, icon, paletteId, items, emptyMessage, isDemo, onClick }) {
  // Get palette for this card
  const palette = getPalette(paletteId);

  // Card background: vertical gradient (light → dark, since text-RTL flows top-down)
  // Or rotating colors for LSD
  let cardBg, textColor, accentColor, secondaryTextColor, itemBg, itemTextColor;
  if (palette.lsd) {
    // LSD: vivid pink/purple gradient
    cardBg = 'linear-gradient(180deg, #9B5DE5 0%, #FF6BCB 100%)';
    textColor = '#FFFFFF';
    accentColor = '#FFD93D';
    secondaryTextColor = 'rgba(255,255,255,0.85)';
    itemBg = 'rgba(255,255,255,0.15)';
    itemTextColor = '#FFFFFF';
  } else {
    const { dark, light } = palette;
    cardBg = `linear-gradient(180deg, rgb(${dark.r},${dark.g},${dark.b}) 0%, rgb(${light.r},${light.g},${light.b}) 100%)`;
    textColor = '#FFFFFF';
    accentColor = `rgb(${light.r},${light.g},${light.b})`;
    secondaryTextColor = 'rgba(255,255,255,0.85)';
    itemBg = 'rgba(255,255,255,0.18)';
    itemTextColor = '#FFFFFF';
  }

  // Tap vs scroll detection
  const pointerStartRef = useRef({ x: 0, y: 0, time: 0 });
  const movedRef = useRef(false);

  const handlePointerDown = (e) => {
    pointerStartRef.current = { x: e.clientX, y: e.clientY, time: Date.now() };
    movedRef.current = false;
  };

  const handlePointerMove = (e) => {
    const dx = Math.abs(e.clientX - pointerStartRef.current.x);
    const dy = Math.abs(e.clientY - pointerStartRef.current.y);
    if (dx > 6 || dy > 6) movedRef.current = true;
  };

  const handlePointerUp = () => {
    const elapsed = Date.now() - pointerStartRef.current.time;
    if (!movedRef.current && elapsed < 400) {
      onClick();
    }
  };

  const showItems = items.length > 0;

  return (
    <motion.div
      id={cardId}
      whileTap={{ scale: 0.985 }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      className="w-full text-right p-5 block cursor-pointer select-none"
      style={{
        background: cardBg,
        borderRadius: '24px',
        boxShadow: '0 8px 24px rgba(0,0,0,0.12), 0 2px 6px rgba(0,0,0,0.06)',
        color: textColor,
      }}
    >
      {/* Header row */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <div
            className="w-11 h-11 flex items-center justify-center flex-shrink-0"
            style={{
              background: 'rgba(255,255,255,0.25)',
              color: '#FFFFFF',
              borderRadius: '50%',
              backdropFilter: 'blur(8px)',
            }}
          >
            {icon}
          </div>
          <div>
            <div className="text-lg" style={{ color: textColor, fontWeight: 700 }}>{title}</div>
            <div className="text-xs mt-0.5" style={{ color: secondaryTextColor, fontWeight: 500 }}>{countLabel}</div>
          </div>
        </div>
      </div>

      {/* Inner items - vertically scrollable */}
      <div
        className="space-y-2 overflow-y-auto"
        style={{
          maxHeight: '180px',
          touchAction: 'pan-y',
          WebkitOverflowScrolling: 'touch',
          scrollbarWidth: 'none',
        }}
        onPointerDown={(e) => e.stopPropagation()}
        onPointerMove={(e) => e.stopPropagation()}
        onPointerUp={(e) => e.stopPropagation()}
        onClick={() => onClick()}
      >
        {!showItems ? (
          <div
            className="text-sm text-center py-4"
            style={{ color: secondaryTextColor, background: itemBg, borderRadius: '12px', opacity: 0.9 }}
          >
            {emptyMessage}
          </div>
        ) : (
          items.map((text, idx) => (
            <div
              key={idx}
              className="text-sm px-3 py-2.5 truncate flex items-center gap-2"
              style={{ background: itemBg, color: itemTextColor, borderRadius: '10px', fontWeight: 500 }}
            >
              <span style={{ color: '#FFFFFF', fontWeight: 700, opacity: 0.7 }}>•</span>
              <span className="truncate">{text}</span>
            </div>
          ))
        )}
        {isDemo && (
          <div className="text-xs text-center pt-1" style={{ color: secondaryTextColor, opacity: 0.7, fontWeight: 500 }}>
            דוגמה - יתחבר לנתונים אמיתיים בקרוב
          </div>
        )}
      </div>
    </motion.div>
  );
}

// ========== TASKS ==========
function TasksView({ tasks, setTasks, theme, paletteId }) {
  const [editingId, setEditingId] = useState(null);
  const [editText, setEditText] = useState('');
  const [lastDeleted, setLastDeleted] = useState(null);
  const [pullDistance, setPullDistance] = useState(0);
  const [showAddInput, setShowAddInput] = useState(false);
  const [newTaskText, setNewTaskText] = useState('');
  const [reminderForId, setReminderForId] = useState(null);
  const editRef = useRef(null);
  const addInputRef = useRef(null);
  const scrollRef = useRef(null);
  const pullStartY = useRef(null);
  const undoTimerRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(TASKS_UNDO_KEY);
          if (!cancelled && r && r.value) { try { setLastDeleted(JSON.parse(r.value)); } catch (e) {} }
        }
      } catch (e) {}
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (typeof window !== 'undefined' && window.storage) {
      try {
        if (lastDeleted) window.storage.set(TASKS_UNDO_KEY, JSON.stringify(lastDeleted)).catch(() => {});
        else window.storage.delete(TASKS_UNDO_KEY).catch(() => {});
      } catch (e) {}
    }
  }, [lastDeleted]);

  useEffect(() => { if (editingId && editRef.current) editRef.current.focus(); }, [editingId]);
  useEffect(() => { if (showAddInput && addInputRef.current) setTimeout(() => addInputRef.current?.focus(), 50); }, [showAddInput]);

  const saveEdit = () => {
    if (!editingId) return;
    const text = editText.trim();
    if (!text) { setEditingId(null); setEditText(''); return; }
    setTasks(tasks.map(t => t.id === editingId ? { ...t, text } : t));
    setEditingId(null); setEditText(''); setLastDeleted(null);
  };
  const toggleDone = (id) => { setTasks(tasks.map(t => t.id === id ? { ...t, done: !t.done } : t)); setLastDeleted(null); };
  const deleteTask = (id, y) => {
    const idx = tasks.findIndex(t => t.id === id);
    if (idx === -1) return;
    setLastDeleted({ task: tasks[idx], index: idx, y: y || (window.innerHeight / 2) });
    setTasks(tasks.filter(t => t.id !== id));
    if (undoTimerRef.current) clearTimeout(undoTimerRef.current);
    undoTimerRef.current = setTimeout(() => setLastDeleted(null), 2500);
  };
  const undo = () => {
    if (!lastDeleted) return;
    if (undoTimerRef.current) clearTimeout(undoTimerRef.current);
    const newTasks = [...tasks];
    // Tag the restored task so TaskRow can animate it sliding back in from the left
    const restored = { ...lastDeleted.task, _justRestored: true };
    newTasks.splice(lastDeleted.index, 0, restored);
    setTasks(newTasks); setLastDeleted(null);
    // Clear the tag shortly after so it doesn't persist
    setTimeout(() => {
      setTasks(curr => curr.map(t => t.id === restored.id ? (() => { const { _justRestored, ...rest } = t; return rest; })() : t));
    }, 1600);
  };
  const submitNewTask = () => {
    const text = newTaskText.trim();
    if (!text) { setShowAddInput(false); return; }
    setTasks([{ id: Date.now() + '' + Math.random().toString(36).slice(2), text, done: false }, ...tasks]);
    setNewTaskText('');
    setShowAddInput(false);
    setLastDeleted(null);
  };

  // Apply selected palette - if 'lsd', switch to LSD rotating mode; otherwise use that palette's gradient
  const palette = getPalette(paletteId);
  let viewTheme;
  if (palette.lsd) {
    viewTheme = THEMES.lsd;
  } else {
    viewTheme = { ...THEMES.psycho, rowColors: { dark: palette.dark, light: palette.light } };
  }

  // First row color = page background (only relevant for Clear/gradient mode)
  const isClearMode = viewTheme.taskRowMode === 'gradient';
  const firstColor = isClearMode && viewTheme.rowColors ? viewTheme.rowColors.dark : null;
  const clearPageBg = firstColor
    ? `rgb(${firstColor.r}, ${firstColor.g}, ${firstColor.b})`
    : viewTheme.pageBg;

  // Pull-to-add handlers.
  // Only triggers when the finger starts at the very top of an already-settled list
  // AND the user makes a long, deliberate downward pull. This avoids accidental
  // adds when the user is just scrolling back up to the top.
  const PULL_THRESHOLD = 110; // long pull required
  const handleTouchStart = (e) => {
    // Ignore touches that start in the right-edge handle zone (used for reorder)
    const touchX = e.touches[0].clientX;
    const screenW = window.innerWidth || 400;
    if (touchX > screenW - 44) {
      pullStartY.current = null;
      return;
    }
    // Arm pull-to-add only when this fresh touch lands while the list is at the very top.
    // (First entry: scrollTop is 0 → a single downward pull works immediately.
    //  After scrolling down then back up: the touch that scrolled started below the
    //  top, so it isn't armed; you must lift and place a new finger at the top.)
    if (scrollRef.current && scrollRef.current.scrollTop <= 0) {
      pullStartY.current = e.touches[0].clientY;
    } else {
      pullStartY.current = null;
    }
  };
  const handleTouchMove = (e) => {
    if (pullStartY.current === null) return;
    // If content scrolled away from the top during this gesture, cancel and disarm.
    if (scrollRef.current && scrollRef.current.scrollTop > 0) {
      pullStartY.current = null;
      setPullDistance(0);
      return;
    }
    const dy = e.touches[0].clientY - pullStartY.current;
    if (dy > 0) {
      // Apply resistance so it feels deliberate
      setPullDistance(Math.min(dy * 0.7, 150));
    }
  };
  const handleTouchEnd = () => {
    if (pullDistance > PULL_THRESHOLD) {
      setShowAddInput(true);
    }
    setPullDistance(0);
    pullStartY.current = null;
  };

  return (
    <div className="min-h-screen flex flex-col" style={{ background: isClearMode ? clearPageBg : viewTheme.pageBgGradient }}>
      <ViewHeader title="משימות" subtitle={`${tasks.filter(t => !t.done).length} פעילות מתוך ${tasks.length}`} theme={viewTheme} />

      {/* Pull-to-add indicator */}
      {pullDistance > 0 && !showAddInput && (
        <div
          className="flex items-center justify-center overflow-hidden"
          style={{
            height: `${pullDistance}px`,
            background: 'rgba(255,255,255,0.15)',
            color: '#FFFFFF',
            fontSize: '13px',
            fontWeight: 500,
          }}
        >
          {pullDistance > 110 ? '↓ שחרר להוספת משימה' : '↓ המשך למשוך להוספה'}
        </div>
      )}

      {/* Add input overlay */}
      {showAddInput && (
        <div
          className="px-5 py-3"
          style={{ background: 'rgba(255,255,255,0.15)' }}
        >
          <input
            ref={addInputRef}
            type="text"
            value={newTaskText}
            onChange={(e) => setNewTaskText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submitNewTask();
              if (e.key === 'Escape') { setShowAddInput(false); setNewTaskText(''); }
            }}
            onBlur={submitNewTask}
            placeholder="משימה חדשה..."
            dir="rtl"
            className="w-full bg-transparent text-base outline-none"
            style={{ color: '#FFFFFF', fontWeight: 400 }}
          />
        </div>
      )}

      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto pb-32"
        style={{ touchAction: 'pan-y' }}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
      >
        {tasks.length === 0 ? (
          <EmptyState text="אין משימות. משוך מטה להוספה." theme={viewTheme} />
        ) : (
          <Reorder.Group axis="y" values={tasks} onReorder={(newOrder) => { setTasks(newOrder); setLastDeleted(null); }} className="flex flex-col">
            {tasks.map((task, index) => (
              <TaskRow
                key={task.id}
                task={task}
                index={index}
                totalRows={tasks.length}
                isEditing={editingId === task.id}
                editText={editText}
                editRef={editRef}
                onEditChange={setEditText}
                onEditSave={saveEdit}
                onEditCancel={() => { setEditingId(null); setEditText(''); }}
                onStartEdit={() => { setEditingId(task.id); setEditText(task.text); }}
                onToggleDone={() => toggleDone(task.id)}
                onDelete={(y) => deleteTask(task.id, y)}
                onReminder={() => setReminderForId(task.id)}
                theme={viewTheme}
              />
            ))}
          </Reorder.Group>
        )}
      </div>

      <FloatingUndo deleted={lastDeleted} onUndo={undo} />
      <ReminderPicker
        open={!!reminderForId}
        onClose={() => setReminderForId(null)}
        accent={clearPageBg}
        onConfirm={(iso) => {
          setTasks(tasks.map(t => t.id === reminderForId ? { ...t, reminder: iso } : t));
          setReminderForId(null);
        }}
        onReset={() => {
          setTasks(tasks.map(t => t.id === reminderForId ? { ...t, reminder: null } : t));
          setReminderForId(null);
        }}
      />
    </div>
  );
}

// ========== FLOATING UNDO ==========
function FloatingUndo({ deleted, onUndo }) {
  const show = !!deleted;
  const BTN = 48;
  // Center the button on the row by subtracting half its height (framer animates
  // `scale` via transform, which would otherwise clobber a translateY(-50%)).
  const y = deleted ? deleted.y - BTN / 2 : 0;
  return (
    <AnimatePresence>
      {show && (
        <motion.button
          initial={{ opacity: 0, scale: 0.5, x: -40 }}
          animate={{
            opacity: [0, 1, 1, 1, 0.4],
            scale: [0.5, 1, 1, 1, 0.92],
            x: [-40, 0, 0, 0, 0],
          }}
          exit={{ opacity: 0, scale: 0.5, x: -40, transition: { duration: 0.22 } }}
          transition={{
            duration: 2.5,
            times: [0, 0.12, 0.6, 0.78, 1],
            ease: 'easeInOut',
          }}
          onClick={onUndo}
          className="fixed z-40 flex items-center justify-center active:scale-90"
          style={{
            top: `${y}px`,
            left: '20px',
            width: BTN,
            height: BTN,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.92)',
            backdropFilter: 'blur(12px)',
            WebkitBackdropFilter: 'blur(12px)',
            boxShadow: '0 6px 20px rgba(0,0,0,0.18), 0 1px 3px rgba(0,0,0,0.1)',
            border: '1px solid rgba(255,255,255,0.6)',
          }}
          aria-label="בטל מחיקה"
        >
          <div className="relative flex items-center justify-center" style={{ width: 24, height: 24 }}>
            <Trash2 size={22} strokeWidth={2.2} style={{ color: '#374151' }} />
            <svg
              width="28" height="28" viewBox="0 0 28 28"
              style={{ position: 'absolute', top: -2, left: -2, pointerEvents: 'none' }}
            >
              <line x1="5" y1="23" x2="23" y2="5" stroke="#374151" strokeWidth="2.4" strokeLinecap="round" />
            </svg>
          </div>
        </motion.button>
      )}
    </AnimatePresence>
  );
}

// ========== IDEAS ==========
function IdeasView({ ideas, setIdeas, theme, paletteId, title = 'רעיונות', addPlaceholder = 'רעיון חדש...', undoKey = IDEAS_UNDO_KEY }) {
  const [editingId, setEditingId] = useState(null);
  const [editText, setEditText] = useState('');
  const [lastDeleted, setLastDeleted] = useState(null);
  const [pullDistance, setPullDistance] = useState(0);
  const [showAddInput, setShowAddInput] = useState(false);
  const [newText, setNewText] = useState('');
  const [reminderForId, setReminderForId] = useState(null);
  const editRef = useRef(null);
  const addInputRef = useRef(null);
  const scrollRef = useRef(null);
  const pullStartY = useRef(null);
  const undoTimerRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        if (typeof window !== 'undefined' && window.storage) {
          const r = await window.storage.get(undoKey);
          if (!cancelled && r && r.value) { try { setLastDeleted(JSON.parse(r.value)); } catch (e) {} }
        }
      } catch (e) {}
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (typeof window !== 'undefined' && window.storage) {
      try {
        if (lastDeleted) window.storage.set(undoKey, JSON.stringify(lastDeleted)).catch(() => {});
        else window.storage.delete(undoKey).catch(() => {});
      } catch (e) {}
    }
  }, [lastDeleted]);

  useEffect(() => { if (editingId && editRef.current) editRef.current.focus(); }, [editingId]);
  useEffect(() => { if (showAddInput && addInputRef.current) setTimeout(() => addInputRef.current?.focus(), 50); }, [showAddInput]);

  const saveEdit = () => {
    if (!editingId) return;
    const text = editText.trim();
    if (!text) { setEditingId(null); setEditText(''); return; }
    setIdeas(ideas.map(t => t.id === editingId ? { ...t, text } : t));
    setEditingId(null); setEditText(''); setLastDeleted(null);
  };
  const toggleDone = (id) => { setIdeas(ideas.map(t => t.id === id ? { ...t, done: !t.done } : t)); setLastDeleted(null); };
  const deleteIdea = (id, y) => {
    const idx = ideas.findIndex(t => t.id === id);
    if (idx === -1) return;
    setLastDeleted({ task: ideas[idx], index: idx, y: y || (window.innerHeight / 2) });
    setIdeas(ideas.filter(t => t.id !== id));
    if (undoTimerRef.current) clearTimeout(undoTimerRef.current);
    undoTimerRef.current = setTimeout(() => setLastDeleted(null), 2500);
  };
  const undo = () => {
    if (!lastDeleted) return;
    if (undoTimerRef.current) clearTimeout(undoTimerRef.current);
    const newIdeas = [...ideas];
    const restored = { ...lastDeleted.task, _justRestored: true };
    newIdeas.splice(lastDeleted.index, 0, restored);
    setIdeas(newIdeas); setLastDeleted(null);
    setTimeout(() => {
      setIdeas(curr => curr.map(t => t.id === restored.id ? (() => { const { _justRestored, ...rest } = t; return rest; })() : t));
    }, 1600);
  };
  const submitNew = () => {
    const text = newText.trim();
    if (!text) { setShowAddInput(false); return; }
    setIdeas([{ id: Date.now() + '' + Math.random().toString(36).slice(2), text, done: false }, ...ideas]);
    setNewText('');
    setShowAddInput(false);
    setLastDeleted(null);
  };

  // Apply selected palette
  const palette = getPalette(paletteId);
  let viewTheme;
  if (palette.lsd) {
    viewTheme = THEMES.lsd;
  } else {
    viewTheme = { ...THEMES.psycho, rowColors: { dark: palette.dark, light: palette.light } };
  }

  const isClearMode = viewTheme.taskRowMode === 'gradient';
  const firstColor = isClearMode && viewTheme.rowColors ? viewTheme.rowColors.dark : null;
  const clearPageBg = firstColor
    ? `rgb(${firstColor.r}, ${firstColor.g}, ${firstColor.b})`
    : viewTheme.pageBg;

  const PULL_THRESHOLD = 110;
  const handleTouchStart = (e) => {
    const touchX = e.touches[0].clientX;
    const screenW = window.innerWidth || 400;
    if (touchX > screenW - 44) {
      pullStartY.current = null;
      return;
    }
    if (scrollRef.current && scrollRef.current.scrollTop <= 0) {
      pullStartY.current = e.touches[0].clientY;
    } else {
      pullStartY.current = null;
    }
  };
  const handleTouchMove = (e) => {
    if (pullStartY.current === null) return;
    if (scrollRef.current && scrollRef.current.scrollTop > 0) {
      pullStartY.current = null;
      setPullDistance(0);
      return;
    }
    const dy = e.touches[0].clientY - pullStartY.current;
    if (dy > 0) setPullDistance(Math.min(dy * 0.7, 150));
  };
  const handleTouchEnd = () => {
    if (pullDistance > PULL_THRESHOLD) setShowAddInput(true);
    setPullDistance(0);
    pullStartY.current = null;
  };

  return (
    <div className="min-h-screen flex flex-col" style={{ background: isClearMode ? clearPageBg : viewTheme.pageBgGradient }}>
      <ViewHeader title={title} subtitle={`${ideas.length} פריטים`} theme={viewTheme} />

      {pullDistance > 0 && !showAddInput && (
        <div
          className="flex items-center justify-center overflow-hidden"
          style={{
            height: `${pullDistance}px`,
            background: 'rgba(255,255,255,0.15)',
            color: '#FFFFFF',
            fontSize: '13px',
            fontWeight: 500,
          }}
        >
          {pullDistance > 110 ? '↓ שחרר להוספה' : '↓ המשך למשוך להוספה'}
        </div>
      )}

      {showAddInput && (
        <div className="px-5 py-3" style={{ background: 'rgba(255,255,255,0.15)' }}>
          <input
            ref={addInputRef}
            type="text"
            value={newText}
            onChange={(e) => setNewText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submitNew();
              if (e.key === 'Escape') { setShowAddInput(false); setNewText(''); }
            }}
            onBlur={submitNew}
            placeholder={addPlaceholder}
            dir="rtl"
            className="w-full bg-transparent text-base outline-none"
            style={{ color: '#FFFFFF', fontWeight: 400 }}
          />
        </div>
      )}

      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto pb-32"
        style={{ touchAction: 'pan-y' }}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
      >
        {ideas.length === 0 ? (
          <EmptyState text="אין רעיונות. משוך מטה להוספה." theme={viewTheme} />
        ) : (
          <Reorder.Group axis="y" values={ideas} onReorder={(newOrder) => { setIdeas(newOrder); setLastDeleted(null); }} className="flex flex-col">
            {ideas.map((idea, index) => (
              <TaskRow
                key={idea.id}
                task={idea}
                index={index}
                totalRows={ideas.length}
                isEditing={editingId === idea.id}
                editText={editText}
                editRef={editRef}
                onEditChange={setEditText}
                onEditSave={saveEdit}
                onEditCancel={() => { setEditingId(null); setEditText(''); }}
                onStartEdit={() => { setEditingId(idea.id); setEditText(idea.text); }}
                onToggleDone={() => toggleDone(idea.id)}
                onDelete={(y) => deleteIdea(idea.id, y)}
                onReminder={() => setReminderForId(idea.id)}
                theme={viewTheme}
              />
            ))}
          </Reorder.Group>
        )}
      </div>

      <FloatingUndo deleted={lastDeleted} onUndo={undo} />
      <ReminderPicker
        open={!!reminderForId}
        onClose={() => setReminderForId(null)}
        accent={clearPageBg}
        onConfirm={(iso) => {
          setIdeas(ideas.map(t => t.id === reminderForId ? { ...t, reminder: iso } : t));
          setReminderForId(null);
        }}
        onReset={() => {
          setIdeas(ideas.map(t => t.id === reminderForId ? { ...t, reminder: null } : t));
          setReminderForId(null);
        }}
      />
    </div>
  );
}

// ========== PLACEHOLDER ==========
function PlaceholderView({ title, subtitle, theme, pill, items = [], paletteId }) {
  // Apply selected palette - same logic as TasksView/IdeasView
  const palette = getPalette(paletteId);
  let viewTheme;
  if (palette.lsd) {
    viewTheme = THEMES.lsd;
  } else {
    viewTheme = { ...THEMES.psycho, rowColors: { dark: palette.dark, light: palette.light } };
  }

  // Page background = palette dark color (matches inner views' Clear-style page bg)
  const pageBg = palette.lsd
    ? viewTheme.pageBg
    : `rgb(${palette.dark.r}, ${palette.dark.g}, ${palette.dark.b})`;

  // Pill / accent for icon, derived from palette
  const accentBg = palette.lsd
    ? '#FF6BCB'
    : `rgb(${palette.light.r}, ${palette.light.g}, ${palette.light.b})`;
  const accentIconColor = palette.lsd
    ? '#FFFFFF'
    : `rgb(${palette.dark.r}, ${palette.dark.g}, ${palette.dark.b})`;

  // Item card colors (semi-transparent over the palette page bg)
  const itemBg = 'rgba(255,255,255,0.18)';
  const itemTextColor = '#FFFFFF';

  return (
    <div className="min-h-screen flex flex-col pb-32" style={{ background: pageBg }}>
      <ViewHeader title={title} subtitle={subtitle} theme={viewTheme} />
      <div className="flex-1 px-4 pt-4">
        {items.length > 0 ? (
          <div className="w-full space-y-2 text-right">
            {items.map((item, idx) => (
              <div
                key={idx}
                className="text-sm px-4 py-3 flex items-center gap-2"
                style={{
                  background: itemBg,
                  color: itemTextColor,
                  borderRadius: '12px',
                  fontWeight: 500,
                  backdropFilter: 'blur(8px)',
                }}
              >
                <span style={{ color: accentBg, fontWeight: 700 }}>•</span>
                <span className="truncate">{item.text}</span>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}

// ========== SHARED ==========
function ViewHeader({ title, subtitle, theme }) {
  const isClearMode = theme.taskRowMode === 'gradient';

  // Clear mode: header looks like the darkest row in the list.
  // Same flat dark color as the page background, no separation, just a big bold white title.
  if (isClearMode) {
    return (
      <div className="px-5 pt-7 pb-4">
        <h1 style={{ color: '#FFFFFF', fontWeight: 800, fontSize: '28px', letterSpacing: '0.01em' }}>{title}</h1>
      </div>
    );
  }

  // Non-clear (LSD rotating) mode: keep a simple readable header
  return (
    <div className="px-5 pt-7 pb-4">
      <h1 style={{ color: theme.headerText || '#FFFFFF', fontWeight: 800, fontSize: '28px', letterSpacing: '0.01em' }}>{title}</h1>
    </div>
  );
}

function EmptyState({ text, theme }) {
  return (
    <div className="flex items-center justify-center h-64 px-8">
      <p className="text-center text-sm" style={{ color: theme.headerSubText, fontWeight: 500 }}>{text}</p>
    </div>
  );
}

function UndoBar({ lastDeleted, onUndo, theme }) {
  return (
    <AnimatePresence>
      {lastDeleted && (
        <motion.div
          initial={{ y: 60, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: 60, opacity: 0 }}
          transition={{ type: 'spring', damping: 25, stiffness: 300 }}
          className="fixed left-4 right-4 bottom-36 z-20"
        >
          <div
            className="px-4 py-3 flex items-center justify-between"
            style={{ background: '#FFFFFF', borderRadius: '20px', boxShadow: '0 8px 24px rgba(0,0,0,0.18)' }}
          >
            <span className="text-sm truncate flex-1 ml-3" style={{ color: '#1F2937' }}>
              נמחק: {lastDeleted.task.text}
            </span>
            <button
              onClick={onUndo}
              className="flex items-center gap-1.5 text-sm px-3 py-1 rounded-lg active:scale-95 transition"
              style={{ color: theme.addBtn, background: 'rgba(0,0,0,0.04)', fontWeight: 600 }}
            >
              <Undo2 size={16} />
              בטל
            </button>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function InputBar({ value, onChange, onSubmit, placeholder, theme }) {
  return (
    <div
      className="fixed left-0 right-0 bottom-24 pt-3 pb-2 px-4 z-10"
      style={{ background: `linear-gradient(to top, ${theme.pageBg} 60%, transparent)` }}
    >
      <div
        className="flex items-center gap-2 px-4 py-3"
        style={{
          background: theme.inputBg,
          border: `1px solid ${theme.inputBorder}`,
          borderRadius: '20px',
          boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
        }}
      >
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') onSubmit(); }}
          placeholder={placeholder}
          className="flex-1 bg-transparent text-base outline-none"
          dir="rtl"
          style={{ color: theme.inputText }}
        />
        <button
          onClick={onSubmit}
          disabled={!value.trim()}
          className="flex items-center justify-center w-9 h-9 rounded-xl text-white transition active:scale-95"
          style={{ background: value.trim() ? theme.addBtn : theme.addBtnDisabled }}
        >
          <Plus size={20} />
        </button>
      </div>
    </div>
  );
}

// ========== TASK ROW ==========
function getRowStyle(index, theme, totalRows) {
  if (theme.taskRowMode === 'rotating') {
    const offset = theme.rotatingColorsOffset || 0;
    const colors = theme.rotatingColors;
    const c = colors[(index + offset) % colors.length];
    return {
      bg: c.bg,
      textColor: c.text,
      boxShadow: [
        'inset 0 2px 0 rgba(255,255,255,0.3)',
        'inset 0 -3px 0 rgba(0,0,0,0.18)',
        '0 5px 0 rgba(0,0,0,0.12)',
        '0 8px 16px rgba(0,0,0,0.18)',
      ].join(', '),
      isClear: false,
    };
  } else {
    // Clear-style: each row is flat solid color, but each row is one step lighter
    // The gradient effect comes from row-to-row, not within a row
    const { dark, light } = theme.rowColors;
    // Distribute colors across all rows, so first = dark, last = light
    const denom = Math.max(1, (totalRows || 10) - 1);
    const t = Math.min(index / denom, 1);
    const r = Math.round(dark.r + (light.r - dark.r) * t);
    const g = Math.round(dark.g + (light.g - dark.g) * t);
    const b = Math.round(dark.b + (light.b - dark.b) * t);
    return {
      bg: `rgb(${r},${g},${b})`,
      textColor: '#FFFFFF',
      boxShadow: 'none',
      isClear: true,
    };
  }
}

// Format a reminder timestamp into a short countdown badge label
function fmtReminderCountdown(ts) {
  const target = new Date(ts);
  const now = new Date();
  const diff = target - now;
  if (diff <= 0) return 'עכשיו';
  const mins = Math.round(diff / 60000);
  if (mins < 60) return `${mins} ד׳`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `${hrs} שע׳`;
  const days = Math.round(hrs / 24);
  if (days === 1) return 'מחר';
  if (days < 7) return `${days} ימים`;
  const wk = Math.round(days / 7);
  return `${wk} שב׳`;
}

// A single iOS-style scroll wheel
function PickerWheel({ items, value, onChange, width }) {
  const ref = useRef(null);
  const ITEM_H = 38;
  const settleTimer = useRef(null);
  useEffect(() => {
    if (ref.current) ref.current.scrollTop = value * ITEM_H;
    // eslint-disable-next-line
  }, []);
  const onScroll = () => {
    if (!ref.current) return;
    if (settleTimer.current) clearTimeout(settleTimer.current);
    settleTimer.current = setTimeout(() => {
      const idx = Math.round(ref.current.scrollTop / ITEM_H);
      const clamped = Math.max(0, Math.min(items.length - 1, idx));
      if (clamped !== value) onChange(clamped);
      ref.current.scrollTo({ top: clamped * ITEM_H, behavior: 'smooth' });
    }, 90);
  };
  return (
    <div style={{ position: 'relative', width, height: ITEM_H * 5 }}>
      <div style={{ position: 'absolute', top: ITEM_H * 2, left: 0, right: 0, height: ITEM_H, background: 'rgba(255,255,255,0.12)', borderRadius: 10, pointerEvents: 'none' }} />
      <div ref={ref} onScroll={onScroll} style={{ height: '100%', overflowY: 'auto', scrollSnapType: 'y mandatory', scrollbarWidth: 'none' }}>
        <div style={{ height: ITEM_H * 2 }} />
        {items.map((it, i) => (
          <div key={i} style={{
            height: ITEM_H, display: 'flex', alignItems: 'center', justifyContent: 'center',
            scrollSnapAlign: 'center',
            fontWeight: i === value ? 800 : 500,
            fontSize: i === value ? 18 : 15,
            color: i === value ? '#FFFFFF' : 'rgba(255,255,255,0.4)',
            transition: 'color 0.15s, font-size 0.15s',
          }}>{it}</div>
        ))}
        <div style={{ height: ITEM_H * 2 }} />
      </div>
    </div>
  );
}

// Bottom-sheet reminder picker with iOS-style wheels (days / hours / minutes)
function ReminderPicker({ open, onClose, onConfirm, onReset, accent }) {
  const dayItems = ['היום', 'מחר'];
  const baseNow = new Date();
  for (let i = 2; i < 14; i++) {
    const d = new Date(baseNow);
    d.setDate(d.getDate() + i);
    dayItems.push(d.toLocaleDateString('he-IL', { weekday: 'short', day: 'numeric', month: 'numeric' }));
  }
  // hours 01..23 then 00
  const hourItems = [];
  for (let h = 1; h <= 23; h++) hourItems.push(String(h).padStart(2, '0'));
  hourItems.push('00');
  const minItems = ['00', '10', '20', '30', '40', '50'];

  const [d, setD] = useState(1);
  const [h, setH] = useState(8); // index → 09
  const [m, setM] = useState(0);

  const buildDate = () => {
    const target = new Date();
    target.setDate(target.getDate() + d);
    const hourVal = parseInt(hourItems[h], 10);
    target.setHours(hourVal, parseInt(minItems[m], 10), 0, 0);
    return target;
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-50"
            style={{ background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
          />
          <motion.div
            initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 32, stiffness: 340 }}
            className="fixed left-0 right-0 bottom-0 z-50"
            dir="rtl"
          >
            <div style={{ background: accent || 'rgb(30,90,100)', borderRadius: '28px 28px 0 0', padding: '14px 18px 26px', boxShadow: '0 -16px 50px rgba(0,0,0,0.3)' }}>
              <div className="flex justify-center mb-3">
                <div style={{ width: 40, height: 5, borderRadius: 999, background: 'rgba(255,255,255,0.3)' }} />
              </div>
              <div className="flex items-center justify-between mb-3">
                <button onClick={onReset} style={{ color: 'rgba(255,255,255,0.9)', fontWeight: 600, fontSize: 15 }}>איפוס</button>
                <div className="flex items-center gap-2">
                  <Clock size={18} style={{ color: '#fff' }} />
                  <span style={{ color: '#fff', fontWeight: 800, fontSize: 16 }}>קביעת תזכורת</span>
                </div>
                <button
                  onClick={() => onConfirm(buildDate().toISOString())}
                  style={{ color: '#fff', fontWeight: 800, fontSize: 15, background: 'rgba(255,255,255,0.2)', padding: '6px 16px', borderRadius: 999 }}
                >שמור</button>
              </div>
              <div className="flex items-center justify-center gap-1" style={{ direction: 'ltr' }}>
                <PickerWheel items={dayItems} value={d} onChange={setD} width={130} />
                <PickerWheel items={hourItems} value={h} onChange={setH} width={54} />
                <span style={{ color: '#fff', fontWeight: 800, fontSize: 20 }}>:</span>
                <PickerWheel items={minItems} value={m} onChange={setM} width={54} />
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}

function TaskRow({
  task, index, totalRows, isEditing, editText, editRef,
  onEditChange, onEditSave, onEditCancel, onStartEdit, onToggleDone, onDelete, onReminder, theme,
}) {
  const x = useMotionValue(0);
  const dragControls = useDragControls();
  const rowRef = useRef(null);
  const [grabbed, setGrabbed] = useState(false);
  const [overflowing, setOverflowing] = useState(false);
  const textWrapRef = useRef(null);
  const textRef = useRef(null);
  const swipeControls = useDragControls();
  // Measure whether the text overflows its container (so we can enable inner
  // horizontal scrolling and restrict swipe to the edges).
  useEffect(() => {
    const check = () => {
      if (textWrapRef.current) {
        const ov = textWrapRef.current.scrollWidth > textWrapRef.current.clientWidth + 4;
        setOverflowing(ov);
      }
    };
    check();
    const r = requestAnimationFrame(check);
    const t1 = setTimeout(check, 60);
    const t2 = setTimeout(check, 200);
    window.addEventListener('resize', check);
    return () => {
      cancelAnimationFrame(r);
      clearTimeout(t1); clearTimeout(t2);
      window.removeEventListener('resize', check);
    };
  }, [task.text, isEditing]);
  const { bg, textColor, boxShadow, isClear } = getRowStyle(index, theme, totalRows);

  // Clear-style swipe behavior, decided by the live visual position (x), which
  // always resets to 0 between swipes (dragSnapToOrigin). Using info.offset
  // accumulated incorrectly across consecutive swipes.
  const handleSwipeEnd = (event, info) => {
    const dist = x.get();
    const rowWidth = rowRef.current ? rowRef.current.getBoundingClientRect().width : 360;
    const reminderThreshold = rowWidth * (2 / 3); // must cross 2/3 of the row to set a reminder
    if (dist < -110) {
      let y = null;
      if (rowRef.current) {
        const rect = rowRef.current.getBoundingClientRect();
        y = rect.top + rect.height / 2;
      }
      onDelete(y);
    } else if (dist > reminderThreshold) {
      if (onReminder) onReminder();
    } else if (dist > 60) {
      onToggleDone();
    }
    // x returns to 0 via dragSnapToOrigin on the motion element
  };

  const handleStyle = {
    touchAction: 'none',
    background: isClear ? 'rgba(0,0,0,0.1)' : 'linear-gradient(180deg, rgba(0,0,0,0.12), rgba(0,0,0,0.3))',
    cursor: 'grab',
  };

  // Outer wrapper styling - Clear style has no margin/padding/radius between rows
  const outerClassName = isClear ? "relative" : "relative mb-2 mx-2";
  const rowRadius = isClear ? '0px' : theme.rowRadius;

  if (isEditing) {
    return (
      <Reorder.Item
        value={task}
        dragListener={false}
        style={{ background: bg, boxShadow, borderRadius: rowRadius }}
        className={outerClassName}
      >
        <div className="px-5 py-4">
          <textarea
            ref={editRef}
            value={editText}
            onChange={(e) => onEditChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); onEditSave(); }
              else if (e.key === 'Escape') onEditCancel();
            }}
            onBlur={onEditSave}
            rows={1}
            className="w-full bg-transparent text-base outline-none resize-none"
            dir="rtl"
            style={{ minHeight: '1.5rem', textShadow: isClear ? 'none' : '0 1px 1px rgba(0,0,0,0.25)', fontWeight: isClear ? 400 : 600, color: textColor }}
          />
        </div>
      </Reorder.Item>
    );
  }

  return (
    <Reorder.Item
      ref={rowRef}
      value={task}
      dragListener={false}
      dragControls={dragControls}
      onDragStart={() => setGrabbed(true)}
      onDragEnd={() => setGrabbed(false)}
      initial={task._justRestored ? { opacity: 0, x: -120 } : false}
      animate={task._justRestored
        ? { opacity: 1, x: 0 }
        : { opacity: 1, x: 0, scale: grabbed ? 1.04 : 1, boxShadow: grabbed ? '0 12px 28px rgba(0,0,0,0.28)' : '0 0px 0px rgba(0,0,0,0)' }}
      whileDrag={{ zIndex: 10 }}
      transition={task._justRestored
        ? { duration: 1.5, ease: [0.22, 1, 0.36, 1] }
        : { type: 'spring', damping: 26, stiffness: 400 }}
      style={{ position: 'relative', zIndex: grabbed ? 10 : 1 }}
      className={outerClassName}
    >
      <div className="relative flex items-stretch" style={{ borderRadius: rowRadius, overflow: 'hidden' }}>
        {/* Swipe background filled with repeating icons (so an icon is always visible) */}
        <SwipeIconFill x={x} />

        {/* Swipeable content area. When text overflows, the middle scrolls the text
            and swipe is restricted to the left/right edge zones. */}
        <motion.div
          drag="x"
          dragControls={swipeControls}
          dragDirectionLock
          dragConstraints={{ left: 0, right: 0 }}
          dragElastic={0.9}
          dragSnapToOrigin
          dragTransition={{ bounceStiffness: 400, bounceDamping: 30 }}
          onDragEnd={handleSwipeEnd}
          style={{ x, background: bg, boxShadow, borderRadius: rowRadius, touchAction: 'pan-y' }}
          className="relative flex-1 flex items-center min-w-0 select-none"
        >
          {/* Fixed side margins (swipe/grab zones) + centered scroll area between them. */}
          <div className="flex-1 flex items-center min-w-0" style={{ paddingRight: '20px', paddingLeft: '44px' }}>
            <div
              ref={textWrapRef}
              className="flex-1 py-4 cursor-pointer min-w-0"
              style={{
                overflowX: overflowing ? 'auto' : 'hidden',
                WebkitOverflowScrolling: 'touch',
                touchAction: overflowing ? 'pan-x' : 'pan-y',
              }}
              onClick={() => { if (Math.abs(x.get()) < 5) onStartEdit(); }}
              onPointerDownCapture={(e) => {
                // For overflowing text, touches in the middle should scroll the text,
                // not trigger the row swipe. Stop them from reaching framer's drag,
                // but only if NOT near the row edges (edges stay as swipe zones).
                if (!overflowing) return;
                const row = e.currentTarget.closest('.relative');
                const rect = (row || e.currentTarget).getBoundingClientRect();
                const fromLeft = e.clientX - rect.left;
                const fromRight = rect.right - e.clientX;
                const EDGE = 50;
                if (fromLeft > EDGE && fromRight > EDGE) {
                  e.stopPropagation();
                }
              }}
            >
              <div
                ref={textRef}
                className={overflowing ? "text-base" : "text-base truncate"}
                style={{
                  color: textColor,
                  whiteSpace: 'nowrap',
                  textDecoration: task.done ? 'line-through' : 'none',
                  textDecorationColor: textColor,
                  textDecorationThickness: '2px',
                  opacity: task.done ? 0.55 : 1,
                  textShadow: isClear ? 'none' : '0 1px 1px rgba(0,0,0,0.2)',
                  fontWeight: isClear ? 400 : 600,
                  fontSize: isClear ? '17px' : '16px',
                  letterSpacing: isClear ? '0.01em' : 'normal',
                }}
              >
                {task.text}
              </div>
            </div>
          </div>
          {/* Reminder badge - absolute overlay on the left so it doesn't affect
              the text measurement or horizontal scroll. */}
          {task.reminder && (
            <div
              className="flex items-center gap-1 pointer-events-none"
              style={{
                position: 'absolute',
                left: 8,
                top: '50%',
                transform: 'translateY(-50%)',
                background: 'rgba(0,0,0,0.28)',
                borderRadius: 999,
                padding: '3px 9px',
                backdropFilter: 'blur(6px)',
                zIndex: 3,
              }}
            >
              <Clock size={12} strokeWidth={2.4} style={{ color: '#FFFFFF' }} />
              <span style={{ color: '#FFFFFF', fontSize: 11, fontWeight: 700, whiteSpace: 'nowrap' }}>
                {fmtReminderCountdown(task.reminder)}
              </span>
            </div>
          )}
        </motion.div>

        {/* RIGHT reorder handle - immediate drag on touch.
            The 30px width on the right edge is isolated enough that we don't
            need long-press to distinguish reorder intent from swipe intent.
            Simple = reliable: no timer, no state, no window listeners.
            Adding vibration directly here (no timer) keeps it stable. */}
        <div
          onPointerDown={(e) => {
            e.stopPropagation();
            if (navigator.vibrate) { try { navigator.vibrate(25); } catch (err) {} }
            dragControls.start(e);
          }}
          onTouchStart={(e) => { e.stopPropagation(); }}
          className="absolute top-0 bottom-0 right-0"
          style={{
            width: '34px',
            touchAction: 'none',
            cursor: 'grab',
            zIndex: 5,
          }}
        />
      </div>
    </Reorder.Item>
  );
}

// Shows a single icon on the side revealed by the swipe, with padding from the edge.
function SwipeIconFill({ x }) {
  const swipeBg = useTransform(
    x,
    [-220, -80, 0, 80, 220, 250],
    [
      'rgba(220, 38, 38, 0.95)',
      'rgba(220, 38, 38, 0.5)',
      'rgba(0, 0, 0, 0)',
      'rgba(34, 197, 94, 0.55)',
      'rgba(34, 197, 94, 0.95)',
      'rgba(245, 158, 11, 0.97)',
    ]
  );
  // Swipe left (x<0) reveals the RIGHT side → trash icon on the right
  const deleteOpacity = useTransform(x, [-150, -40, 0], [1, 0.3, 0]);
  // Swipe right (x>0) reveals the LEFT side → check (short) / clock (long) on the left
  const checkOpacity = useTransform(x, [0, 45, 220, 245], [0, 1, 1, 0]);
  const clockOpacity = useTransform(x, [235, 260, 290], [0, 1, 1]);

  return (
    <motion.div style={{ backgroundColor: swipeBg }} className="absolute inset-0 pointer-events-none overflow-hidden">
      {/* delete icon - right side (revealed on left-swipe) */}
      <motion.div
        style={{ opacity: deleteOpacity }}
        className="absolute top-0 bottom-0 right-0 flex items-center"
      >
        <div style={{ paddingRight: '22px' }}>
          <Trash2 size={24} strokeWidth={2.3} style={{ color: '#FFFFFF' }} />
        </div>
      </motion.div>
      {/* check icon - left side (revealed on right-swipe, short) */}
      <motion.div
        style={{ opacity: checkOpacity }}
        className="absolute top-0 bottom-0 left-0 flex items-center"
      >
        <div style={{ paddingLeft: '22px' }}>
          <Check size={26} strokeWidth={2.6} style={{ color: '#FFFFFF' }} />
        </div>
      </motion.div>
      {/* clock icon - left side (revealed on right-swipe, long = reminder) */}
      <motion.div
        style={{ opacity: clockOpacity }}
        className="absolute top-0 bottom-0 left-0 flex items-center"
      >
        <div style={{ paddingLeft: '22px' }}>
          <Clock size={24} strokeWidth={2.3} style={{ color: '#FFFFFF' }} />
        </div>
      </motion.div>
    </motion.div>
  );
}
