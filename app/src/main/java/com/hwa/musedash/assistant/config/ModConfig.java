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
 * <p>mod.js(src/config.ts)와 같은 규칙으로 읽는다: 키의 공백은 무시하고 대소문자를 가리지 않으며,
 * 별칭(예: '크리스마스' = '강제크리스마스')도 같은 키로 본다. 같은 키가 여러 번 있으면 마지막 줄이 이긴다.
 * 저장할 때는 원본에서 해당 키(별칭 포함) 줄만 바꿔서 앱이 모르는 키(지연·오프셋 등)와 주석을 그대로 둔다.
 */
public final class ModConfig {

    // OPTIONS보다 먼저 초기화돼야 한다 (Option 생성자가 normKey로 쓴다).
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\uFEFF]+");

    /** 앱 토글 하나. id는 UI·브리지에서 쓰는 이름, key는 저장할 때 쓰는 config.txt 키, aliases는 mod.js가 같이 받는 키. */
    public static final class Option {
        public final String id;
        public final String key;
        public final boolean defaultValue;
        private final List<String> names;

        Option(String id, String key, boolean defaultValue, String... aliases) {
            this.id = id;
            this.key = key;
            this.defaultValue = defaultValue;
            List<String> n = new ArrayList<>();
            n.add(normKey(key));
            for (String a : aliases) n.add(normKey(a));
            this.names = Collections.unmodifiableList(n);
        }

        /** keyOf()가 돌려준 키가 이 토글인가 (대소문자 무시, 별칭 포함). */
        public boolean matches(String key) {
            return key != null && names.contains(normKey(key));
        }
    }

    // 키·별칭·기본값은 mod.js src/config.ts의 currentConfig와 맞춘다 (게임 저장소 tools/frida/mod).
    public static final List<Option> OPTIONS = Collections.unmodifiableList(new ArrayList<Option>() {{
        add(new Option("autoPlay", "오토플레이", false, "autoplay"));
        add(new Option("forcePerfect", "강제퍼펙트", true, "forceperfect", "올퍼펙트"));
        add(new Option("blockFever", "피버충전금지", false, "blockfever", "피버차단"));
        add(new Option("enableCinema", "시네마", true, "cinema", "bga"));
        add(new Option("showGhostNotes", "고스트노트보이기", true, "공식곡에서도고스트노트보이기", "ghostnotes"));
        add(new Option("forceChristmas", "강제크리스마스", false, "크리스마스", "forcechristmas"));
        add(new Option("enableSpineSkin", "스파인스킨", true, "커스텀스킨", "enablespineskin", "spineskin"));
    }});

    private static final char BOM = (char) 0xFEFF;
    private static final char NBSP = (char) 0x00A0;
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

    /** 앱 토글 값. 파일에 없거나 읽을 수 없는 값이면 기본값. 키는 Option.id. 별칭 줄도 읽고, 마지막 줄이 이긴다. */
    public static Map<String, Boolean> resolve(String text) {
        Map<String, Boolean> out = defaults();
        if (text == null) return out;
        for (String line : splitLines(text)) {
            Option o = optionForKey(keyOf(line));
            if (o == null) continue;
            Boolean b = parseBool(line.substring(line.indexOf('=') + 1));
            if (b != null) out.put(o.id, b);
        }
        return out;
    }

    /** config.txt 키(별칭 포함) → 토글. 모르는 키는 null. */
    static Option optionForKey(String key) {
        if (key == null) return null;
        for (Option o : OPTIONS) if (o.matches(key)) return o;
        return null;
    }

    public static Map<String, Boolean> defaults() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Option o : OPTIONS) out.put(o.id, o.defaultValue);
        return out;
    }

    /**
     * raw에 values를 반영한 새 텍스트. 해당 키 줄(별칭 줄 포함)을 '정식키=값'으로 바꾸고, 없는 키는 끝에 붙인다.
     * raw가 비어 있으면 주석이 달린 기본 파일을 만든다.
     */
    public static String apply(String raw, Map<String, Boolean> values) {
        if (raw == null || strip(raw).isEmpty()) return defaultText(values);
        List<String> lines = splitLines(raw);
        for (Option o : OPTIONS) {
            boolean value = valueOf(values, o);
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (o.matches(keyOf(lines.get(i)))) {
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

    /**
     * 파일이 비었을 때 쓰는 주석 달린 기본 파일. 주석은 mod.js src/config.ts의 DEFAULT_CONFIG_TEXT(게임이 앱 전용 폴더에
     * 만드는 폴백 파일)와 같은 설명을 쓰되, 적용 시점과 곡 info.txt와의 우선순위를 덧붙였다(게임 코드 기준:
     * autoplay.ts·fever.ts는 전역 또는 곡, customChart.ts 시네마는 전역 그리고 곡, ghost.ts는 곡 값 우선).
     */
    public static String defaultText(Map<String, Boolean> values) {
        return "# ============================================================================\n"
                + "#  [Muse Dash Mod Config - save custom key]\n"
                + "#  뮤즈대시 모드 비서 앱이 만든 설정 파일입니다. 앱 토글이나 직접 편집으로 바꿀 수 있습니다.\n"
                + "#  게임이 1초마다 다시 읽습니다. 시네마·강제크리스마스·스파인스킨은 다음 판(배틀 시작)부터 반영됩니다.\n"
                + "#  값: true / false (활성화·비활성화, on·off도 됩니다)\n"
                + "# ============================================================================\n"
                + "\n"
                + "# ----------------------------------------------------------------------------\n"
                + "#  [1] 플레이 보조\n"
                + "#  - 오토플레이   : 차트를 완전 자동으로 연주합니다. 자동 연주는 기록을 남기지 않습니다.\n"
                + "#  - 강제퍼펙트   : 타격 판정을 무조건 Perfect로 올립니다.\n"
                + "#  - 피버충전금지 : 피버 게이지가 차지 않게 막습니다.\n"
                + "#  (오토플레이·피버충전금지는 곡 info.txt에서 켜 두면 그 곡에서는 여기 값과 상관없이 켜집니다)\n"
                + "# ----------------------------------------------------------------------------\n"
                + "오토플레이=" + valueOf(values, option("autoPlay")) + "\n"
                + "강제퍼펙트=" + valueOf(values, option("forcePerfect")) + "\n"
                + "피버충전금지=" + valueOf(values, option("blockFever")) + "\n"
                + "\n"
                + "# ----------------------------------------------------------------------------\n"
                + "#  [2] 연출\n"
                + "#  - 시네마           : 커스텀 곡의 BGA 비디오(.mp4)를 재생합니다. 곡 info.txt에서 끄면 그 곡은 꺼집니다.\n"
                + "#  - 고스트노트보이기 : 고스트 노트가 투명해지지 않게 유지합니다. 곡 info.txt에 값이 있으면 그 값이 우선입니다.\n"
                + "# ----------------------------------------------------------------------------\n"
                + "시네마=" + valueOf(values, option("enableCinema")) + "\n"
                + "고스트노트보이기=" + valueOf(values, option("showGhostNotes")) + "\n"
                + "\n"
                + "# ----------------------------------------------------------------------------\n"
                + "#  [3] 테스트용\n"
                + "#  - 강제크리스마스 : 날짜와 상관없이 크리스마스(12월 24·25일) 연출을 켭니다.\n"
                + "#                     공식 곡의 5번 씬 배경·보스가 크리스마스 판으로 바뀝니다.\n"
                + "#                     커스텀 곡은 곡 info.txt의 '크리스마스 배경 = 활성화'로만 바뀝니다.\n"
                + "# ----------------------------------------------------------------------------\n"
                + "강제크리스마스=" + valueOf(values, option("forceChristmas")) + "\n"
                + "\n"
                + "# ----------------------------------------------------------------------------\n"
                + "#  [4] 스킨\n"
                + "#  - 스파인스킨 : <게임 files>/skin test/ 의 커스텀 Spine 스킨을 씌웁니다.\n"
                + "#                 캐릭터(character/)는 모든 곡, 노트(note/)는 크리스마스 곡에만 적용됩니다.\n"
                + "# ----------------------------------------------------------------------------\n"
                + "스파인스킨=" + valueOf(values, option("enableSpineSkin")) + "\n";
    }

    private static boolean valueOf(Map<String, Boolean> values, Option o) {
        Boolean v = values == null ? null : values.get(o.id);
        return v != null ? v : o.defaultValue;
    }

    /** 키 비교용: 공백을 지우고 소문자로 (mod.js의 replace(/\s+/g, "").toLowerCase()). */
    static String normKey(String key) {
        return SPACES.matcher(key).replaceAll("").toLowerCase(java.util.Locale.ROOT);
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
