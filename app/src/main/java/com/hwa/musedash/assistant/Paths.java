package com.hwa.musedash.assistant;

/** 기기 경로·패키지 이름. 게임 쪽 mod.js와 맞춰야 하므로 바꿀 때는 AGENTS.md 1절과 함께 고친다. */
public final class Paths {
    public static final String GAME_PACKAGE = "com.prpr.musedash.mod";

    // ---- 뮤즈대시 2 감지 (꺼 둠) ----
    // 뮤즈대시 2의 실제 패키지 이름은 아직 모른다(2026-11-15 검색으로 못 찾음). 아래 이름은 추측이다.
    // 쓰려면 Play 주소의 id= 값으로 고친 뒤, 'MUSE_DASH_2' 주석을 아래 곳에서 함께 푼다:
    // Paths(여기) · AppStatus.museDash2Version · BridgeJson.appState · AndroidBridge.getAppState ·
    // AndroidManifest.xml <queries> · assets/js/main.js refreshAppState.
    // public static final String MUSE_DASH_2_PACKAGE = "com.prpr.musedash2";

    public static final String STORAGE_ROOT = "/storage/emulated/0";
    public static final String PUBLIC_DIR = STORAGE_ROOT + "/MuseDash";
    public static final String PUBLIC_CONFIG = PUBLIC_DIR + "/config.txt";
    /** 비서 앱이 곡을 받는 곳. 게임은 시작할 때 SongProvider로 이 폴더를 읽어 간다. */
    public static final String HWA_DIR = PUBLIC_DIR + "/hwa";
    public static final String DOWNLOAD_DIR = STORAGE_ROOT + "/Download";

    public static final String GAME_FILES_DIR = STORAGE_ROOT + "/Android/data/" + GAME_PACKAGE + "/files";
    public static final String GAME_CONFIG = GAME_FILES_DIR + "/save custom key/config.txt";

    private Paths() {}
}
