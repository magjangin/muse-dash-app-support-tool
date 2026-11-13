'use strict';

// hwa.moe 탭: 앨범 목록(Supabase REST), 검색·필터, 받기 진행.

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
