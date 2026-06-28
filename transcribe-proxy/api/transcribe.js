// Serverless proxy for OpenAI audio transcription.
// The real OpenAI key NEVER lives in this file or the repo — only as a Vercel
// env var (OPENAI_API_KEY). Requests must carry the shared secret header
// (x-app-secret == APP_SHARED_SECRET) or they are rejected.
//
// The app POSTs the raw audio bytes as the request body, with headers:
//   x-app-secret : the shared gate secret
//   x-filename   : e.g. "audio.m4a"
//   x-mime       : e.g. "audio/m4a"
//   x-lang       : "" (auto) | "he" (force Hebrew) | "en" (translate to English)

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, x-app-secret, x-filename, x-mime, x-lang');

  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'POST') return res.status(405).send('method not allowed');

  const secret = process.env.APP_SHARED_SECRET || '';
  if (!secret || req.headers['x-app-secret'] !== secret) {
    return res.status(401).send('unauthorized');
  }
  const apiKey = process.env.OPENAI_API_KEY;
  if (!apiKey) return res.status(500).send('server not configured');

  // read the raw audio body
  let buf;
  if (Buffer.isBuffer(req.body)) {
    buf = req.body;
  } else {
    const chunks = [];
    for await (const c of req) chunks.push(c);
    buf = Buffer.concat(chunks);
  }
  if (!buf || !buf.length) return res.status(400).send('no audio');

  const lang = (req.headers['x-lang'] || '').toString();
  const filename = (req.headers['x-filename'] || 'audio.m4a').toString();
  const mime = (req.headers['x-mime'] || 'application/octet-stream').toString();
  const translate = lang === 'en';

  const fd = new FormData();
  fd.append('file', new Blob([buf], { type: mime }), filename);
  fd.append('model', translate ? 'whisper-1' : 'gpt-4o-transcribe');
  fd.append('response_format', 'text');
  if (!translate && lang) fd.append('language', lang);
  if (!translate && (lang === 'he' || !lang)) {
    fd.append('prompt', 'תמלול הקלטה קולית בעברית. כתוב את כל המספרים בספרות ולא במילים. למשל: שישים ושלוש = 63, מאה עשרים = 120, אלף חמש מאות = 1500, רחוב הרצל שתים עשרה = רחוב הרצל 12, קומה שלוש = קומה 3. שמור על פיסוק תקין.');
  }

  const url = translate
    ? 'https://api.openai.com/v1/audio/translations'
    : 'https://api.openai.com/v1/audio/transcriptions';

  try {
    const r = await fetch(url, {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + apiKey },
      body: fd,
    });
    const text = await r.text();
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    return res.status(r.ok ? 200 : r.status).send(text);
  } catch (e) {
    return res.status(502).send('upstream error');
  }
}
