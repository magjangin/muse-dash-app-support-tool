package com.hwa.musedash.assistant.songs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 기기에 있는 커스텀 곡 하나. 게임 mod.js {@code src/hwa/library.ts} loadLibrary()와 같은 규칙으로 파일을 고르고
 * info.txt를 읽는다({@link ChartFiles}, {@link HwaManifest}). 그래서 앱에 보이는 제목·아티스트·BGM·자켓이 게임과 같고,
 * 게임이 건너뛸 곡은 {@link #gameSkipReason}으로 알려 준다.
 */
public final class SongInfo {
    public static final String DEFAULT_ARTIST = "Custom";

    public String folder;
    /** 게임이 보여 주는 제목(커스텀 곡 제목 → 곡 이름). info.txt에 없으면 폴더 이름. */
    public String title;
    public String artist = DEFAULT_ARTIST;
    /** 게임이 읽는 info 파일 이름(없으면 null). */
    public String infoFile;
    public HwaManifest manifest = new HwaManifest();
    /** 정식 채보 수(편집기 임시본 제외). */
    public int bmsCount;
    /** 난이도별 지정이 없는 난이도가 쓰는 채보(없으면 null). */
    public String defaultBms;
    /** 게임이 고르는 음악 파일: music.ogg/wav/mp3, 없으면 아무 .ogg. */
    public String music;
    public boolean hasMusic;
    public boolean hasVideo;
    /** 자켓 파일 이름(cover.png/jpg/jpeg, 없으면 null). */
    public String cover;
    public String path;
    public boolean pendingReset;
    /** 게임이 이 곡을 목록에 넣지 않는 이유(info 파일·채보 없음). 넣으면 null. */
    public String gameSkipReason;
    /** 곡은 들어가지만 의도와 다르게 돌 수 있는 것(난이도별 채보를 못 찾음, 음악 없음, 모르는 info 키). */
    public final List<String> warnings = new ArrayList<>();
    /** 난이도별로 실제 쓸 채보 파일. 칸 번호 = 난이도 번호(1~5). 지정이 없거나 못 쓰는 값이면 null(기본 채보). */
    public final String[] difficultyCharts = new String[HwaManifest.MAX_DIFFICULTY + 1];

    /** 앱 목록에 띄울 곡인가: 채보나 음악이 하나라도 있으면(게임이 건너뛰는 곡도 지울 수 있게 보여 준다). */
    public boolean isValid() {
        return bmsCount > 0 || hasMusic;
    }

    public boolean visibleInGame() {
        return gameSkipReason == null;
    }

    /**
     * 폴더 바로 아래 파일 이름과 info 파일 내용으로 곡을 채운다.
     *
     * @param fileNames 폴더 바로 아래 파일 이름(디렉터리 나열 순서 그대로, 하위 폴더 제외)
     * @param infoText  {@link ChartFiles#pickManifest}가 고른 파일의 내용(없으면 null)
     * @param exists    곡 폴더 기준 상대 경로의 파일이 있는가(하위 경로 '난이도N bms' 확인용)
     */
    public static SongInfo of(String folder, List<String> fileNames, String infoText, Predicate<String> exists) {
        SongInfo s = new SongInfo();
        s.folder = folder;
        s.title = folder;
        s.infoFile = ChartFiles.pickManifest(fileNames);
        if (s.infoFile != null && infoText != null) s.manifest = HwaManifest.parse(infoText);
        HwaManifest m = s.manifest;

        if (m.displayTitle() != null) s.title = m.displayTitle();
        if (m.displayArtist() != null) s.artist = m.displayArtist();

        s.bmsCount = ChartFiles.bmsFiles(fileNames).size();
        List<String> claimed = new ArrayList<>();
        for (String f : m.difficultyBms) if (f != null) claimed.add(f);
        s.defaultBms = ChartFiles.pickDefaultBms(fileNames, claimed);

        s.music = ChartFiles.pickFile(fileNames, ChartFiles.MUSIC);
        if (s.music == null) s.music = ChartFiles.pickFile(fileNames, ChartFiles.ANY_OGG);
        s.hasMusic = s.music != null;
        s.cover = ChartFiles.pickFile(fileNames, ChartFiles.COVER);
        s.hasVideo = ChartFiles.pickFile(fileNames, ChartFiles.ANY_MP4) != null;

        if (s.infoFile == null) {
            s.gameSkipReason = "info.txt가 없어 게임이 이 곡을 건너뜁니다.";
        } else if (s.defaultBms == null) {
            s.gameSkipReason = "BMS 채보가 없어 게임이 이 곡을 건너뜁니다.";
        }

        for (int d = 1; d <= HwaManifest.MAX_DIFFICULTY; d++) {
            String configured = m.difficultyBms[d];
            if (configured == null) continue;
            String name = ChartFiles.resolveChartName(configured, fileNames);
            if (name != null && name.contains("/") && (exists == null || !exists.test(name))) name = null;
            if (name == null) {
                s.warnings.add(String.format(Locale.ROOT, "난이도%d 채보 '%s'를 곡 폴더에서 찾지 못해 기본 채보로 대신합니다.", d, configured));
            } else {
                s.difficultyCharts[d] = name;
            }
        }
        if (s.visibleInGame() && !s.hasMusic) s.warnings.add("음악 파일(music.ogg 등)이 없습니다.");
        if (!m.unknownKeys.isEmpty()) s.warnings.add("게임이 모르는 info.txt 항목: " + join(m.unknownKeys));
        return s;
    }

    /** 게임에 실제로 있는 난이도의 레벨(칸 번호 = 난이도 번호). 값이 없거나 0이면 null. */
    public Integer level(int difficulty) {
        Integer v = manifest.difficulty[difficulty];
        return v == null || v == 0 ? null : v;
    }

    private static String join(Collection<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(v);
        }
        return sb.toString();
    }
}
