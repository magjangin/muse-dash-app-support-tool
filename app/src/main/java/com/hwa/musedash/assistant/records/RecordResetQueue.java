package com.hwa.musedash.assistant.records;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 커스텀 곡 기록 초기화 예약 목록 (AGENTS.md #14).
 * 비서 앱은 게임의 files/record에 못 쓰므로, 여기에 예약만 해 두면 게임이 시작할 때
 * SongProvider의 record_resets로 읽어 자기 권한으로 지우고 record_resets_ack로 비운다.
 *
 * <p>SharedPreferences 이름·키는 v1.0.4와 같다 (업데이트해도 예약이 남도록).
 */
public final class RecordResetQueue {
    public static final String ALL = "ALL";
    static final String PREFS = "custom_record_resets";
    static final String KEY = "pending_targets";

    private RecordResetQueue() {}

    public static Set<String> pending(Context ctx) {
        Set<String> set = prefs(ctx).getStringSet(KEY, null);
        return set != null ? new HashSet<>(set) : new HashSet<>();
    }

    public static void add(Context ctx, String target) {
        Set<String> updated = merge(pending(ctx), target);
        prefs(ctx).edit().putStringSet(KEY, updated).apply();
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().remove(KEY).apply();
    }

    /** 예약 목록에 target을 더한 결과. ALL은 개별 예약을 모두 대신하고, ALL이 있으면 개별 예약은 무시한다. */
    public static Set<String> merge(Set<String> current, String target) {
        Set<String> updated = new HashSet<>(current);
        if (target == null || target.trim().isEmpty()) return updated;
        String t = target.trim();
        if (ALL.equals(t)) {
            updated.clear();
            updated.add(ALL);
        } else if (!updated.contains(ALL)) {
            updated.add(t);
        }
        return updated;
    }

    /** 이 곡 폴더의 기록 초기화가 예약돼 있는가 (전체 예약 포함). */
    public static boolean isPending(Set<String> pending, String folder) {
        return pending.contains(ALL) || pending.contains(folder);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
