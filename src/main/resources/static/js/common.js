// ========== common.js — shared utilities for Doc Generator UI ==========

/**
 * HTML-escape a string to prevent XSS.
 */
function esc(s) {
    if (!s) return '';
    const el = document.createElement('span');
    el.textContent = s;
    return el.innerHTML;
}

/**
 * Show a toast notification with dismiss button. Supports up to 3 stacked toasts.
 * @param {string} msg - Message text
 * @param {'error'|'success'} [type='error'] - Toast type
 */
function showToast(msg, type) {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    // Limit to 3 toasts — remove the oldest
    const toasts = container.querySelectorAll('.toast');
    if (toasts.length >= 3) toasts[0].remove();

    const t = document.createElement('div');
    t.className = 'toast ' + (type === 'success' ? 'toast-success' : 'toast-error');
    t.textContent = msg;

    const dismiss = document.createElement('button');
    dismiss.className = 'toast-dismiss';
    dismiss.innerHTML = '&times;';
    dismiss.onclick = () => { t.remove(); cleanupToastContainer(); };
    t.appendChild(dismiss);

    container.appendChild(t);
    setTimeout(() => { t.remove(); cleanupToastContainer(); }, 6000);
}

function cleanupToastContainer() {
    const container = document.querySelector('.toast-container');
    if (container && container.children.length === 0) container.remove();
}

/**
 * Map index status string to a CSS class name.
 */
function getStatusClass(status) {
    if (!status) return 'status-unknown';
    const s = status.toLowerCase();
    if (s === 'success' || s === 'completed') return 'status-success';
    if (s === 'failed' || s === 'error') return 'status-failed';
    if (s === 'running' || s === 'in_progress') return 'status-running';
    if (s === 'partial') return 'status-partial';
    return 'status-unknown';
}

/**
 * Format ISO date string to Russian locale (dd.mm.yyyy hh:mm).
 */
function formatDate(iso) {
    if (!iso) return '';
    try {
        const d = new Date(iso);
        return d.toLocaleDateString('ru-RU') + ' ' +
            d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    } catch {
        return iso;
    }
}

/**
 * Format ISO date to relative time ("5 минут назад", "2 часа назад", etc).
 * Falls back to formatDate() for dates older than 7 days.
 */
function formatRelativeTime(iso) {
    if (!iso) return '';
    try {
        const d = new Date(iso);
        const now = new Date();
        const diffMs = now - d;
        if (diffMs < 0) return formatDate(iso);

        const seconds = Math.floor(diffMs / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);

        if (seconds < 60) return 'только что';
        if (minutes < 60) return minutes + ' ' + pluralize(minutes, 'минуту', 'минуты', 'минут') + ' назад';
        if (hours < 24) return hours + ' ' + pluralize(hours, 'час', 'часа', 'часов') + ' назад';
        if (days === 1) return 'вчера';
        if (days < 7) return days + ' ' + pluralize(days, 'день', 'дня', 'дней') + ' назад';
        return formatDate(iso);
    } catch {
        return iso;
    }
}

/**
 * Russian pluralization helper.
 */
function pluralize(n, one, few, many) {
    const abs = Math.abs(n) % 100;
    const lastDigit = abs % 10;
    if (abs > 10 && abs < 20) return many;
    if (lastDigit === 1) return one;
    if (lastDigit >= 2 && lastDigit <= 4) return few;
    return many;
}

/**
 * Format number with Russian locale thousand separators.
 */
function fmt(n) {
    if (n == null) return '0';
    return Number(n).toLocaleString('ru-RU');
}

/**
 * Format duration in milliseconds to human-readable string.
 */
function formatDuration(ms) {
    if (ms == null) return '\u2014';
    if (ms < 1000) return ms + 'ms';
    const s = Math.floor(ms / 1000);
    if (s < 60) return s + 's';
    return Math.floor(s / 60) + 'm ' + (s % 60) + 's';
}

// ========== Theme ==========

function initTheme() {
    const stored = localStorage.getItem('docgen_theme');
    let theme = stored;
    if (!theme) {
        theme = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    }
    document.documentElement.dataset.theme = theme;
    updateThemeIcon(theme);

    // Dynamic page title based on current URL
    const pageNames = { '/': 'Dashboard', '/graph': 'Graph Explorer', '/chat': 'Chat', '/ingest': 'Ingest', '/health': 'Health' };
    const pageName = pageNames[window.location.pathname];
    if (pageName) document.title = 'Doc Generator \u2014 ' + pageName;

    // Add title hints for nav links (Alt+1..5)
    const shortcuts = { '/': '1', '/graph': '2', '/chat': '3', '/ingest': '4', '/health': '5' };
    const labels = { '/': 'Dashboard', '/graph': 'Graph', '/chat': 'Chat', '/ingest': 'Ingest', '/health': 'Health' };
    document.querySelectorAll('.nav-links a, .sidebar-nav a').forEach(a => {
        const path = new URL(a.href, location.origin).pathname;
        if (shortcuts[path]) {
            a.title = labels[path] + ' (Alt+' + shortcuts[path] + ')';
        }
    });
}

function toggleTheme() {
    const current = document.documentElement.dataset.theme || 'dark';
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    localStorage.setItem('docgen_theme', next);
    updateThemeIcon(next);
}

function updateThemeIcon(theme) {
    const btn = document.getElementById('themeToggle');
    if (btn) btn.textContent = theme === 'dark' ? '\u2600' : '\u263E';
}

// ========== Keyboard Shortcuts (Alt+1..5) ==========
document.addEventListener('keydown', function(e) {
    if (!e.altKey) return;
    var tag = (e.target.tagName || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea' || tag === 'select') return;

    var routes = { '1': '/', '2': '/graph', '3': '/chat', '4': '/ingest', '5': '/health' };
    if (routes[e.key]) {
        e.preventDefault();
        window.location.href = routes[e.key];
    }
});

// ========== Scroll-to-top button ==========
function initScrollToTop() {
    // Only for scrollable pages (not graph/chat which handle their own scroll)
    const path = window.location.pathname;
    if (path === '/graph' || path === '/chat') return;

    const btn = document.createElement('button');
    btn.className = 'scroll-top-btn';
    btn.innerHTML = '&#9650;';
    btn.title = 'Scroll to top';
    btn.onclick = () => window.scrollTo({ top: 0, behavior: 'smooth' });
    document.body.appendChild(btn);

    window.addEventListener('scroll', () => {
        btn.classList.toggle('visible', window.scrollY > 300);
    }, { passive: true });
}

// ========== Mobile hamburger menu ==========
function initHamburger() {
    const nav = document.querySelector('.header .nav-links');
    if (!nav) return;

    const btn = document.createElement('button');
    btn.className = 'hamburger-btn';
    btn.innerHTML = '&#9776;';
    btn.title = 'Menu';
    btn.onclick = () => nav.classList.toggle('mobile-open');
    nav.parentElement.insertBefore(btn, nav);

    document.addEventListener('click', e => {
        if (!e.target.closest('.hamburger-btn') && !e.target.closest('.nav-links')) {
            nav.classList.remove('mobile-open');
        }
    });
}

// Init theme on load
initTheme();
initScrollToTop();
initHamburger();
