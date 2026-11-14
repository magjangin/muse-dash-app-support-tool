package com.hwa.musedash.assistant.songs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 곡 폴더의 info.txt. 게임 mod.js {@code src/hwa/manifest.ts}(PC HwaManifestLoader.cs) 파서를 그대로 옮겼다.
 *
 * <p>키는 공백과 기호(- _ / · . ( ) [ ])를 빼고 소문자로 만든 뒤 "포함" 규칙으로 고른다. 예: "내가 가져올 uid" → uid,
 * "커스텀 곡 제목" → customTitle, "곡 이름" → title. 확인 순서가 의미를 가진다('난이도1 bms'는 '난이도1'보다 먼저,
 * 'christmas scene'은 씬 번호보다 먼저, 'android delay'는 delay보다 먼저). 구분자는 줄에서 처음 나오는 ':' '=' '：'이고,
 * 앞에 공백이 있는 '//'부터는 주석이다. 게임 쪽 규칙이 바뀌면 여기도 같이 고친다.
 */
public final class HwaManifest {
    public static final int MAX_DIFFICULTY = 5;

    public String uid;
    public String title;
    public String customTitle;
    public String artist;
    public String customArtist;
    public String levelDesigner;
    public String album;
    public Integer scene;
    /** 칸 번호 = 난이도 번호(1~5), 0번 칸은 안 쓴다. null이면 그 난이도 없음. */
    public final Integer[] difficulty = new Integer[MAX_DIFFICULTY + 1];
    /** '난이도N bms: easy.bms' 줄. 칸 번호 = 난이도 번호. 빈 난이도는 기본 채보를 쓴다. */
    public final String[] difficultyBms = new String[MAX_DIFFICULTY + 1];
    public Double delay;
    public Double androidDelay;
    public Double offset;
    public Boolean showGhostNotes;
    public Boolean fixDtToRecommended;
    public Boolean autoPlay;
    public Boolean cinema;
    public Boolean blockFever;
    /** '크리스마스 배경 = 활성화': 이 곡의 5번 씬·보스를 크리스마스 판으로. 커스텀 곡은 전역 강제크리스마스가 아니라 이 값만 본다. */
    public Boolean christmasScene;
    public final List<String> unknownKeys = new ArrayList<>();

    /** 게임이 보여 주는 제목: 커스텀 곡 제목 → 곡 이름 순. 둘 다 없으면 null. */
    public String displayTitle() {
        return customTitle != null ? customTitle : title;
    }

    public String displayArtist() {
        return customArtist != null ? customArtist : artist;
    }

    public static HwaManifest parse(String text) {
        HwaManifest m = new HwaManifest();
        if (text == null) return m;
        for (String raw : text.replace("\r\n", "\n").split("\n", -1)) {
            String[] kv = parseLine(raw);
            if (kv != null) m.apply(kv[0], kv[1]);
        }
        return m;
    }

    static String[] parseLine(String raw) {
        String line = jsTrim(raw);
        if (line.isEmpty() || line.startsWith("//")) return null;
        line = stripTrailingComment(line);
        if (line.isEmpty()) return null;
        int at = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ':' || c == '=' || c == '：') {
                at = i;
                break;
            }
        }
        if (at < 0) return null;
        String key = jsTrim(line.substring(0, at));
        String value = jsTrim(line.substring(at + 1));
        return key.isEmpty() || value.isEmpty() ? null : new String[]{key, value};
    }

    /** 앞에 공백이 있는 '//'부터 자른다(값 안의 URL 등 '//'는 보존). */
    private static String stripTrailingComment(String line) {
        for (int i = 1; i < line.length() - 1; i++) {
            if (line.charAt(i) == '/' && line.charAt(i + 1) == '/' && isJsSpace(line.charAt(i - 1))) {
                return jsTrim(line.substring(0, i));
            }
        }
        return line;
    }

    /** JS의 \s·trim()이 지우는 문자(유니코드 공백, NBSP, BOM). Java trim()·isWhitespace는 NBSP·BOM을 못 지운다. */
    static boolean isJsSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0xFEFF;
    }

    static String jsTrim(String s) {
        int start = 0, end = s.length();
        while (start < end && isJsSpace(s.charAt(start))) start++;
        while (end > start && isJsSpace(s.charAt(end - 1))) end--;
        return s.substring(start, end);
    }

    public static String normalizeKey(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isJsSpace(ch) || "-_/·.()[]".indexOf(ch) >= 0) continue;
            out.append(Character.toLowerCase(ch));
        }
        return out.toString();
    }

    private static boolean has(String key, String... tokens) {
        for (String t : tokens) if (key.contains(t)) return true;
        return false;
    }

    static Integer parseNullableInt(String v) {
        String t = v == null ? "" : jsTrim(v);
        if (t.isEmpty() || t.equalsIgnoreCase("null") || !t.matches("[+-]?\\d+")) return null;
        try {
            return Integer.parseInt(t.startsWith("+") ? t.substring(1) : t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** JS parseFloat처럼 앞쪽 숫자 부분만 읽는다("12ms" → 12). */
    static Double parseNullableDouble(String v) {
        String t = v == null ? "" : jsTrim(v);
        if (t.isEmpty() || t.equalsIgnoreCase("null")) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?").matcher(t);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Boolean parseNullableBool(String v) {
        switch (normalizeKey(v)) {
            case "활성화": case "켜기": case "켜짐": case "true": case "on": case "yes": case "1":
                return Boolean.TRUE;
            case "비활성화": case "끄기": case "끔": case "false": case "off": case "no": case "0":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private void apply(String rawKey, String value) {
        String k = normalizeKey(rawKey);
        for (int d = 1; d <= MAX_DIFFICULTY; d++) {
            if (has(k, "난이도" + d + "bms", "난이도" + d + "채보", "difficulty" + d + "bms", "difficulty" + d + "chart")) {
                difficultyBms[d] = value.equalsIgnoreCase("null") ? null : value;
                return;
            }
        }
        if (has(k, "고스트노트보이기", "고스트보이기", "노트보이기", "ghostnote")) { showGhostNotes = parseNullableBool(value); return; }
        if (has(k, "오토플레이", "오토", "autoplay", "auto")) { autoPlay = parseNullableBool(value); return; }
        if (has(k, "시네마", "bga", "video", "cinema", "배경영상")) { cinema = parseNullableBool(value); return; }
        if (has(k, "피버충전금지", "피버차단", "피버금지", "blockfever", "nofever")) { blockFever = parseNullableBool(value); return; }
        if (has(k, "권장표", "권장dt", "dt보정", "recommendeddt", "fixdt")) { fixDtToRecommended = parseNullableBool(value); return; }
        if (has(k, "크리스마스", "christmas", "xmas")) { christmasScene = parseNullableBool(value); return; }
        String text = TextNormalizer.normalizeEnglishContractions(value);
        if (has(k, "가져올앨범", "앨범", "album")) { album = text; return; }
        if (has(k, "uid")) { uid = text; return; }
        if (has(k, "커스텀아티스트", "customartist", "customauthor")) { customArtist = text; return; }
        if (has(k, "레벨디자이너", "leveldesigner")) { levelDesigner = text; return; }
        if (has(k, "artist", "아티스트", "author")) { artist = text; return; }
        if (has(k, "커스텀곡제목", "커스텀곡이름", "커스텀곡명", "customsongtitle", "customsongname", "customtitle")) { customTitle = text; return; }
        if (has(k, "곡이름", "곡명", "곡제목", "가져올곡", "song", "title", "music")) { title = text; return; }
        if (has(k, "씬번호", "scene")) { scene = parseNullableInt(value); return; }
        for (int d = 1; d <= MAX_DIFFICULTY; d++) {
            if (has(k, "난이도" + d, "difficulty" + d)) { difficulty[d] = parseNullableInt(value); return; }
        }
        if (has(k, "androiddelay", "안드로이드delay", "안드로이드지연")) { androidDelay = parseNullableDouble(value); return; }
        if (has(k, "delay", "지연")) { delay = parseNullableDouble(value); return; }
        if (has(k, "offset", "오프셋", "싱크")) { offset = parseNullableDouble(value); return; }
        unknownKeys.add(rawKey);
    }

    /** 디버그용 한 줄 요약. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "HwaManifest{title=%s, customTitle=%s, uid=%s, scene=%s}", title, customTitle, uid, scene);
    }
}
