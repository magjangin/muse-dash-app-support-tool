# 문제 해결

## 토글을 바꿔도 게임에 반영되지 않아요

1. 비서 앱 설정 탭 맨 위를 확인합니다.
   - ⚠️ **게임이 설정 파일을 읽을 수 없습니다**가 보이면 [설치 가이드 3번](INSTALL.md#3-게임-설정-연동-android-11-이상-기기당-1회)의 명령을 한 번 실행합니다.
2. 그래도 안 되면 게임이 실제로 어느 파일을 읽었는지 로그로 확인합니다.

```bash
adb shell "grep -a config-loaded /storage/emulated/0/Android/data/com.prpr.musedash.mod/files/mod.log | tail -1"
```

- `"source":"/storage/emulated/0/MuseDash/config.txt"`: 연동 정상입니다. 같은 줄에서 `autoPlay`, `blockFever` 같은 값이 앱의 토글과 맞는지 보세요.
- `"source":".../save custom key/config.txt"`: 게임이 공용 설정 파일을 못 읽고 자기 전용 파일을 쓰고 있습니다. 설치 가이드 3번 명령을 다시 실행하고 게임을 재시작하세요.

## 토글이 자꾸 원래대로 돌아가요

v1.0.0에서 토글을 바꾸고 [설정 저장]을 누르지 않으면, 앱으로 돌아올 때 원래 값으로 돌아가는 버그가 있었습니다.
**v1.0.1부터는 토글을 바꾸는 즉시 저장**되므로 최신 버전으로 업데이트하세요.

## 게임 연동이 갑자기 풀렸어요

`MuseDash/config.txt`를 지웠다가 다시 만들면 파일의 소유 앱이 바뀌어 연동이 풀립니다.
파일 관리자로 지웠거나, 다른 도구로 덮어썼거나, 기기를 초기화한 경우입니다.
설치 가이드 3번 명령을 다시 실행하면 돌아옵니다.

## `adb: device not found` / `adb server version doesn't match`

PC에 Nox 같은 안드로이드 에뮬레이터가 켜져 있으면, 에뮬레이터에 들어 있는 다른 버전의 adb가 서버를 계속 가로챕니다.

- 에뮬레이터를 끄고 다시 시도하거나,
- 에뮬레이터에 들어 있는 adb를 대신 씁니다. Nox라면 `C:\Program Files (x86)\Nox\bin\nox_adb.exe`입니다.

## 받은 곡이 게임에 안 보여요

Android 11 이상에서는 게임이 비서 앱이 받은 곡 파일을 직접 읽을 수 없습니다.
그래서 **게임을 시작할 때** 모드(`mod.js`)가 비서 앱에서 곡을 가져와 게임 전용 폴더로 복사합니다.

1. **게임을 완전히 껐다가** 다시 켜세요. 곡은 게임이 시작될 때만 가져옵니다. 이미 켜져 있던 게임을 앞으로 가져오기만 하면 새 곡이 안 보입니다.
2. 게임은 비서 앱의 **▶ 뮤즈대시 모드 실행하기**로 켜거나, 비서 앱을 한 번 열었다가 켜세요. 게임을 강제 종료했거나 기기를 재부팅하면 곡을 가져올 권한이 사라지고, 비서 앱이 앞에 나올 때 다시 줍니다.
3. `mod.js`가 곡 가져오기를 지원하는 버전인지 확인합니다. [설치 가이드 4번](INSTALL.md#4-모드-modjs-최신화)을 보세요.
4. 그래도 안 되면 게임 로그를 확인합니다.

```bash
adb shell "grep -a assistant-sync /storage/emulated/0/Android/data/com.prpr.musedash.mod/files/mod.log | tail -3"
```

- `"t":"assistant-sync"`: 가져오기에 성공했습니다. `copied`는 이번에 복사한 파일 수, `removed`는 지운 곡 수입니다.
- `"t":"assistant-sync-skip"` + `Permission Denial`: 권한이 없었습니다. 2번처럼 비서 앱을 연 뒤 게임을 다시 켜세요.
- 로그에 `assistant-sync`가 아예 없음: `mod.js`가 예전 버전입니다. 3번을 보세요.

## 앱 상단에 "파일 접근 권한 필요"가 계속 떠요

**권한 설정 열기**를 눌러 **모든 파일에 대한 접근 허용**을 켜세요. 설정 화면이 안 열리면 PC에서:

```bash
adb shell appops set com.hwa.musedash.assistant MANAGE_EXTERNAL_STORAGE allow
```

## hwa.moe 목록이 안 떠요 / 다운로드가 실패해요

- 인터넷 연결을 확인하고 hwa.moe 탭 오른쪽 위 **🔄 새로고침**을 누르세요.
- 다운로드 파일은 hwa.moe를 거쳐 Cloudflare R2 저장소에서 받습니다. 브라우저에서 `https://hwa.moe`가 열리는지도 확인해 보세요.

## XAPK 설치가 "XAPK 처리 중 오류 … ENOENT"로 끝나요

v1.0.2 이하에서 생기던 문제입니다. Android 11 이상에서는 **'출처를 알 수 없는 앱 설치'를 허용받은 앱만** 다른 앱의 OBB 폴더(`/Android/obb/...`)에 쓸 수 있는데, 예전 앱은 이 허용을 OBB를 다 푼 뒤에야 물었습니다.

- v1.0.3 이상으로 업데이트하세요. [설치]를 누르면 허용 화면이 먼저 열립니다.
- 또는 **설정 → 애플리케이션 → 뮤즈대시 모드 비서 → 출처를 알 수 없는 앱 설치**를 켠 뒤 [설치]를 다시 누르세요.
