package com.hwa.musedash.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

/** 앱·게임 버전, 저장소 권한 확인과 요청. 화면 맨 위 상태와 권한 배너가 쓴다. */
final class AppStatus {
    private AppStatus() {}

    static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static String appVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** 게임이 없으면 null, versionName이 없으면 "". */
    static String gameVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(Paths.GAME_PACKAGE, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception e) {
            return null;
        }
    }

    /** Android 11+는 '모든 파일에 대한 접근' 설정 화면, 그 아래는 런타임 권한 요청. UI 스레드에서 부른다. */
    static void requestStoragePermission(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } else {
                activity.requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 101);
            }
        } catch (Exception e) {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }
}
