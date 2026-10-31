# Muse Dash App Support Tool (뮤즈대시 모드 비서)

안드로이드 실기기(`SM-G991N`, Android 15)에서 Muse Dash 모딩 환경(`com.prpr.musedash.mod`)의 실시간 설정 파일(`config.txt`) 제어, 커스텀 곡 확인, XAPK 패키지 감지 및 자동 설치를 지원하는 네이티브 컴패니언 APK 앱입니다.

---

## 1. 대상 게임 및 환경 정보
- **대상 게임 패키지**: `com.prpr.musedash.mod` (Muse Dash v6.8.0, Unity 2019.4.41f1, IL2CPP 64-bit arm64-v8a)
- **컴패니언 앱 패키지**: `com.hwa.musedash.assistant`
- **테스트 실기기**: Samsung Galaxy S21 5G (`SM-G991N`, Android 15, 장치 ID: `R3CR10T2JFA`)
- **전역 설정 경로**:
  - 1순위 (공용): `/storage/emulated/0/MuseDash/config.txt`
  - 2순위 (앱 격리 Scoped Storage 대비): `/storage/emulated/0/Android/data/com.prpr.musedash.mod/files/save custom key/config.txt`
- **커스텀 곡 저장 경로**:
  - 공용 경로: `/storage/emulated/0/MuseDash/hwa/`
  - 앱 전용 경로: `/storage/emulated/0/Android/data/com.prpr.musedash.mod/files/hwa/`
- **XAPK 검색 경로**: `/storage/emulated/0/Download/`, `/storage/emulated/0/MuseDash/`

---

## 2. 아키텍처 및 설계 Rationale
- **경량 네이티브 + Fluent WebView 하이브리드 아키텍처**:
  - Android Studio/Gradle 의존성 및 무거운 .NET MAUI/Avalonia Workload UAC 권한 문제(Windows UAC 차단 및 pending reboot)를 우회하기 위해, **Google 공식 Android Build-Tools(aapt2, d8) + Java 21 + Uber-APK-Signer** 기반의 독립 빌드 파이프라인(`build_apk.py`)을 구축.
  - UI는 Avalonia Fluent Dark 모던 감성(아크릴 블러, 네온 퍼플/시안 악센트, 부드러운 스위치 토글)을 100% 반영한 CSS3/HTML5 기반.
  - 백엔드는 Java Reflection/Native Android SDK 기반의 `AndroidBridge`로 시스템 파일 I/O 및 패키지 인스톨러 인텐트를 직결 제어.

---

## 3. 핵심 기능 명세
1. **모드 설정 관리 (Config Manager)**:
   - `config.txt` 실시간 읽기/쓰기 파싱.
   - 플레이 보조: **오토플레이** (`autoPlay`), **강제퍼펙트** (`forcePerfect`), **피버충전금지** (`blockFever`).
   - 연출 옵션: **시네마** (MP4 BGA 비디오 재생, `enableCinema`), **고스트노트보이기** (`showGhostNotes`).
   - [설정 저장], [새로고침], [기본값 복원] 원클릭 지원.
2. **hwa.moe 온라인 커스텀 곡 다운로더 & 주입기 (Online Store)**:
   - 화평님의 공식 커스텀 앨범 플랫폼 `https://hwa.moe` Supabase REST API 연동.
   - 온라인 앨범 실시간 탐색 (자켓 커버, 곡 제목, 난이도 뱃지, 태그).
   - [⬇️ 주입] 원클릭 시 `https://hwa.moe/api/download/<album_id>`에서 ZIP 스트림 다운로드 -> `/storage/emulated/0/MuseDash/hwa/` 및 `/storage/emulated/0/Android/data/com.prpr.musedash.mod/files/hwa/`로 동시 자동 압축 해제 및 주입.
   - 이미 기기에 설치된 곡 자동 감지 (`[✅ 설치됨]` 및 `[🗑️]` 삭제 지원).
   - 삭제 시 다시 `[⬇️ 주입]`으로 자동 전환되어 자유로운 다운로드/삭제 루프 지원.
3. **커스텀 곡 & 런처 (Custom Songs & Launcher)**:
   - 기기 내 등록된 커스텀 곡 목록 실시간 파싱 및 세부 메타데이터(BMS, BGM, 자켓) 표시.
   - 각 곡별 **`[🗑️ 삭제]`** 버튼 지원: 터치 시 공용 및 앱 내부 저장소에서 동시 안전 제거.
   - `[ ▶ 뮤즈대시 모드 실행하기 ]` 터치 시 `com.prpr.musedash.mod` 앱 자동 런칭.
4. **XAPK 탐색 및 인스톨러 (XAPK Manager)**:
   - 기기 내 다운로드 폴더의 `.xapk` / `.apk` 자동 탐색 (MuseDash 관련 파일 하이라이트).
   - XAPK 감지 시 내부 ZIP 스트림에서 `base.apk`를 캐시로 임시 추출 후 `GenericFileProvider`를 통해 시스템 패키지 설치 창 자동 호출.
   - XAPK 내 `*.obb` 파일 존재 시 `/storage/emulated/0/Android/obb/com.prpr.musedash.mod/` 경로로 자동 추출/배치.
3. **커스텀 곡 & 런처 (Custom Songs & Launcher)**:
   - `/sdcard/MuseDash/hwa/` 내부 폴더 목록 실시간 파싱 및 등록된 커스텀 곡 수 표시.
   - `[ ▶ 뮤즈대시 모드 실행하기 ]` 터치 시 `com.prpr.musedash.mod` 앱 자동 런칭.

---

## 4. 빌드 및 배포 파이프라인
```powershell
# 1. 원클릭 빌드 (AAPT2 리소스 컴파일 -> javac -> d8 dex -> uber-apk-signer 서명)
python build_apk.py

# 산출물: build/bin/MuseDash_Mod_Assistant.apk (약 33KB)

# 2. 실기기(ADB) 설치
nox_adb -s R3CR10T2JFA push "build\bin\MuseDash_Mod_Assistant.apk" /data/local/tmp/app.apk
nox_adb -s R3CR10T2JFA shell pm install -r -d -t --user 0 /data/local/tmp/app.apk

# 3. All Files Access 권한 부여 (Android 11+)
nox_adb -s R3CR10T2JFA shell appops set com.hwa.musedash.assistant MANAGE_EXTERNAL_STORAGE allow

# 4. 앱 실행
nox_adb -s R3CR10T2JFA shell monkey -p com.hwa.musedash.assistant -c android.intent.category.LAUNCHER 1
```

---

## 5. 시도했다가 실패한 것들 (Dead Ends / Lessons Learned)
1. **`dotnet workload install android` 실패**:
   - .NET 10 SDK 환경에서 관리자 권한(UAC) 부족 및 시스템에 "보류 중인 재부팅(Pending Reboot)" 플래그가 있어 `An error occurred trying to start process... 지원되지 않는 요청입니다` 오류 발생.
   - -> 공식 Standalone Google `build-tools_r34` 및 `platform-35`를 직접 활용하는 자체 `build_apk.py` 파이프라인으로 전환하여 즉시 무관리자 빌드 성공.
2. **`javac @sources.txt` 공백 경로 문제**:
   - `H:\source\repos\muse dash app support tool`처럼 경로에 띄어쓰기가 있을 때 `javac @sources.txt`가 `H:\source\repos\muse`를 찾지 못함.
   - -> `build_apk.py`에서 `cwd=ROOT`로 지정하고 상대 경로(`src/main/...`)를 사용하도록 변경하여 해결.
3. **D8 익명 클래스 NPE 버그**:
   - Java 21에서 `javac -source 8 -target 8` 컴파일 시 `new Runnable() { ... }` 익명 내부 클래스의 synthetic parameter metadata가 null로 생성되어 D8 8.2에서 `NullPointerException: Cannot invoke "String.length()" because "<parameter1>" is null` 발생.
   - -> 익명 클래스 대신 명시적 정적 내부 클래스(`static class RequestStorageTask implements Runnable` 등)로 분리 선언하여 D8 NPE 원천 차단.
4. **`hwa.moe` 다단계 리다이렉트(308 -> 307 Cloudflare R2) 미처리 버그**:
   - Java 기본 `HttpURLConnection`은 307 Temporary Redirect 및 308 Permanent Redirect를 자동 추적하지 않아 다운로드 실패.
   - -> `setInstanceFollowRedirects(false)` 후 수동 루프로 `Location` 헤더를 최대 6회 추적하도록 구현.
5. **Android 15 Scoped Storage의 `/Android/data/` 직접 쓰기 차단(EACCES)**:
   - `MANAGE_EXTERNAL_STORAGE` 권한을 받아도 Android 시스템 보안 정책상 다른 패키지의 비공개 데이터 경로(`/Android/data/com.prpr.musedash.mod/`)는 UID 접근이 차단되어 `FileOutputStream` 생성 시 `Permission denied` 예외 발생.
   - -> 공용 경로(`/sdcard/MuseDash/hwa/`)를 1순위 주입 타깃으로 안전하게 기록하고, `Android/data` 미러링은 쓰기 가능 여부 사전 프로브 및 예외 격리(Graceful Fallback) 처리하여 다운로드 중단 원천 해결.
6. **WebView `confirm()` 다이얼로그 차단 버그**:
   - Android WebView 환경에서는 기본 `WebChromeClient`가 없거나 `onJsConfirm()`이 구현되지 않으면 JavaScript의 `confirm()`이 항상 `false`를 반환하여 삭제 로직이 실행되지 않고 무시됨.
   - -> WebView의 브라우저 기본 모달 의존성을 제거하여 원터치 즉시 삭제 및 Toast 알림으로 직결하고, `WebChromeClient`를 등록하여 콘솔 로그 추적 지원.
7. **모드 게임(`com.prpr.musedash.mod`) 인게임 커스텀 앨범 아이콘 미표시 원인**:
   - `mod.js`는 곡 라이브러리(`re.length`)에 유효한 커스텀 곡이 1곡 이상 로드되었을 때만 앨범 선택 화면에 태그/아이콘을 등록함.
   - Android 15에서 게임 패키지의 `LEGACY_STORAGE` 및 권한 거부(`rejectTime`)로 인해 폴더 스캔이 0곡으로 판정되어 아이콘이 숨겨졌던 현상.
   - `LEGACY_STORAGE allow` 재부여 및 공용 경로(`/sdcard/MuseDash/hwa_tag_icon.png`) 아이콘 배치로 2곡 정상 인식(`count: 2`) 및 커스텀 앨범 태그 활성화 복구.
8. **삭제된 곡(아기상어, Neon)의 인게임 및 앱 잔여 불일치 및 아이콘 소멸 원천 차단**:
   - 원인:
     - Android 15에서는 공용 경로(`/sdcard/MuseDash/hwa/`)의 서브디렉토리에 대해 게임의 libc `opendir()`이 `EACCES`로 거부됨.
     - 곡 삭제 시 앱 내부 경로(`/Android/data/.../files/hwa/`)가 비워지면 게임이 인식하는 곡 수가 0이 되어 커스텀 앨범 태그 아이콘(`[🎮 실험 모드]`)이 인게임에서 완전히 숨겨짐.
   - 해결:
     - 비서 앱(`AndroidBridge.java`)에 `syncSongsToGameDataDir()` 자동 동기화 엔진 탑재.
     - 앱 시작, 곡 목록 스캔(`scanCustomSongs`), 곡 다운로드 완료, 곡 삭제 시 공용 경로와 앱 격리 경로 간의 양방향 미러링을 자동 수행.
     - 유효 곡(`MARENOIA`, `korea`, `summertime`) 3곡이 항상 게임 격리 경로에 유지되어 인게임 앨범 태그 아이콘이 절대로 사라지지 않도록 원천 보장.
