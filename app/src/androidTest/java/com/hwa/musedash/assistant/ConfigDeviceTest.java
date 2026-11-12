package com.hwa.musedash.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Environment;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.hwa.musedash.assistant.config.ConfigRepository;
import com.hwa.musedash.assistant.util.Io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 실제 공용 config.txt로 저장 경로를 확인한다. 가장 중요한 건 저장 뒤에도 MediaStore 소유자가
 * 바뀌지 않는 것(Dead End #9: 바뀌면 게임이 설정을 못 읽는다). 끝나면 원래 내용을 같은 방식(덮어쓰기)으로 되돌린다.
 */
@RunWith(AndroidJUnit4.class)
public class ConfigDeviceTest {
    private Context ctx;
    private File config;
    private byte[] original;

    @Before
    public void setUp() throws Exception {
        assumeTrue("모든 파일 접근 권한 필요", Environment.isExternalStorageManager());
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        config = new File(Paths.PUBLIC_CONFIG);
        assumeTrue("공용 config.txt가 있어야 한다", config.isFile());
        original = Io.readAll(config);
    }

    @After
    public void restore() throws Exception {
        if (original == null) return;
        try (FileOutputStream out = new FileOutputStream(config, false)) {
            out.write(original);
        }
    }

    @Test
    public void toggleSave_keepsOwnerAndOtherLines() throws Exception {
        ConfigRepository repo = new ConfigRepository(ctx);
        ConfigRepository.State before = repo.state();
        boolean flipped = !before.values.get("blockFever");

        ConfigRepository.State after = repo.setOption("blockFever", flipped);
        assertTrue("저장 실패: " + after.saveError, after.saved);
        assertEquals(flipped, after.values.get("blockFever"));
        assertEquals("MediaStore 소유자가 바뀌면 게임이 못 읽는다", before.owner, after.owner);

        // 다시 읽어도 같은 값, 다른 토글은 그대로
        ConfigRepository.State reread = repo.state();
        assertEquals(flipped, reread.values.get("blockFever"));
        for (String id : new String[]{"autoPlay", "forcePerfect", "enableCinema", "showGhostNotes"}) {
            assertEquals(id, before.values.get(id), reread.values.get(id));
        }

        // 앱이 모르는 줄은 그대로 남는다
        String originalText = new String(original, java.nio.charset.StandardCharsets.UTF_8);
        String now = repo.readRaw();
        for (String line : originalText.split("\r?\n")) {
            if (line.contains("피버충전금지")) continue;
            assertTrue("사라진 줄: " + line, now.contains(line));
        }
    }
}
