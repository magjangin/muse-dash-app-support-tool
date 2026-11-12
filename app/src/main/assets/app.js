'use strict';

// 뮤즈대시 모드 비서 화면. 네이티브 쪽은 window.AndroidBridge(AndroidBridge.java)이고,
// 오래 걸리는 작업 결과는 window.on* 콜백으로 돌아온다(MainActivity.callJs).
// 서버(hwa.moe)·파일에서 온 문자열은 innerHTML에 넣지 않고 textContent로만 넣는다.

const Bridge = window.AndroidBridge || null;

function call(name, ...args) {
  if (!Bridge || typeof Bridge[name] !== 'function') return null;
  try { return Bridge[name](...args); } catch (e) { console.error(name, e); return null; }
}
function callJson(name, fallback, ...args) {
  const raw = call(name, ...args);
  if (raw == null || raw === '') return fallback;
  try { return JSON.parse(raw); } catch (e) { return fallback; }
}
function toast(msg) { if (!call('showToast', msg)) console.log('[toast]', msg); }

function el(tag, props, ...children) {
  const n = document.createElement(tag);
  if (props) {
    for (const [k, v] of Object.entries(props)) {
      if (v == null || v === false) continue;
      if (k === 'class') n.className = v;
      else if (k === 'text') n.textContent = v;
      else if (k === 'dataset') Object.assign(n.dataset, v);
      else if (k.startsWith('on') && typeof v === 'function') n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v === true ? '' : v);
    }
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    n.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return n;
}
const $ = (id) => document.getElementById(id);

function formatBytes(b) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
  if (b < 1073741824) return (b / 1048576).toFixed(1) + ' MB';
  return (b / 1073741824).toFixed(2) + ' GB';
}
function formatDate(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
}
const norm = (s) => String(s || '').trim().toLowerCase();

// ---------------------------------------------------------------- 확인 창

let modalResolve = null;
function confirmBox(title, msg, okLabel = '확인', danger = true) {
  $('modalTitle').textContent = title;
  $('modalMsg').textContent = msg;
  const ok = $('modalOk');
  ok.textContent = okLabel;
  ok.className = 'btn ' + (danger ? 'btn-danger' : 'btn-primary');
  $('modal').hidden = false;
  return new Promise((resolve) => { modalResolve = resolve; });
}
function closeModal(result) {
  $('modal').hidden = true;
  const r = modalResolve;
  modalResolve = null;
  if (r) r(result);
}

// ---------------------------------------------------------------- 앱 상태

const state = {
  app: {},
  songs: [],
  albums: null,
  albumsLoadedAt: 0,
  albumsError: null,
  storeFilter: 'all',
  storeQuery: '',
  downloads: {},   // albumId -> percent (-1 = 크기 모름)
  deleting: {},    // folder(lower) -> true
  packages: [],
  pkgStatus: {},   // path -> {msg, pct, busy}
};

function refreshAppState() {
  state.app = callJson('getAppState', {});
  $('permBanner').hidden = !Bridge || state.app.storage !== false;
  $('appVersion').textContent = state.app.version ? 'v' + state.app.version : '';
  const gs = $('gameStatus');
  if (state.app.gameInstalled) {
    gs.textContent = '뮤즈대시 모드 설치됨' + (state.app.gameVersion ? ` (v${state.app.gameVersion})` : '');
    gs.className = 'hero-status ok';
  } else {
    gs.textContent = '뮤즈대시 모드 미설치 — XAPK 탭에서 설치하세요';
    gs.className = 'hero-status bad';
  }
}

// ---------------------------------------------------------------- 설정

const OPTIONS = [
  { id: 'autoPlay', group: 'play', name: '오토플레이', desc: '수면 린 베개 모션으로 차트를 자동 연주합니다.' },
  { id: 'forcePerfect', group: 'play', name: '강제퍼펙트', desc: '판정을 무조건 Perfect로 올립니다.' },
  { id: 'blockFever', group: 'play', name: '피버충전금지', desc: '피버 게이지가 차오르지 않게 고정합니다.' },
  { id: 'enableCinema', group: 'visual', name: '시네마 (BGA 비디오)', desc: '커스텀 곡 배경에 MP4 비디오를 재생합니다.' },
  { id: 'showGhostNotes', group: 'visual', name: '고스트노트보이기', desc: '고스트 노트를 투명하지 않게 보여 줍니다. 곡 info.txt에 값이 있으면 그 값이 우선입니다.' },
];
const OWNER_CMD = 'adb shell content update --uri content://media/external/file --bind owner_package_name:s:com.prpr.musedash.mod --where "_data=\'/storage/emulated/0/MuseDash/config.txt\'"';

function buildSettings() {
  for (const o of OPTIONS) {
    const input = el('input', { type: 'checkbox', id: 'opt_' + o.id });
    input.addEventListener('change', () => onToggle(o, input));
    document.querySelector(`.settings[data-group="${o.group}"]`).append(
      el('label', { class: 'setting', for: 'opt_' + o.id },
        el('div', null,
          el('div', { class: 'setting-name', text: o.name }),
          el('div', { class: 'setting-desc', text: o.desc })),
        el('span', { class: 'switch' }, input, el('span', { class: 'slider' }))));
  }
}

function applyConfig(st) {
  if (!st || !st.values) return;
  for (const o of OPTIONS) {
    const input = $('opt_' + o.id);
    if (input && typeof st.values[o.id] === 'boolean') input.checked = st.values[o.id];
  }
  const box = $('cfgLinkStatus');
  box.replaceChildren();
  if (st.gameReadable) {
    box.className = 'link-status ok';
    box.append(el('span', { class: 'dot' }), el('div', { text: '게임 연동됨 — 토글을 바꾸면 바로 저장되고, 게임이 1초 안에 반영합니다.' }));
  } else {
    box.className = 'link-status bad';
    box.append(el('span', { class: 'dot' }), el('div', null,
      `게임이 설정 파일을 읽을 수 없습니다 (파일 소유자: ${st.owner || (st.exists ? '알 수 없음' : '파일 없음')}). PC에서 한 번만 실행하세요:`,
      el('code', { text: OWNER_CMD })));
  }
}

function loadConfig() {
  applyConfig(callJson('getConfig', null));
}

function onToggle(option, input) {
  const st = callJson('setConfigOption', null, option.id, input.checked);
  if (!st || !st.saved) {
    input.checked = !input.checked;
    toast('저장 실패: ' + ((st && (st.error || st.readError)) || '알 수 없는 오류'));
    return;
  }
  applyConfig(st);
}

async function resetConfig() {
  const ok = await confirmBox('기본값으로 되돌리기', '토글 5개를 기본값(강제퍼펙트·시네마·고스트노트 켜짐)으로 되돌립니다.\n파일의 다른 키(지연·오프셋 등)는 그대로 둡니다.', '되돌리기', false);
  if (!ok) return;
  const st = callJson('resetConfig', null);
  if (st && st.saved) { applyConfig(st); toast('기본값으로 저장했습니다.'); }
  else toast('저장 실패: ' + ((st && st.error) || '알 수 없는 오류'));
}

// ---------------------------------------------------------------- 내 곡

function refreshSongs() {
  state.songs = callJson('listSongs', []) || [];
  renderSongs();
  renderPending();
}

function coverUrl(folder) {
  return 'https://assistant.local/cover/' + encodeURIComponent(folder);
}

function thumb(src, square) {
  const fallback = el('div', { class: 'thumb thumb-fallback' + (square ? ' square' : ''), text: '♪' });
  if (!src) return fallback;
  const img = el('img', { class: 'thumb' + (square ? ' square' : ''), src, loading: 'lazy', alt: '' });
  img.addEventListener('error', () => img.replaceWith(fallback));
  return img;
}

function renderSongs() {
  const list = $('songList');
  list.replaceChildren();
  $('songCount').textContent = state.songs.length ? `(${state.songs.length})` : '';
  if (!state.songs.length) {
    list.append(el('div', { class: 'empty', text: '기기에 커스텀 곡이 없습니다. hwa.moe 탭에서 받아 보세요.' }));
    return;
  }
  for (const s of state.songs) {
    const busy = !!state.deleting[norm(s.folder)];
    list.append(el('div', { class: 'item' },
      thumb(s.hasCover ? coverUrl(s.folder) : null, true),
      el('div', { class: 'item-body' },
        el('div', { class: 'item-title', text: s.title }),
        el('div', { class: 'item-sub', text: `${s.artist} · ${s.folder}` }),
        el('div', { class: 'meta' },
          s.bmsCount > 0 && el('span', { class: 'badge cyan', text: `BMS ${s.bmsCount}` }),
          s.hasMusic && el('span', { class: 'badge pink', text: 'BGM' }),
          s.hasVideo && el('span', { class: 'badge', text: 'BGA' }),
          s.pendingReset && el('span', { class: 'badge amber', text: '기록 초기화 예약' }))),
      el('div', { class: 'item-actions' },
        el('button', { class: 'btn btn-small btn-ghost', disabled: s.pendingReset || busy, text: '기록 리셋', onclick: () => resetSongRecord(s) }),
        el('button', { class: 'btn btn-small btn-danger-soft', disabled: busy, text: busy ? '삭제 중…' : '삭제', onclick: () => deleteSong(s.folder, s.title) }))));
  }
}

function renderPending() {
  const pending = callJson('getPendingRecordResets', []) || [];
  const box = $('pendingResets');
  box.hidden = pending.length === 0;
  box.replaceChildren();
  if (!pending.length) return;
  const label = pending.includes('ALL') ? '전체 커스텀 곡' : `${pending.length}곡`;
  box.append(
    el('span', { text: `⏳ 기록 초기화 예약: ${label} — 뮤즈대시를 실행하면 적용됩니다.` }),
    el('button', { class: 'btn btn-small btn-ghost', text: '예약 취소', onclick: cancelResets }));
}

async function deleteSong(folder, title) {
  const ok = await confirmBox('곡 삭제', `'${title}' 곡을 기기에서 지웁니다.\n게임 폴더의 사본은 게임을 다음에 켤 때 함께 지워집니다.`, '삭제');
  if (!ok) return;
  state.deleting[norm(folder)] = true;
  renderSongs();
  renderStore();
  call('deleteSong', folder);
}

window.onSongDeleted = (folder) => {
  delete state.deleting[norm(folder)];
  refreshSongs();
  renderStore();
};

async function resetSongRecord(s) {
  const ok = await confirmBox('곡 기록 초기화', `'${s.title}'의 모든 난이도 기록(점수·정확도·풀콤보)을 지웁니다.\n뮤즈대시를 실행할 때 실제로 지워집니다.`, '초기화');
  if (!ok) return;
  call('resetSongRecord', s.folder);
  refreshSongs();
}

async function resetAllRecords() {
  const ok = await confirmBox('전체 기록 초기화', '모든 커스텀 곡의 로컬 플레이 기록(점수·정확도·풀콤보)을 지웁니다.\n뮤즈대시를 실행할 때 실제로 지워집니다.', '전체 초기화');
  if (!ok) return;
  call('resetAllRecords');
  refreshSongs();
}

function cancelResets() {
  call('cancelRecordResets');
  refreshSongs();
}

// ---------------------------------------------------------------- hwa.moe

const SUPABASE_ALBUMS_URL = 'https://dhoisshndrljjvlruvuk.supabase.co/rest/v1/albums?select=*&order=created_at.desc';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRob2lzc2huZHJsamp2bHJ1dnVrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE2MzQyMDIsImV4cCI6MjA5NzIxMDIwMn0.wuTmjb8_k_-YsCmSxFdmpSznCLKt8X_R_9U4hh9tMaY';

async function fetchAlbums(force) {
  if (!force && state.albums && Date.now() - state.albumsLoadedAt < 60000) { renderStore(); return; }
  if (!state.albums) {
    const list = $('albumList');
    list.replaceChildren(...[0, 1, 2, 3].map(() => el('div', { class: 'skeleton' })));
  }
  try {
    const r = await fetch(SUPABASE_ALBUMS_URL, { headers: { apikey: SUPABASE_ANON_KEY, Authorization: 'Bearer ' + SUPABASE_ANON_KEY } });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const data = await r.json();
    state.albums = Array.isArray(data) ? data.filter((a) => a && !a.is_disabled && a.id != null) : [];
    state.albumsLoadedAt = Date.now();
    state.albumsError = null;
  } catch (e) {
    state.albumsError = e.message || String(e);
  }
  renderStore();
}

function albumFolder(a) {
  return String(a.download_filename || '').replace(/\.zip$/i, '').trim() || String(a.title || '').trim();
}

/** 받은 곡 찾기: 폴더 이름 또는 info.txt 제목이 앨범 파일 이름·제목과 같으면 받은 것으로 본다. */
function installedSongFor(a) {
  const keys = [norm(albumFolder(a)), norm(a.title)].filter(Boolean);
  return state.songs.find((s) => keys.includes(norm(s.folder)) || keys.includes(norm(s.title)));
}

function renderStore() {
  const list = $('albumList');
  if (!list) return;
  if (state.albums == null) {
    if (state.albumsError) list.replaceChildren(storeError());
    return;
  }
  const q = norm(state.storeQuery);
  const rows = [];
  let shown = 0;
  for (const a of state.albums) {
    const song = installedSongFor(a);
    if (state.storeFilter === 'new' && song) continue;
    if (state.storeFilter === 'installed' && !song) continue;
    if (q) {
      const hay = [a.title, a.artist, ...(Array.isArray(a.tags) ? a.tags : [])].map(norm).join(' ');
      if (!hay.includes(q)) continue;
    }
    shown++;
    rows.push(albumRow(a, song));
  }
  $('storeCount').textContent = `${shown} / ${state.albums.length}곡`;
  if (state.albumsError) rows.unshift(storeError());
  if (!rows.length) rows.push(el('div', { class: 'empty', text: q ? '검색 결과가 없습니다.' : '표시할 곡이 없습니다.' }));
  list.replaceChildren(...rows);
}

function storeError() {
  return el('div', { class: 'empty' }, `hwa.moe 목록을 불러오지 못했습니다 (${state.albumsError}). `,
    el('button', { class: 'btn btn-small btn-ghost', text: '다시 시도', onclick: () => fetchAlbums(true) }));
}

function albumRow(a, song) {
  const id = String(a.id);
  const dl = state.downloads[id];
  const tags = (Array.isArray(a.tags) ? a.tags : []).slice(0, 4);
  let actions;
  if (dl !== undefined) {
    actions = el('div', { class: 'item-actions' }, el('button', { class: 'btn btn-small btn-ghost', disabled: true, text: dl >= 0 ? `${dl}%` : '받는 중' }));
  } else if (song) {
    const busy = !!state.deleting[norm(song.folder)];
    actions = el('div', { class: 'item-actions' },
      el('button', { class: 'btn btn-small btn-ok', disabled: true, text: '✓ 받음' }),
      el('button', { class: 'btn btn-small btn-danger-soft', disabled: busy, text: busy ? '삭제 중…' : '삭제', onclick: () => deleteSong(song.folder, a.title || song.title) }));
  } else {
    actions = el('div', { class: 'item-actions' }, el('button', { class: 'btn btn-small btn-primary', text: '⬇ 받기', onclick: () => downloadAlbum(a) }));
  }
  const body = el('div', { class: 'item-body' },
    el('div', { class: 'item-title', text: a.title || '(제목 없음)' }),
    a.artist && el('div', { class: 'item-sub', text: a.artist }),
    el('div', { class: 'meta' },
      el('span', { class: 'badge amber', text: 'Lv.' + (a.difficulty != null && a.difficulty !== '' ? a.difficulty : '?') }),
      tags.map((t) => el('span', { class: 'badge', text: String(t) }))));
  if (dl !== undefined) {
    const bar = el('div', { class: 'progress' + (dl < 0 ? ' indeterminate' : ''), id: 'prog_' + id }, el('div'));
    if (dl >= 0) bar.firstChild.style.width = dl + '%';
    body.append(bar);
  }
  const coverSrc = typeof a.cover_url === 'string' && /^https:\/\//.test(a.cover_url) ? a.cover_url : null;
  return el('div', { class: 'item', dataset: { album: id } }, thumb(coverSrc, false), body, actions);
}

function downloadAlbum(a) {
  const id = String(a.id);
  state.downloads[id] = -1;
  renderStore();
  call('downloadSong', id, String(a.title || ''), String(a.download_filename || ''));
}

window.onSongProgress = (id, pct) => {
  if (state.downloads[id] === undefined) return;
  const changedMode = (state.downloads[id] < 0) !== (pct < 0);
  state.downloads[id] = pct;
  const bar = $('prog_' + id);
  if (!bar || changedMode) { renderStore(); return; }
  bar.firstChild.style.width = pct + '%';
  const btn = document.querySelector(`[data-album="${CSS.escape(String(id))}"] .item-actions button`);
  if (btn) btn.textContent = pct >= 0 ? `${pct}%` : '받는 중';
};

window.onSongInstalled = (id) => {
  delete state.downloads[id];
  refreshSongs();
  renderStore();
};

window.onSongDownloadFailed = (id) => {
  delete state.downloads[id];
  renderStore();
};

// ---------------------------------------------------------------- XAPK

function scanPackages() {
  state.packages = callJson('scanPackages', []) || [];
  state.packages.sort((x, y) => (y.isMuseDash - x.isMuseDash) || (y.modified - x.modified));
  renderPackages();
}

function renderPackages() {
  const list = $('packageList');
  list.replaceChildren();
  if (!state.packages.length) {
    list.append(el('div', { class: 'empty', text: '찾은 XAPK / APK 파일이 없습니다.' }));
    return;
  }
  for (const f of state.packages) {
    const st = state.pkgStatus[f.path];
    const body = el('div', { class: 'item-body' },
      el('div', { class: 'item-title', text: f.name }),
      el('div', { class: 'meta' },
        el('span', { class: 'badge ' + (f.isXapk ? 'pink' : 'cyan'), text: f.isXapk ? 'XAPK' : 'APK' }),
        el('span', { class: 'badge', text: formatBytes(f.size) }),
        f.modified && el('span', { class: 'badge', text: formatDate(f.modified) }),
        f.isMuseDash && el('span', { class: 'badge green', text: 'Muse Dash' })));
    if (st) {
      const bar = el('div', { class: 'progress' + (st.pct < 0 || st.pct == null ? ' indeterminate' : '') }, el('div'));
      if (st.pct >= 0) bar.firstChild.style.width = st.pct + '%';
      body.append(el('div', { class: 'status-line', text: st.msg + (st.pct >= 0 ? ` (${st.pct}%)` : '') }));
      if (st.busy) body.append(bar);
    }
    list.append(el('div', { class: 'item' + (f.isMuseDash ? ' highlight' : '') },
      el('div', { class: 'thumb thumb-fallback square', text: f.isXapk ? '📦' : '🤖' }),
      body,
      el('div', { class: 'item-actions' },
        el('button', { class: 'btn btn-small btn-primary', disabled: st && st.busy, text: st && st.busy ? '처리 중' : '설치', onclick: () => installPackage(f) }))));
  }
}

async function installPackage(f) {
  if (f.isXapk) {
    const ok = await confirmBox('XAPK 설치', `${f.name}\n\nOBB를 Android/obb 폴더에 넣은 뒤 시스템 설치 화면을 엽니다. OBB가 크면 몇 분 걸립니다.`, '설치', false);
    if (!ok) return;
  }
  state.pkgStatus[f.path] = { msg: f.isXapk ? 'XAPK 여는 중…' : '설치 화면 여는 중…', pct: -1, busy: true };
  renderPackages();
  call('installPackage', f.path);
}

let pkgRenderTimer = null;
window.onPackageStatus = (path, msg, pct) => {
  state.pkgStatus[path] = { msg, pct, busy: true };
  if (pkgRenderTimer) return;
  pkgRenderTimer = setTimeout(() => { pkgRenderTimer = null; renderPackages(); }, 150);
};

window.onPackageDone = (path, ok) => {
  const prev = state.pkgStatus[path];
  state.pkgStatus[path] = { msg: ok ? '설치 화면을 열었습니다' : (prev && prev.busy ? prev.msg + ' — 중단됨' : '중단됨'), pct: -1, busy: false };
  renderPackages();
};

// ---------------------------------------------------------------- 탭 / 이벤트

function currentTab() {
  const t = document.querySelector('.tab.active');
  return t ? t.id : 'tab-config';
}

function showTab(id) {
  if (!$(id)) id = 'tab-config';
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.id === id));
  document.querySelectorAll('.tabbtn').forEach((b) => b.classList.toggle('active', b.dataset.tab === id));
  try { localStorage.setItem('tab', id); } catch (e) { /* 저장 못 해도 동작에는 영향 없음 */ }
  window.scrollTo(0, 0);
  onTabShown(id, false);
}

function onTabShown(id, force) {
  if (id === 'tab-config') loadConfig();
  if (id === 'tab-store') { refreshSongs(); fetchAlbums(force); }
  if (id === 'tab-songs') { refreshAppState(); refreshSongs(); }
  if (id === 'tab-xapk') scanPackages();
}

const ACTIONS = {
  requestPermission: () => call('requestStoragePermission'),
  reloadConfig: () => { loadConfig(); toast('설정 파일을 다시 읽었습니다.'); },
  resetConfig,
  launchGame: () => call('launchGame'),
  resetAllRecords,
  scanPackages,
  modalOk: () => closeModal(true),
  modalCancel: () => closeModal(false),
};

document.addEventListener('click', (e) => {
  const tabBtn = e.target.closest('.tabbtn');
  if (tabBtn) { showTab(tabBtn.dataset.tab); return; }
  const chip = e.target.closest('.chip[data-filter]');
  if (chip) {
    state.storeFilter = chip.dataset.filter;
    document.querySelectorAll('.chip[data-filter]').forEach((c) => c.classList.toggle('active', c === chip));
    renderStore();
    return;
  }
  const act = e.target.closest('[data-action]');
  if (act && ACTIONS[act.dataset.action]) ACTIONS[act.dataset.action]();
});

$('modal').addEventListener('click', (e) => { if (e.target === $('modal')) closeModal(false); });
$('storeSearch').addEventListener('input', (e) => { state.storeQuery = e.target.value; renderStore(); });
$('refreshAllBtn').addEventListener('click', () => { refreshAppState(); onTabShown(currentTab(), true); });

window.onAppResume = () => {
  refreshAppState();
  onTabShown(currentTab(), false);
};

window.onBackPressed = () => {
  if (!$('modal').hidden) { closeModal(false); return true; }
  if (currentTab() !== 'tab-config') { showTab('tab-config'); return true; }
  return false;
};

// ---------------------------------------------------------------- 시작

buildSettings();
refreshAppState();
loadConfig();
refreshSongs();
let startTab = 'tab-config';
try { startTab = localStorage.getItem('tab') || startTab; } catch (e) { /* 기본 탭 */ }
showTab(startTab);
