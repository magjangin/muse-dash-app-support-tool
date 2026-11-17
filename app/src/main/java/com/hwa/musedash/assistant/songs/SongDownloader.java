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
import java.util.concurrent.atomic.AtomicBoolean;
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
    /** 곡 하나를 푼 뒤 크기 상한. 정상 곡은 100~200MB라 넉넉하게 두고, 압축 폭탄만 막는다. */
    static final long MAX_UNPACKED_BYTES = 2L << 30;
    /** 받는 중 남겨 둘 여유 공간. ZIP 크기를 아는 경우 ZIP 크기에 더한다. */
    static final long SPACE_MARGIN = 64L << 20;
    /** ZIP 크기를 모를 때 최소로 요구하는 공간. */
    static final long SPACE_UNKNOWN_LENGTH = 256L << 20;

    /** 사용자가 받기를 취소했을 때. 임시 폴더는 지워진다. */
    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("받기를 취소했습니다");
        }
    }

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
        return download(albumId, title, filename, listener, null);
    }

    /** cancel이 true가 되면 다음 읽기에서 {@link CancelledException}으로 멈추고 임시 폴더를 지운다. */
    public File download(String albumId, String title, String filename, Listener listener, AtomicBoolean cancel) throws IOException {
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

        HttpURLConnection conn = null;
        try {
            if (cancel != null && cancel.get()) throw new CancelledException();
            conn = open(DOWNLOAD_URL + albumId);
            long length = conn.getContentLengthLong();
            String lack = spaceProblem(base.getUsableSpace(), length);
            if (lack != null) throw new IOException(lack);
            InputStream raw = new CountingStream(new BufferedInputStream(conn.getInputStream(), 1 << 16), length, listener, cancel);
            int files = extract(raw, temp, MAX_UNPACKED_BYTES);
            if (files == 0) throw new IOException("ZIP 안에 파일이 없습니다");
        } catch (IOException e) {
            Io.deleteRecursive(temp);
            throw e;
        } finally {
            if (conn != null) conn.disconnect();
        }

        if (target.exists()) Io.deleteRecursive(target);
        if (!temp.renameTo(target)) {
            Io.deleteRecursive(temp);
            throw new IOException("곡 폴더 이름을 바꿀 수 없습니다: " + target.getName());
        }
        return target;
    }

    /** 남은 공간이 모자라면 사용자에게 보일 문장, 충분하면 null. length는 ZIP 크기(모르면 0 이하). */
    static String spaceProblem(long usable, long length) {
        long need = length > 0 ? length + SPACE_MARGIN : SPACE_UNKNOWN_LENGTH;
        if (usable >= need) return null;
        return "저장 공간이 부족합니다 (필요 약 " + ((need + 1048575) >> 20) + "MB, 남음 " + (usable >> 20) + "MB)";
    }

    static int extract(InputStream in, File dir) throws IOException {
        return extract(in, dir, MAX_UNPACKED_BYTES);
    }

    /** ZIP을 dir에 푼다. 폴더 밖을 가리키는 항목이나 maxBytes를 넘는 내용이 있으면 IOException. 푼 파일 수를 돌려준다. */
    static int extract(InputStream in, File dir, long maxBytes) throws IOException {
        int files = 0;
        long total = 0;
        byte[] buf = new byte[1 << 16];
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
                        int r;
                        while ((r = zis.read(buf)) != -1) {
                            total += r;
                            if (total > maxBytes) throw new IOException("ZIP 안의 내용이 너무 큽니다 (" + (maxBytes >> 20) + "MB 초과)");
                            fos.write(buf, 0, r);
                        }
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
        private final AtomicBoolean cancel;
        private long count;
        private int lastPercent = -2;
        private long lastBytesReported;

        CountingStream(InputStream in, long length, Listener listener) {
            this(in, length, listener, null);
        }

        CountingStream(InputStream in, long length, Listener listener, AtomicBoolean cancel) {
            super(in);
            this.length = length;
            this.listener = listener;
            this.cancel = cancel;
        }

        private void checkCancel() throws CancelledException {
            if (cancel != null && cancel.get()) throw new CancelledException();
        }

        @Override
        public int read() throws IOException {
            checkCancel();
            int b = super.read();
            if (b >= 0) advance(1);
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            checkCancel();
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
