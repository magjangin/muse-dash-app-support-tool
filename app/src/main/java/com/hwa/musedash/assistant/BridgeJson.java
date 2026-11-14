package com.hwa.musedash.assistant;

import com.hwa.musedash.assistant.config.ConfigRepository;
import com.hwa.musedash.assistant.records.RecordResetQueue;
import com.hwa.musedash.assistant.songs.HwaManifest;
import com.hwa.musedash.assistant.songs.SongInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** AndroidBridge가 WebView(assets/js)에 돌려주는 JSON 모양. 키 이름을 바꾸면 js 쪽도 같이 고친다. */
final class BridgeJson {
    private BridgeJson() {}

    /** {version, storage, gameInstalled, gameVersion, canInstallPackages} */
    static String appState(String version, boolean storage, String gameVersion, boolean canInstallPackages) {
        JSONObject o = new JSONObject();
        try {
            o.put("version", version);
            o.put("storage", storage);
            o.put("gameInstalled", gameVersion != null);
            o.put("gameVersion", gameVersion == null ? JSONObject.NULL : gameVersion);
            o.put("canInstallPackages", canInstallPackages);
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    /** {values:{id:bool}, exists, owner, gameReadable, saved, mirrored, error?, readError?} */
    static String config(ConfigRepository.State st) {
        JSONObject o = new JSONObject();
        try {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, Boolean> e : st.values.entrySet()) values.put(e.getKey(), e.getValue());
            o.put("values", values);
            o.put("exists", st.exists);
            o.put("owner", st.owner == null ? JSONObject.NULL : st.owner);
            o.put("gameReadable", st.gameReadable());
            o.put("saved", st.saved);
            o.put("mirrored", st.mirrored);
            if (st.saveError != null) o.put("error", st.saveError);
            if (st.readError != null) o.put("readError", st.readError);
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    /**
     * [{folder, title, artist, bmsCount, hasMusic, hasVideo, hasCover, pendingReset,
     *   levels: [난이도1..5 레벨 또는 null], difficultyCharts: 난이도별 채보 지정 수, christmas: bool,
     *   overrides: {autoPlay, blockFever, cinema, showGhostNotes, fixDt} 중 info.txt에 값이 있는 것만,
     *   gameSkip: 게임이 건너뛰는 이유 또는 null, warnings: [문자열]}]
     */
    static String songs(List<SongInfo> songs, Set<String> pendingResets) {
        JSONArray arr = new JSONArray();
        for (SongInfo s : songs) {
            try {
                JSONObject o = new JSONObject();
                o.put("folder", s.folder);
                o.put("title", s.title);
                o.put("artist", s.artist);
                o.put("bmsCount", s.bmsCount);
                o.put("hasMusic", s.hasMusic);
                o.put("hasVideo", s.hasVideo);
                o.put("hasCover", s.cover != null);
                o.put("pendingReset", RecordResetQueue.isPending(pendingResets, s.folder));
                JSONArray levels = new JSONArray();
                int charts = 0;
                for (int d = 1; d <= HwaManifest.MAX_DIFFICULTY; d++) {
                    Integer lv = s.level(d);
                    levels.put(lv == null ? JSONObject.NULL : lv);
                    if (s.difficultyCharts[d] != null) charts++;
                }
                o.put("levels", levels);
                o.put("difficultyCharts", charts);
                o.put("christmas", Boolean.TRUE.equals(s.manifest.christmasScene));
                JSONObject overrides = new JSONObject();
                putIfSet(overrides, "autoPlay", s.manifest.autoPlay);
                putIfSet(overrides, "blockFever", s.manifest.blockFever);
                putIfSet(overrides, "cinema", s.manifest.cinema);
                putIfSet(overrides, "showGhostNotes", s.manifest.showGhostNotes);
                putIfSet(overrides, "fixDt", s.manifest.fixDtToRecommended);
                o.put("overrides", overrides);
                o.put("gameSkip", s.gameSkipReason == null ? JSONObject.NULL : s.gameSkipReason);
                o.put("warnings", new JSONArray(s.warnings));
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }

    private static void putIfSet(JSONObject o, String key, Boolean value) throws org.json.JSONException {
        if (value != null) o.put(key, value.booleanValue());
    }

    static String strings(Collection<String> values) {
        JSONArray arr = new JSONArray();
        for (String s : values) arr.put(s);
        return arr.toString();
    }

    static String error(String msg) {
        JSONObject o = new JSONObject();
        try {
            o.put("error", msg);
        } catch (Exception ignored) {
        }
        return o.toString();
    }
}
