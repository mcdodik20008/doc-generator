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
 * Show a toast notification.
 * @param {string} msg - Message text
 * @param {'error'|'success'} [type='error'] - Toast type
 */
function showToast(msg, type) {
    const existing = document.querySelector('.toast');
    if (existing) existing.remove();
    const t = document.createElement('div');
    t.className = 'toast ' + (type === 'success' ? 'toast-success' : 'toast-error');
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 6000);
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

// Init theme on load
initTheme();
