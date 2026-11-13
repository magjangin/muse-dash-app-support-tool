package com.hwa.musedash.assistant.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class ModConfigTest {

    @Test
    public void parse_ignoresSpacesInKeys_andLastLineWins() {
        Map<String, Boolean> m = ModConfig.parse("오토 플레이 = true\n오토플레이=false\n");
        assertEquals(Boolean.FALSE, m.get("오토플레이"));
    }

    @Test
    public void parse_acceptsKoreanAndNumericWords() {
        Map<String, Boolean> m = ModConfig.parse("시네마=켜짐\n강제퍼펙트=0\n피버충전금지 = ON\n고스트노트보이기=끔");
        assertEquals(Boolean.TRUE, m.get("시네마"));
        assertEquals(Boolean.FALSE, m.get("강제퍼펙트"));
        assertEquals(Boolean.TRUE, m.get("피버충전금지"));
        assertEquals(Boolean.FALSE, m.get("고스트노트보이기"));
    }

    @Test
    public void parse_skipsCommentsAndUnknownValues() {
        Map<String, Boolean> m = ModConfig.parse("# 오토플레이=true\n// 시네마=false\n지연=120\n오토플레이=maybe\n");
        assertFalse(m.containsKey("오토플레이"));
        assertFalse(m.containsKey("시네마"));
        assertFalse(m.containsKey("지연"));
    }

    @Test
    public void parse_handlesBomAndCrlf() {
        Map<String, Boolean> m = ModConfig.parse("﻿오토플레이=true\r\n시네마=false\r\n");
        assertEquals(Boolean.TRUE, m.get("오토플레이"));
        assertEquals(Boolean.FALSE, m.get("시네마"));
    }

    @Test
    public void resolve_fillsDefaults() {
        Map<String, Boolean> v = ModConfig.resolve("오토플레이=true\n");
        assertTrue(v.get("autoPlay"));
        assertTrue(v.get("forcePerfect"));     // 기본 true
        assertFalse(v.get("blockFever"));      // 기본 false
        assertTrue(v.get("enableCinema"));
        assertTrue(v.get("showGhostNotes"));
    }

    @Test
    public void apply_replacesOnlyKnownKeys_andKeepsOthers() {
        String raw = "# 내 설정\n지연=120\n오토 플레이 = false\n오프셋=-30\n강제퍼펙트=true\n피버충전금지=false\n시네마=true\n고스트노트보이기=true\n";
        Map<String, Boolean> v = ModConfig.resolve(raw);
        v.put("autoPlay", true);
        String out = ModConfig.apply(raw, v);
        // v2.1.0 이전 파일에는 새 키 두 개가 없어서 끝에 붙는다 (기본값 = mod.js 기본값이라 게임 동작은 그대로)
        assertEquals("# 내 설정\n지연=120\n오토플레이=true\n오프셋=-30\n강제퍼펙트=true\n피버충전금지=false\n시네마=true\n고스트노트보이기=true\n"
                + "강제크리스마스=false\n스파인스킨=true\n", out);
    }

    @Test
    public void apply_appendsMissingKeysAtEnd() {
        String out = ModConfig.apply("지연=120\n", ModConfig.defaults());
        assertEquals("지연=120\n오토플레이=false\n강제퍼펙트=true\n피버충전금지=false\n시네마=true\n고스트노트보이기=true\n"
                + "강제크리스마스=false\n스파인스킨=true\n", out);
    }

    @Test
    public void resolve_newOptionsDefaultLikeModJs() {
        // mod.js src/config.ts 기본값: forceChristmas false, enableSpineSkin true
        Map<String, Boolean> v = ModConfig.resolve("오토플레이=false\n");
        assertFalse(v.get("forceChristmas"));
        assertTrue(v.get("enableSpineSkin"));
    }

    @Test
    public void resolve_readsAliasesCaseInsensitive_lastLineWins() {
        // mod.js와 같은 별칭: 크리스마스·forcechristmas, 커스텀스킨·SpineSkin, 올퍼펙트
        Map<String, Boolean> v = ModConfig.resolve("강제크리스마스=false\n크리스마스 = true\nSpineSkin=false\n올퍼펙트=false\n");
        assertTrue(v.get("forceChristmas"));
        assertFalse(v.get("enableSpineSkin"));
        assertFalse(v.get("forcePerfect"));
        v = ModConfig.resolve("커스텀스킨=false\n스파인스킨=true\n");
        assertTrue(v.get("enableSpineSkin"));
    }

    @Test
    public void apply_rewritesAliasLinesToCanonicalKey_withoutAppendingDuplicates() {
        String raw = "# 테스트\n크리스마스=false\n커스텀 스킨=true\n";
        Map<String, Boolean> v = ModConfig.resolve(raw);
        v.put("forceChristmas", true);
        v.put("enableSpineSkin", false);
        String out = ModConfig.apply(raw, v);
        assertTrue(out.startsWith("# 테스트\n강제크리스마스=true\n스파인스킨=false\n"));
        assertEquals(1, out.split("크리스마스", -1).length - 1);
        assertEquals(1, out.split("스파인스킨", -1).length - 1);
        assertEquals(v, ModConfig.resolve(out));
    }

    @Test
    public void defaultTemplate_containsAllOptions() {
        String out = ModConfig.defaultText(ModConfig.defaults());
        for (ModConfig.Option o : ModConfig.OPTIONS) {
            assertTrue(o.key, out.contains("\n" + o.key + "=" + o.defaultValue + "\n"));
        }
    }

    @Test
    public void apply_replacesEveryDuplicateLine() {
        String out = ModConfig.apply("시네마=true\n시네마=false\n", ModConfig.defaults());
        assertFalse(out.contains("시네마=false"));
        assertEquals(2, out.split("시네마=true", -1).length - 1);
    }

    @Test
    public void apply_emptyRawWritesDefaultTemplate() {
        Map<String, Boolean> v = ModConfig.defaults();
        v.put("blockFever", true);
        String out = ModConfig.apply("  \n", v);
        assertTrue(out.startsWith("# ===="));
        assertTrue(out.contains("피버충전금지=true\n"));
        assertEquals(v, ModConfig.resolve(out));
    }

    @Test
    public void apply_isStableWhenNothingChanges() {
        String raw = ModConfig.defaultText(ModConfig.defaults());
        assertEquals(raw, ModConfig.apply(raw, ModConfig.resolve(raw)));
    }

    @Test
    public void option_lookup() {
        assertEquals("오토플레이", ModConfig.option("autoPlay").key);
        assertNull(ModConfig.option("nope"));
    }
}
