/* SweepApp service worker — network-first, offline fallback for the app shell */
const CACHE = 'sweepapp-v1';
const ASSETS = [
  'SweepApp.html',
  'sweepapp.webmanifest',
  'icon-any-192.png',
  'icon-any-512.png',
  'icon-maskable-512.png',
  'apple-touch-180.png',
  'favicon-64.png'
];

self.addEventListener('install', e => {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE)
      .then(c => c.addAll(ASSETS.map(a => new Request(a, { cache: 'reload' }))))
      .catch(() => {})
  );
});

self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(k => k.startsWith('sweepapp-') && k !== CACHE).map(k => caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    fetch(e.request)
      .then(r => {
        if (r && r.ok && e.request.url.startsWith(self.registration.scope)) {
          const cp = r.clone();
          caches.open(CACHE).then(c => c.put(e.request, cp)).catch(() => {});
        }
        return r;
      })
      .catch(() => caches.match(e.request))
  );
});
