'use strict';

// 공통: 브리지 호출, DOM 헬퍼, 형식, 확인 창, 화면 상태.
// 뮤즈대시 모드 비서 화면. 네이티브 쪽은 window.AndroidBridge(AndroidBridge.java)이고,
// 오래 걸리는 작업 결과는 window.on* 콜백으로 돌아온다(MainActivity.callJs).
// 서버(hwa.moe)·파일에서 온 문자열은 innerHTML에 넣지 않고 textContent로만 넣는다.
// 파일은 index.html에서 core → config → songs → store → xapk → main 순서로 읽는다(일반 script, 전역 공유).

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

// ---------------------------------------------------------------- 화면 상태

const state = {
  app: {},
  songs: [],
  albums: null,
  albumsLoadedAt: 0,
  albumsError: null,
  storeFilter: 'all',
  storeQuery: '',
  storeTag: '',      // 태그(소문자), '' = 전체
  storeSort: 'new',
  downloads: {},     // albumId -> percent (-1 = 크기 모름) 또는 'queued'
  downloadQueue: [], // 아직 시작 안 한 앨범(차례대로)
  downloadErrors: {}, // albumId -> 마지막 실패 메시지
  storeInstalledNow: 0, // 이번에 받은 곡 수(게임 다시 켜기 안내용)
  deleting: {},    // folder(lower) -> true
  packages: [],
  pkgStatus: {},   // path -> {msg, pct, busy}
};
