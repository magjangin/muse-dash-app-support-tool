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

## 커스텀 곡이 게임에 안 보여요 / 커스텀 앨범 아이콘이 사라졌어요

Android 15에서는 게임이 공용 폴더(`/MuseDash/hwa/`)의 곡을 못 읽고, 게임 전용 폴더(`/Android/data/com.prpr.musedash.mod/files/hwa/`)의 곡만 읽습니다.
게임 전용 폴더가 비어 있으면 커스텀 앨범 아이콘도 숨겨집니다.

비서 앱은 곡을 받거나 지울 때 두 폴더를 맞추려고 시도하지만, Android 11 이상에서는 다른 앱의 `Android/data` 폴더에 쓰기가 막혀 실패할 수 있습니다.
이럴 때는 PC에서 곡을 게임 전용 폴더로 직접 복사하세요.

```bash
adb shell "cp -r /storage/emulated/0/MuseDash/hwa/. /storage/emulated/0/Android/data/com.prpr.musedash.mod/files/hwa/"
```

```bash
adb shell "cp /storage/emulated/0/MuseDash/hwa_tag_icon.png /storage/emulated/0/Android/data/com.prpr.musedash.mod/files/"
```

## 앱 상단에 "파일 접근 권한 필요"가 계속 떠요

**권한 설정 열기**를 눌러 **모든 파일에 대한 접근 허용**을 켜세요. 설정 화면이 안 열리면 PC에서:

```bash
adb shell appops set com.hwa.musedash.assistant MANAGE_EXTERNAL_STORAGE allow
```

## hwa.moe 목록이 안 떠요 / 다운로드가 실패해요

- 인터넷 연결을 확인하고 hwa.moe 탭 오른쪽 위 **🔄 새로고침**을 누르세요.
- 다운로드 파일은 hwa.moe를 거쳐 Cloudflare R2 저장소에서 받습니다. 브라우저에서 `https://hwa.moe`가 열리는지도 확인해 보세요.
