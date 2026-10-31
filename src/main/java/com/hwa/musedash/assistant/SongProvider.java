package com.hwa.musedash.assistant;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

// 게임(com.prpr.musedash.mod)이 공용 /MuseDash/hwa 의 곡을 읽어 가는 읽기 전용 통로.
// Android 11+에서 게임은 남이 만든 공용 파일을 못 읽고, 비서 앱은 게임의 Android/data 에 못 쓴다.
// 그래서 게임 쪽 mod.js(src/hwa/assistant.ts)가 이 provider로 목록을 받고 파일을 열어 자기 폴더로 복사한다.
//   content://com.hwa.musedash.assistant.songs/list          -> (path, size, mtime) 행. path는 hwa/ 기준 상대 경로
//   content://com.hwa.musedash.assistant.songs/file/<경로...> -> 읽기 전용 파일
// exported=false라 grantToGame()으로 게임에만 읽기 권한을 준다.
public class SongProvider extends ContentProvider {
    public static final String AUTHORITY = "com.hwa.musedash.assistant.songs";
    static final String HWA_DIR = "/storage/emulated/0/MuseDash/hwa";
    static final String GAME_PACKAGE = "com.prpr.musedash.mod";

    // 재부팅하면 권한이 사라지므로 앱 시작과 게임 실행 때마다 다시 준다.
    public static void grantToGame(Context ctx) {
        try {
            ctx.grantUriPermission(GAME_PACKAGE, Uri.parse("content://" + AUTHORITY + "/"),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 1 || !"list".equals(seg.get(0))) return null;
        MatrixCursor cursor = new MatrixCursor(new String[]{"path", "size", "mtime"});
        File base = new File(HWA_DIR);
        if (base.isDirectory()) addFiles(base, "", cursor);
        return cursor;
    }

    private void addFiles(File dir, String prefix, MatrixCursor cursor) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.getName().startsWith(".")) continue;
            String rel = prefix + f.getName();
            if (f.isDirectory()) {
                addFiles(f, rel + "/", cursor);
            } else {
                cursor.addRow(new Object[]{rel, f.length(), f.lastModified()});
            }
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        List<String> seg = uri.getPathSegments();
        if (seg.size() < 2 || !"file".equals(seg.get(0))) throw new FileNotFoundException(uri.toString());

        File file = new File(HWA_DIR);
        for (int i = 1; i < seg.size(); i++) file = new File(file, seg.get(i));
        try {
            // ../ 등으로 hwa 폴더 밖을 가리키면 거부
            String basePath = new File(HWA_DIR).getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(basePath)) throw new FileNotFoundException(uri.toString());
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        if (!file.isFile()) throw new FileNotFoundException(file.getPath());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }
}
