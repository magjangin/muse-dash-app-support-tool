# 설치 가이드

뮤즈대시 모드 비서(`com.hwa.musedash.assistant`)를 설치하고, 모드 게임과 연결하는 방법입니다.

## 준비물

| 항목 | 내용 |
|---|---|
| 기기 | Android 8.0 이상 (실기 검증: Galaxy S21, Android 15) |
| 게임 | 뮤즈대시 모드판 `com.prpr.musedash.mod` (v6.8.0) 설치 완료 |
| 모드 | 곡 가져오기를 지원하는 `mod.js` ([muse-dash-phone](https://github.com/magjangin/muse-dash-phone) 2026-10-31 이후 빌드) |
| 설정 연동용 (Android 11+) | PC + USB 케이블 + [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools)의 `adb` — 처음 한 번만 필요 |

## 1. APK 설치

1. [Releases](https://github.com/magjangin/muse-dash-app-support-tool/releases/latest)에서 `MuseDash_Mod_Assistant-vX.Y.Z.apk`를 받습니다.
2. 휴대폰에서 파일을 열고 "출처를 알 수 없는 앱 설치"를 허용한 뒤 설치합니다.

> 릴리스 APK는 디버그 키로 서명되어 있습니다. Play 프로텍트 경고가 뜨면 "무시하고 설치"를 누르세요.
> 예전에 직접 빌드한 버전을 설치했다면 그대로 업데이트됩니다. 서명 충돌로 설치가 막히면 기존 앱을 지우고 다시 설치하세요.

PC에서 설치하려면:

```bash
adb install -r MuseDash_Mod_Assistant-v1.0.1.apk
```

## 2. 파일 접근 권한 허용

앱을 처음 열면 상단에 **⚠️ 파일 접근 권한 필요** 배너가 뜹니다.

1. **권한 설정 열기**를 누릅니다.
2. **모든 파일에 대한 접근 허용**을 켜고 앱으로 돌아옵니다.

PC에서 한 번에 처리하려면:

```bash
adb shell appops set com.hwa.musedash.assistant MANAGE_EXTERNAL_STORAGE allow
```

## 3. 게임 설정 연동 (Android 11 이상, 기기당 1회)

설정 탭 맨 위 표시를 확인하세요.

- 🔗 **게임 연동됨**: 할 일 없습니다. 바로 4번으로 넘어가세요.
- ⚠️ **게임이 설정 파일을 읽을 수 없습니다**: 아래 작업이 필요합니다.

### 왜 필요한가요?

Android 11 이상에서는 앱이 다른 앱이 만든 `.txt` 파일을 읽을 수 없습니다.
그래서 비서 앱이 저장한 `/MuseDash/config.txt`를 게임이 못 읽고, 토글을 바꿔도 게임에 반영되지 않습니다.
아래 명령은 이 파일의 "소유 앱"을 게임으로 바꿔서, 게임은 읽을 수 있고 비서 앱은 계속 쓸 수 있게 합니다.

### 방법

1. 휴대폰에서 **개발자 옵션 → USB 디버깅**을 켜고 PC에 연결합니다.
2. 비서 앱을 한 번 열어 `config.txt`가 만들어지게 합니다. 설정 탭에서 아무 토글이나 한 번 바꿨다가 되돌려도 됩니다.
3. PC에서 실행합니다.

```bash
adb shell "content update --uri content://media/external/file --bind owner_package_name:s:com.prpr.musedash.mod --where \"_data='/storage/emulated/0/MuseDash/config.txt'\""
```

4. 비서 앱으로 돌아오면 설정 탭 맨 위가 🔗 **게임 연동됨**으로 바뀝니다.

> 이 연결은 `config.txt` 파일이 지워지지 않는 한 계속 유지됩니다.
> 파일 관리자로 `MuseDash/config.txt`를 지웠거나 기기를 초기화했다면 위 명령을 다시 실행하세요.

## 4. 모드 (mod.js) 최신화

비서 앱에서 받은 곡은 게임이 시작될 때 모드가 가져갑니다. 이 기능은 2026-10-31 이후의 `mod.js`에만 있습니다.
[muse-dash-phone 릴리스](https://github.com/magjangin/muse-dash-phone/releases/latest)에서 `mod.js`를 받아 게임 폴더에 덮어씁니다.

```bash
adb push mod.js /sdcard/Android/data/com.prpr.musedash.mod/files/mod.js
```

> 게임을 다시 설치할 필요는 없습니다. `mod.js` 파일만 바꾸면 됩니다.

## 5. 동작 확인

1. 설정 탭에서 원하는 토글을 바꿉니다. 바꾸는 즉시 저장되며, 따로 [설정 저장]을 누를 필요가 없습니다.
2. hwa.moe 탭에서 곡을 **⬇️ 주입**합니다.
3. 게임이 켜져 있었다면 완전히 끄고, 내 곡 탭에서 **▶ 뮤즈대시 모드 실행하기**를 누릅니다. 게임이 시작되면서 새 곡을 가져옵니다.
4. 게임 플레이 중에도 비서 앱에서 토글을 바꾸면 1초 안에 반영됩니다. 게임을 다시 시작할 필요가 없습니다.

문제가 생기면 [문제 해결](TROUBLESHOOTING.md)을 보세요.
