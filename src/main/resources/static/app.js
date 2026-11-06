'use strict';

/*
 * Minimal vanilla-JS client for the DatingApp API.
 * - Token is kept in sessionStorage, so every browser tab can be logged in as a different user.
 * - All user-supplied text is rendered with textContent (never innerHTML) to avoid XSS.
 */

const PAGE_SIZE = 20;
const MESSAGE_PAGE_SIZE = 50;

const state = {
    token: sessionStorage.getItem('token'),
    me: null,
    ws: null,
    wsRetry: null,
    candidates: [],
    matches: [],
    activeMatchId: null,
    chat: [],            // messages of the open match, ordered by id
    unread: {},          // matchId -> count of messages received while the chat wasn't open
};

const $ = (sel) => document.querySelector(sel);

/** Small DOM builder: el('div', {class: 'x'}, 'text', childNode). Strings become text nodes. */
function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
        if (v === null || v === undefined || v === false) continue;
        if (k === 'class') node.className = v;
        else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
        else node.setAttribute(k, v === true ? '' : v);
    }
    for (const c of children.flat()) {
        if (c === null || c === undefined || c === false) continue;
        node.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return node;
}

function toast(message, kind = '') {
    const t = el('div', {class: `toast ${kind}`}, message);
    const box = $('#toasts');
    box.append(t);
    while (box.children.length > 3) box.firstElementChild.remove();   // keep the stack short
    setTimeout(() => t.remove(), kind === 'error' ? 6000 : 4000);
}

/* ====================== API ====================== */

class ApiError extends Error {
    constructor(status, problem) {
        const fieldErrors = problem && problem.errors
            ? Object.entries(problem.errors).map(([f, m]) => `${f}: ${m}`).join('; ')
            : '';
        const detail = (problem && (problem.detail || problem.title)) || `Request failed (${status})`;
        super(fieldErrors ? `${detail} — ${fieldErrors}` : detail);
        this.status = status;
    }
}

async function api(path, {method = 'GET', body} = {}) {
    const headers = {};
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    const res = await fetch(path, {method, headers, body: body !== undefined ? JSON.stringify(body) : undefined});
    const text = await res.text();
    let data = null;
    if (text) {
        try { data = JSON.parse(text); } catch { data = null; }
    }
    if (!res.ok) {
        // Token expired or account deleted elsewhere: back to the login screen.
        if (res.status === 401 && state.token && !path.startsWith('/api/auth/')) {
            logout('Your session has expired. Please log in again.');
        }
        throw new ApiError(res.status, data);
    }
    return data;
}

/** Wrap an async handler so failures show a toast and buttons can't be double-submitted. */
function guarded(fn) {
    return async (event) => {
        if (event) event.preventDefault();
        const btn = event && (event.submitter || (event.currentTarget instanceof HTMLButtonElement ? event.currentTarget : null));
        if (btn) btn.disabled = true;
        try {
            await fn(event);
        } catch (e) {
            toast(e.message || String(e), 'error');
        } finally {
            if (btn) btn.disabled = false;
        }
    };
}

/* ====================== Auth ====================== */

function showAuthTab(which) {
    const login = which === 'login';
    $('#tab-login').setAttribute('aria-selected', String(login));
    $('#tab-register').setAttribute('aria-selected', String(!login));
    $('#login-form').hidden = !login;
    $('#register-form').hidden = login;
}

function parseInterests(text) {
    return (text || '').split(',').map(s => s.trim()).filter(Boolean);
}

function numberOrNull(value) {
    return value === '' || value === null || value === undefined ? null : Number(value);
}

async function onAuthSuccess(auth) {
    state.token = auth.accessToken;
    sessionStorage.setItem('token', auth.accessToken);
    state.me = auth.user;
    await enterApp();
}

const handleLogin = guarded(async (e) => {
    const f = new FormData(e.target);
    const auth = await api('/api/auth/login', {
        method: 'POST',
        body: {email: f.get('email'), password: f.get('password')},
    });
    e.target.reset();
    await onAuthSuccess(auth);
    toast(`Welcome back, ${auth.user.name}!`, 'success');
});

const handleRegister = guarded(async (e) => {
    const f = new FormData(e.target);
    const body = {
        email: f.get('email'),
        password: f.get('password'),
        name: f.get('name'),
        dateOfBirth: f.get('dateOfBirth') || null,
        gender: f.get('gender'),
        preferredGender: f.get('preferredGender') || null,
        latitude: numberOrNull(f.get('latitude')),
        longitude: numberOrNull(f.get('longitude')),
        interests: parseInterests(f.get('interests')),
        bio: f.get('bio') || null,
        maxDistanceKm: numberOrNull(f.get('maxDistanceKm')),
        minAge: numberOrNull(f.get('minAge')),
        maxAge: numberOrNull(f.get('maxAge')),
    };
    const auth = await api('/api/auth/register', {method: 'POST', body});
    await onAuthSuccess(auth);
    toast(`Account created. Welcome, ${auth.user.name}!`, 'success');
});

function logout(message) {
    state.token = null;
    state.me = null;
    sessionStorage.removeItem('token');
    disconnectWs();
    Object.assign(state, {candidates: [], matches: [], activeMatchId: null, chat: [], unread: {}});
    $('#me-name').textContent = '';
    closeChat();
    $('#match-list').replaceChildren();
    $('#candidate-area').replaceChildren();
    $('#app-view').hidden = true;
    $('#auth-view').hidden = false;
    showAuthTab('login');
    if (message) toast(message);
}

/* ====================== App shell ====================== */

async function enterApp() {
    if (!state.me) state.me = await api('/api/users/me');
    $('#me-name').textContent = state.me.name;
    $('#auth-view').hidden = true;
    $('#app-view').hidden = false;
    connectWs();
    showTab('discover');
    await Promise.all([loadCandidates(), loadMatches()]);
}

function showTab(name) {
    document.querySelectorAll('.tabs [data-tab]').forEach(b => {
        b.setAttribute('aria-selected', String(b.dataset.tab === name));
    });
    for (const p of ['discover', 'matches', 'profile']) {
        $(`#panel-${p}`).hidden = p !== name;
    }
    if (name === 'profile') fillProfileForm();
    if (name === 'matches') {
        // Messages that arrived for the open chat while another tab was showing are now visible.
        if (state.activeMatchId && state.unread[state.activeMatchId]) {
            delete state.unread[state.activeMatchId];
            markRead();
        }
        loadMatches().catch(e => toast(e.message, 'error'));
    }
    if (name === 'discover' && state.candidates.length === 0) loadCandidates().catch(e => toast(e.message, 'error'));
}

function avatar(profile) {
    const box = el('span', {class: 'avatar', 'aria-hidden': 'true'});
    if (profile.profilePictureUrl) {
        box.append(el('img', {src: profile.profilePictureUrl, alt: ''}));
    } else {
        box.textContent = (profile.name || '?').charAt(0).toUpperCase();
    }
    return box;
}

/* ====================== Discover ====================== */

async function loadCandidates() {
    // Already-swiped users drop out of the results, so page 0 always holds the next batch.
    state.candidates = await api(`/api/users/me/potential-matches?page=0&size=${PAGE_SIZE}`);
    renderCandidate();
}

function renderCandidate() {
    const area = $('#candidate-area');
    area.replaceChildren();
    const c = state.candidates[0];
    if (!c) {
        area.append(el('div', {class: 'empty-state'},
            el('div', {class: 'big', 'aria-hidden': 'true'}, '🌍'),
            el('p', {}, 'No more people nearby who match your preferences.'),
            el('p', {class: 'muted'}, 'Try widening your distance or age range in your profile, or register more users.'),
            el('button', {class: 'btn ghost', type: 'button', onclick: guarded(() => loadCandidates())}, 'Refresh')));
        return;
    }

    const photo = el('div', {class: 'photo'});
    if (c.profilePictureUrl) photo.append(el('img', {src: c.profilePictureUrl, alt: `Photo of ${c.name}`}));
    else photo.append(el('span', {'aria-hidden': 'true'}, c.name.charAt(0).toUpperCase()));

    area.append(el('article', {class: 'candidate', 'aria-label': `${c.name}, ${c.age}`},
        photo,
        el('div', {class: 'info'},
            el('h2', {}, `${c.name}, ${c.age}`),
            el('p', {class: 'meta'}, `${formatGender(c.gender)} · ${c.distanceKm} km away`),
            c.bio ? el('p', {class: 'bio'}, c.bio) : null,
            c.interests.length ? el('ul', {class: 'chips', 'aria-label': 'Interests'}, c.interests.map(i => el('li', {}, i))) : null),
        el('div', {class: 'actions'},
            el('button', {class: 'btn pass', type: 'button', onclick: guarded((e) => swipe(c, false))}, '✕ Pass'),
            el('button', {class: 'btn like', type: 'button', onclick: guarded((e) => swipe(c, true))}, '♥ Like'))));
}

async function swipe(candidate, liked) {
    try {
        const res = await api('/api/swipes', {method: 'POST', body: {swipedId: candidate.id, liked}});
        if (res.matched) {
            // The MATCH WebSocket push also announces this; only toast here if the socket is down.
            if (!wsOpen()) toast(`💘 It's a match with ${candidate.name}!`, 'match');
            loadMatches().catch(() => {});
        }
    } catch (e) {
        if (e.status !== 409) throw e;   // 409 = already swiped (e.g. from another tab): just skip.
    }
    state.candidates.shift();
    if (state.candidates.length === 0) await loadCandidates();
    else renderCandidate();
}

function formatGender(g) {
    return {MALE: 'Man', FEMALE: 'Woman', NON_BINARY: 'Non-binary'}[g] || g;
}

/* ====================== Matches ====================== */

async function loadMatches() {
    state.matches = await api('/api/matches');
    // The open chat may have been unmatched by the other user.
    if (state.activeMatchId && !state.matches.some(m => m.matchId === state.activeMatchId)) {
        closeChat();
    }
    renderMatches();
}

function renderMatches() {
    const list = $('#match-list');
    list.replaceChildren();
    if (state.matches.length === 0) {
        list.append(el('li', {class: 'empty'}, 'No matches yet. Start liking people in Discover!'));
    }
    for (const m of state.matches) {
        const count = state.unread[m.matchId] || 0;
        list.append(el('li', {},
            el('button', {
                type: 'button',
                'aria-current': m.matchId === state.activeMatchId ? 'true' : null,
                onclick: guarded(() => openChat(m.matchId)),
            },
                avatar(m.matchedUser),
                el('span', {class: 'name'}, m.matchedUser.name),
                count ? el('span', {class: 'badge', 'aria-label': `${count} unread`}, count) : null)));
    }
    const total = Object.values(state.unread).reduce((a, b) => a + b, 0);
    const badge = $('#unread-total');
    badge.hidden = total === 0;
    badge.textContent = total;
}

function matchById(id) {
    return state.matches.find(m => m.matchId === id);
}

/* ====================== Chat ====================== */

async function openChat(matchId) {
    const match = matchById(matchId);
    if (!match) return;
    state.activeMatchId = matchId;
    state.chat = [];
    delete state.unread[matchId];
    renderMatches();

    $('#chat-empty').hidden = true;
    $('#chat-inner').hidden = false;
    $('#chat-title').replaceChildren(avatar(match.matchedUser),
        el('span', {}, `${match.matchedUser.name}, ${match.matchedUser.age} · ${match.matchedUser.distanceKm} km`));
    $('#message-list').replaceChildren();

    const page = await api(`/api/matches/${matchId}/messages?size=${MESSAGE_PAGE_SIZE}`);
    if (state.activeMatchId !== matchId) return;   // user switched chats meanwhile
    addMessages(page);
    $('#load-older').hidden = page.length < MESSAGE_PAGE_SIZE;
    scrollToBottom();
    markRead();
    $('#message-input').focus();
}

function closeChat() {
    state.activeMatchId = null;
    state.chat = [];
    $('#chat-inner').hidden = true;
    $('#chat-empty').hidden = false;
}

/** Merge messages into the open chat (de-duplicated by id, kept in id order) and re-render. */
function addMessages(messages) {
    const ids = new Set(state.chat.map(m => m.id));
    for (const m of messages) if (!ids.has(m.id)) state.chat.push(m);
    state.chat.sort((a, b) => a.id - b.id);
    renderMessages();
}

function renderMessages() {
    const list = $('#message-list');
    list.replaceChildren(...state.chat.map(m => {
        const mine = m.senderId === state.me.id;
        const sent = new Date(m.sentAt);
        return el('li', {class: `msg ${mine ? 'mine' : ''}`},
            m.content,
            el('time', {datetime: m.sentAt}, sent.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})));
    }));
}

function scrollToBottom() {
    const box = $('#messages');
    box.scrollTop = box.scrollHeight;
}

const loadOlder = guarded(async () => {
    if (!state.activeMatchId || state.chat.length === 0) return;
    const matchId = state.activeMatchId;
    const box = $('#messages');
    const prevHeight = box.scrollHeight;
    const page = await api(`/api/matches/${matchId}/messages?beforeId=${state.chat[0].id}&size=${MESSAGE_PAGE_SIZE}`);
    if (state.activeMatchId !== matchId) return;
    addMessages(page);
    $('#load-older').hidden = page.length < MESSAGE_PAGE_SIZE;
    box.scrollTop = box.scrollHeight - prevHeight;   // keep the view where it was
});

let markReadTimer = null;
function markRead() {
    clearTimeout(markReadTimer);
    const matchId = state.activeMatchId;
    markReadTimer = setTimeout(() => {
        if (matchId) api(`/api/matches/${matchId}/messages/read`, {method: 'POST'}).catch(() => {});
    }, 300);
}

const sendMessage = guarded(async () => {
    const input = $('#message-input');
    const content = input.value.trim();
    if (!content || !state.activeMatchId) return;

    if (wsOpen()) {
        // The server echoes the saved message back to all of our sessions, which renders it.
        state.ws.send(JSON.stringify({type: 'CHAT', matchId: state.activeMatchId, content}));
    } else {
        const saved = await api(`/api/matches/${state.activeMatchId}/messages`, {method: 'POST', body: {content}});
        addMessages([saved]);
        scrollToBottom();
    }
    input.value = '';
    input.focus();
});

const unmatch = guarded(async () => {
    const match = matchById(state.activeMatchId);
    if (!match || !confirm(`Unmatch ${match.matchedUser.name}? You won't be able to message each other any more.`)) return;
    await api(`/api/matches/${match.matchId}/unmatch`, {method: 'POST'});
    closeChat();
    await loadMatches();
    toast(`Unmatched ${match.matchedUser.name}.`);
});

/* ====================== WebSocket ====================== */

function wsOpen() {
    return state.ws && state.ws.readyState === WebSocket.OPEN;
}

function setWsStatus(online) {
    const dot = $('#ws-status');
    dot.classList.toggle('online', online);
    const label = `Real-time: ${online ? 'connected' : 'disconnected'}`;
    dot.title = label;
    dot.setAttribute('aria-label', label);
}

function connectWs() {
    disconnectWs();
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(`${scheme}://${location.host}/ws/chat?access_token=${encodeURIComponent(state.token)}`);
    state.ws = ws;

    ws.onopen = () => setWsStatus(true);
    ws.onclose = () => {
        setWsStatus(false);
        // Reconnect while still logged in (e.g. after a server restart).
        if (state.ws === ws && state.token) state.wsRetry = setTimeout(connectWs, 3000);
    };
    ws.onmessage = (event) => {
        let msg;
        try { msg = JSON.parse(event.data); } catch { return; }
        handleWsMessage(msg);
    };
}

function disconnectWs() {
    clearTimeout(state.wsRetry);
    if (state.ws) {
        const ws = state.ws;
        state.ws = null;      // prevents the onclose handler from reconnecting
        ws.close();
    }
    setWsStatus(false);
}

function handleWsMessage(msg) {
    switch (msg.type) {
        case 'CHAT': {
            const fromMe = msg.senderId === state.me.id;
            if (msg.matchId === state.activeMatchId) {
                const box = $('#messages');
                const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
                addMessages([{id: msg.messageId, matchId: msg.matchId, senderId: msg.senderId,
                    content: msg.content, sentAt: msg.sentAt, read: false}]);
                if (fromMe || nearBottom) scrollToBottom();
                if (!fromMe && !$('#panel-matches').hidden) markRead();
                else if (!fromMe) bumpUnread(msg);
            } else if (!fromMe) {
                bumpUnread(msg);
            }
            break;
        }
        case 'MATCH':
            toast(`💘 It's a match with ${msg.matchedUserName}!`, 'match');
            loadMatches().catch(() => {});
            break;
        case 'ERROR':
            toast(msg.message, 'error');
            break;
    }
}

function bumpUnread(msg) {
    state.unread[msg.matchId] = (state.unread[msg.matchId] || 0) + 1;
    renderMatches();
    const match = matchById(msg.matchId);
    const preview = msg.content.length > 60 ? msg.content.slice(0, 60) + '…' : msg.content;
    toast(`${match ? match.matchedUser.name : 'New message'}: ${preview}`);
    if (!match) loadMatches().catch(() => {});
}

/* ====================== Profile ====================== */

function fillProfileForm() {
    const me = state.me;
    const f = $('#profile-form');
    f.name.value = me.name;
    f.bio.value = me.bio || '';
    f.profilePictureUrl.value = me.profilePictureUrl || '';
    f.interests.value = me.interests.join(', ');
    f.latitude.value = me.latitude;
    f.longitude.value = me.longitude;
    f.preferredGender.value = me.preferredGender || '';
    f.maxDistanceKm.value = me.maxDistanceKm;
    f.minAge.value = me.minAge;
    f.maxAge.value = me.maxAge;
    $('#profile-meta').textContent =
        `${me.email} · ${formatGender(me.gender)}, ${me.age} · born ${me.dateOfBirth}`;
}

const saveProfile = guarded(async (e) => {
    const f = new FormData(e.target);
    const body = {
        name: f.get('name') || null,
        bio: f.get('bio'),
        profilePictureUrl: f.get('profilePictureUrl') || null,
        interests: parseInterests(f.get('interests')),
        latitude: numberOrNull(f.get('latitude')),
        longitude: numberOrNull(f.get('longitude')),
        preferredGender: f.get('preferredGender') || null,
        maxDistanceKm: numberOrNull(f.get('maxDistanceKm')),
        minAge: numberOrNull(f.get('minAge')),
        maxAge: numberOrNull(f.get('maxAge')),
    };
    state.me = await api('/api/users/me', {method: 'PUT', body});
    $('#me-name').textContent = state.me.name;
    fillProfileForm();
    await loadCandidates();   // preferences may have changed who shows up
    toast('Profile saved.', 'success');
});

const deleteAccount = guarded(async () => {
    if (!confirm('Permanently delete your account, matches and messages? This cannot be undone.')) return;
    await api('/api/users/me', {method: 'DELETE'});
    logout('Your account was deleted.');
});

function geolocate(event) {
    const button = event.currentTarget;
    const form = button.closest('form');
    if (!navigator.geolocation) {
        toast('This browser does not support location. Enter latitude and longitude manually.', 'error');
        return;
    }
    const original = button.textContent;
    button.disabled = true;
    button.textContent = '📍 Locating…';
    const done = () => { button.disabled = false; button.textContent = original; };

    navigator.geolocation.getCurrentPosition(
        (pos) => {
            done();
            form.latitude.value = pos.coords.latitude.toFixed(4);
            form.longitude.value = pos.coords.longitude.toFixed(4);
            toast('Location filled in.', 'success');
        },
        (err) => {
            done();
            const help = {
                [err.PERMISSION_DENIED]:
                    'Location permission was denied. Click the icon left of the address bar → Site settings → Location → Allow, '
                    + 'and on macOS enable your browser in System Settings → Privacy & Security → Location Services. Then reload.',
                [err.POSITION_UNAVAILABLE]:
                    'Your position is unavailable. On macOS, turn on Location Services for your browser and make sure Wi-Fi is on.',
                [err.TIMEOUT]:
                    'Finding your location timed out. Try again, or enter latitude and longitude manually.',
            }[err.code] || `Couldn't get your location: ${err.message}`;
            toast(help, 'error');
        },
        {timeout: 15000, maximumAge: 5 * 60 * 1000, enableHighAccuracy: false});
}

/* ====================== Wiring ====================== */

function init() {
    $('#tab-login').addEventListener('click', () => showAuthTab('login'));
    $('#tab-register').addEventListener('click', () => showAuthTab('register'));
    $('#login-form').addEventListener('submit', handleLogin);
    $('#register-form').addEventListener('submit', handleRegister);
    document.querySelectorAll('[data-geolocate]').forEach(b => b.addEventListener('click', geolocate));

    document.querySelectorAll('.tabs [data-tab]').forEach(b =>
        b.addEventListener('click', () => showTab(b.dataset.tab)));
    $('#logout').addEventListener('click', () => logout('Logged out.'));

    $('#message-form').addEventListener('submit', sendMessage);
    $('#load-older').addEventListener('click', loadOlder);
    $('#unmatch').addEventListener('click', unmatch);

    $('#profile-form').addEventListener('submit', saveProfile);
    $('#delete-account').addEventListener('click', deleteAccount);

    if (state.token) {
        enterApp().catch(() => logout());
    } else {
        $('#auth-view').hidden = false;
    }
}

init();
