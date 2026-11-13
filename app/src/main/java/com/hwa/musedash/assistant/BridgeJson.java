package com.hwa.musedash.assistant;

import com.hwa.musedash.assistant.config.ConfigRepository;
import com.hwa.musedash.assistant.records.RecordResetQueue;
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

    /** [{folder, title, artist, bmsCount, hasMusic, hasVideo, hasCover, pendingReset}] */
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
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
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
