package com.hwa.musedash.assistant;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.hwa.musedash.assistant.config.ConfigRepository;
import com.hwa.musedash.assistant.records.RecordResetQueue;
import com.hwa.musedash.assistant.songs.SongDownloader;
import com.hwa.musedash.assistant.songs.SongLibrary;
import com.hwa.musedash.assistant.xapk.PackageInstaller;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebView(assets/js)가 부르는 window.AndroidBridge. 실제 일은 기능별 클래스가 하고, 여기서는
 * 스레드를 나누고 결과를 넘긴다. JSON 모양은 BridgeJson, 앱·게임 상태와 권한은 AppStatus. 오래 걸리는 일(다운로드·XAPK·삭제)은 백그라운드에서 돌리고
 * 결과는 MainActivity.callJs로 window.on* 콜백에 알린다.
 *
 * <p>@JavascriptInterface 메서드는 WebView의 JavaBridge 스레드에서 불린다. UI를 만지는 일은 runOnUiThread로 넘긴다.
 */
public class AndroidBridge {
    private static final String TAG = "MuseDashAssistant";

    private final MainActivity activity;
    private final ConfigRepository config;
    private final SongLibrary library;
    private final SongDownloader downloader;
    private final PackageInstaller installer;
    private final ExecutorService background = Executors.newCachedThreadPool();

    public AndroidBridge(MainActivity activity) {
        this.activity = activity;
        this.config = new ConfigRepository(activity);
        this.library = new SongLibrary();
        this.downloader = new SongDownloader(library);
        this.installer = new PackageInstaller(activity);
        // 앞선 실행이 받다가/지우다가 끊긴 임시 폴더 정리
        background.execute(library::cleanupLeftovers);
    }

    public void shutdown() {
        background.shutdown();
    }

    // ---- 앱 상태 ----------------------------------------------------------------

    /** {version, storage, gameInstalled, gameVersion, canInstallPackages} */
    @JavascriptInterface
    public String getAppState() {
        return BridgeJson.appState(AppStatus.appVersion(activity), AppStatus.hasStoragePermission(activity),
                AppStatus.gameVersion(activity), installer.canInstallPackages());
    }

    @JavascriptInterface
    public void requestStoragePermission() {
        activity.runOnUiThread(() -> AppStatus.requestStoragePermission(activity));
    }

    // ---- 설정 (config.txt) ------------------------------------------------------

    @JavascriptInterface
    public String getConfig() {
        return BridgeJson.config(config.state());
    }

    @JavascriptInterface
    public String setConfigOption(String id, boolean value) {
        try {
            return BridgeJson.config(config.setOption(id, value));
        } catch (IllegalArgumentException e) {
            return BridgeJson.error(e.getMessage());
        }
    }

    @JavascriptInterface
    public String resetConfig() {
        return BridgeJson.config(config.resetDefaults());
    }

    // ---- 내 곡 ------------------------------------------------------------------

    @JavascriptInterface
    public String listSongs() {
        return BridgeJson.songs(library.scan(), RecordResetQueue.pending(activity));
    }

    /** 결과: window.onSongDeleted(folder, count) */
    @JavascriptInterface
    public void deleteSong(String folder) {
        background.execute(() -> {
            // 이름부터 바꿔 목록에서 바로 빼고(화면 갱신), 실제 파일 삭제는 그 뒤에 한다.
            List<File> hidden = library.hide(folder);
            toast(!hidden.isEmpty() ? "'" + folder + "' 곡을 삭제했습니다." : "'" + folder + "' 곡을 찾지 못했습니다.");
            activity.callJs("onSongDeleted", folder, hidden.size());
            SongLibrary.purge(hidden);
        });
    }

    /** 진행: onSongProgress(albumId, percent, bytes) / 완료: onSongInstalled(albumId, folder) / 실패: onSongDownloadFailed(albumId, message) */
    @JavascriptInterface
    public void downloadSong(String albumId, String title, String filename) {
        background.execute(() -> {
            try {
                File dir = downloader.download(albumId, title, filename,
                        (pct, bytes) -> activity.callJs("onSongProgress", albumId, pct, bytes));
                toast("'" + title + "' 받기 완료! 게임을 다시 켜면 들어갑니다.");
                activity.callJs("onSongInstalled", albumId, dir.getName());
            } catch (Exception e) {
                Log.w(TAG, "download failed: " + albumId, e);
                toast("받기 실패: " + e.getMessage());
                activity.callJs("onSongDownloadFailed", albumId, String.valueOf(e.getMessage()));
            }
        });
    }

    // ---- 기록 초기화 예약 --------------------------------------------------------

    @JavascriptInterface
    public void resetAllRecords() {
        RecordResetQueue.add(activity, RecordResetQueue.ALL);
        SongProvider.grantToGame(activity);
        toast("전체 커스텀 기록 초기화를 예약했습니다.\n(뮤즈대시를 실행하면 적용)");
    }

    @JavascriptInterface
    public void resetSongRecord(String folder) {
        if (folder == null || folder.trim().isEmpty()) return;
        RecordResetQueue.add(activity, folder.trim());
        SongProvider.grantToGame(activity);
        toast("'" + folder + "' 기록 초기화를 예약했습니다.\n(뮤즈대시를 실행하면 적용)");
    }

    @JavascriptInterface
    public void cancelRecordResets() {
        RecordResetQueue.clear(activity);
        toast("기록 초기화 예약을 취소했습니다.");
    }

    @JavascriptInterface
    public String getPendingRecordResets() {
        return BridgeJson.strings(RecordResetQueue.pending(activity));
    }

    // ---- XAPK / APK ------------------------------------------------------------

    @JavascriptInterface
    public String scanPackages() {
        return installer.scan().toString();
    }

    /** 진행: onPackageStatus(path, message, percent) / 끝: onPackageDone(path, ok) */
    @JavascriptInterface
    public void installPackage(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            toast("파일을 찾을 수 없습니다: " + path);
            activity.callJs("onPackageDone", path, false);
            return;
        }
        if (!installer.canInstallPackages()) {
            // XAPK는 OBB를 쓰기 전에 이 허용이 있어야 한다 (Dead End #12). APK 설치에도 필요하다.
            activity.runOnUiThread(() -> {
                try {
                    installer.openInstallPermissionSettings();
                } catch (Exception ignored) {
                }
                Toast.makeText(activity, "'알 수 없는 앱 설치'를 허용한 뒤 [설치]를 다시 눌러 주세요.", Toast.LENGTH_LONG).show();
            });
            activity.callJs("onPackageDone", path, false);
            return;
        }
        if (!path.toLowerCase(Locale.ROOT).endsWith(".xapk")) {
            launchInstaller(path, file);
            return;
        }
        background.execute(() -> {
            try {
                File apk = installer.extractXapk(file, new PackageInstaller.Listener() {
                    @Override
                    public void onStatus(String message, int percent) {
                        activity.callJs("onPackageStatus", path, message, percent);
                    }

                    @Override
                    public void onError(String message) {
                        activity.callJs("onPackageStatus", path, message, -1);
                    }
                });
                if (apk == null) {
                    toast("XAPK 안에서 APK를 찾지 못했습니다.");
                    activity.callJs("onPackageDone", path, false);
                    return;
                }
                launchInstaller(path, apk);
            } catch (Exception e) {
                Log.w(TAG, "XAPK extract failed", e);
                toast("XAPK 처리 중 오류: " + e.getMessage());
                activity.callJs("onPackageDone", path, false);
            }
        });
    }

    private void launchInstaller(String path, File apk) {
        activity.runOnUiThread(() -> {
            try {
                installer.launchInstaller(apk);
                activity.callJs("onPackageDone", path, true);
            } catch (Exception e) {
                Toast.makeText(activity, "설치 실행 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                activity.callJs("onPackageDone", path, false);
            }
        });
    }

    // ---- 게임 실행 / 공통 --------------------------------------------------------

    @JavascriptInterface
    public void launchGame() {
        activity.runOnUiThread(() -> {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(Paths.GAME_PACKAGE);
            if (launch == null) {
                Toast.makeText(activity, "뮤즈대시 모드(" + Paths.GAME_PACKAGE + ")가 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            SongProvider.grantToGame(activity);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                activity.startActivity(launch);
            } catch (Exception e) {
                Toast.makeText(activity, "게임 실행 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void openUrl(String url) {
        if (url == null || !url.startsWith("https://")) return;
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
        });
    }

    @JavascriptInterface
    public void showToast(String msg) {
        toast(msg);
    }

    private void toast(String msg) {
        activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }
}
