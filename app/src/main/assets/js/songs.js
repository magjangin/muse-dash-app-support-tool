'use strict';

// 내 곡 탭: 기기의 커스텀 곡 목록, 삭제, 기록 초기화 예약.

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
