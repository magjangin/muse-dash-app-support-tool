package com.hwa.musedash.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hwa.musedash.assistant.records.RecordResetQueue;
import com.hwa.musedash.assistant.xapk.XapkRules;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RulesTest {
    private static final String ROOT = "/storage/emulated/0";

    // ---- XAPK

    @Test
    public void obbTarget_keepsAndroidObbPath() {
        assertEquals(ROOT + "/Android/obb/com.prpr.musedash.mod/main.1.com.prpr.musedash.mod.obb",
                XapkRules.obbTarget(ROOT, "Android/obb/com.prpr.musedash.mod/main.1.com.prpr.musedash.mod.obb", "x.y"));
    }

    @Test
    public void obbTarget_otherPathsGoToPackageFolder() {
        assertEquals(ROOT + "/Android/obb/com.a.b/main.obb", XapkRules.obbTarget(ROOT, "obb/main.obb", "com.a.b"));
        assertEquals(ROOT + "/Android/obb/com.a.b/main.obb", XapkRules.obbTarget(ROOT, "main.obb", "com.a.b"));
        // ..이 든 Android/obb 경로는 믿지 않고 파일 이름만 쓴다
        assertEquals(ROOT + "/Android/obb/com.a.b/evil.obb", XapkRules.obbTarget(ROOT, "Android/obb/../../evil.obb", "com.a.b"));
    }

    @Test
    public void packageName_validation() {
        assertTrue(XapkRules.isValidPackageName("com.prpr.musedash.mod"));
        assertFalse(XapkRules.isValidPackageName("musedash"));
        assertFalse(XapkRules.isValidPackageName("com/evil"));
        assertFalse(XapkRules.isValidPackageName("../com.a"));
        assertFalse(XapkRules.isValidPackageName(""));
        assertFalse(XapkRules.isValidPackageName(null));
    }

    @Test
    public void takeApk_firstThenBase() {
        assertTrue(XapkRules.takeApk("config.arm64.apk", false));
        assertFalse(XapkRules.takeApk("config.xxhdpi.apk", true));
        assertTrue(XapkRules.takeApk("base.apk", true));
        assertTrue(XapkRules.takeApk("com.prpr.musedash.mod.apk", true));
        assertFalse(XapkRules.takeApk("icon.png", false));
    }

    @Test
    public void looksLikeMuseDash() {
        assertTrue(XapkRules.looksLikeMuseDash("MuseDash_v6.8.0.xapk"));
        assertTrue(XapkRules.looksLikeMuseDash("com.prpr.musedash.mod.apk"));
        assertTrue(XapkRules.looksLikeMuseDash("Muse Dash.xapk"));
        assertFalse(XapkRules.looksLikeMuseDash("other.apk"));
    }

    // ---- 기록 초기화 예약

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    @Test
    public void resetQueue_allReplacesIndividual() {
        assertEquals(set("ALL"), RecordResetQueue.merge(set("a", "b"), "ALL"));
    }

    @Test
    public void resetQueue_individualIgnoredWhenAllPending() {
        assertEquals(set("ALL"), RecordResetQueue.merge(set("ALL"), "a"));
    }

    @Test
    public void resetQueue_addsTrimmedAndIgnoresBlank() {
        assertEquals(set("a", "b"), RecordResetQueue.merge(set("a"), "  b "));
        assertEquals(set("a"), RecordResetQueue.merge(set("a"), "  "));
        assertEquals(set("a"), RecordResetQueue.merge(set("a"), null));
    }

    @Test
    public void resetQueue_isPending() {
        assertTrue(RecordResetQueue.isPending(set("ALL"), "x"));
        assertTrue(RecordResetQueue.isPending(set("x"), "x"));
        assertFalse(RecordResetQueue.isPending(set("y"), "x"));
    }
}
