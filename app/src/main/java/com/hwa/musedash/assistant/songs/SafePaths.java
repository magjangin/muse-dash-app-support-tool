package com.hwa.musedash.assistant.songs;

import java.io.File;
import java.io.IOException;

/** 서버·ZIP에서 온 이름을 파일 경로로 바꿀 때의 안전 규칙. 순수 Java라 단위 테스트로 검증한다. */
public final class SafePaths {
    private SafePaths() {}

    /**
     * 곡 폴더 이름. ".zip"을 떼고, 경로 구분자·윈도 금지 문자·제어 문자를 '_'로 바꾸고,
     * 앞쪽 점은 지운다(점으로 시작하는 폴더는 SongProvider가 숨김으로 취급한다). 쓸 수 없으면 null.
     */
    public static String folderName(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) s = s.substring(0, s.length() - 4).trim();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F || "/\\:*?\"<>|".indexOf(c) >= 0) sb.append('_');
            else sb.append(c);
        }
        s = sb.toString().trim();
        while (s.startsWith(".")) s = s.substring(1);
        s = s.trim();
        if (s.isEmpty()) return null;
        if (s.length() > 120) s = s.substring(0, 120).trim();
        return s;
    }

    /** base 아래의 entryName 경로. "../" 등으로 base 밖을 가리키면 IOException (zip slip 방지). */
    public static File resolveInside(File base, String entryName) throws IOException {
        File target = new File(base, entryName);
        String basePath = base.getCanonicalPath() + File.separator;
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(basePath)) {
            throw new IOException("허용되지 않는 경로: " + entryName);
        }
        return target;
    }
}
