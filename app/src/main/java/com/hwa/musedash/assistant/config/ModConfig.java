package com.hwa.musedash.assistant.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * config.txt 형식 규칙. 안드로이드·파일 I/O에 의존하지 않아 JVM 단위 테스트로 검증한다.
 *
 * <p>mod.js(src/config.ts)와 같은 규칙으로 읽는다: 키의 공백은 무시하고, 같은 키가 여러 번 있으면
 * 마지막 줄이 이긴다. 저장할 때는 원본에서 해당 키 줄만 바꿔서 앱이 모르는 키(지연·오프셋 등)와
 * 주석을 그대로 둔다.
 */
public final class ModConfig {

    /** 앱 토글 하나. id는 UI·브리지에서 쓰는 이름, key는 config.txt 의 키. */
    public static final class Option {
        public final String id;
        public final String key;
        public final boolean defaultValue;

        Option(String id, String key, boolean defaultValue) {
            this.id = id;
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }

    public static final List<Option> OPTIONS = Collections.unmodifiableList(new ArrayList<Option>() {{
        add(new Option("autoPlay", "오토플레이", false));
        add(new Option("forcePerfect", "강제퍼펙트", true));
        add(new Option("blockFever", "피버충전금지", false));
        add(new Option("enableCinema", "시네마", true));
        add(new Option("showGhostNotes", "고스트노트보이기", true));
    }});

    private static final char BOM = (char) 0xFEFF;
    private static final char NBSP = (char) 0x00A0;
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\uFEFF]+");
    private static final List<String> TRUE_WORDS = Arrays.asList("true", "1", "on", "yes", "활성화", "켜기", "켜짐");
    private static final List<String> FALSE_WORDS = Arrays.asList("false", "0", "off", "no", "비활성화", "끄기", "끔");

    private ModConfig() {}

    public static Option option(String id) {
        for (Option o : OPTIONS) if (o.id.equals(id)) return o;
        return null;
    }

    /** 설정 줄의 키(공백 제거). 주석·빈 줄·'='가 없는 줄은 null. */
    static String keyOf(String line) {
        String trimmed = strip(line);
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return null;
        int idx = trimmed.indexOf('=');
        if (idx < 0) return null;
        return SPACES.matcher(trimmed.substring(0, idx)).replaceAll("");
    }

    static Boolean parseBool(String value) {
        String v = strip(value).toLowerCase(java.util.Locale.ROOT);
        if (TRUE_WORDS.contains(v)) return Boolean.TRUE;
        if (FALSE_WORDS.contains(v)) return Boolean.FALSE;
        return null;
    }

    /** config.txt의 모든 불리언 키. 키는 config.txt 이름(한글). */
    public static Map<String, Boolean> parse(String text) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        if (text == null) return map;
        for (String line : splitLines(text)) {
            String key = keyOf(line);
            if (key == null) continue;
            Boolean b = parseBool(line.substring(line.indexOf('=') + 1));
            if (b != null) map.put(key, b);
        }
        return map;
    }

    /** 앱 토글 값. 파일에 없거나 읽을 수 없는 값이면 기본값. 키는 Option.id. */
    public static Map<String, Boolean> resolve(String text) {
        Map<String, Boolean> parsed = parse(text);
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Option o : OPTIONS) {
            Boolean v = parsed.get(o.key);
            out.put(o.id, v != null ? v : o.defaultValue);
        }
        return out;
    }

    public static Map<String, Boolean> defaults() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Option o : OPTIONS) out.put(o.id, o.defaultValue);
        return out;
    }

    /**
     * raw에 values를 반영한 새 텍스트. 해당 키 줄만 바꾸고, 없는 키는 끝에 붙인다.
     * raw가 비어 있으면 주석이 달린 기본 파일을 만든다.
     */
    public static String apply(String raw, Map<String, Boolean> values) {
        if (raw == null || strip(raw).isEmpty()) return defaultText(values);
        List<String> lines = splitLines(raw);
        for (Option o : OPTIONS) {
            boolean value = valueOf(values, o);
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (o.key.equals(keyOf(lines.get(i)))) {
                    lines.set(i, o.key + "=" + value);
                    found = true;
                }
            }
            if (!found) {
                if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
                lines.add(o.key + "=" + value);
                lines.add("");
            }
        }
        return String.join("\n", lines);
    }

    public static String defaultText(Map<String, Boolean> values) {
        return "# ============================================================================\n"
                + "#  [Muse Dash Mod Config - save custom key]\n"
                + "#  뮤즈대시 모드 비서 앱을 통해 저장된 설정 파일입니다.\n"
                + "# ============================================================================\n"
                + "\n"
                + "# ----------------------------------------------------------------------------\n"
                + "#  [1] 플레이 보조\n"
                + "# ----------------------------------------------------------------------------\n"
                + "오토플레이=" + valueOf(values, option("autoPlay")) + "\n"
                + "강제퍼펙트=" + valueOf(values, option("forcePerfect")) + "\n"
                + "피버충전금지=" + valueOf(values, option("blockFever")) + "\n"
                + "\n"
                + "# ----------------------------------------------------------------------------\n"
                + "#  [2] 연출\n"
                + "# ----------------------------------------------------------------------------\n"
                + "시네마=" + valueOf(values, option("enableCinema")) + "\n"
                + "고스트노트보이기=" + valueOf(values, option("showGhostNotes")) + "\n";
    }

    private static boolean valueOf(Map<String, Boolean> values, Option o) {
        Boolean v = values == null ? null : values.get(o.id);
        return v != null ? v : o.defaultValue;
    }

    /** "\r\n"을 "\n"으로 바꾼 뒤 줄로 나눈다. 끝의 빈 줄도 남긴다(JS split과 같다). */
    private static List<String> splitLines(String text) {
        String[] parts = text.replace("\r\n", "\n").split("\n", -1);
        List<String> out = new ArrayList<>(parts.length);
        Collections.addAll(out, parts);
        return out;
    }

    /** JS trim()처럼 BOM과 유니코드 공백까지 양끝에서 지운다. */
    static String strip(String s) {
        if (s == null) return "";
        int start = 0, end = s.length();
        while (start < end && isSpace(s.charAt(start))) start++;
        while (end > start && isSpace(s.charAt(end - 1))) end--;
        return s.substring(start, end);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == NBSP || c == BOM;
    }
}
