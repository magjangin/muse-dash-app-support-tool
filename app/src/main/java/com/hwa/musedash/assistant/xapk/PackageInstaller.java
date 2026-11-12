package com.hwa.musedash.assistant.xapk;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.hwa.musedash.assistant.GenericFileProvider;
import com.hwa.musedash.assistant.Paths;
import com.hwa.musedash.assistant.util.Io;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 다운로드 폴더의 APK/XAPK 찾기와 설치. */
public final class PackageInstaller {
    private static final String TAG = "MuseDashAssistant";

    public interface Listener {
        /** 진행 메시지. percent는 0~100, 모르면 -1. */
        void onStatus(String message, int percent);

        void onError(String message);
    }

    private final Activity activity;

    public PackageInstaller(Activity activity) {
        this.activity = activity;
    }

    public JSONArray scan() {
        JSONArray arr = new JSONArray();
        String[] searchPaths = {Paths.DOWNLOAD_DIR, Paths.PUBLIC_DIR, Paths.STORAGE_ROOT};
        for (String p : searchPaths) {
            File[] list = new File(p).listFiles();
            if (list == null) continue;
            for (File f : list) {
                if (!f.isFile()) continue;
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".xapk") && !name.endsWith(".apk")) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("name", f.getName());
                    o.put("path", f.getAbsolutePath());
                    o.put("size", f.length());
                    o.put("modified", f.lastModified());
                    o.put("isXapk", name.endsWith(".xapk"));
                    o.put("isMuseDash", XapkRules.looksLikeMuseDash(f.getName()));
                    arr.put(o);
                } catch (Exception ignored) {
                }
            }
        }
        return arr;
    }

    public boolean canInstallPackages() {
        return activity.getPackageManager().canRequestPackageInstalls();
    }

    /** '출처를 알 수 없는 앱 설치' 설정 화면을 연다 (UI 스레드). */
    public void openInstallPermissionSettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        i.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(i);
    }

    /**
     * XAPK를 푼다(백그라운드 스레드에서 호출). OBB는 Android/obb에 바로 쓰고 base APK는 캐시에 뽑아
     * 돌려준다. APK를 못 찾으면 null.
     *
     * <p>Android 11+에서는 '알 수 없는 앱 설치'를 허용받은 앱만 남의 Android/obb/<패키지>에 쓸 수 있다
     * (Dead End #12). 호출 전에 canInstallPackages()를 확인할 것.
     */
    public File extractXapk(File xapk, Listener listener) throws IOException {
        File apkOut = new File(activity.getCacheDir(), "extracted_base.apk");
        String targetPackage = Paths.GAME_PACKAGE;
        boolean foundApk = false;
        int obbCount = 0;
        long totalSize = xapk.length();

        try (FileInputStream fin = new FileInputStream(xapk);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fin, 1 << 20))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("manifest.json")) {
                    String pkg = new JSONObject(new String(Io.readAll(zis), StandardCharsets.UTF_8)).optString("package_name", "");
                    if (XapkRules.isValidPackageName(pkg)) targetPackage = pkg;
                } else if (XapkRules.takeApk(name, foundApk)) {
                    listener.onStatus("APK 추출 중: " + new File(name).getName(), -1);
                    try (FileOutputStream fos = new FileOutputStream(apkOut)) {
                        Io.copy(zis, fos, null);
                    }
                    foundApk = true;
                } else if (XapkRules.isObb(name)) {
                    File obb = new File(XapkRules.obbTarget(Paths.STORAGE_ROOT, name, targetPackage));
                    obbCount++;
                    // 같은 크기로 이미 있으면 건너뛴다 (재설치 때 4GB를 또 쓰지 않게). 끊긴 파일은 크기가 달라 다시 쓴다.
                    if (entry.getSize() >= 0 && obb.length() == entry.getSize()) {
                        listener.onStatus("OBB 이미 있음: " + obb.getName(), -1);
                    } else {
                        File parent = obb.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            throw new IOException("OBB 폴더를 만들 수 없습니다 (" + parent.getPath() + "). '알 수 없는 앱 설치' 허용을 확인해 주세요.");
                        }
                        final String label = "OBB 배치 중: " + obb.getName();
                        final long entrySize = entry.getSize();
                        try (FileOutputStream fos = new FileOutputStream(obb)) {
                            final int[] last = {-1};
                            Io.copy(zis, fos, done -> {
                                int pct = entrySize > 0 ? (int) (done * 100 / entrySize) : -1;
                                if (pct != last[0]) {
                                    last[0] = pct;
                                    listener.onStatus(label, pct);
                                }
                            });
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (org.json.JSONException e) {
            throw new IOException("manifest.json 형식 오류: " + e.getMessage());
        }
        Log.i(TAG, "XAPK extracted: apk=" + foundApk + " obb=" + obbCount + " pkg=" + targetPackage + " size=" + totalSize);
        if (obbCount > 0) listener.onStatus("OBB " + obbCount + "개 배치 완료", 100);
        return foundApk ? apkOut : null;
    }

    /**
     * 시스템 설치 화면을 연다 (UI 스레드).
     * 'APK 열기' 기본 앱이 파일 관리자로 잡힌 폰에선 ACTION_VIEW가 그 앱으로 간다(Dead End #13).
     * 그래서 INSTALL_PACKAGE를 받는 시스템 앱으로 받는 곳을 못박는다.
     */
    public void launchInstaller(File apk) {
        Uri apkUri = GenericFileProvider.getUriForFile(apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent probe = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        probe.setDataAndType(apkUri, "application/vnd.android.package-archive");
        List<ResolveInfo> installers = activity.getPackageManager().queryIntentActivities(probe, PackageManager.MATCH_SYSTEM_ONLY);
        if (!installers.isEmpty()) intent.setPackage(installers.get(0).activityInfo.packageName);
        activity.startActivity(intent);
    }
}
