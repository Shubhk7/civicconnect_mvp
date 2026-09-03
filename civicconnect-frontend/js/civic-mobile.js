(function () {
  var DRAFT_DB = 'civicconnect';
  var DRAFT_STORE = 'reportDrafts';

  function path() {
    return (location.pathname.replace(/\/+$/, '') || '/') + '/';
  }

  function isStaff() {
    var u = window.CivicApp && CivicApp.getUser();
    return u && (u.role === 'OFFICER' || u.role === 'ADMIN');
  }

  function injectOfflineBanner() {
    if (document.querySelector('.civic-offline-banner')) return;
    var b = document.createElement('div');
    b.className = 'civic-offline-banner';
    b.setAttribute('role', 'status');
    b.textContent = 'You are offline. Reports will not be marked submitted until the server confirms.';
    document.body.insertBefore(b, document.body.firstChild);
    function sync() {
      b.classList.toggle('show', !navigator.onLine);
    }
    window.addEventListener('online', sync);
    window.addEventListener('offline', sync);
    sync();
  }

  function injectDrawer() {
    if (document.querySelector('.civic-drawer')) return;
    var d = document.createElement('div');
    d.className = 'civic-drawer';
    d.id = 'civic-drawer';
    var logged = CivicApp.isLoggedIn();
    var staff = isStaff();
    var links = staff
      ? [
          ['/officer/', 'Officer queue'],
          ['/heatmap/', 'Live map'],
          ['/ev-stations/', 'EV infrastructure'],
          ['/profile/', 'Profile']
        ]
      : [
          ['/', 'Home'],
          ['/report/', 'Report an issue'],
          ['/my-reports/', 'My reports'],
          ['/heatmap/', 'Live map'],
          ['/ev-stations/', 'EV stations'],
          ['/dashboard/', 'Dashboard'],
          ['/profile/', 'Profile']
        ];
    if (!logged) {
      links = [
        ['/', 'Home'],
        ['/report/', 'Report an issue'],
        ['/heatmap/', 'Live map'],
        ['/ev-stations/', 'EV stations'],
        ['/login/', 'Log in'],
        ['/register/', 'Create account']
      ];
    }
    var html = '<div class="civic-drawer-panel" role="dialog" aria-label="Menu">';
    html += '<button type="button" id="civic-drawer-close">Close</button>';
    links.forEach(function (pair) {
      html += '<a href="' + pair[0] + '">' + pair[1] + '</a>';
    });
    if (logged) html += '<button type="button" id="civic-drawer-logout">Log out</button>';
    html += '</div>';
    d.innerHTML = html;
    document.body.appendChild(d);
    d.addEventListener('click', function (e) {
      if (e.target === d) d.classList.remove('open');
    });
    var close = document.getElementById('civic-drawer-close');
    if (close) close.addEventListener('click', function () { d.classList.remove('open'); });
    var lo = document.getElementById('civic-drawer-logout');
    if (lo) lo.addEventListener('click', function () {
      CivicApp.clearSession();
      location.href = '/';
    });
  }

  function injectMenuButton() {
    var nav = document.querySelector('header .nav');
    if (!nav || document.getElementById('civic-menu-btn')) return;
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nav-menu-btn';
    btn.id = 'civic-menu-btn';
    btn.setAttribute('aria-label', 'Open menu');
    btn.innerHTML = '<span aria-hidden="true">☰</span>';
    var actions = document.getElementById('civic-nav-actions');
    nav.insertBefore(btn, actions || null);
    btn.addEventListener('click', function () {
      var d = document.getElementById('civic-drawer');
      if (d) d.classList.add('open');
    });
  }

  function injectTabbar() {
    if (document.querySelector('.civic-tabbar')) return;
    var logged = CivicApp.isLoggedIn();
    var staff = isStaff();
    if (!logged) return;
    document.body.classList.add('has-tabbar');
    var bar = document.createElement('nav');
    bar.className = 'civic-tabbar';
    bar.setAttribute('aria-label', 'Primary');
    var p = path();
    function item(href, label, extra, mark) {
      var active = p === href || (href !== '/' && p.indexOf(href) === 0);
      return '<a class="' + (extra || '') + (active ? ' active' : '') + '" href="' + href + '">' +
        (mark || '') + '<span>' + label + '</span></a>';
    }
    if (staff) {
      bar.innerHTML =
        item('/officer/', 'Queue') +
        item('/heatmap/', 'Map') +
        item('/report/', 'Report', 'tab-report', '+') +
        item('/ev-stations/', 'EV') +
        item('/profile/', 'Profile');
    } else {
      bar.innerHTML =
        item('/dashboard/', 'Home') +
        item('/my-reports/', 'Reports') +
        item('/report/', 'Report', 'tab-report', '+') +
        item('/heatmap/', 'Map') +
        item('/profile/', 'Profile');
    }
    document.body.appendChild(bar);
  }

  function compressImage(file, maxEdge, quality) {
    maxEdge = maxEdge || 1600;
    quality = quality || 0.82;
    return new Promise(function (resolve) {
      if (!file || !file.type || file.type.indexOf('image/') !== 0) {
        resolve(file);
        return;
      }
      if (file.size < 350000) {
        resolve(file);
        return;
      }
      var img = new Image();
      var url = URL.createObjectURL(file);
      img.onload = function () {
        URL.revokeObjectURL(url);
        var w = img.width, h = img.height;
        var scale = Math.min(1, maxEdge / Math.max(w, h));
        var canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(w * scale));
        canvas.height = Math.max(1, Math.round(h * scale));
        var ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        canvas.toBlob(function (blob) {
          if (!blob) { resolve(file); return; }
          resolve(new File([blob], file.name.replace(/\.\w+$/, '.jpg'), { type: 'image/jpeg' }));
        }, 'image/jpeg', quality);
      };
      img.onerror = function () { URL.revokeObjectURL(url); resolve(file); };
      img.src = url;
    });
  }

  function openDraftDb() {
    return new Promise(function (resolve, reject) {
      if (!window.indexedDB) { resolve(null); return; }
      var req = indexedDB.open(DRAFT_DB, 1);
      req.onupgradeneeded = function () {
        var db = req.result;
        if (!db.objectStoreNames.contains(DRAFT_STORE)) db.createObjectStore(DRAFT_STORE);
      };
      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { resolve(null); };
    });
  }

  function saveReportDraft(payload) {
    return openDraftDb().then(function (db) {
      if (!db) return;
      return new Promise(function (resolve) {
        var tx = db.transaction(DRAFT_STORE, 'readwrite');
        tx.objectStore(DRAFT_STORE).put(payload, 'latest');
        tx.oncomplete = function () { resolve(); };
        tx.onerror = function () { resolve(); };
      });
    });
  }

  function loadReportDraft() {
    return openDraftDb().then(function (db) {
      if (!db) return null;
      return new Promise(function (resolve) {
        var tx = db.transaction(DRAFT_STORE, 'readonly');
        var q = tx.objectStore(DRAFT_STORE).get('latest');
        q.onsuccess = function () { resolve(q.result || null); };
        q.onerror = function () { resolve(null); };
      });
    });
  }

  function clearReportDraft() {
    return openDraftDb().then(function (db) {
      if (!db) return;
      db.transaction(DRAFT_STORE, 'readwrite').objectStore(DRAFT_STORE).delete('latest');
    });
  }

  function registerSw() {
    if (!('serviceWorker' in navigator)) return;
    navigator.serviceWorker.register('/sw.js').catch(function () {});
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!window.CivicApp) return;
    injectOfflineBanner();
    injectDrawer();
    injectMenuButton();
    injectTabbar();
    registerSw();
  });

  window.CivicMobile = {
    compressImage: compressImage,
    saveReportDraft: saveReportDraft,
    loadReportDraft: loadReportDraft,
    clearReportDraft: clearReportDraft
  };
})();
