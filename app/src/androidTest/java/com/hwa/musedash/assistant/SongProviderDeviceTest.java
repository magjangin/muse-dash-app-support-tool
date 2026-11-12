package com.hwa.musedash.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.hwa.musedash.assistant.records.RecordResetQueue;
import com.hwa.musedash.assistant.util.Io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 실기기에서 SongProvider 규약(게임 mod.js가 쓰는 것)을 확인한다.
 * 공용 /MuseDash/hwa 아래에 시험용 폴더 하나만 만들고 끝나면 지운다. 기존 곡은 건드리지 않는다.
 */
@RunWith(AndroidJUnit4.class)
public class SongProviderDeviceTest {
    private static final String TEST_FOLDER = "__assistant_device_test__";
    private static final Uri BASE = Uri.parse("content://" + SongProvider.AUTHORITY);

    private Context ctx;
    private ContentResolver resolver;
    private File testDir;
    private Set<String> savedResets;

    @Before
    public void setUp() throws Exception {
        assumeTrue("모든 파일 접근 권한 필요", Environment.isExternalStorageManager());
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        resolver = ctx.getContentResolver();
        testDir = new File(Paths.HWA_DIR, TEST_FOLDER);
        Io.deleteRecursive(testDir);
        assertTrue(new File(testDir, "sub").mkdirs());
        write(new File(testDir, "info.txt"), "title=Device Test");
        write(new File(testDir, "sub/chart.bms"), "#TITLE test\n");
        write(new File(testDir, ".hidden"), "x");
        savedResets = RecordResetQueue.pending(ctx);
    }

    @After
    public void tearDown() {
        if (testDir != null) Io.deleteRecursive(testDir);
        if (ctx != null) {
            RecordResetQueue.clear(ctx);
            for (String s : savedResets) RecordResetQueue.add(ctx, s);
        }
    }

    private static void write(File f, String text) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void list_returnsRelativePathsWithSizes_andSkipsDotFiles() {
        Map<String, Long> rows = new HashMap<>();
        try (Cursor c = resolver.query(BASE.buildUpon().appendPath("list").build(), null, null, null, null)) {
            assertNotNull(c);
            assertEquals(3, c.getColumnCount());
            int p = c.getColumnIndexOrThrow("path"), s = c.getColumnIndexOrThrow("size");
            c.getColumnIndexOrThrow("mtime");
            while (c.moveToNext()) rows.put(c.getString(p), c.getLong(s));
        }
        assertEquals(Long.valueOf(17), rows.get(TEST_FOLDER + "/info.txt"));
        assertEquals(Long.valueOf(12), rows.get(TEST_FOLDER + "/sub/chart.bms"));
        assertFalse(rows.containsKey(TEST_FOLDER + "/.hidden"));
    }

    @Test
    public void openFile_readsInsideHwa() throws Exception {
        Uri uri = BASE.buildUpon().appendPath("file").appendPath(TEST_FOLDER).appendPath("sub").appendPath("chart.bms").build();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            assertNotNull(pfd);
            byte[] data = Io.readAll(new FileInputStream(pfd.getFileDescriptor()));
            assertEquals("#TITLE test\n", new String(data, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void openFile_rejectsTraversalAndWrite() throws Exception {
        Uri escape = Uri.parse("content://" + SongProvider.AUTHORITY + "/file/" + TEST_FOLDER + "/../../config.txt");
        try (ParcelFileDescriptor ignored = resolver.openFileDescriptor(escape, "r")) {
            fail("hwa 밖 파일이 열렸다");
        } catch (FileNotFoundException expected) {
        }
        Uri inside = BASE.buildUpon().appendPath("file").appendPath(TEST_FOLDER).appendPath("info.txt").build();
        try (ParcelFileDescriptor ignored = resolver.openFileDescriptor(inside, "w")) {
            fail("쓰기 모드가 허용됐다");
        } catch (FileNotFoundException expected) {
        }
    }

    @Test
    public void recordResets_roundTrip() {
        RecordResetQueue.clear(ctx);
        RecordResetQueue.add(ctx, "songA");
        RecordResetQueue.add(ctx, "songB");
        Set<String> got = new HashSet<>();
        try (Cursor c = resolver.query(BASE.buildUpon().appendPath("record_resets").build(), null, null, null, null)) {
            assertNotNull(c);
            while (c.moveToNext()) got.add(c.getString(c.getColumnIndexOrThrow("target")));
        }
        assertEquals(new HashSet<>(java.util.Arrays.asList("songA", "songB")), got);

        try (Cursor c = resolver.query(BASE.buildUpon().appendPath("record_resets_ack").build(), null, null, null, null)) {
            assertNotNull(c);
        }
        assertTrue(RecordResetQueue.pending(ctx).isEmpty());
    }

    @Test
    public void unknownPath_returnsNull() {
        assertEquals(null, resolver.query(BASE.buildUpon().appendPath("nope").build(), null, null, null, null));
    }
}
