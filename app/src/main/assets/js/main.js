'use strict';

// 앱 상태(권한·게임 설치), 탭 전환, 버튼 이벤트, 네이티브 콜백(onAppResume·onBackPressed), 시작.

// ---------------------------------------------------------------- 앱 상태

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
