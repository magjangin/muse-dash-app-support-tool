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
- **현재 비서 앱 버전**: v2.3.0 (versionCode 10, 2026-11-14, hwa.moe 탭 검색·태그·정렬·모두 받기·받기 대기열). **GitHub Latest = v2.3.0** (https://github.com/magjangin/muse-dash-app-support-tool/releases/tag/v2.3.0, 태그 `v2.3.0` → 머지 커밋 `fc6010c`, 첨부 `MuseDash_Mod_Assistant-v2.3.0.apk` 62,129 B, SHA-256 `db507736…cb01`, 서명 인증서 `1e08a903…5953`, latest 링크로 다시 받아 해시 일치). 기기에는 v2.3.0 release 빌드(브랜치 빌드, 소스 같음)가 깔려 있다. 이전 GitHub 릴리스 v2.2.0(versionCode 9, 내 곡 탭이 게임과 같은 곡 규칙 사용): 태그 `v2.2.0` → 머지 커밋 `0de7a61`, 첨부 58,301 B, SHA-256 `2367bcc9…af7b`, 서명 인증서 `1e08a903…5953`. v2.1.0(versionCode 8, 토글 2개)은 태그·릴리스 없이 v2.2.0 릴리스 노트에 함께 적었다.
- **게임 버전과 고정 여부**:
  - 기기의 모드 게임 `com.prpr.musedash.mod`: versionName 6.8.0, versionCode 632, targetSdk 36. adb로 사이드로드한 복제 패키지라(`installerPackageName=null`, 마지막 갱신 2026-11-01) 스토어 자동 업데이트를 받지 않는다. 사실상 고정이다.
  - Steam Build ID·DepotDownloader·`.acf` 패치 차단은 안드로이드라 해당 없다. 정품 `com.prpr.musedash`(Play 설치본)는 Play 자동 업데이트를 꺼 둬야 한다. 원본 APK 해시와 OBB 크기는 게임 저장소 AGENTS.md ①에 있다.
  - 비서 앱은 게임의 `libil2cpp.so` RVA를 쓰지 않는다. 게임이 바뀌어도 영향받는 것은 SongProvider 규약을 쓰는 mod.js 쪽이다(3-1절).
- **런타임 백엔드와 디컴파일 경로**:
  - 게임은 Unity 2019.4.41f1 **IL2CPP**(arm64-v8a)다. 덤프·디컴파일은 게임 저장소 `H:\source\repos\com.prpr.musedash_v6.8.0-unity-android`에 있다: `il2cpp_dump\`(dump.cs·script.json·il2cpp.h), `jadx_src\`(Java), 후킹 RVA 표 `docs\hooks.md`.
  - 비서 앱은 일반 Java 앱이라 디컴파일할 대상이 없다. 소스가 전부다.

---

## 2. 아키텍처 및 설계 Rationale
- **v2.0.0 (2026-11-12)부터 일반 Gradle 프로젝트** (`app/` 모듈, AGP 9.5.1, Gradle 9.9.1 래퍼, compileSdk 36, minSdk 26, targetSdk 34, Java 17). Android Studio 2026.2로 연다.
  - 빌드 PC 환경: Android SDK `H:\Android\Sdk` (cmdline-tools, platform-tools 37, platforms;android-36, build-tools 36.1.0, 사용자 환경 변수 `ANDROID_HOME`), JDK 21 Temurin, Android Studio `C:\Program Files\Android\Android Studio`. `local.properties`(커밋 안 함)에 `sdk.dir=H\:/Android/Sdk`.
  - v1.0.x의 `build_apk.py`(aapt2+javac+d8+uber-apk-signer)와 프로젝트 루트 `sdk/`(무시 목록)는 더 쓰지 않는다. `sdk/`는 지워도 된다.
- **UI는 WebView 유지** (사용자 결정: Compose 재작성 대신 구조 정리). `assets/index.html`(뼈대) + `app.css` + `js/*.js`(v2.0.1에서 `app.js`를 나눔: `core`(브리지 호출·`el()`·확인 창·`state`) → `config` → `songs` → `store` → `xapk` → `main`(탭·이벤트·시작) 순서로 읽는 일반 `<script>`). ES 모듈은 `file:///android_asset`에서 CORS로 막힐 수 있어 쓰지 않고, 최상위 `const`/`function`을 전역으로 공유한다. 실기기 테스트가 전역 `confirmBox`를 부르므로 이름을 바꾸면 테스트도 고친다. 서버·파일에서 온 문자열은 `el()` 헬퍼로 `textContent`에만 넣는다 — v1.0.x는 hwa.moe 제목·태그를 `innerHTML`로 넣어, 서버 데이터가 파일 삭제·설치까지 하는 브리지를 부를 수 있었다.
- **네이티브는 기능별 클래스, `AndroidBridge`는 얇은 JS 창구**: 스레드 분리와 결과 전달만 한다. JSON 모양은 `BridgeJson`, 앱·게임 버전과 저장소 권한은 `AppStatus`(v2.0.1). 결과는 `MainActivity.callJs(fn, args...)`가 `JSONObject.quote`로 인코딩해 `window.on*`를 부른다.
  - `config/ModConfig`(config.txt 규칙, 순수 Java) · `config/ConfigRepository`(파일·MediaStore 소유자)
  - `songs/SongLibrary`(목록·삭제·자켓) · `songs/SongInfo`(곡 하나, `of()`가 파일 이름·info 내용으로 채움) · `songs/SongDownloader`(hwa.moe ZIP) · `songs/SafePaths`(폴더 이름·zip slip)
  - `songs/HwaManifest`·`songs/ChartFiles`·`songs/TextNormalizer`(v2.2.0, 순수): 게임 mod.js 곡 규칙의 Java 사본. 3-2절.
  - `xapk/XapkRules`(순수) · `xapk/PackageInstaller` · `records/RecordResetQueue`
  - 순수 Java로 뺀 규칙은 JVM 단위 테스트(`app/src/test`)로, 기기 의존 동작은 실기기 테스트(`app/src/androidTest`)로 검증한다.
- **앱 본체는 외부 라이브러리 0개** (androidx 없음 → release APK 약 58KB, v2.2.0). AGP 9의 내장 Kotlin이 kotlin-stdlib(약 2MB)를 넣어서 `gradle.properties`에 `android.builtInKotlin=false`를 둔다. FileProvider도 androidx 대신 `GenericFileProvider`(캐시·공용 저장소 아래 파일만 엶).
- **서명 키 고정**: `signing/uber-debug.keystore` = v1.0.x를 서명한 uber-apk-signer 내장 공개 debug 키(alias `androiddebugkey`, 암호 `android`, SHA-256 `1e08a903…5953`). debug·release 모두 이 키. 바꾸면 기존 사용자가 앱을 지워야 해서 설정 예약·권한이 날아간다.
- **targetSdk 34 유지**: 35+는 edge-to-edge 강제 등 동작이 바뀌고, 사이드로드 전용이라 올릴 이득이 없다.
- **`QUERY_ALL_PACKAGES` 제거** → `<queries>`에 게임 패키지와 `INSTALL_PACKAGE` 인텐트만. 게임 설치 확인·실행·`grantUriPermission`, 시스템 설치기 찾기(#13) 모두 실기로 확인.
- 곡 자켓은 `https://assistant.local/cover/<폴더>`를 `shouldInterceptRequest`에서 파일로 답한다(실제 네트워크로 안 나감). WebView에 `file://` 기기 경로를 열지 않기 위해서다.

---

## 3. 핵심 기능 명세
1. **모드 설정 관리 (Config Manager)**:
   - `config.txt` 실시간 읽기/쓰기 파싱.
   - 플레이 보조: **오토플레이** (`autoPlay`), **강제퍼펙트** (`forcePerfect`), **피버충전금지** (`blockFever`).
   - 연출 옵션: **시네마** (MP4 BGA 비디오 재생, `enableCinema`), **고스트노트보이기** (`showGhostNotes`).
   - 스킨·이벤트 (v2.1.0): **커스텀 스킨** (`enableSpineSkin`, 키 `스파인스킨`, 기본 켬), **강제크리스마스** (`forceChristmas`, 기본 끔). mod.js(`spine/index.ts`의 Spine Init 훅, `christmas.ts`)가 판을 시작할 때 값을 보므로 실시간이 아니라 다음 판부터 적용된다.
   - 키·별칭·기본값은 `ModConfig.OPTIONS`가 mod.js `src/config.ts`의 `currentConfig`와 똑같이 갖는다. 별칭(`크리스마스`, `커스텀스킨`, `올퍼펙트`, 영문 키 등)도 대소문자 없이 읽고, 저장할 때 그 줄을 정식 키로 바꾼다. 별칭을 안 읽으면 사용자가 `크리스마스=true`로 적어 둔 파일에 앱이 `강제크리스마스=false`를 덧붙여 덮어써 버린다. mod.js에 설정이 늘면 여기 `OPTIONS`·`assets/js/config.js`의 `OPTIONS`·`defaultText`·실기기 테스트 토글 수를 같이 고친다.
   - 토글은 바꾸는 즉시 저장, [파일 다시 읽기], [기본값으로](확인 창). 저장 때마다 파일을 새로 읽어 바꾼 키 줄만 교체한다(앱을 켜 둔 채 PC에서 고친 내용을 옛 사본으로 덮지 않게). v2.0.0에서 [설정 저장] 버튼은 없앴다.
2. **hwa.moe 온라인 커스텀 곡 다운로더 & 주입기 (Online Store)**:
   - 화평님의 공식 커스텀 앨범 플랫폼 `https://hwa.moe` Supabase REST API 연동.
   - 온라인 앨범 실시간 탐색 (자켓 커버, 곡 제목, 난이도 뱃지, 태그).
   - 검색·필터(전체/안 받은 곡/받은 곡), 받기 진행 막대(서버가 Content-Length를 안 주면 '받는 중' 애니메이션 — hwa.moe가 그렇다).
   - [⬇ 받기] 원클릭 시 `https://hwa.moe/api/download/<album_id>`에서 ZIP 스트림 다운로드 -> `/storage/emulated/0/MuseDash/hwa/`에 압축 해제. 게임은 다음 시작 때 `SongProvider`로 가져간다 (#10). v1.0.3부터 게임 폴더(`Android/data/.../files/hwa/`)에 직접 쓰는 미러링 코드는 없다 (#8 정정).
   - v2.0.0: `.dl_<폴더>` 임시 폴더에 다 푼 뒤 `renameTo`로 바꾼다. 끊기면 반쪽 곡이 남지 않는다(점 폴더는 앱 목록·`SongProvider` 모두 건너뜀). 같은 폴더가 있으면 지우고 바꾼다. 남은 `.dl_`/`.del_`은 앱 시작 때 `SongLibrary.cleanupLeftovers()`가 지운다.
   - 이미 기기에 있는 곡 자동 감지(폴더 이름 또는 info.txt 제목이 앨범 파일 이름·제목과 같으면) → `[✓ 받음]` + `[삭제]`.
   - **v2.3.0 검색·받기 (전부 `assets/js/store.js`, 네이티브는 그대로)**:
     - 서버 데이터(2026-11-14, 14곡): 제목이 `뮤즈대시 커스텀 N레벨 <곡>` 꼴이고 머리말 레벨이 실제 `difficulty`와 다른 곡이 있다(`5레벨 You’re Under Arrest` = 7). 그래서 `albumName()`이 머리말을 떼고 레벨은 `difficulty_easy/hard/master/hidden`(없으면 `difficulty`) 배지로만 보인다. `artist`는 모두 null이라 검색 대상에서 뺐다. `platforms`(`['pc']`만인 곡이 많음)는 기기에서 그 곡들이 잘 돌아서 무시한다.
     - `albumMatches()`: 제목·폴더(download_filename)·description·태그를 `searchKey()`(NFC, 소문자, 공백·따옴표·기호 제거)로 비교. 낱말은 AND. 자음만으로 된 낱말은 `choseong()`로 비교. **NFKC를 쓰면 안 된다**: 호환 자모 `ㄱ`(U+3131)이 첫가끝 자모(U+1100)로 바뀌어 초성 검색이 깨진다.
     - 받기 대기열: `state.downloads[id]`가 `'queued'` 또는 진행률. `pumpDownloads()`가 동시에 `MAX_PARALLEL_DOWNLOADS`(2)개까지만 `downloadSong`을 부른다. 브리지 쪽 `background`는 `newCachedThreadPool`이라 JS가 막지 않으면 전부 동시에 받는다. 받는 중인 곡을 멈추는 기능은 없다(브리지에 취소가 없음).
3. **커스텀 곡 & 런처 (Custom Songs & Launcher)**:
   - 기기 내 등록된 커스텀 곡 목록 실시간 파싱 및 세부 메타데이터(BMS, BGM, 자켓) 표시.
   - **v2.2.0: 게임과 같은 곡 규칙** (3-2절). 제목(`커스텀 곡 제목` → `곡 이름` → 폴더), 아티스트, 음악·자켓·영상 고르기, 임시본 뺀 BMS 수, 레벨, 난이도별 채보, 크리스마스 배경, info.txt 곡별 설정을 게임 코드 그대로 계산한다. 게임이 건너뛸 곡(info `.txt`·정식 `.bms` 없음)은 `gameSkip` → 빨간 '게임에 안 보임', 그 밖의 문제(난이도별 채보 못 찾음·음악 없음·모르는 info 키)는 `warnings` → 주황 글씨. 건너뛸 곡도 목록에는 둔다(지울 수 있게, `isValid()`는 예전처럼 BMS나 음악이 하나라도 있으면). 사용자용 설명은 `docs/SONG_FOLDER.md`.
   - 앱 표시용 아티스트 기본값은 `Custom`(게임은 `화영왕 N`). 하위 경로 난이도별 채보(`sub/a.bms`)는 게임은 존재 확인 없이 받고 나중에 읽다 실패하면 기본 채보로 대신하므로, 앱은 실제 파일이 없을 때 경고를 띄운다. 이 두 가지만 게임과 일부러 다르다.
   - **커스텀 로컬 기록 초기화 (전체 / 곡별)**:
     - **`[ 🔄 전체 기록 초기화 ]`**: 모든 커스텀 곡의 로컬 플레이 기록(점수, 정확도, 풀콤보 등) 일괄 리셋 예약.
     - 각 곡별 **`[ 🔄 기록 리셋 ]`**: 특정 곡의 모든 난이도 기록 리셋 예약.
     - 오터치 방지를 위한 Fluent 다크 감성의 인앱 확인 모달(`confirmModal`) 지원.
     - `SongProvider` IPC를 통해 게임 시작 시 `files/record/` 내 대상 `.json`, `.bak` 파일을 게임 UID 권한으로 완벽 클렌징 (#14).
   - 예약된 곡에 '기록 초기화 예약' 표시, [예약 취소](`cancelRecordResets`).
   - 각 곡별 **`[삭제]`** (확인 창): 폴더를 `.del_<시각>_<이름>`으로 바꿔 목록·게임에서 바로 빼고 뒤에서 지운다(119MB·수천 파일 곡은 실제 삭제에 5~10초, 실측). 공용 폴더에서 지운다. 게임 폴더의 사본은 게임이 다음 시작 때 지운다(`.from_assistant` 표시가 있는 폴더만, adb로 넣은 곡은 그대로).
   - `[ ▶ 뮤즈대시 모드 실행하기 ]` 터치 시 `com.prpr.musedash.mod` 앱 자동 런칭.
4. **XAPK 탐색 및 인스톨러 (XAPK Manager)**:
   - 기기 내 다운로드 폴더의 `.xapk` / `.apk` 자동 탐색 (MuseDash 관련 파일 하이라이트).
   - XAPK 감지 시 내부 ZIP 스트림에서 `base.apk`를 캐시로 임시 추출 후 `GenericFileProvider`를 통해 시스템 패키지 설치 창 자동 호출.
   - XAPK 내 `*.obb`는 항목 경로(`Android/obb/<패키지>/...`) 그대로, 그런 경로가 아니면 `manifest.json`의 `package_name` 폴더로 배치한다(v1.0.3, 전엔 `com.prpr.musedash.mod` 고정). 같은 크기 OBB가 이미 있으면 건너뛴다.
   - XAPK는 압축을 풀기 **전에** `canRequestPackageInstalls()`를 확인하고, 없으면 '출처를 알 수 없는 앱 설치' 설정 화면을 띄운다 (#12).

---

## 3-1. 게임 연동 규약 (주입 파이프라인·브리지 시그니처)
비서 앱은 게임 프로세스에 아무것도 주입하지 않는다. 주입은 게임 쪽 Frida Gadget이 하고, 비서 앱은 그 mod.js가 읽어 가는 파일과 provider만 내놓는다. 아래 규약 가운데 하나를 바꾸면 게임 저장소의 mod.js도 같이 고쳐 다시 올려야 한다.

- **게임 쪽 주입 경로** (자세한 건 게임 저장소 AGENTS.md ④):
  - `com.prpr.musedash.mod`는 정품 APK에 `System.loadLibrary("frida-gadget")`을 넣은 복제 패키지다.
  - Gadget은 script 모드로 `/storage/emulated/0/Android/data/com.prpr.musedash.mod/files/mod.js`를 실행한다(`on_change: reload`).
  - mod.js 빌드: 게임 저장소 `tools/frida/mod`에서 `npm run build` → `build/clone/mod.js` → `nox_adb push build/clone/mod.js /sdcard/Android/data/com.prpr.musedash.mod/files/mod.js`.
  - 이 경로가 PC 모드의 `Mods/`에 해당하는 배포 위치다. 기기에는 개인 전체판만 올린다(Dead End #10의 사고 기록).
  - 공개본(`muse-dash-phone/mod`)으로 옮길 때는 2026-11-13부터 게임 저장소의 `python -I tools/sync_public.py`를 쓴다(`preserve/`와 `// @private` 줄을 빼고 해금 문자열이 들어가면 실패). 자세한 건 게임 저장소 AGENTS.md.
- **config.txt**: mod.js `src/config.ts`가 공용 `/MuseDash/config.txt`를 1초마다 읽는다. 못 읽으면 `files/save custom key/config.txt`로 폴백한다. 키는 `ModConfig`가 정의한다. 소유자 문제는 Dead End #9.
- **SongProvider** (`content://com.hwa.musedash.assistant.songs`, `exported=false`, `grantToGame()`이 prefix 읽기 권한을 준다):
  | URI | 반환 | 쓰는 곳 |
  |---|---|---|
  | `/list` | 커서 `(path, size, mtime)`, path는 `hwa/` 기준 상대 경로, 점 파일·폴더 제외 | `assistant.ts` `pullFromAssistant()` |
  | `/file/<경로>` | 읽기 전용 파일(`..` 탈출·쓰기 거부) | 같은 곳, 크기가 다른 파일만 `files/hwa`로 복사 |
  | `/record_resets` | 커서 `(target)`, `ALL` 또는 곡 폴더 이름 | 같은 곳, `files/record/`의 `.json`·`.bak`·`.tmp` 삭제 |
  | `/record_resets_ack` | 커서 `(status)`, 예약 목록 비우기 | 삭제 뒤 |
  - 권한은 `MainActivity.onCreate`·`onResume`·`launchGame`·기록 초기화 예약 때 준다. 게임을 강제 종료하거나 재부팅하면 권한이 사라진다.
  - 게임이 provider를 부르는 시점: 2026-11-09(게임 커밋 `9e7b7e1`)부터 `index.ts`가 **콜드 스타트 때 Unity가 뜨기 전(onCreate 안)** `restoreCache` 다음에 `pullFromAssistant(HWA_DIR)`를 부른다. `loadLibrary()` 안에서는 더 부르지 않으므로 mod.js 핫리로드(`on_change: reload`)로는 곡·기록 초기화가 반영되지 않는다. 시작 로그는 `assistant-sync-start` → `assistant-sync`(또는 `-skip`), 기록 초기화는 `record-resets-completed`.
- **화면(JS) ↔ 네이티브** (`window.AndroidBridge`, 모든 메서드는 `@JavascriptInterface`, JavaBridge 스레드에서 불린다):
  - 바로 JSON 문자열을 돌려주는 메서드: `getAppState`, `getConfig`, `setConfigOption(id, bool)`, `resetConfig`, `listSongs`, `getPendingRecordResets`, `scanPackages`. 모양은 `BridgeJson`이 정한다.
  - 오래 걸리는 일은 `void`로 돌아오고, 결과는 `MainActivity.callJs`가 `window.on*`로 알린다:
    - 곡 받기 `downloadSong(albumId, title, filename)` → `onSongProgress(albumId, percent, bytes)`, 끝나면 `onSongInstalled(albumId, folder)`, 실패하면 `onSongDownloadFailed(albumId, message)`.
    - 곡 삭제 `deleteSong(folder)` → `onSongDeleted(folder, count)`.
    - 설치 `installPackage(path)` → `onPackageStatus(path, message, percent)`, 끝나면 `onPackageDone(path, ok)`.
  - 그 밖의 `void` 메서드: `requestStoragePermission`, `resetAllRecords`, `resetSongRecord(folder)`, `cancelRecordResets`, `launchGame`, `openUrl(https만)`, `showToast`.
  - 네이티브가 부르는 것: `onAppResume()`, `onBackPressed()`. `onBackPressed()`가 `true`를 돌려주면 네이티브는 뒤로 가기를 하지 않는다.
  - `listSongs` 항목(v2.2.0): `{folder, title, artist, bmsCount, hasMusic, hasVideo, hasCover, pendingReset, levels[5](레벨 또는 null), difficultyCharts(찾은 난이도별 채보 수), christmas, overrides{autoPlay, blockFever, cinema, showGhostNotes, fixDt 중 info에 값이 있는 것}, gameSkip(문자열 또는 null), warnings[]}`. 화면(`songs.js`)은 그리기만 하고 판단은 네이티브가 한다.

---

## 3-2. 게임 소스와 짝을 이루는 코드 (게임 쪽이 바뀌면 같이 고칠 것)
비서 앱 일부는 게임 mod.js 소스(`H:\source\repos\com.prpr.musedash_v6.8.0-unity-android\tools\frida\mod\src`)를 Java로 옮긴 사본이다. 게임 저장소에는 이 규칙들의 테스트가 없으므로 앱의 JVM 테스트와 아래 대조가 사실상 유일한 검증이다.

| 게임 파일 | 앱 파일 | 무엇 |
|---|---|---|
| `config.ts` (`currentConfig`, `reloadConfig`의 키·별칭, `DEFAULT_CONFIG_TEXT`) | `config/ModConfig.java` (`OPTIONS`, `parseBool`, `defaultText`) · `assets/js/config.js` (`OPTIONS` 설명) · 실기기 테스트 토글 수 | 설정 키·별칭·기본값·주석 |
| `hwa/manifest.ts` (`parseManifest`, `normalizeKey`, 키 확인 순서) | `songs/HwaManifest.java` | info.txt 읽기 |
| `hwa/chartFiles.ts` (`isTempFile`, `pickDefaultBms`, `resolveChartName`) | `songs/ChartFiles.java` | 채보 고르기 |
| `hwa/library.ts` (`pickManifest`, music/cover/video 정규식, 건너뛰기 조건) | `songs/ChartFiles.java` 상수 · `songs/SongInfo.of()` | 파일 고르기, '게임에 안 보임' |
| `hwa/text.ts` (`normalizeEnglishContractions`) | `songs/TextNormalizer.java` | 제목 축약형 |
| `autoplay.ts`·`fever.ts`(전역 또는 곡), `customChart.ts` 시네마(전역 그리고 곡), `ghost.ts`(곡 우선), `christmas.ts`(커스텀은 곡 값만) | `assets/js/songs.js` 주석 · `ModConfig.defaultText` 주석 · `docs/SONG_FOLDER.md` 4절 | 곡별 설정 우선순위 설명 |
| `hwa/assistant.ts` (`AUTHORITY`, `/list`·`/file`·`/record_resets`·`/record_resets_ack`, `.from_assistant`) | `SongProvider.java` · `records/RecordResetQueue.java` | 3-1절 provider 규약 |

- 확인 방법: `git -C <게임 저장소> log --oneline <마지막 대조 커밋>..HEAD -- tools/frida/mod/src/config.ts tools/frida/mod/src/hwa/` 로 바뀐 게 있는지 본다. **마지막 대조: 게임 `0f4bfb2`(2026-11-14)**. 이때 `config.ts`·`hwa/`의 마지막 변경은 `257f834`(크리스마스·스킨)였고 앱은 v2.1.0에서 이미 맞췄다. 11-13 리팩토링(`b988f81`)은 `spineSkin.ts`→`spine/`, `customTag.ts`→`tag/`, `customAudio.ts`→`audio/`, `battle.ts`→`battle/`, `preserve*.ts`→`preserve/`로 나눈 것뿐이라 앱 규약에 영향이 없다.
- **교차 대조 도구** (`H:\Android\_xcheck\`, 저장소 밖, 2026-11-14 만듦): 같은 입력을 게임 TS 원본과 앱 Java로 돌려 결과를 줄 단위로 비교한다.
  1. 기기 곡 덤프: `nox_adb shell 'cd /sdcard/MuseDash/hwa && for d in *; do echo "##DIR $d"; ls -1p "$d" | while read f; do echo "##F $f"; done; for t in "$d"/*.txt; do [ -f "$t" ] && echo "##TXT ${t#*/}" && base64 -w0 "$t" && echo; done; done' > raw.txt`
  2. `python -I make_corpus.py raw.txt corpus.json` (기기 곡 + 경계 사례 5개) → `python -I to_lines.py corpus.json corpus.lines`
  3. TS: 게임 `hwa/manifest.ts`·`chartFiles.ts`·`text.ts`를 `ts/`에 복사하면서 `from "./x.js"`를 `"./x.ts"`로 바꾸고 `node ts/run.ts corpus.json > ts.txt` (Node 24의 타입 제거 실행, 빌드 도구 불필요). `run.ts`의 `pickManifest`·`pickFile`은 `library.ts`에서 복사한 것이라 그쪽이 바뀌면 같이 고친다.
  4. Java: `javac -encoding UTF-8 -cp <app\build\intermediates\javac\debug\compileDebugJavaWithJavac\classes> -d out XCheck.java` → `java -cp "out;<classes>" XCheck corpus.lines > java.txt`
  5. 두 파일을 줄 단위로 비교. 2026-11-14: 곡 10개 × 25항목 = 250줄, 차이 0.

---

---

## 4. 빌드 및 배포 파이프라인
```powershell
# 1. 빌드 + 단위 테스트 + lint (JAVA_HOME = JDK 17+)
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
# 산출물: app\build\outputs\apk\release\app-release.apk (약 58KB)

# 2. 실기기 계측 테스트 — connectedAndroidTest는 쓰지 않는다 (아래 Dead End #15)
.\gradlew.bat assembleDebug assembleDebugAndroidTest
nox_adb -s R3CR10T2JFA push app\build\outputs\apk\debug\app-debug.apk /data/local/tmp/t.apk
nox_adb -s R3CR10T2JFA shell pm install -r -t --user 0 /data/local/tmp/t.apk
nox_adb -s R3CR10T2JFA push app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk /data/local/tmp/t.apk
nox_adb -s R3CR10T2JFA shell pm install -r -t --user 0 /data/local/tmp/t.apk
nox_adb -s R3CR10T2JFA shell am instrument -w com.hwa.musedash.assistant.test/androidx.test.runner.AndroidJUnitRunner
nox_adb -s R3CR10T2JFA shell pm uninstall com.hwa.musedash.assistant.test

# 3. 배포본 설치 (debug 위에 release를 덮어도 됨, 같은 키)
nox_adb -s R3CR10T2JFA push app\build\outputs\apk\release\app-release.apk /data/local/tmp/app.apk
nox_adb -s R3CR10T2JFA shell pm install -r --user 0 /data/local/tmp/app.apk

# 4. All Files Access 권한 부여 (Android 11+, 처음 한 번)
nox_adb -s R3CR10T2JFA shell appops set com.hwa.musedash.assistant MANAGE_EXTERNAL_STORAGE allow

# 5. 앱 실행
nox_adb -s R3CR10T2JFA shell monkey -p com.hwa.musedash.assistant -c android.intent.category.LAUNCHER 1

# 6. (기기당 1회) 공용 config.txt 소유자를 게임으로 지정 — 이게 없으면 게임이 설정 토글을 못 읽음 (Dead End #9)
nox_adb -s R3CR10T2JFA shell "content update --uri content://media/external/file --bind owner_package_name:s:com.prpr.musedash.mod --where \"_data='/storage/emulated/0/MuseDash/config.txt'\""
```
- 실기기 테스트 내용: `SongProviderDeviceTest`(list/file/record_resets 규약, `..` 탈출·쓰기 거부, 공용 hwa에 `__assistant_device_test__` 폴더만 만들고 지움, 예약 목록은 백업 후 복원), `ConfigDeviceTest`(실제 config.txt 토글 저장 뒤 MediaStore 소유자 유지 확인, 원래 바이트로 덮어써 복원), `MainActivityDeviceTest`(WebView·브리지·탭·확인 창·뒤로 가기, v2.3.0부터 hwa.moe 탭 검색·태그·정렬·대기열을 가짜 앨범으로 — 네트워크·실제 받기 없음).
- 2026-11-12 결과: 단위 34/34, 실기기 7/7. 실기 수동 확인: 설정 탭 연동 표시, hwa.moe 받기(119MB)→내 곡 표시→삭제, 가짜 XAPK(비서 앱 자신의 APK+3MB OBB)로 OBB 배치→시스템 설치기, 앱의 [실행]으로 켠 게임의 `mod.log`에 `assistant-sync songs:5 failed:0`, `config-loaded source:/MuseDash/config.txt`.
- 2026-11-14 v2.3.0(hwa.moe 검색·받기) 결과: 단위 42/42, lint 통과(경고만, 이전과 같음), 실기기 8/8(`MainActivityDeviceTest.storeSearch_tags_queue` 추가: 가짜 앨범 3개로 따옴표·여러 낱말·초성·파일 이름·태그 검색, 태그 칩, 정렬, 대기열 넣고 빼기). 실기: release 빌드로 `under arrest` 검색 → 1곡, `neon` 검색 → ⬇ 받기 → 받는 중 1곡 → 21초 뒤 ✓ 받음 + '새로 받은 1곡…' 안내·▶ 게임 실행 → 삭제해 기기 곡 5개로 되돌림. 게임은 켜지 않았다(게임에 넘기는 것은 그대로).
- 2026-11-14 v2.2.0(게임과 같은 곡 규칙) 결과: 단위 42/42(곡 규칙 7개 추가, 옛 `SongInfo` 3개 대체), lint 통과, 실기기 7/7. 3-2절 교차 대조 250줄 차이 0. 실기: 내 곡 탭에서 기기 곡 5개가 레벨(`Lv 5`, summertime `Lv 3 · 5`)·`곡 설정: 고스트 켬 · 권장표 켬`·`🎄 크리스마스`로 표시되고 `You’re Under Arrest`는 `You're Under Arrest`로 보였다. 공용 hwa에 시험 폴더 두 개(info 없음 / 없는 `easy.bms` 지정·음악 없음·모르는 키)를 만들어 '게임에 안 보임'·주황 경고 3줄을 화면으로 확인한 뒤 지웠다. 게임은 이번에 켜지 않았다(앱은 게임에 넘기는 것을 바꾸지 않았다).
- 2026-11-13 v2.1.0(토글 2개) 결과: 단위 38/38, lint 통과, 실기기 7/7. 실기: 설정 탭에 '스킨 · 이벤트' 카드 표시 → [강제크리스마스] 켬 → 게임 재시작 시 `config-loaded ... forceChristmas:true`, `christmas-ready`, MediaStore 소유자는 계속 게임 → 다시 끔 → 게임을 앞으로 가져오자 `forceChristmas:false`로 다시 읽음. 원래 `config.txt` 끝에 `강제크리스마스=false`·`스파인스킨=true` 두 줄만 붙었다(나머지 바이트 그대로).
- 2026-11-13 v2.0.1(코드 분할만) 결과: 단위 34/34, lint 통과, 실기기 7/7. 브랜치 빌드(50,805바이트)를 실기에 깔아 실행했고 WebView 콘솔 오류가 없었다. 릴리스 첨부 파일은 머지 뒤 main에서 다시 빌드한 것이다(50,865바이트, 소스는 같고 바이트만 다름).
- PC에 Nox가 떠 있으면 `nox_adb`(서버 v36)가 계속 adb 서버를 재시작하므로, 플랫폼툴 `adb`(v41) 대신 반드시 `C:\Program Files (x86)\Nox\bin\nox_adb.exe`를 쓴다 (버전 충돌로 `device not found` 발생).

### 릴리스 절차 (v2.0.0·v2.0.1·v2.2.0에서 쓴 순서)
```powershell
# 1. 브랜치에서 versionCode/versionName 올리기 + CHANGELOG.md·README 릴리스 명령·이 문서 갱신 → 위 빌드·테스트 전부 통과
git switch -c refactor/<이름>; git commit ...; git push -u origin refactor/<이름>
gh pr create --base main --title "vX.Y.Z: ..." --body-file <파일>
gh pr merge <번호> --merge --delete-branch     # 머지 커밋으로 합친다 (v2.0.0·v2.0.1 모두)
git switch main; git pull --ff-only origin main; git fetch --prune origin
# 2. 태그는 머지 커밋에 붙인다 (브랜치 커밋에 미리 붙였으면 지우고 다시)
git tag -a vX.Y.Z -m "vX.Y.Z" <머지 커밋>
git push origin vX.Y.Z
git push local main vX.Y.Z                     # 원격 local = H:\local-repos\muse dash app support tool.git (로컬 미러)
# 3. main에서 release를 다시 빌드해 버전 이름으로 첨부
.\gradlew.bat assembleRelease
Copy-Item app\build\outputs\apk\release\app-release.apk MuseDash_Mod_Assistant-vX.Y.Z.apk
gh release create vX.Y.Z MuseDash_Mod_Assistant-vX.Y.Z.apk --title "vX.Y.Z — <한 줄 요약>" --notes-file <BOM 없는 UTF-8 파일> --verify-tag
Remove-Item MuseDash_Mod_Assistant-vX.Y.Z.apk  # *.apk는 .gitignore에 있지만 루트에 남기지 않는다
```
- 이 저장소에는 CI가 없다. PR에 검사가 안 뜨는 게 정상이고, 위 로컬 빌드·실기기 테스트가 검증의 전부다.

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
   - **v1.0.3**: 미러링 코드(다운로드 때 사본 쓰기, `syncSongsToGameDataDir`의 곡 복사·삭제, 삭제 때 게임 폴더 지우기)를 뺐다. Android 11+에선 어차피 실패했고, 쓸 수 있는 기기(Android 10 이하)에선 공용 폴더에 없는 곡을 게임 폴더에서 지워 install.bat·push_songs.bat으로 넣은 곡까지 날릴 수 있었다. `syncSongsToGameDataDir`의 아이콘 복사 부분은 #10의 사용자 지시대로 그대로 두었다.
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
   - **정정 (2026-10-31 실측)**: 이 미러링은 Android 15에서 동작하지 않는다. 게임 격리 경로 `files/hwa/` 아래 곡 파일은 전부 `shell` 소유(adb로 넣은 것)였고, 앱으로 받은 곡은 그 경로에 생기지 않았다. 실제 해결은 #10.
9. **설정 토글이 유지되지 않고 게임에 반영되지 않던 버그 (2026-10-31 해결, 실기 검증 완료)**:
   - 원인 1 (유지 안 됨): 토글은 [설정 저장]을 눌러야만 파일에 기록됐고, `onAppResume`이 앱 복귀 때마다 파일을 다시 읽어 저장 안 한 토글을 원래 값으로 덮었다.
     - 해결: 토글 `change` 시 즉시 자동 저장. [기본값]도 즉시 저장.
   - 원인 2 (게임 미반영): `mod.js`의 `src/config.ts`는 공용 `/MuseDash/config.txt`가 **읽히면** 그걸 쓰고, 아니면 `Android/data/.../save custom key/config.txt`로 폴백한다 (1초마다 내용 변경 감지 → 재시작 없이 실시간 반영). `mod.log`의 `config-loaded.source`가 항상 폴백 경로였다.
     - 게임은 targetSdk 36이고 `MANAGE_EXTERNAL_STORAGE`를 선언하지 않아, 공용 저장소의 남이 만든 비미디어 파일(.txt, MediaStore `media_type=6`)은 못 읽는다. 비서 앱은 반대로 게임의 `Android/data`에 못 쓴다 (`saveConfig`가 그 예외를 삼켜서 성공 토스트만 뜸).
     - 해결: MediaStore에서 공용 `config.txt`의 `owner_package_name`을 `com.prpr.musedash.mod`로 바꾸면 FUSE가 게임에 소유자 권한을 준다. 비서 앱은 MANAGE 권한으로 계속 쓸 수 있다. (빌드/배포 5번 명령, 기기당 1회)
     - **설계 제약**: 비서 앱은 공용 `config.txt`를 절대 삭제·재생성하지 않고 truncate 덮어쓰기만 한다. 재생성하면 소유자가 비서 앱으로 바뀌어 게임이 다시 못 읽는다. 소유 상태는 `AndroidBridge.getConfigStatus()`가 MediaStore를 조회해 설정 탭 상단에 표시한다 (연동 안 되면 위 adb 명령을 안내).
     - 저장 시 앱이 모르는 키(지연·오프셋 등)와 주석을 보존하도록, 원본 텍스트에서 해당 키 줄만 교체한다.
   - 실패한 시도: `appops set com.prpr.musedash.mod MANAGE_EXTERNAL_STORAGE allow`는 게임이 권한을 매니페스트에 선언하지 않아 `default`에서 바뀌지 않음 (효과 없음).
10. **앱으로 받은 커스텀 곡이 게임에 안 들어가던 버그 (2026-10-31 해결, 실기 검증 완료)**:
   - 원인: 게임은 `[files/hwa, /MuseDash/hwa]` 순서로 `opendir`한다. 공용 `/MuseDash/hwa`는 목록은 보이지만(폴더 이름은 `hwa-skip`으로 찍힘), 안의 파일은 MediaStore 소유자가 비서 앱이라 FUSE가 게임에 숨긴다(`manifest:null, bms:null`). 비서 앱은 `files/hwa`에 못 쓴다(#5). 결국 곡이 게임에 들어갈 길이 없었다.
   - 실패한 시도: 앱에서 `ContentResolver.update(Files, owner_package_name=게임)` → 0행 갱신. MediaProvider가 shell/system 외에는 소유자 변경을 무시한다. (config.txt는 adb `content update`로만 가능, #9)
   - 해결 (두 저장소에 걸친 변경):
     - 비서 앱 `SongProvider` (`content://com.hwa.musedash.assistant.songs`): `/list`는 `/MuseDash/hwa` 아래 파일의 (path, size, mtime), `/file/<경로>`는 읽기 전용 파일. `exported=false` + `grantUriPermissions=true`로 두고, `SongProvider.grantToGame()`이 게임에 prefix URI 읽기 권한을 준다.
     - 권한을 주는 시점: `MainActivity.onCreate`, `onResume`, `launchGame`. **게임을 `am force-stop` 하거나 재부팅하면 URI 권한이 사라진다** (실측: 강제 종료 뒤 재실행 시 `Permission Denial ... not exported`). 그래서 비서 앱이 앞에 올 때마다 다시 준다. URI 권한이 패키지 가시성도 함께 열어 줘서, 게임(targetSdk 36)의 매니페스트에 `<queries>`가 없어도 provider를 찾는다.
     - 게임 쪽: `src/hwa/assistant.ts`의 `pullFromAssistant()`를 `loadLibrary()` 맨 앞에서 호출. (**2026-11-09 정정**: 게임 로딩 멈춤 대응으로 `index.ts`가 콜드 스타트 때 Unity 전에 부르도록 옮겼다. 3-1절.) 같은 코드가 두 곳에 있다: 개인 전체판 `H:/source/repos/com.prpr.musedash_v6.8.0-unity-android/tools/frida/mod` (기기용, `npm run build` → `build/clone/mod.js`)와 공개본 `muse-dash-phone/mod` (릴리스용, 보존 모드 3개 없음). `frida-java-bridge`로 provider를 읽어, 크기가 다른 파일만 `files/hwa`로 복사한다. 가져온 곡 폴더에는 `.from_assistant` 마커를 남긴다. 목록에서 사라진 곡은 마커가 있는 폴더만 지우고, adb로 넣은 곡은 건드리지 않는다. provider를 못 읽으면 아무것도 지우지 않고 기존 곡으로 진행한다.
     - 곡은 게임 **시작 시에만** 가져온다. 게임이 켜져 있으면 완전히 껐다 켜야 반영된다.
   - 검증 로그 (`mod.log`): `{"t":"assistant-sync","songs":4,"files":2507,"copied":633,"failed":0,"removed":0}` → `hwa-library count:4` (`매칭앱 프린세스`가 `files/hwa`에서 로드됨). 더미 폴더를 추가하자 `copied:1`, 지우자 `removed:1`을 확인했다.
   - **아이콘은 건드리지 않는다 (사용자 지시)**: `hwa_tag_icon.png`와 앱 런처 아이콘, 그리고 아이콘 관련 코드(`syncSongsToGameDataDir`의 아이콘 복사 부분 포함)는 수정·이동·덮어쓰기하지 않는다. 문서에도 아이콘을 복사하는 명령을 넣지 않는다.
   - **mod.js 배포 주의 (2026-10-31 사고)**: 기기에 `muse-dash-phone/mod/dist/mod.js`(공개본)를 올렸더니 캐릭터·아이템 해금(`preserve`/`unlockItems`/`unlockLevels`)이 빠져 사용자가 지적했다. **기기에는 개인 전체판 `build/clone/mod.js`만 올린다.** 공개 릴리스(`muse-dash-phone` v6.8.0)에는 공개본만 올린다 (유료 DLC 해금을 공개 배포하지 않는다는 사용자 결정). 올리기 전에 `grep -c unlockItems mod.js`로 확인할 것.
11. **고스트노트보이기 토글이 먹통 (2026-10-31 해결, 실기 확인)**:
   - 원인: 안드로이드 `ghost.ts`가 `battleSong()?.manifest.showGhostNotes === true`만 봤다. 전역 config(`고스트노트보이기`, 비서 앱 토글)를 완전히 무시해서 공식 곡과 설정 없는 커스텀 곡(korea, summertime)에서는 절대 켜지지 않았다. 또 PC 원본(`muse dash test/.../GhostNoteAlphaHold.cs`)에 있던 판마다 캐시 초기화(`ResetCache`, PnlBattle GameStart)가 빠져 있었다.
   - 해결: PC `GhostNoteVisibility` 규칙 그대로. 곡 `info.txt`에 값이 있으면 그 값, 없으면 `currentConfig.showGhostNotes`. `battleSerial()`이 바뀌면 `opaqueState`만 비운다. `originalAlphas`는 끌 때 되돌리려고 유지한다.
   - 확인: 공식 곡 플레이 중 `{"t":"ghost-alpha","anim":"in_nor_38","opaque":true}`가 찍혔다 (수정 전에는 공식 곡에서 이 이벤트가 없음). 개인 전체판·공개본 양쪽 `ghost.ts`에 반영했다.
   - 공개본 동기화: 이때 공개본 `config.ts`(공용 config.txt 읽기)·`customTag.ts`(공용 아이콘 경로 탐색, 2026-11-13 리팩토링 뒤 `tag/icon.ts`)도 전체판과 맞췄다. 이 세션 전 공개 릴리스는 전체판이라 원래 이 동작이었다. 아이콘 파일 자체는 건드리지 않았다.
12. **XAPK 설치가 OBB 단계에서 `ENOENT`로 실패 (2026-11-01, 실기 원인 확인)**:
   - 증상: [설치]를 누르면 "XAPK 처리 중 오류: /storage/emulated/0/Android/obb/<패키지>/main…obb: open failed: ENOENT". 로그 태그 `MuseDashAssistant`(v1.0.3에서 추가)에 스택이 남는다.
   - 원인: Android 11+에서 다른 앱의 `Android/obb/<패키지>/`는 '출처를 알 수 없는 앱 설치'(appop `REQUEST_INSTALL_PACKAGES`)를 허용받은 앱만 만들고 쓸 수 있다. `MANAGE_EXTERNAL_STORAGE`만으로는 안 된다. 허용 전엔 `mkdirs()`가 조용히 false를 돌려주고 쓰기가 `ENOENT`로 끝난다. v1.0.2까지는 이 권한을 OBB를 다 푼 **뒤** APK 설치 단계에서야 물어서, 이 기기(허용 안 함 상태)에선 한 번도 성공할 수 없었다.
   - 해결: `installPackage`가 XAPK면 먼저 `canRequestPackageInstalls()`를 보고, 없으면 설정 화면(`ACTION_MANAGE_UNKNOWN_APP_SOURCES`)을 띄우고 멈춘다. 허용 뒤 [설치]를 다시 누르면 진행. OBB 폴더를 못 만들면 그 이유를 토스트로 알린다.
   - 이 설정은 휴대폰 보안 설정이라 에이전트가 adb `appops`로 켜지 않고 사용자가 켠다.
   - **실기 확인 (2026-11-01, 사용자가 허용을 켠 뒤)**: OBB 두 개(4.3 GB)를 약 1분에 배치. 설치 뒤 게임 실행·캐시 복원까지 정상(#13 수정 후).
13. **XAPK [설치] 뒤 설치 화면 대신 파일 관리자(헥스 뷰어)가 열림 (2026-11-01 해결, 실기)**:
   - 원인: APK를 `ACTION_VIEW` + `application/vnd.android.package-archive`로 넘겼는데, 이 폰은 그 기본 앱이 FV 파일 관리자의 HexViewerActivity였다(`pm resolve-activity`로 확인). 사용자 기기의 '기본 앱' 설정에 따라 설치가 안 될 수 있었다.
   - 해결: `ACTION_INSTALL_PACKAGE`를 받는 **시스템 앱**(`queryIntentActivities(..., MATCH_SYSTEM_ONLY)`, 이 기기에선 `com.google.android.packageinstaller` 하나)으로 `setPackage`를 못박는다. 이후 [설치] → 시스템 설치 창 → 설치 완료(`installerPackageName=com.google.android.packageinstaller`).
   - 비서 앱이 미리 만든 `Android/obb/<패키지>/` 파일(소유자 = 비서 앱 uid)은 게임이 설치되는 순간 게임 uid로 바뀌어 게임이 읽을 수 있다(실측).
14. **커스텀 로컬 플레이 기록 초기화 (전체 및 곡별) 파이프라인 (2026-11-09 구현)**:
   - **설계 Rationale**: 커스텀 곡 기록은 `safety.ts`로 공식 세이브에서 인메모리 격리되어 `files/record/<곡폴더>_<난이도>.json`에 보존됨. Android 11+의 Scoped Storage로 인해 비서 앱이 게임 격리 폴더에 직접 접근하여 파일을 삭제할 수 없음(EACCES).
   - **해결 (SongProvider 기반 IPC 통신)**:
     - 비서 앱(`SongProvider.java`): `content://com.hwa.musedash.assistant.songs/record_resets` 쿼리 시 초기화 예약된 대상 목록(`ALL` 또는 개별 곡 폴더명) 반환, `record_resets_ack` 시 대기열 클리어.
     - 비서 앱 UI(`index.html`): WebView 기본 `confirm()` 모달 차단 버그(Dead End #6)를 완벽 우회하는 인앱 커스텀 확인 모달(`confirmModal`) 탑재.
     - 게임 쪽(`assistant.ts`): `pullFromAssistant()` 호출 시 `record_resets` 쿼리를 확인하여, 게임 자신의 프로세스(UID) 권한으로 `MOD_DIR/record` 내 대상 `.json`, `.bak`, `.tmp` 파일 삭제 후 `record_resets_ack` 전송. 개인 전체판 및 공개본 양쪽 모두 동기화 적용 완료.
15. **v2.0.0 Gradle 전환 때 걸린 것들 (2026-11-12)**:
   - `connectedAndroidTest`를 쓰지 않는다: 끝나면 앱을 **지워서** 비서 앱 데이터(기록 초기화 예약)와 `MANAGE_EXTERNAL_STORAGE` appop이 날아가고, Gradle은 SDK의 adb를 써서 Nox adb 서버와 충돌한다. 그래서 `assembleDebugAndroidTest` 후 `nox_adb`로 설치하고 `am instrument`로 돌린다.
   - AGP 9는 Kotlin을 기본으로 켜서 Java 전용 앱에도 kotlin-stdlib가 들어가 debug APK가 약 900KB가 됐다 → `android.builtInKotlin=false`.
   - `minSdk 26`이라 `String.strip()`, `List.of()`(API 30+)를 못 쓴다. lint `NewApi`가 잡는다. 대신 직접 만든 `ModConfig.strip()`(JS `trim()`처럼 BOM·NBSP까지 지움), `Arrays.asList`.
   - 소스에 `'﻿'` 같은 BOM 문자 리터럴이 있으면 lint `ByteOrderMark` 오류 → `(char) 0xFEFF`.
   - `sdkmanager` 최신판은 패키지 이름을 `platforms/android-36`처럼 `/`로 받는다. `platforms;android-36`은 조용히 무시돼 platform-tools만 깔렸다.
   - Git Bash에서 `C:/Users/화평/...` 스크래치 경로가 깨져 파일이 엉뚱한 곳에 생겼다 → 도구·백업은 ASCII 경로(`H:\Android\...`)에 둔다. 기기 백업·시험 XAPK 생성기는 `H:\Android\_device_backup\`.
   - Dead End #3(D8 익명 클래스 NPE)은 AGP의 D8에선 생기지 않는다. 이제 람다·익명 클래스를 쓴다.
   - 시작 탭을 localStorage로 기억하게 했더니, XAPK 탭으로 시작하면 설정 상태가 안 그려졌다(실기기 테스트가 잡음) → 시작 때 `loadConfig()`를 항상 부른다.
16. **v2.0.1 릴리스 때 걸린 것들 (2026-11-13)**:
   - 릴리스 노트를 PowerShell 5.1 `Set-Content -Encoding utf8`로 만들었더니 BOM이 붙어, GitHub 릴리스 본문 맨 앞에 `U+FEFF`가 들어갔다. `gh release edit --notes-file`로 BOM을 뺀 파일을 다시 올려 고쳤다. 노트는 Git Bash heredoc이나 `[IO.File]::WriteAllText(path, text, (New-Object Text.UTF8Encoding $false))`로 만든다.
   - 태그를 브랜치 커밋에 먼저 붙였더니 PR 머지 커밋과 어긋났다. 트리는 같았지만 v2.0.0처럼 머지 커밋으로 옮겼다. 태그는 머지한 뒤에 붙인다.
   - `app.js`를 나눌 때 ES 모듈(`type="module"`)은 쓰지 않았다. `file:///android_asset`에서 모듈 로딩이 CORS로 막힐 수 있고, 실기기 테스트가 전역 함수(`confirmBox`)를 직접 부른다. 일반 `<script>`는 최상위 `const`·`function`을 전역 렉시컬 환경으로 공유하므로 읽는 순서만 지키면 된다.
17. **v2.2.0 곡 규칙 이식 때 걸린 것들 (2026-11-14)**:
   - JS와 Java의 공백 정의가 다르다. JS `trim()`·`\s`는 NBSP(U+00A0)·BOM(U+FEFF)·유니코드 공백까지 지우지만 Java `trim()`·`Character.isWhitespace`는 못 지운다 → `HwaManifest.jsTrim`/`isJsSpace`로 맞췄다. 줄 구분자도 JS `search(/[:=：]/)`처럼 "처음 나오는 것"이어야 한다(`=` 먼저 찾고 없으면 `:`가 아니다).
   - 게임은 파일 이름을 `toLowerCase().localeCompare()`로 정렬한다. Java `compareTo`(코드포인트)와는 `_`·`-` 같은 기호가 섞일 때 순서가 다르다 → `Collator.getInstance(Locale.ROOT)`. info 파일·기본 채보가 '이름순 첫 번째'로 정해지는 경우에만 영향이 있다.
   - JS `parseFloat`는 `0.12s`를 0.12로 읽는다. Java `Double.parseDouble`은 예외 → 앞쪽 숫자만 정규식으로 잘라 읽는다.
   - (v2.3.0) Git Bash에서 `nox_adb push 파일 /data/local/tmp/t.apk`를 하면 MSYS가 `/data/...`를 `C:/Program Files/Git/data/...`로 바꿔 push·install이 둘 다 실패한다. 기기 명령은 PowerShell로 돌리거나 `MSYS_NO_PATHCONV=1`을 붙인다. PowerShell에서 `exec-out screencap -p > 파일`은 바이너리가 깨지므로 `shell screencap -p /data/local/tmp/s.png` 뒤 `pull`.
   - Git Bash heredoc 안의 Python 문자열로 Java 소스를 고치다가 `"\n"` 문자열이 실제 줄바꿈으로 들어가 컴파일이 87줄 깨졌다. 다른 heredoc은 `'`가 섞여 bash가 `unexpected EOF`로 거부했다. 여러 줄 편집은 Edit 도구나, Write로 만든 스크립트 파일(`python -I 파일`)로 한다.
   - 기기 화면 캡처가 보라색 그라데이션만 나오면 화면 보호기(`com.android.dreams.basic`, `mWakefulness=Dreaming`)다. `input keyevent KEYCODE_WAKEUP` 뒤 `am start -n com.hwa.musedash.assistant/.MainActivity`로 앞에 띄운다(`monkey`만으로는 안 올라옴).
