self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open('civic-static-v1').then(function (cache) {
      return cache.addAll([
        '/css/tokens.css',
        '/css/mobile.css',
        '/js/civic-app.js',
        '/js/civic-mobile.js',
        '/manifest.json',
        '/icons/icon.svg'
      ]);
    }).then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', function (event) {
  var req = event.request;
  if (req.method !== 'GET') return;
  var url = new URL(req.url);
  if (url.origin !== location.origin) return;
  if (url.pathname.indexOf('/api/') === 0) return;
  if (url.pathname.indexOf('/uploads/') === 0) return;

  event.respondWith(
    fetch(req).then(function (res) {
      return res;
    }).catch(function () {
      return caches.match(req).then(function (cached) {
        if (cached) return cached;
        if (req.mode === 'navigate') {
          return new Response(
            '<!doctype html><title>Offline</title><meta name="viewport" content="width=device-width,initial-scale=1"><body style="font-family:sans-serif;padding:24px"><h1>Unable to reach CivicConnect</h1><p>You are offline. Reconnect and retry.</p></body>',
            { headers: { 'Content-Type': 'text/html; charset=utf-8' } }
          );
        }
        return new Response('', { status: 503, statusText: 'Offline' });
      });
    })
  );
});
