package com.hwa.musedash.assistant.songs;

import com.hwa.musedash.assistant.util.Io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * hwa.moe 앨범 ZIP을 받아 곡 폴더로 푼다.
 *
 * <p>hwa.moe는 308 → 307(Cloudflare R2) 리다이렉트를 거친다. HttpURLConnection은 307/308과
 * 호스트가 바뀌는 리다이렉트를 따라가지 않아서 Location을 직접 따라간다(Dead End #4).
 *
 * <p>먼저 "."으로 시작하는 임시 폴더에 다 푼 뒤 이름을 바꾼다. 중간에 끊겨도 반쪽 곡이
 * 목록이나 게임(SongProvider는 점 폴더를 건너뜀)에 나타나지 않는다.
 */
public final class SongDownloader {
    public static final String DOWNLOAD_URL = "https://www.hwa.moe/api/download/";
    static final int MAX_REDIRECTS = 6;
    static final String TEMP_PREFIX = ".dl_";

    public interface Listener {
        /** percent: 0~100, 크기를 모르면 -1. bytes: 받은 바이트. */
        void onProgress(int percent, long bytes);
    }

    private final SongLibrary library;

    public SongDownloader(SongLibrary library) {
        this.library = library;
    }

    /** 받아서 푼 곡 폴더를 돌려준다. */
    public File download(String albumId, String title, String filename, Listener listener) throws IOException {
        if (albumId == null || !albumId.matches("[A-Za-z0-9_-]+")) throw new IOException("잘못된 앨범 ID");
        String folder = SafePaths.folderName(filename);
        if (folder == null) folder = SafePaths.folderName(title);
        if (folder == null) folder = albumId;

        File base = library.baseDir();
        if (!base.exists() && !base.mkdirs()) throw new IOException("곡 폴더를 만들 수 없습니다: " + base);
        File target = new File(base, folder);
        File temp = new File(base, TEMP_PREFIX + folder);
        if (temp.exists()) Io.deleteRecursive(temp);
        if (!temp.mkdirs()) throw new IOException("임시 폴더를 만들 수 없습니다: " + temp);

        HttpURLConnection conn = open(DOWNLOAD_URL + albumId);
        try {
            long length = conn.getContentLengthLong();
            InputStream raw = new CountingStream(new BufferedInputStream(conn.getInputStream(), 1 << 16), length, listener);
            int files = extract(raw, temp);
            if (files == 0) throw new IOException("ZIP 안에 파일이 없습니다");
        } catch (IOException e) {
            Io.deleteRecursive(temp);
            throw e;
        } finally {
            conn.disconnect();
        }

        if (target.exists()) Io.deleteRecursive(target);
        if (!temp.renameTo(target)) {
            Io.deleteRecursive(temp);
            throw new IOException("곡 폴더 이름을 바꿀 수 없습니다: " + target.getName());
        }
        return target;
    }

    /** ZIP을 dir에 푼다. 폴더 밖을 가리키는 항목이 있으면 IOException. 푼 파일 수를 돌려준다. */
    static int extract(InputStream in, File dir) throws IOException {
        int files = 0;
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = SafePaths.resolveInside(dir, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        Io.copy(zis, fos, null);
                    }
                    files++;
                }
                zis.closeEntry();
            }
        }
        return files;
    }

    static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            URL u = new URL(current);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MuseDashModAssistant");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400 && code != 304) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) throw new IOException("리다이렉트 주소 없음 (HTTP " + code + ")");
                current = resolveRedirect(u, loc);
                continue;
            }
            if (code == 200) return conn;
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }
        throw new IOException("리다이렉트가 너무 많습니다");
    }

    /** 상대 Location을 절대 주소로. http(s)가 아니면 거부한다. */
    static String resolveRedirect(URL from, String location) throws IOException {
        URL next = new URL(from, location);
        String p = next.getProtocol();
        if (!"https".equals(p) && !"http".equals(p)) throw new IOException("지원하지 않는 리다이렉트: " + location);
        return next.toString();
    }

    /** 읽은 바이트를 세어 진행률을 알린다(1% 단위로만). */
    static final class CountingStream extends FilterInputStream {
        private final long length;
        private final Listener listener;
        private long count;
        private int lastPercent = -2;
        private long lastBytesReported;

        CountingStream(InputStream in, long length, Listener listener) {
            super(in);
            this.length = length;
            this.listener = listener;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) advance(1);
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) advance(n);
            return n;
        }

        private void advance(long n) {
            count += n;
            if (listener == null) return;
            if (length > 0) {
                int pct = (int) Math.min(100, count * 100 / length);
                if (pct != lastPercent) {
                    lastPercent = pct;
                    listener.onProgress(pct, count);
                }
            } else if (count - lastBytesReported >= 256 * 1024) {
                lastBytesReported = count;
                listener.onProgress(-1, count);
            }
        }
    }
}
