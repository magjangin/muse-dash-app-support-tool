package com.hwa.musedash.assistant.songs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SongsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ---- SafePaths

    @Test
    public void folderName_stripsZipAndUnsafeChars() {
        assertEquals("My Song", SafePaths.folderName("  My Song.ZIP "));
        assertEquals("a_b_c", SafePaths.folderName("a/b\\c"));
        assertEquals("_ evil", SafePaths.folderName("../ evil"));
        assertEquals("hidden", SafePaths.folderName("...hidden"));
        assertEquals("매칭앱 프린세스", SafePaths.folderName("매칭앱 프린세스.zip"));
    }

    @Test
    public void folderName_rejectsEmpty() {
        assertNull(SafePaths.folderName(null));
        assertNull(SafePaths.folderName("   "));
        assertNull(SafePaths.folderName(".zip"));
        assertNull(SafePaths.folderName(".."));
    }

    @Test
    public void resolveInside_blocksTraversal() throws IOException {
        File base = tmp.newFolder("base");
        assertEquals(new File(base, "a/b.txt").getCanonicalPath(), SafePaths.resolveInside(base, "a/b.txt").getCanonicalPath());
        for (String bad : new String[]{"../x.txt", "a/../../x.txt", "../base2/x"}) {
            try {
                SafePaths.resolveInside(base, bad);
                fail("should reject " + bad);
            } catch (IOException expected) {
            }
        }
    }

    // ---- HwaManifest (게임 src/hwa/manifest.ts와 같은 규칙)

    @Test
    public void manifest_keysByContainsRule_firstSeparatorWins() {
        HwaManifest m = HwaManifest.parse("\uFEFF커스텀 곡 제목 = Re:Zero 테마\n커스텀 아티스트: 화평\n"
                + "곡 이름 = 원곡\n내가 가져올 uid : 0-4\n씬 번호 = 5\n난이도1 = 3\n난이도 2 : 7\nDifficulty3=null\n");
        assertEquals("Re:Zero 테마", m.customTitle);
        assertEquals("원곡", m.title);
        assertEquals("Re:Zero 테마", m.displayTitle());
        assertEquals("화평", m.displayArtist());
        assertEquals("0-4", m.uid);
        assertEquals(Integer.valueOf(5), m.scene);
        assertEquals(Integer.valueOf(3), m.difficulty[1]);
        assertEquals(Integer.valueOf(7), m.difficulty[2]);
        assertNull(m.difficulty[3]);
        assertTrue(m.unknownKeys.isEmpty());
    }

    @Test
    public void manifest_orderMatters_difficultyBmsChristmasAndroidDelay() {
        HwaManifest m = HwaManifest.parse("난이도1 bms: easy.bms\n난이도 3 채보 = hard.BMS\n크리스마스 배경 = 활성화\n"
                + "christmas scene: on\nandroid delay = -0.05\ndelay = 0.12s\noffset=+30\n고스트 노트 보이기 = 끔\n"
                + "권장표 = 켜기\n오토 = maybe\nbpm = 180\n");
        assertEquals("easy.bms", m.difficultyBms[1]);
        assertEquals("hard.BMS", m.difficultyBms[3]);
        assertNull(m.difficulty[1]);          // 'bms' 줄이 레벨 숫자로 가지 않는다
        assertEquals(Boolean.TRUE, m.christmasScene);
        assertNull(m.scene);                  // 'christmas scene'이 씬 번호로 가지 않는다
        assertEquals(-0.05, m.androidDelay, 1e-9);
        assertEquals(0.12, m.delay, 1e-9);    // parseFloat처럼 앞쪽 숫자만
        assertEquals(30.0, m.offset, 1e-9);
        assertEquals(Boolean.FALSE, m.showGhostNotes);
        assertEquals(Boolean.TRUE, m.fixDtToRecommended);
        assertNull(m.autoPlay);               // 모르는 값은 null(전역 설정을 따름)
        assertEquals(java.util.Collections.singletonList("bpm"), m.unknownKeys);
    }

    @Test
    public void manifest_commentsAndContractions() {
        HwaManifest m = HwaManifest.parse("// 주석\ntitle = Don ' t Stop  // 끝 주석\nartist = https://a.b/c\nscene=\n");
        assertEquals("Don't Stop", m.title);
        assertEquals("https://a.b/c", m.artist);   // 앞에 공백 없는 '//'는 주석이 아니다
        assertNull(m.scene);
        assertEquals("you're it's DON'T", TextNormalizer.normalizeEnglishContractions("you'  re it \u2019 s DON ' T"));
    }

    // ---- ChartFiles (게임 src/hwa/chartFiles.ts·library.ts와 같은 규칙)

    @Test
    public void chartFiles_tempFilesAndDefaultBms() {
        assertTrue(ChartFiles.isTempFile("~a.bms"));
        assertTrue(ChartFiles.isTempFile("temp.bms"));
        assertTrue(ChartFiles.isTempFile("tmp_1.bms"));
        assertTrue(ChartFiles.isTempFile(".bms"));
        assertFalse(ChartFiles.isTempFile("tempo.bms"));
        assertEquals("main.bms", ChartFiles.pickDefaultBms(Arrays.asList("b.bms", "main.bms", "a.bms"), null));
        assertEquals("a.bms", ChartFiles.pickDefaultBms(Arrays.asList("b.bms", "a.bms", "temp.bms"), null));
        // 난이도에 지정된 파일은 다른 난이도의 기본 채보가 되지 않는다
        assertEquals("hwa2.bms", ChartFiles.pickDefaultBms(Arrays.asList("easy.bms", "hwa2.bms"), Arrays.asList("EASY.bms")));
        assertEquals("easy.bms", ChartFiles.pickDefaultBms(Arrays.asList("easy.bms"), Arrays.asList("easy.bms")));
        assertNull(ChartFiles.pickDefaultBms(Arrays.asList("music.ogg"), null));
    }

    @Test
    public void chartFiles_manifestAndChartNames() {
        assertEquals("Info.txt", ChartFiles.pickManifest(Arrays.asList("a.txt", "Info.txt", "z.txt")));
        assertEquals("hwa info.txt", ChartFiles.pickManifest(Arrays.asList("readme.txt", "hwa info.txt")));
        assertNull(ChartFiles.pickManifest(Arrays.asList("a.bms")));
        List<String> names = Arrays.asList("Easy.bms", "x.bms");
        assertEquals("Easy.bms", ChartFiles.resolveChartName(" easy.bms ", names));
        assertEquals("sub/a.bms", ChartFiles.resolveChartName("sub\\a.bms", names));
        assertNull(ChartFiles.resolveChartName("../a.bms", names));
        assertNull(ChartFiles.resolveChartName("/sdcard/a.bms", names));
        assertNull(ChartFiles.resolveChartName("C:/a.bms", names));
        assertNull(ChartFiles.resolveChartName("a.txt", names));
        assertNull(ChartFiles.resolveChartName("none.bms", names));
    }

    // ---- SongInfo

    @Test
    public void songInfo_usesGameRules() {
        SongInfo s = SongInfo.of("folder", Arrays.asList("map1.bms", "MAP2.BMS", "~map3.bms", "song.mp3", "cover2.jpg", "cover.PNG", "bga.mp4", "info.txt"),
                "커스텀 곡 제목 = Hello\n난이도1 = 2\n난이도2 = 0\n난이도3 = 9\n난이도2 bms = map2.bms\n난이도3 bms = sub/none.bms\n오토플레이 = 켜기\n", rel -> false);
        assertEquals("Hello", s.title);
        assertEquals(SongInfo.DEFAULT_ARTIST, s.artist);
        assertEquals(2, s.bmsCount);
        assertEquals("map1.bms", s.defaultBms);
        assertEquals("MAP2.BMS", s.difficultyCharts[2]);
        assertNull(s.difficultyCharts[3]);
        assertEquals("cover.PNG", s.cover);
        assertFalse(s.hasMusic);            // song.mp3는 게임이 고르지 않는다(music.* 또는 .ogg)
        assertTrue(s.hasVideo);
        assertTrue(s.visibleInGame());
        assertEquals(Integer.valueOf(2), s.level(1));
        assertNull(s.level(2));             // 0 = 그 난이도 없음
        assertEquals(Integer.valueOf(9), s.level(3));
        assertEquals(Boolean.TRUE, s.manifest.autoPlay);
        assertEquals(2, s.warnings.size()); // 난이도3 채보 없음 + 음악 없음
        assertTrue(s.warnings.get(0).contains("난이도3"));
    }

    @Test
    public void songInfo_gameSkipReasons() {
        SongInfo noInfo = SongInfo.of("a", Arrays.asList("chart.bms", "music.ogg"), null, null);
        assertTrue(noInfo.isValid());
        assertFalse(noInfo.visibleInGame());
        assertTrue(noInfo.gameSkipReason.contains("info"));
        assertEquals("a", noInfo.title);

        SongInfo noBms = SongInfo.of("b", Arrays.asList("info.txt", "x.ogg", "temp.bms"), "title=B", null);
        assertTrue(noBms.isValid());
        assertFalse(noBms.visibleInGame());
        assertTrue(noBms.gameSkipReason.contains("BMS"));
        assertEquals("x.ogg", noBms.music);

        SongInfo ok = SongInfo.of("c", Arrays.asList("readme.txt", "1.bms", "music.wav"), "title: C", null);
        assertTrue(ok.visibleInGame());
        assertEquals("readme.txt", ok.infoFile);   // info.txt가 없으면 이름순 첫 .txt
        assertEquals("C", ok.title);
        assertTrue(ok.warnings.isEmpty());
    }

    // ---- SongLibrary

    private File song(File base, String folder, String info, String... files) throws IOException {
        File d = new File(base, folder);
        d.mkdirs();
        for (String f : files) new File(d, f).createNewFile();
        if (info != null) write(new File(d, "info.txt"), info);
        return d;
    }

    private static void write(File f, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void library_scanSkipsInvalidAndTempFolders_sortsByTitle() throws IOException {
        File base = tmp.newFolder("hwa");
        song(base, "b-song", "title=Beta", "1.bms");
        song(base, "a-song", "title=alpha", "music.ogg", "cover.jpg");
        song(base, "empty", null, "readme.txt");
        song(base, "no-info", null, "chart.bms");    // 게임은 건너뛰지만 앱 목록엔 보인다(지울 수 있게)
        song(base, ".dl_partial", null, "1.bms");
        List<SongInfo> list = new SongLibrary(base).scan();
        assertEquals(3, list.size());
        assertEquals("alpha", list.get(0).title);
        assertEquals("Beta", list.get(1).title);
        assertEquals("no-info", list.get(2).title);
        assertEquals("cover.jpg", list.get(0).cover);
        assertFalse(list.get(0).visibleInGame());      // a-song: BMS 없음
        assertTrue(list.get(1).visibleInGame());
        assertFalse(list.get(2).visibleInGame());
    }

    @Test
    public void library_deletePrefersExactFolder() throws IOException {
        File base = tmp.newFolder("hwa");
        File a = song(base, "Song", "title=Other", "1.bms");
        File b = song(base, "x", "title=Song", "1.bms");
        assertEquals(1, new SongLibrary(base).delete("song"));
        assertFalse(a.exists());
        assertTrue(b.exists());
        // 폴더 이름이 없으면 제목으로 찾는다
        assertEquals(1, new SongLibrary(base).delete("Song"));
        assertFalse(b.exists());
        assertEquals(0, new SongLibrary(base).delete("none"));
        assertEquals(0, new SongLibrary(base).delete("  "));
    }

    @Test
    public void library_hideRemovesFromListAtOnce_cleanupClearsLeftovers() throws IOException {
        File base = tmp.newFolder("hwa");
        song(base, "keep", null, "1.bms");
        song(base, "gone", null, "1.bms", "2.bms");
        song(base, ".dl_stale", null, "1.bms");
        SongLibrary lib = new SongLibrary(base);
        List<File> hidden = lib.hide("gone");
        assertEquals(1, hidden.size());
        assertTrue(hidden.get(0).getName().startsWith(".del_"));
        assertEquals(1, lib.scan().size());          // 지우기 전에 이미 목록에서 빠짐
        assertEquals(2, lib.cleanupLeftovers());      // .del_ + .dl_
        assertFalse(hidden.get(0).exists());
        assertEquals(1, base.listFiles().length);
    }

    @Test
    public void library_coverOf() throws IOException {
        File base = tmp.newFolder("hwa");
        song(base, "s", null, "1.bms", "cover.png");
        song(base, "n", null, "1.bms");
        SongLibrary lib = new SongLibrary(base);
        assertEquals("cover.png", lib.coverOf("s").getName());
        assertNull(lib.coverOf("n"));
        assertNull(lib.coverOf("../s"));
        assertNull(lib.coverOf("missing"));
    }

    // ---- SongDownloader

    private static byte[] zip(String... namesAndContents) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                z.putNextEntry(new ZipEntry(namesAndContents[i]));
                if (namesAndContents[i + 1] != null) z.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    public void extract_writesFilesAndDirs() throws IOException {
        File dir = tmp.newFolder("out");
        int n = SongDownloader.extract(new ByteArrayInputStream(zip("sub/", null, "sub/a.bms", "x", "info.txt", "title=t")), dir);
        assertEquals(2, n);
        assertTrue(new File(dir, "sub/a.bms").isFile());
        assertTrue(new File(dir, "info.txt").isFile());
    }

    @Test
    public void extract_rejectsZipSlip() throws IOException {
        File dir = tmp.newFolder("out");
        try {
            SongDownloader.extract(new ByteArrayInputStream(zip("ok.txt", "1", "../escape.txt", "2")), dir);
            fail("zip slip not rejected");
        } catch (IOException expected) {
        }
        assertFalse(new File(dir.getParentFile(), "escape.txt").exists());
    }

    @Test
    public void redirect_resolvesRelativeAndRejectsOtherSchemes() throws IOException {
        URL from = new URL("https://www.hwa.moe/api/download/1");
        assertEquals("https://www.hwa.moe/files/a.zip", SongDownloader.resolveRedirect(from, "/files/a.zip"));
        assertEquals("https://cdn.example.com/a.zip", SongDownloader.resolveRedirect(from, "https://cdn.example.com/a.zip"));
        try {
            SongDownloader.resolveRedirect(from, "file:///etc/passwd");
            fail("file: redirect accepted");
        } catch (IOException expected) {
        }
    }

    @Test
    public void countingStream_reportsWholePercentsOnce() throws IOException {
        StringBuilder seen = new StringBuilder();
        byte[] data = new byte[1000];
        SongDownloader.CountingStream in = new SongDownloader.CountingStream(new ByteArrayInputStream(data), 1000,
                (pct, bytes) -> seen.append(pct).append(','));
        byte[] buf = new byte[250];
        while (in.read(buf, 0, buf.length) != -1) { /* drain */ }
        assertEquals("25,50,75,100,", seen.toString());
    }
}
