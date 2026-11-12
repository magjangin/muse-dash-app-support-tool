package com.hwa.musedash.assistant.songs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

/** 기기에 있는 커스텀 곡 하나. */
public final class SongInfo {
    public String folder;
    public String title;
    public String artist = "Custom";
    public int bmsCount;
    public boolean hasMusic;
    public boolean hasVideo;
    /** 자켓 파일 이름 (없으면 null). */
    public String cover;
    public String path;
    public boolean pendingReset;

    public boolean isValid() {
        return bmsCount > 0 || hasMusic;
    }

    /** 파일 이름 하나를 보고 개수·플래그를 갱신한다. */
    public void countFile(String fileName) {
        String fn = fileName.toLowerCase(Locale.ROOT);
        if (fn.endsWith(".bms")) bmsCount++;
        if (fn.endsWith(".ogg") || fn.endsWith(".mp3") || fn.endsWith(".wav")) hasMusic = true;
        if (fn.endsWith(".mp4")) hasVideo = true;
        if (cover == null && fn.startsWith("cover") && (fn.endsWith(".png") || fn.endsWith(".jpg") || fn.endsWith(".jpeg"))) {
            cover = fileName;
        }
    }

    public static boolean isInfoFile(String fileName) {
        String fn = fileName.toLowerCase(Locale.ROOT);
        return fn.contains("info") && fn.endsWith(".txt");
    }

    /**
     * info.txt에서 제목·아티스트를 읽는다. "커스텀 곡 제목 = …" / "title: …" 형식.
     * 값은 첫 ':' 또는 '=' 뒤 전체다(제목에 ':'가 들어가도 잘리지 않게).
     */
    public void applyInfoText(String text) {
        try (BufferedReader r = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.replace(String.valueOf((char) 0xFEFF), "").trim();
                String lower = t.toLowerCase(Locale.ROOT);
                if (t.startsWith("커스텀 곡 제목") || lower.startsWith("title")) {
                    String v = valueOf(t);
                    if (v != null) title = v;
                } else if (t.startsWith("커스텀 아티스트") || lower.startsWith("artist")) {
                    String v = valueOf(t);
                    if (v != null) artist = v;
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** info.txt 텍스트의 제목 (없으면 null). */
    public static String titleOf(String infoText) {
        SongInfo s = new SongInfo();
        s.applyInfoText(infoText);
        return s.title;
    }

    private static String valueOf(String line) {
        int idx = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ':' || c == '=') {
                idx = i;
                break;
            }
        }
        if (idx < 0) return null;
        String v = line.substring(idx + 1).trim();
        return v.isEmpty() ? null : v;
    }
}
