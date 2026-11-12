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

    // ---- SongInfo

    @Test
    public void info_readsKoreanAndEnglishKeys_keepsColonsInTitle() {
        SongInfo s = new SongInfo();
        s.applyInfoText("﻿커스텀 곡 제목 = Re:Zero 테마\n커스텀 아티스트: 화평\n");
        assertEquals("Re:Zero 테마", s.title);
        assertEquals("화평", s.artist);

        SongInfo e = new SongInfo();
        e.title = "folder";
        e.applyInfoText("Title=Hello\r\nartist = World\r\n");
        assertEquals("Hello", e.title);
        assertEquals("World", e.artist);
    }

    @Test
    public void info_emptyValueKeepsDefault() {
        SongInfo s = new SongInfo();
        s.title = "folder";
        s.applyInfoText("title=\nartist:   \n");
        assertEquals("folder", s.title);
        assertEquals("Custom", s.artist);
    }

    @Test
    public void countFile_flags() {
        SongInfo s = new SongInfo();
        for (String f : new String[]{"map1.bms", "MAP2.BMS", "music.ogg", "cover.PNG", "cover2.jpg", "bga.mp4", "info.txt"}) s.countFile(f);
        assertEquals(2, s.bmsCount);
        assertTrue(s.hasMusic);
        assertTrue(s.hasVideo);
        assertEquals("cover.PNG", s.cover);
        assertTrue(s.isValid());
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
        song(base, ".dl_partial", null, "1.bms");
        List<SongInfo> list = new SongLibrary(base).scan();
        assertEquals(2, list.size());
        assertEquals("alpha", list.get(0).title);
        assertEquals("Beta", list.get(1).title);
        assertEquals("cover.jpg", list.get(0).cover);
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
