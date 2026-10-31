package com.hwa.musedash.assistant;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AndroidBridge {
    public final MainActivity activity;

    private static final String PUBLIC_DIR = "/storage/emulated/0/MuseDash";
    private static final String PUBLIC_CONFIG = "/storage/emulated/0/MuseDash/config.txt";
    private static final String DOWNLOAD_DIR = "/storage/emulated/0/Download";
    private static final String MOD_PACKAGE = "com.prpr.musedash.mod";

    public AndroidBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static class RequestStorageTask implements Runnable {
        final MainActivity act;
        RequestStorageTask(MainActivity act) { this.act = act; }
        @Override
        public void run() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + act.getPackageName()));
                    act.startActivity(intent);
                } else {
                    act.requestPermissions(new String[]{
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 101);
                }
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                act.startActivity(intent);
            }
        }
    }

    @JavascriptInterface
    public void requestStoragePermission() {
        activity.runOnUiThread(new RequestStorageTask(activity));
    }

    @JavascriptInterface
    public String loadConfig() {
        File cfg = new File(PUBLIC_CONFIG);
        if (!cfg.exists()) {
            File fallback = new File("/storage/emulated/0/Android/data/" + MOD_PACKAGE + "/files/save custom key/config.txt");
            if (fallback.exists()) cfg = fallback;
        }

        if (!cfg.exists()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cfg), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
        return sb.toString();
    }

    @JavascriptInterface
    public boolean saveConfig(String text) {
        try {
            File dir = new File(PUBLIC_DIR);
            if (!dir.exists()) dir.mkdirs();

            File cfg = new File(PUBLIC_CONFIG);
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(cfg, false), StandardCharsets.UTF_8)) {
                writer.write(text);
                writer.flush();
            }

            File fallbackDir = new File("/storage/emulated/0/Android/data/" + MOD_PACKAGE + "/files/save custom key");
            if (fallbackDir.exists()) {
                File fbCfg = new File(fallbackDir, "config.txt");
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(fbCfg, false), StandardCharsets.UTF_8)) {
                    writer.write(text);
                    writer.flush();
                } catch (Exception ignored) {}
            }

            showToast("설정 파일이 성공적으로 저장되었습니다!");
            return true;
        } catch (Exception e) {
            showToast("저장 실패: " + e.getMessage());
            return false;
        }
    }

    @JavascriptInterface
    public String scanFiles() {
        JSONArray arr = new JSONArray();
        String[] searchPaths = new String[]{ DOWNLOAD_DIR, PUBLIC_DIR, "/storage/emulated/0" };

        for (String p : searchPaths) {
            File folder = new File(p);
            if (!folder.exists() || !folder.isDirectory()) continue;

            File[] list = folder.listFiles();
            if (list == null) continue;

            for (File f : list) {
                if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".xapk") || name.endsWith(".apk")) {
                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("name", f.getName());
                            obj.put("path", f.getAbsolutePath());
                            obj.put("size", f.length());
                            obj.put("isXapk", name.endsWith(".xapk"));
                            obj.put("isMuseDash", name.contains("musedash") || name.contains("muse-dash") || name.contains("prpr"));
                            arr.put(obj);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return arr.toString();
    }

    static class XapkExtractTask implements Runnable {
        final AndroidBridge bridge;
        final File file;

        XapkExtractTask(AndroidBridge bridge, File file) {
            this.bridge = bridge;
            this.file = file;
        }

        @Override
        public void run() {
            try {
                bridge.showToast("XAPK 분석 및 패키지 추출 중...");
                File cacheDir = bridge.activity.getCacheDir();
                File extractedApk = new File(cacheDir, "extracted_base.apk");
                File obbDestDir = new File("/storage/emulated/0/Android/obb/" + MOD_PACKAGE);

                if (!obbDestDir.exists()) {
                    obbDestDir.mkdirs();
                }

                boolean foundApk = false;
                boolean copiedObb = false;

                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[65536];
                    while ((entry = zis.getNextEntry()) != null) {
                        String entryName = entry.getName();

                        if (entryName.endsWith(".apk") && (!foundApk || entryName.contains("base") || entryName.contains("musedash"))) {
                            try (FileOutputStream fos = new FileOutputStream(extractedApk)) {
                                int read;
                                while ((read = zis.read(buffer)) != -1) {
                                    fos.write(buffer, 0, read);
                                }
                            }
                            foundApk = true;
                        } else if (entryName.endsWith(".obb")) {
                            String obbName = new File(entryName).getName();
                            File obbTarget = new File(obbDestDir, obbName);
                            try (FileOutputStream fos = new FileOutputStream(obbTarget)) {
                                int read;
                                while ((read = zis.read(buffer)) != -1) {
                                    fos.write(buffer, 0, read);
                                }
                            }
                            copiedObb = true;
                        }
                        zis.closeEntry();
                    }
                }

                if (foundApk) {
                    String msg = "APK 추출 완료!";
                    if (copiedObb) msg += " (OBB 배치 완료)";
                    bridge.showToast(msg);
                    bridge.installApkFile(extractedApk);
                } else {
                    bridge.showToast("XAPK 내에서 APK 파일을 찾지 못했습니다.");
                }
            } catch (Exception e) {
                bridge.showToast("XAPK 처리 중 오류: " + e.getMessage());
            }
        }
    }

    @JavascriptInterface
    public void installPackage(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            showToast("파일을 찾을 수 없습니다: " + filePath);
            return;
        }

        if (filePath.toLowerCase().endsWith(".apk")) {
            installApkFile(file);
            return;
        }

        new Thread(new XapkExtractTask(this, file)).start();
    }

    static class InstallApkTask implements Runnable {
        final MainActivity act;
        final File apkFile;

        InstallApkTask(MainActivity act, File apkFile) {
            this.act = act;
            this.apkFile = apkFile;
        }

        @Override
        public void run() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!act.getPackageManager().canRequestPackageInstalls()) {
                        Intent reqIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        reqIntent.setData(Uri.parse("package:" + act.getPackageName()));
                        act.startActivity(reqIntent);
                        Toast.makeText(act, "알 수 없는 앱 설치 권한을 허용해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                Uri apkUri = GenericFileProvider.getUriForFile(apkFile);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(act, "설치 실행 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void installApkFile(File apkFile) {
        activity.runOnUiThread(new InstallApkTask(activity, apkFile));
    }

    @JavascriptInterface
    public String scanCustomSongs() {
        // 게임 경로와 실시간 동기화
        syncSongsToGameDataDir();

        JSONArray arr = new JSONArray();
        Set<String> processedFolders = new HashSet<>();

        // 공용 경로를 단일 진실 소스(Single Source of Truth)로 사용
        File baseDir = new File(PUBLIC_DIR + "/hwa");
        if (baseDir.exists() && baseDir.isDirectory()) {
            File[] songDirs = baseDir.listFiles();
            if (songDirs != null) {
                for (File songDir : songDirs) {
                    if (!songDir.isDirectory()) continue;
                    String folderName = songDir.getName();
                    if (processedFolders.contains(folderName.toLowerCase())) continue;
                    processedFolders.add(folderName.toLowerCase());

                    String title = folderName;
                    String artist = "Custom";
                    int bmsCount = 0;
                    boolean hasMusic = false;
                    boolean hasCover = false;

                    File[] songFiles = songDir.listFiles();
                    if (songFiles != null) {
                        for (File sf : songFiles) {
                            String fn = sf.getName().toLowerCase();
                            if (fn.endsWith(".bms")) bmsCount++;
                            if (fn.endsWith(".ogg") || fn.endsWith(".mp3") || fn.endsWith(".wav")) hasMusic = true;
                            if (fn.startsWith("cover") && (fn.endsWith(".png") || fn.endsWith(".jpg"))) hasCover = true;

                            if (fn.contains("info") && fn.endsWith(".txt")) {
                                try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(sf), StandardCharsets.UTF_8))) {
                                    String line;
                                    while ((line = r.readLine()) != null) {
                                        String trimmed = line.trim();
                                        if (trimmed.startsWith("커스텀 곡 제목") || trimmed.toLowerCase().startsWith("title")) {
                                            String[] p = trimmed.split("[:=]");
                                            if (p.length > 1 && !p[1].trim().isEmpty()) title = p[1].trim();
                                        } else if (trimmed.startsWith("커스텀 아티스트") || trimmed.toLowerCase().startsWith("artist")) {
                                            String[] p = trimmed.split("[:=]");
                                            if (p.length > 1 && !p[1].trim().isEmpty()) artist = p[1].trim();
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    // 최소한 BMS 파일 또는 음원 파일이 있는 유효한 곡만 등록
                    if (bmsCount > 0 || hasMusic) {
                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("folder", folderName);
                            obj.put("title", title);
                            obj.put("artist", artist);
                            obj.put("bmsCount", bmsCount);
                            obj.put("hasMusic", hasMusic);
                            obj.put("hasCover", hasCover);
                            obj.put("path", songDir.getAbsolutePath());
                            arr.put(obj);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        return arr.toString();
    }

    public void copyDirectory(File src, File dest) {
        try {
            if (src.isDirectory()) {
                if (!dest.exists()) dest.mkdirs();
                File[] list = src.listFiles();
                if (list != null) {
                    for (File f : list) {
                        copyDirectory(f, new File(dest, f.getName()));
                    }
                }
            } else {
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                if (!dest.exists() || dest.length() != src.length()) {
                    try (FileInputStream in = new FileInputStream(src);
                         FileOutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = in.read(buf)) != -1) {
                            out.write(buf, 0, r);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void syncSongsToGameDataDir() {
        try {
            File publicHwa = new File(PUBLIC_DIR + "/hwa");
            File gameHwa = new File("/storage/emulated/0/Android/data/" + MOD_PACKAGE + "/files/hwa");
            if (!publicHwa.exists() || !publicHwa.isDirectory()) return;

            // 1. 공용 경로에 있는 유효 곡을 게임 내부 격리 경로로 동기화
            File[] publicSongs = publicHwa.listFiles();
            Set<String> validFolders = new HashSet<>();
            if (publicSongs != null) {
                for (File ps : publicSongs) {
                    if (ps.isDirectory()) {
                        validFolders.add(ps.getName().toLowerCase());
                        copyDirectory(ps, new File(gameHwa, ps.getName()));
                    }
                }
            }

            // 2. 공용에 없는(삭제된) 곡은 게임 내부 경로에서도 완전 삭제
            if (gameHwa.exists() && gameHwa.isDirectory()) {
                File[] gameSongs = gameHwa.listFiles();
                if (gameSongs != null) {
                    for (File gs : gameSongs) {
                        if (gs.isDirectory() && !validFolders.contains(gs.getName().toLowerCase())) {
                            deleteRecursive(gs);
                        }
                    }
                }
            }

            // 3. 커스텀 태그 아이콘 동기화
            File publicIcon = new File(PUBLIC_DIR + "/hwa_tag_icon.png");
            File gameIcon = new File("/storage/emulated/0/Android/data/" + MOD_PACKAGE + "/files/hwa_tag_icon.png");
            if (publicIcon.exists() && (!gameIcon.exists() || gameIcon.length() != publicIcon.length())) {
                copyDirectory(publicIcon, gameIcon);
            }
        } catch (Throwable ignored) {}
    }

    public void deleteRecursive(File f) {
        try {
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            f.delete();
        } catch (Throwable ignored) {}
    }

    static class DeleteSongTask implements Runnable {
        final AndroidBridge bridge;
        final String targetName;

        DeleteSongTask(AndroidBridge bridge, String targetName) {
            this.bridge = bridge;
            this.targetName = targetName;
        }

        @Override
        public void run() {
            try {
                String searchLower = targetName.toLowerCase().trim();
                boolean deletedAny = false;

                String[] basePaths = new String[]{
                        PUBLIC_DIR + "/hwa",
                        "/storage/emulated/0/Android/data/" + MOD_PACKAGE + "/files/hwa"
                };

                for (String basePath : basePaths) {
                    File base = new File(basePath);
                    if (!base.exists() || !base.isDirectory()) continue;

                    File[] subDirs = base.listFiles();
                    if (subDirs == null) continue;

                    for (File d : subDirs) {
                        if (!d.isDirectory()) continue;
                        String dName = d.getName().trim();

                        // 1. 폴더명 직접 매칭 (대소문자 무시)
                        if (dName.equalsIgnoreCase(searchLower)) {
                            bridge.deleteRecursive(d);
                            deletedAny = true;
                            continue;
                        }

                        // 2. info.txt 내부 곡 제목 매칭
                        File[] files = d.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().contains("info") && f.getName().toLowerCase().endsWith(".txt")) {
                                    try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                                        String line;
                                        while ((line = r.readLine()) != null) {
                                            String tr = line.trim();
                                            if (tr.startsWith("커스텀 곡 제목") || tr.toLowerCase().startsWith("title")) {
                                                String[] p = tr.split("[:=]");
                                                if (p.length > 1 && p[1].trim().equalsIgnoreCase(searchLower)) {
                                                    bridge.deleteRecursive(d);
                                                    deletedAny = true;
                                                    break;
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                }

                bridge.syncSongsToGameDataDir();
                bridge.showToast("'" + targetName + "' 곡이 완전히 삭제되었습니다.");
                bridge.activity.runOnUiThread(new SongDeletedCallbackTask(bridge.activity, targetName));
            } catch (Exception e) {
                bridge.showToast("삭제 실패: " + e.getMessage());
            }
        }
    }

    static class SongDeletedCallbackTask implements Runnable {
        final MainActivity act;
        final String folderName;

        SongDeletedCallbackTask(MainActivity act, String folderName) {
            this.act = act;
            this.folderName = folderName;
        }

        @Override
        public void run() {
            if (act.webView != null) {
                act.webView.evaluateJavascript("if(window.onSongDeleted) window.onSongDeleted('" + folderName.replace("'", "\\'") + "');", null);
            }
        }
    }

    @JavascriptInterface
    public void deleteCustomSong(String folderName) {
        new Thread(new DeleteSongTask(this, folderName)).start();
    }

    static class NotifySongCallbackTask implements Runnable {
        final MainActivity act;
        final String albumId;

        NotifySongCallbackTask(MainActivity act, String albumId) {
            this.act = act;
            this.albumId = albumId;
        }

        @Override
        public void run() {
            if (act.webView != null) {
                act.webView.evaluateJavascript("if(window.onSongInstalled) window.onSongInstalled('" + albumId + "');", null);
            }
        }
    }

    static class SongDownloadFailedTask implements Runnable {
        final MainActivity act;
        final String albumId;
        final String error;

        SongDownloadFailedTask(MainActivity act, String albumId, String error) {
            this.act = act;
            this.albumId = albumId;
            this.error = error;
        }

        @Override
        public void run() {
            if (act.webView != null) {
                act.webView.evaluateJavascript("if(window.onSongDownloadFailed) window.onSongDownloadFailed('" + albumId + "', '" + error.replace("'", "\\'") + "');", null);
            }
        }
    }

    static class DownloadSongTask implements Runnable {
        final AndroidBridge bridge;
        final String albumId;
        final String title;
        final String filename;

        DownloadSongTask(AndroidBridge bridge, String albumId, String title, String filename) {
            this.bridge = bridge;
            this.albumId = albumId;
            this.title = title;
            this.filename = filename;
        }

        @Override
        public void run() {
            try {
                bridge.showToast("'" + title + "' 다운로드 시작...");
                String currentUrl = "https://www.hwa.moe/api/download/" + albumId;

                String folderName = filename != null ? filename.trim() : "";
                if (folderName.toLowerCase().endsWith(".zip")) {
                    folderName = folderName.substring(0, folderName.length() - 4).trim();
                }
                if (folderName.isEmpty()) folderName = title.trim();

                File publicTargetDir = new File(PUBLIC_DIR + "/hwa/" + folderName);
                File modTargetDir = new File("/storage/emulated/0/Android/data/" + MOD_PACKAGE + "/files/hwa/" + folderName);

                if (!publicTargetDir.exists()) publicTargetDir.mkdirs();

                boolean canWriteModDir = false;
                try {
                    if (modTargetDir.exists() || modTargetDir.mkdirs()) {
                        File testF = new File(modTargetDir, ".test_write");
                        if (testF.createNewFile()) {
                            testF.delete();
                            canWriteModDir = true;
                        }
                    }
                } catch (Throwable t) {
                    canWriteModDir = false;
                }

                HttpURLConnection conn = null;
                int redirects = 0;
                while (redirects < 6) {
                    URL u = new URL(currentUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(120000);
                    conn.connect();

                    int code = conn.getResponseCode();
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        String loc = conn.getHeaderField("Location");
                        if (loc != null) {
                            if (!loc.startsWith("http")) {
                                loc = new URL(u, loc).toString();
                            }
                            currentUrl = loc;
                            conn.disconnect();
                            redirects++;
                            continue;
                        }
                    }

                    if (code == 200) {
                        break;
                    }

                    bridge.showToast("다운로드 실패 (HTTP " + code + ")");
                    bridge.activity.runOnUiThread(new SongDownloadFailedTask(bridge.activity, albumId, "HTTP " + code));
                    return;
                }

                if (conn == null) {
                    bridge.showToast("연결 실패");
                    bridge.activity.runOnUiThread(new SongDownloadFailedTask(bridge.activity, albumId, "연결 실패"));
                    return;
                }

                byte[] buffer = new byte[65536];
                try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(conn.getInputStream()))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        String eName = entry.getName();
                        if (entry.isDirectory()) {
                            new File(publicTargetDir, eName).mkdirs();
                            if (canWriteModDir) {
                                try { new File(modTargetDir, eName).mkdirs(); } catch (Throwable ignored) {}
                            }
                        } else {
                            File f1 = new File(publicTargetDir, eName);
                            File p1 = f1.getParentFile();
                            if (p1 != null && !p1.exists()) p1.mkdirs();

                            try (FileOutputStream fos1 = new FileOutputStream(f1)) {
                                int read;
                                while ((read = zis.read(buffer)) != -1) {
                                    fos1.write(buffer, 0, read);
                                }
                            }

                            if (canWriteModDir) {
                                try {
                                    File f2 = new File(modTargetDir, eName);
                                    File p2 = f2.getParentFile();
                                    if (p2 != null && !p2.exists()) p2.mkdirs();
                                    try (FileInputStream in1 = new FileInputStream(f1);
                                         FileOutputStream fos2 = new FileOutputStream(f2)) {
                                        int r;
                                        while ((r = in1.read(buffer)) != -1) {
                                            fos2.write(buffer, 0, r);
                                        }
                                    }
                                } catch (Throwable ignored) {
                                    // Android 11+ Scoped storage blocks writing to other app's data dir
                                }
                            }
                        }
                        zis.closeEntry();
                    }
                }

                bridge.syncSongsToGameDataDir();
                bridge.showToast("'" + title + "' 다운로드 및 주입 완료!");
                bridge.activity.runOnUiThread(new NotifySongCallbackTask(bridge.activity, albumId));
            } catch (Exception e) {
                bridge.showToast("다운로드 오류: " + e.getMessage());
                bridge.activity.runOnUiThread(new SongDownloadFailedTask(bridge.activity, albumId, e.getMessage()));
            }
        }
    }

    @JavascriptInterface
    public void downloadHwaSong(String albumId, String title, String filename) {
        new Thread(new DownloadSongTask(this, albumId, title, filename)).start();
    }

    @JavascriptInterface
    public boolean isGameInstalled() {
        try {
            activity.getPackageManager().getPackageInfo(MOD_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static class LaunchGameTask implements Runnable {
        final MainActivity act;

        LaunchGameTask(MainActivity act) {
            this.act = act;
        }

        @Override
        public void run() {
            try {
                Intent launchIntent = act.getPackageManager().getLaunchIntentForPackage(MOD_PACKAGE);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    act.startActivity(launchIntent);
                    Toast.makeText(act, "뮤즈대시 모드를 실행합니다! 🎮", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(act, "뮤즈대시 모드 패키지(" + MOD_PACKAGE + ")가 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(act, "게임 실행 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @JavascriptInterface
    public void launchGame() {
        activity.runOnUiThread(new LaunchGameTask(activity));
    }

    static class ToastTask implements Runnable {
        final MainActivity act;
        final String msg;

        ToastTask(MainActivity act, String msg) {
            this.act = act;
            this.msg = msg;
        }

        @Override
        public void run() {
            Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
        }
    }

    @JavascriptInterface
    public void showToast(String msg) {
        activity.runOnUiThread(new ToastTask(activity, msg));
    }
}
