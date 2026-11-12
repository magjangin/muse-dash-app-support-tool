package com.hwa.musedash.assistant;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * 시스템 설치 화면에 APK를 넘기는 provider. androidx FileProvider 대신 쓰는 최소 구현이다.
 * URI 경로는 파일의 절대 경로이고, 앱 캐시와 공용 저장소 아래 파일만 연다.
 */
public class GenericFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.hwa.musedash.assistant.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    public static Uri getUriForFile(File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).path(file.getAbsolutePath()).build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException(String.valueOf(uri));
        File file = new File(path);
        try {
            String canonical = file.getCanonicalPath();
            String cache = getContext().getCacheDir().getCanonicalPath() + File.separator;
            String storage = new File(Paths.STORAGE_ROOT).getCanonicalPath() + File.separator;
            if (!canonical.startsWith(cache) && !canonical.startsWith(storage)) throw new FileNotFoundException(path);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        return file;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (projection == null) projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        File file;
        try {
            file = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(projection, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) row.add(file.getName());
            else if (OpenableColumns.SIZE.equals(col)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = fileFor(uri);
        if (!file.isFile()) throw new FileNotFoundException("File not found: " + uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
