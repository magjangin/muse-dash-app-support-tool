package com.hwa.musedash.assistant.songs;

import java.util.regex.Pattern;

/**
 * 영문 축약형 공백 교정. 게임 mod.js {@code src/hwa/text.ts}(PC Core/TextNormalizer.cs)와 같은 규칙.
 * "you'  re", "don ' t", "it ’ s"를 "you're", "don't", "it's"로 붙이고 따옴표 변형(’ ＇ ` ´)은 '로 바꾼다.
 * 대소문자는 원문 그대로 둔다. 게임이 곡 제목을 이렇게 고쳐 보여 주므로 앱도 같은 제목을 보여 준다.
 */
final class TextNormalizer {
    private static final Pattern APOSTROPHES = Pattern.compile("['’＇`´]");
    private static final Pattern NEGATIVE = Pattern.compile("\\b([A-Za-z]+?n)\\s*['’＇`´]\\s*(t)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUFFIX = Pattern.compile("\\b([A-Za-z]+)\\s*['’＇`´]\\s*(re|ve|ll|d|m|s)\\b", Pattern.CASE_INSENSITIVE);

    private TextNormalizer() {}

    static String normalizeEnglishContractions(String text) {
        if (text == null || text.isEmpty() || !APOSTROPHES.matcher(text).find()) return text;
        String out = NEGATIVE.matcher(text).replaceAll("$1'$2");
        return SUFFIX.matcher(out).replaceAll("$1'$2");
    }
}
