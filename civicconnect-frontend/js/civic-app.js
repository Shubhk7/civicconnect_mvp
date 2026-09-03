// ============================================================
// CivicConnect shared frontend logic (loaded as /js/civic-app.js).
// ============================================================
(function () {
  const API_BASE = 'https://api.kashnet.online';
  const TOKEN_KEY = 'civic_token';
  const USER_KEY = 'civic_user';

  // ---- session storage helpers ----
  // Using localStorage only to hold the JWT + cached profile — NOT as a
  // substitute for real auth. Every protected call still round-trips to
  // the backend, which validates the JWT itself; nothing here is trusted
  // on its own.
  function getToken() { return localStorage.getItem(TOKEN_KEY); }
  function getUser() {
    try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch (e) { return null; }
  }
  function setSession(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }
  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }
  function isLoggedIn() { return !!getToken(); }

  // ---- central fetch helper ----
  // Automatically attaches the JWT if we have one. On a 401 from a
  // protected endpoint, clears the stale session and bounces to /login/.
  async function apiFetch(path, options = {}) {
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    const res = await fetch(API_BASE + path, Object.assign({}, options, { headers }));

    if (res.status === 401) {
      clearSession();
      // Only force a redirect for pages that actually require login —
      // callers on public pages (e.g. the report form for a logged-out
      // visitor) should check res.ok themselves instead of relying on this.
      if (window.CIVIC_REQUIRE_AUTH) {
        window.location.href = '/login/';
      }
    }

    let data = null;
    try { data = await res.json(); } catch (e) { /* no body */ }
    return { ok: res.ok, status: res.status, data };
  }

  // ---- shared nav rendering ----
  // Call this once per page inside an element with id="civic-nav-actions".
  // Swaps between "Log in / Create account" and "Dashboard / Log out"
  // depending on session state.
  // Where a user lands after login, and which nav link "home base" points
  // to. Officers/admins get the officer console; citizens get the personal
  // dashboard. Keeping this in one place means role routing can't drift
  // between pages.
  function homeFor(user) {
    return (user && (user.role === 'OFFICER' || user.role === 'ADMIN')) ? '/officer/' : '/dashboard/';
  }

  function renderNavActions(el) {
    if (!el) return;
    if (isLoggedIn()) {
      const user = getUser();
      const isStaff = user && (user.role === 'OFFICER' || user.role === 'ADMIN');
      const home = homeFor(user);
      const homeLabel = isStaff ? 'Officer dashboard' : (user && user.fullName ? user.fullName.split(' ')[0] : 'Dashboard');
      el.innerHTML =
        '<a class="btn btn-text" href="' + home + '">' + homeLabel + '</a>' +
        '<button class="btn btn-ghost btn-sm" id="civic-logout-btn">Log out</button>' +
        (isStaff ? '' : '<a class="btn btn-solid" href="/report/">Report an issue</a>');
      const logoutBtn = document.getElementById('civic-logout-btn');
      if (logoutBtn) logoutBtn.addEventListener('click', function () {
        clearSession();
        window.location.href = '/';
      });
    } else {
      el.innerHTML =
        '<a class="btn btn-text" href="/login/">Log in</a>' +
        '<a class="btn btn-ghost" href="/register/">Create account</a>' +
        '<a class="btn btn-solid" href="/report/">Report an issue</a>';
    }
  }

  // ---- small utilities pages reuse ----
  function statusLabel(status) {
    return (status || '').replace(/_/g, ' ');
  }
  function fmtDate(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) +
      ' · ' + d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  }
  function slaRemaining(iso) {
    if (!iso) return null;
    const diffMs = new Date(iso).getTime() - Date.now();
    const hrs = Math.round(diffMs / 3600000);
    if (hrs < 0) return { breached: true, text: Math.abs(hrs) + 'h over SLA' };
    return { breached: false, text: hrs + 'h remaining' };
  }

  // ---- SLA traffic-light badge ----
  // Turns a raw deadline + status into a plain-language accountability
  // signal: on track / approaching / breached. Officer view also gets the
  // colored pill classes (sla-ok / sla-warn / sla-breach); citizen-facing
  // pages reuse the same classes so the language and color stay consistent
  // everywhere a citizen or officer sees an SLA.
  function slaBadge(iso, status) {
    const closed = ['RESOLVED', 'VERIFIED', 'CLOSED'].indexOf(status) !== -1;
    if (closed) return { cls: 'sla-ok', icon: '\ud83d\udfe2', label: 'Resolved', text: '' };
    const sla = slaRemaining(iso);
    if (!sla) return { cls: 'sla-ok', icon: '\u26aa', label: 'No deadline set', text: '' };
    if (sla.breached) return { cls: 'sla-breach', icon: '\ud83d\udd34', label: 'SLA breached', text: sla.text };
    const diffMs = new Date(iso).getTime() - Date.now();
    if (diffMs < 6 * 3600000) return { cls: 'sla-warn', icon: '\ud83d\udfe1', label: 'SLA approaching', text: sla.text };
    return { cls: 'sla-ok', icon: '\ud83d\udfe2', label: 'On track', text: sla.text };
  }

  // Expose everything under one global so page scripts can use it.
  // ---- dark/light theme toggle ----
  var THEME_KEY = 'civic_theme';
  function getTheme() {
    return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
  }
  function setTheme(theme, btn) {
    if (theme === 'dark') {
      document.documentElement.setAttribute('data-theme', 'dark');
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
    try { localStorage.setItem(THEME_KEY, theme); } catch (e) {}
    if (btn) btn.textContent = theme === 'dark' ? '\u2600\ufe0f' : '\ud83c\udf19';
  }
  function initThemeToggle(btn) {
    if (!btn) return;
    setTheme(getTheme(), btn);
    btn.addEventListener('click', function () {
      setTheme(getTheme() === 'dark' ? 'light' : 'dark', btn);
    });
  }

  function reportCardHtml(r) {
    const badge = slaBadge(r.slaDeadline, r.status);
    const id = r.id != null ? '#' + String(r.id).padStart(4, '0') : '';
    const loc = r.wardName || 'Ward pending';
    const when = fmtDate(r.createdAt);
    return '<a class="report-row" href="/report-details/?id=' + r.id + '">' +
      '<div class="issue">' + (r.issueType || 'Issue') + '</div>' +
      '<div class="meta">' + id + (id ? ' · ' : '') + loc + ' · ' + when + '</div>' +
      '<div class="report-row-foot">' +
        '<span class="status-pill status-' + r.status + '">' + statusLabel(r.status) + '</span>' +
        '<span class="sla-tag ' + badge.cls + '">' + badge.icon + ' ' + badge.label + (badge.text ? ' · ' + badge.text : '') + '</span>' +
        (r.upvoteCount != null ? '<span class="meta">▲ ' + r.upvoteCount + '</span>' : '') +
      '</div></a>';
  }

  window.CivicApp = {
    API_BASE, getToken, getUser, setSession, clearSession, isLoggedIn,
    apiFetch, renderNavActions, homeFor, statusLabel, fmtDate, slaRemaining, slaBadge,
    initThemeToggle, reportCardHtml
  };
})();
