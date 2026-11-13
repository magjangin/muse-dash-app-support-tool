'use strict';

// XAPK 탭: 설치 파일 찾기, OBB 배치 진행, 시스템 설치 화면 열기.

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
