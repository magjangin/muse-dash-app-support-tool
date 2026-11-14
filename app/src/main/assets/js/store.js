'use strict';

// hwa.moe 탭: 앨범 목록(Supabase REST), 검색·태그·정렬, 받기 대기열, 모두 받기.

const SUPABASE_ALBUMS_URL = 'https://dhoisshndrljjvlruvuk.supabase.co/rest/v1/albums?select=*&order=created_at.desc';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRob2lzc2huZHJsamp2bHJ1dnVrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE2MzQyMDIsImV4cCI6MjA5NzIxMDIwMn0.wuTmjb8_k_-YsCmSxFdmpSznCLKt8X_R_9U4hh9tMaY';

// 한 번에 받는 곡 수. 곡 하나가 100MB를 넘기도 해서 나머지는 '대기 중'으로 줄 세운다.
const MAX_PARALLEL_DOWNLOADS = 2;

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

// ---------------------------------------------------------------- 앨범 정보

function albumFolder(a) {
  return String(a.download_filename || '').replace(/\.zip$/i, '').trim() || String(a.title || '').trim();
}

/** 목록에 크게 보일 이름. hwa.moe 제목 앞의 '뮤즈대시 커스텀 6레벨 ' 같은 머리말을 뗀다(레벨은 배지로 따로 보인다). */
function albumName(a) {
  const t = String(a.title || '').trim();
  return t.replace(/^뮤즈\s*대시\s*(커스텀\s*)?(\d+\s*레벨\s*)?/, '').trim() || t || '(제목 없음)';
}

/** 난이도별 레벨(easy·hard·master·hidden). 없으면 대표 난이도 하나. */
function albumLevels(a) {
  const lv = [a.difficulty_easy, a.difficulty_hard, a.difficulty_master, a.difficulty_hidden].filter((v) => v != null && v !== '');
  if (lv.length) return lv;
  return a.difficulty != null && a.difficulty !== '' ? [a.difficulty] : [];
}
/** 정렬용 레벨. 낮은순은 가장 쉬운 난이도, 높은순은 가장 어려운 난이도로 본다. 모르면 null. */
function albumLevelRange(a) {
  const n = albumLevels(a).map(Number).filter((v) => !isNaN(v));
  return n.length ? [Math.min(...n), Math.max(...n)] : [null, null];
}

function albumTags(a) {
  return (Array.isArray(a.tags) ? a.tags : []).map((t) => String(t).trim()).filter(Boolean);
}

/** 받은 곡 찾기: 폴더 이름 또는 info.txt 제목이 앨범 파일 이름·제목과 같으면 받은 것으로 본다. */
function installedSongFor(a) {
  const keys = [norm(albumFolder(a)), norm(a.title)].filter(Boolean);
  return state.songs.find((s) => keys.includes(norm(s.folder)) || keys.includes(norm(s.title)));
}

// ---------------------------------------------------------------- 검색

const CHOSEONG = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';

/** 검색용 글자: 소문자, 둥근 따옴표는 곧은 따옴표로, 띄어쓰기·기호는 뺀다. 'You’re Under' → "youreunder" */
function searchKey(s) {
  return String(s || '').normalize('NFC').toLowerCase()
    .replace(/[\s'"‘’“”`´!?.,·・:;~\-_()[\]{}<>/\\|&+×*#@]/g, '');
}

/** 한글 음절을 초성으로. 한글이 아닌 글자는 그대로. '아기상어' → 'ㅇㄱㅅㅇ' */
function choseong(s) {
  let out = '';
  for (const ch of s) {
    const c = ch.charCodeAt(0) - 0xac00;
    out += c >= 0 && c < 11172 ? CHOSEONG[Math.floor(c / 588)] : ch;
  }
  return out;
}

/**
 * 검색어가 앨범에 맞는지. 띄어쓰기로 나눈 낱말이 모두 어딘가에 들어 있어야 한다.
 * 찾는 곳: 제목, 파일(폴더) 이름, 설명, 태그. 자음만 친 낱말(ㅇㄱㅅㅇ)은 초성으로 찾는다.
 */
function albumMatches(a, query) {
  const words = String(query || '').trim().split(/\s+/).map(searchKey).filter(Boolean);
  if (!words.length) return true;
  const keys = [a.title, albumFolder(a), a.description, ...albumTags(a)].map(searchKey).filter(Boolean);
  return words.every((w) => {
    if (/^[ㄱ-ㅎ]+$/.test(w)) return keys.some((k) => choseong(k).includes(w));
    return keys.some((k) => k.includes(w));
  });
}

const STORE_SORTS = {
  new: (a, b) => String(b.created_at || '').localeCompare(String(a.created_at || '')),
  levelAsc: (a, b) => (albumLevelRange(a)[0] ?? 99) - (albumLevelRange(b)[0] ?? 99) || STORE_SORTS.new(a, b),
  levelDesc: (a, b) => (albumLevelRange(b)[1] ?? -1) - (albumLevelRange(a)[1] ?? -1) || STORE_SORTS.new(a, b),
  name: (a, b) => albumName(a).localeCompare(albumName(b), 'ko'),
};

/** 지금 조건(받음 여부·태그·검색어)에 맞는 앨범을 정렬해서 [앨범, 받은 곡] 쌍으로. */
function visibleAlbums() {
  const out = [];
  for (const a of state.albums || []) {
    const song = installedSongFor(a);
    if (state.storeFilter === 'new' && song) continue;
    if (state.storeFilter === 'installed' && !song) continue;
    if (state.storeTag && !albumTags(a).some((t) => norm(t) === state.storeTag)) continue;
    if (!albumMatches(a, state.storeQuery)) continue;
    out.push([a, song]);
  }
  const cmp = STORE_SORTS[state.storeSort] || STORE_SORTS.new;
  return out.sort((x, y) => cmp(x[0], y[0]));
}

// ---------------------------------------------------------------- 그리기

function renderStore() {
  const list = $('albumList');
  if (!list) return;
  $('storeSearchClear').hidden = !state.storeQuery;
  if (state.albums == null) {
    if (state.albumsError) list.replaceChildren(storeError());
    $('storeTags').replaceChildren();
    $('storeBulk').hidden = true;
    return;
  }
  renderTagChips();
  const shown = visibleAlbums();
  $('storeCount').textContent = shown.length === state.albums.length ? `${shown.length}곡` : `${shown.length} / ${state.albums.length}곡`;
  renderBulk(shown);

  const rows = shown.map(([a, song]) => albumRow(a, song));
  if (state.albumsError) rows.unshift(storeError());
  if (!shown.length) rows.push(emptyStore());
  list.replaceChildren(...rows);
}

function renderTagChips() {
  const count = new Map();
  for (const a of state.albums) {
    for (const t of albumTags(a)) {
      const k = norm(t);
      if (!count.has(k)) count.set(k, { label: t, n: 0 });
      count.get(k).n++;
    }
  }
  if (state.storeTag && !count.has(state.storeTag)) state.storeTag = '';
  const tags = [...count.entries()].sort((x, y) => y[1].n - x[1].n || x[1].label.localeCompare(y[1].label, 'ko'));
  $('storeTags').replaceChildren(...tags.map(([k, { label, n }]) =>
    el('button', { class: 'chip chip-tag' + (state.storeTag === k ? ' active' : ''), text: `#${label} ${n}`, onclick: () => setStoreTag(k) })));
}

function setStoreTag(k) {
  state.storeTag = state.storeTag === k ? '' : k;
  renderStore();
}

/** 보이는 곡 가운데 아직 안 받은(받는 중도 아닌) 곡을 한 번에 받는 줄. */
function renderBulk(shown) {
  const box = $('storeBulk');
  const todo = shown.filter(([a, song]) => !song && state.downloads[String(a.id)] === undefined).map(([a]) => a);
  const states = Object.values(state.downloads);
  const busy = states.length;
  const queued = states.filter((v) => v === 'queued').length;
  // 이번에 받은 곡 가운데 아직 기기에 있는 것만 센다(받았다 지운 곡, 같은 곡을 두 번 받은 것은 빼고).
  const fresh = (state.albums || []).filter((a) => state.storeInstalledNow.has(String(a.id)) && installedSongFor(a)).length;
  const parts = [];
  if (busy) parts.push(el('span', { text: `받는 중 ${busy - queued}곡` + (queued ? ` · 대기 ${queued}곡` : '') }));
  if (todo.length >= 2) {
    parts.push(el('button', { class: 'btn btn-small btn-primary', text: `⬇ 보이는 ${todo.length}곡 모두 받기`, onclick: () => downloadMany(todo) }));
  }
  if (busy && state.downloadQueue.length) {
    parts.push(el('button', { class: 'btn btn-small btn-ghost', text: '대기 취소', onclick: cancelQueued }));
  }
  if (fresh > 0 && !busy) {
    parts.push(el('span', { text: `새로 받은 ${fresh}곡은 게임을 완전히 닫고 다시 켜야 들어갑니다.` }),
      el('button', { class: 'btn btn-small btn-ghost', text: '▶ 게임 실행', onclick: ACTIONS.launchGame }));
  }
  box.hidden = !parts.length;
  box.replaceChildren(...parts);
}

function emptyStore() {
  const filtered = state.storeQuery || state.storeTag || state.storeFilter !== 'all';
  if (!filtered) return el('div', { class: 'empty', text: '표시할 곡이 없습니다.' });
  return el('div', { class: 'empty' }, '조건에 맞는 곡이 없습니다. ',
    el('button', { class: 'btn btn-small btn-ghost', text: '조건 지우기', onclick: clearStoreFilters }));
}

function clearStoreFilters() {
  state.storeQuery = '';
  state.storeTag = '';
  state.storeFilter = 'all';
  $('storeSearch').value = '';
  document.querySelectorAll('.chip[data-filter]').forEach((c) => c.classList.toggle('active', c.dataset.filter === 'all'));
  renderStore();
}

function storeError() {
  return el('div', { class: 'empty' }, `hwa.moe 목록을 불러오지 못했습니다 (${state.albumsError}). `,
    el('button', { class: 'btn btn-small btn-ghost', text: '다시 시도', onclick: () => fetchAlbums(true) }));
}

function albumRow(a, song) {
  const id = String(a.id);
  const dl = state.downloads[id];
  let actions;
  if (dl === 'queued') {
    actions = el('div', { class: 'item-actions' }, el('button', { class: 'btn btn-small btn-ghost', text: '대기 중 ✕', onclick: () => cancelQueued(id) }));
  } else if (dl !== undefined) {
    actions = el('div', { class: 'item-actions' }, el('button', { class: 'btn btn-small btn-ghost', disabled: true, text: dl >= 0 ? `${dl}%` : '받는 중' }));
  } else if (song) {
    const busy = !!state.deleting[norm(song.folder)];
    actions = el('div', { class: 'item-actions' },
      el('button', { class: 'btn btn-small btn-ok', disabled: true, text: '✓ 받음' }),
      el('button', { class: 'btn btn-small btn-danger-soft', disabled: busy, text: busy ? '삭제 중…' : '삭제', onclick: () => deleteSong(song.folder, albumName(a)) }));
  } else {
    actions = el('div', { class: 'item-actions' },
      el('button', { class: 'btn btn-small btn-primary', text: state.downloadErrors[id] ? '⟳ 다시 받기' : '⬇ 받기', onclick: () => downloadAlbum(a) }));
  }

  const name = albumName(a);
  const folder = albumFolder(a);
  const levels = albumLevels(a);
  const body = el('div', { class: 'item-body' },
    el('div', { class: 'item-title', text: name }),
    norm(folder) !== norm(name) && el('div', { class: 'item-sub', text: '폴더: ' + folder }),
    el('div', { class: 'meta' },
      el('span', { class: 'badge amber', text: levels.length ? 'Lv ' + levels.join(' · ') : 'Lv ?' }),
      albumTags(a).slice(0, 4).map((t) => el('button', {
        class: 'badge badge-tag' + (state.storeTag === norm(t) ? ' active' : ''), text: '#' + t, onclick: () => setStoreTag(norm(t)),
      }))));
  if (state.downloadErrors[id] && dl === undefined) body.append(el('div', { class: 'item-warn', text: '받기 실패: ' + state.downloadErrors[id] }));
  if (dl !== undefined && dl !== 'queued') {
    const bar = el('div', { class: 'progress' + (dl < 0 ? ' indeterminate' : ''), id: 'prog_' + id }, el('div'));
    if (dl >= 0) bar.firstChild.style.width = dl + '%';
    body.append(bar);
  }
  const coverSrc = typeof a.cover_url === 'string' && /^https:\/\//.test(a.cover_url) ? a.cover_url : null;
  return el('div', { class: 'item', dataset: { album: id } }, thumb(coverSrc, false), body, actions);
}

// ---------------------------------------------------------------- 받기 대기열

function enqueue(a) {
  const id = String(a.id);
  if (state.downloads[id] !== undefined) return;
  delete state.downloadErrors[id];
  state.downloads[id] = 'queued';
  state.downloadQueue.push(a);
}

function pumpDownloads() {
  let active = Object.values(state.downloads).filter((v) => v !== 'queued').length;
  while (active < MAX_PARALLEL_DOWNLOADS && state.downloadQueue.length) {
    const a = state.downloadQueue.shift();
    const id = String(a.id);
    if (state.downloads[id] !== 'queued') continue;
    state.downloads[id] = -1;
    active++;
    call('downloadSong', id, String(a.title || ''), String(a.download_filename || ''));
  }
}

function downloadAlbum(a) {
  enqueue(a);
  pumpDownloads();
  renderStore();
}

async function downloadMany(albums) {
  const ok = await confirmBox(`${albums.length}곡 받기`,
    `지금 보이는 안 받은 곡 ${albums.length}개를 ${MAX_PARALLEL_DOWNLOADS}곡씩 차례로 받습니다.\n곡 하나가 100MB를 넘기도 하니 Wi-Fi에서 받으세요.`, '모두 받기', false);
  if (!ok) return;
  albums.forEach(enqueue);
  pumpDownloads();
  renderStore();
}

/** 대기 중인 곡을 빼낸다. id가 없으면 대기 중인 곡 전부. 이미 받는 중인 곡은 멈추지 않는다. */
function cancelQueued(id) {
  const one = typeof id === 'string' ? id : null;
  state.downloadQueue = state.downloadQueue.filter((a) => {
    const k = String(a.id);
    if (one && k !== one) return true;
    if (state.downloads[k] === 'queued') delete state.downloads[k];
    return false;
  });
  renderStore();
}

window.onSongProgress = (id, pct) => {
  const cur = state.downloads[id];
  if (cur === undefined || cur === 'queued') return;
  const changedMode = (cur < 0) !== (pct < 0);
  state.downloads[id] = pct;
  const bar = $('prog_' + id);
  if (!bar || changedMode) { renderStore(); return; }
  bar.firstChild.style.width = pct + '%';
  const btn = document.querySelector(`[data-album="${CSS.escape(String(id))}"] .item-actions button`);
  if (btn) btn.textContent = pct >= 0 ? `${pct}%` : '받는 중';
};

window.onSongInstalled = (id) => {
  delete state.downloads[id];
  delete state.downloadErrors[id];
  state.storeInstalledNow.add(String(id));
  pumpDownloads();
  refreshSongs();
  renderStore();
};

window.onSongDownloadFailed = (id, message) => {
  delete state.downloads[id];
  state.downloadErrors[id] = message || '알 수 없는 오류';
  pumpDownloads();
  renderStore();
};
