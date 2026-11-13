'use strict';

// 설정 탭: config.txt 토글 7개와 게임 연동 상태. 키·기본값은 네이티브 ModConfig.OPTIONS가 정한다.

const OPTIONS = [
  { id: 'autoPlay', group: 'play', name: '오토플레이', desc: '수면 린 베개 모션으로 차트를 자동 연주합니다.' },
  { id: 'forcePerfect', group: 'play', name: '강제퍼펙트', desc: '판정을 무조건 Perfect로 올립니다.' },
  { id: 'blockFever', group: 'play', name: '피버충전금지', desc: '피버 게이지가 차오르지 않게 고정합니다.' },
  { id: 'enableCinema', group: 'visual', name: '시네마 (BGA 비디오)', desc: '커스텀 곡 배경에 MP4 비디오를 재생합니다.' },
  { id: 'showGhostNotes', group: 'visual', name: '고스트노트보이기', desc: '고스트 노트를 투명하지 않게 보여 줍니다. 곡 info.txt에 값이 있으면 그 값이 우선입니다.' },
  // 아래 둘은 실시간이 아니라 다음 판(배틀 시작)부터 적용된다: 스킨은 Spine Init, 크리스마스는 씬·보스를 불러올 때 본다.
  { id: 'enableSpineSkin', group: 'extra', name: '커스텀 스킨', desc: '게임 폴더 skin test/의 Spine 스킨을 씌웁니다. 캐릭터는 모든 곡, 노트는 크리스마스 곡에만. 다음 판부터 적용됩니다.' },
  { id: 'forceChristmas', group: 'extra', name: '강제크리스마스', desc: '날짜와 상관없이 공식 곡 5번 씬의 배경·보스를 크리스마스 판으로 바꿉니다. 커스텀 곡은 곡 info.txt 설정을 따릅니다. 다음 판부터 적용됩니다.' },
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
  const ok = await confirmBox('기본값으로 되돌리기', '토글 7개를 기본값(강제퍼펙트·시네마·고스트노트·커스텀 스킨 켜짐)으로 되돌립니다.\n파일의 다른 키(지연·오프셋 등)는 그대로 둡니다.', '되돌리기', false);
  if (!ok) return;
  const st = callJson('resetConfig', null);
  if (st && st.saved) { applyConfig(st); toast('기본값으로 저장했습니다.'); }
  else toast('저장 실패: ' + ((st && st.error) || '알 수 없는 오류'));
}
