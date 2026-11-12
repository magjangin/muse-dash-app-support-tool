package com.hwa.musedash.assistant.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Io {
    private Io() {}

    public static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos, null);
        return bos.toByteArray();
    }

    /** 진행 콜백(누적 바이트)은 null이어도 된다. 복사한 바이트 수를 돌려준다. */
    public static long copy(InputStream in, OutputStream out, Progress progress) throws IOException {
        byte[] buf = new byte[1 << 16];
        long total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
            total += r;
            if (progress != null) progress.onBytes(total);
        }
        return total;
    }

    public static void copyFile(File src, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            copy(in, out, null);
        }
    }

    /** 폴더째 지운다. 모두 지웠으면 true. */
    public static boolean deleteRecursive(File f) {
        boolean ok = true;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) ok &= deleteRecursive(c);
        }
        return f.delete() && ok;
    }

    public interface Progress {
        void onBytes(long done);
    }
}
