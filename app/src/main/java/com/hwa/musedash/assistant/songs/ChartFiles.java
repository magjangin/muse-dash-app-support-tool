package com.hwa.musedash.assistant.songs;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 곡 폴더에서 게임이 실제로 쓰는 파일을 고르는 규칙. 게임 mod.js의 {@code src/hwa/chartFiles.ts}와
 * {@code src/hwa/library.ts}(pickManifest·pickFile)를 그대로 옮겼다. 앱 목록이 게임과 같은 파일을 보고
 * "게임에서 이 곡이 보이는가"를 판단하려고 쓴다. 게임 쪽 규칙이 바뀌면 여기도 같이 고친다.
 *
 * <p>게임은 곡 폴더 바로 아래 파일만 본다(하위 폴더는 보지 않는다). 그래서 이름 목록도 폴더 바로 아래 것만 넘긴다.
 */
public final class ChartFiles {
    private static final Pattern BMS = Pattern.compile("(?i).*\\.bms");
    private static final Pattern PREFERRED_BMS = Pattern.compile("(?i)(chart|main|test)\\.bms");
    private static final Pattern PREFERRED_INFO = Pattern.compile("(?i)info1?\\.txt");
    static final Pattern MUSIC = Pattern.compile("(?i)music\\.(ogg|wav|mp3)");
    static final Pattern ANY_OGG = Pattern.compile("(?i).*\\.ogg");
    static final Pattern COVER = Pattern.compile("(?i)cover\\.(png|jpg|jpeg)");
    static final Pattern VIDEO = Pattern.compile("(?i)(video|bga|bg|movie)\\.mp4");
    static final Pattern ANY_MP4 = Pattern.compile("(?i).*\\.mp4");

    private ChartFiles() {}

    /** 편집기·동기화 도구 임시본만 거른다(tempo.bms 같은 정상 채보는 남긴다). chartFiles.ts isTempFile. */
    public static boolean isTempFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return true;
        if (fileName.startsWith("~") || fileName.startsWith("___") || fileName.startsWith(".")) return true;
        if (fileName.endsWith("~")) return true;
        int dot = fileName.lastIndexOf('.');
        String base = dot < 0 ? fileName : fileName.substring(0, dot);
        if (base.trim().isEmpty()) return true;
        String lower = base.toLowerCase(Locale.ROOT);
        for (String word : new String[]{"temp", "tmp"}) {
            if (lower.equals(word)) return true;
            if (lower.length() > word.length() && lower.startsWith(word) && !Character.isLetter(base.charAt(word.length()))) return true;
        }
        return false;
    }

    /** 폴더의 정식 채보 파일(임시본 제외), 게임과 같은 순서. */
    public static List<String> bmsFiles(Collection<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) if (BMS.matcher(n).matches() && !isTempFile(n)) out.add(n);
        sortLikeGame(out);
        return out;
    }

    /**
     * 기본 채보(난이도별 지정이 없는 난이도가 쓰는 채보). chartFiles.ts pickDefaultBms.
     * 난이도에 지정된 파일은 먼저 빼고 고르고, 전부 지정됐으면 그중에서 고른다. chart/main/test.bms가 있으면 그것.
     */
    public static String pickDefaultBms(Collection<String> names, Collection<String> claimedNames) {
        List<String> bms = bmsFiles(names);
        List<String> unclaimed = new ArrayList<>();
        for (String n : bms) {
            boolean claimed = false;
            if (claimedNames != null) for (String c : claimedNames) if (c.equalsIgnoreCase(n)) claimed = true;
            if (!claimed) unclaimed.add(n);
        }
        String pick = pickPreferred(unclaimed);
        return pick != null ? pick : pickPreferred(bms);
    }

    private static String pickPreferred(List<String> sorted) {
        for (String n : sorted) if (PREFERRED_BMS.matcher(n).matches()) return n;
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    /** 게임이 읽는 info 파일. info.txt/info1.txt가 먼저, 없으면 이름순 첫 .txt(어떤 이름이든). library.ts pickManifest. */
    public static String pickManifest(Collection<String> names) {
        List<String> txt = new ArrayList<>();
        for (String n : names) if (n.toLowerCase(Locale.ROOT).endsWith(".txt")) txt.add(n);
        sortLikeGame(txt);
        for (String n : txt) if (PREFERRED_INFO.matcher(n).matches()) return n;
        return txt.isEmpty() ? null : txt.get(0);
    }

    /** 이름 목록에서 pattern에 맞는 첫 파일(디렉터리 나열 순서). library.ts pickFile. */
    public static String pickFile(Collection<String> names, Pattern pattern) {
        for (String n : names) if (pattern.matcher(n).matches()) return n;
        return null;
    }

    /**
     * info.txt '난이도N bms' 값을 곡 폴더 기준 상대 경로로. 쓸 수 없는 값이면 null. chartFiles.ts resolveChartName.
     * 곡 폴더 안의 .bms만 받는다. 폴더 바로 아래 파일은 대소문자 없이 찾고, 하위 경로('sub/a.bms')는 존재 확인 없이 돌려준다
     * (게임도 그렇다. 실제로 없으면 게임은 읽다 실패해서 기본 채보로 대신한다).
     */
    public static String resolveChartName(String configured, Collection<String> names) {
        if (configured == null || configured.trim().isEmpty()) return null;
        String name = configured.trim().replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[a-zA-Z]:.*")) return null;
        if (!name.toLowerCase(Locale.ROOT).endsWith(".bms")) return null;
        String[] segments = name.split("/", -1);
        for (String s : segments) if (s.isEmpty() || s.equals(".") || s.equals("..")) return null;
        if (segments.length > 1) return name;
        for (String n : names) if (n.equalsIgnoreCase(name)) return n;
        return null;
    }

    /**
     * 게임은 소문자로 바꾼 이름을 JS localeCompare로 정렬한다. 가장 가까운 것이 ICU 루트 Collator다
     * (문장부호 < 숫자 < 글자 순). 코드포인트 순서와는 '_'·'-' 같은 기호가 섞일 때만 다르다.
     */
    static void sortLikeGame(List<String> names) {
        final Collator collator = Collator.getInstance(Locale.ROOT);
        Collections.sort(names, (a, b) -> collator.compare(a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT)));
    }
}
