package com.hwa.musedash.assistant.config;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.hwa.musedash.assistant.Paths;
import com.hwa.musedash.assistant.util.Io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 공용 config.txt 읽기/쓰기.
 *
 * <p>게임(targetSdk 36)은 공용 config.txt를 MediaStore 소유자(owner_package_name)가 자기 자신일 때만
 * 읽을 수 있다(AGENTS.md Dead End #9). 그래서 이 파일은 절대 지우거나 새로 만들지 않고, 열어서
 * 덮어쓰기(truncate)만 한다. 다시 만들면 소유자가 비서 앱으로 바뀌어 게임이 다시 못 읽는다.
 *
 * <p>저장할 때마다 파일을 새로 읽어서 바꾼 키만 반영한다. PC 등에서 고친 내용을 옛 사본으로 덮지 않기 위해서다.
 */
public final class ConfigRepository {
    private final Context context;

    public ConfigRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 읽을 파일. 공용 파일이 없으면 게임 전용 폴더의 사본(대개 Android 11+에선 못 읽음). */
    private File sourceFile() {
        File pub = new File(Paths.PUBLIC_CONFIG);
        if (pub.exists()) return pub;
        File fallback = new File(Paths.GAME_CONFIG);
        return fallback.exists() ? fallback : pub;
    }

    public String readRaw() throws IOException {
        File f = sourceFile();
        if (!f.exists()) return "";
        return new String(Io.readAll(f), StandardCharsets.UTF_8);
    }

    /** 현재 상태. {values:{id:bool}, exists, owner, gameReadable, readError?} */
    public State state() {
        State st = new State();
        try {
            st.values = ModConfig.resolve(readRaw());
        } catch (IOException e) {
            st.values = ModConfig.defaults();
            st.readError = String.valueOf(e.getMessage());
        }
        st.exists = new File(Paths.PUBLIC_CONFIG).exists();
        st.owner = queryOwnerPackage(Paths.PUBLIC_CONFIG);
        return st;
    }

    public State setOption(String id, boolean value) {
        if (ModConfig.option(id) == null) throw new IllegalArgumentException("unknown option: " + id);
        return update(values -> values.put(id, value));
    }

    public State resetDefaults() {
        return update(values -> values.putAll(ModConfig.defaults()));
    }

    private interface Change {
        void apply(Map<String, Boolean> values);
    }

    private State update(Change change) {
        String raw;
        try {
            raw = readRaw();
        } catch (IOException e) {
            raw = "";
        }
        Map<String, Boolean> values = ModConfig.resolve(raw);
        change.apply(values);
        String text = ModConfig.apply(raw, values);

        State st = new State();
        try {
            writePublic(text);
            st.saved = true;
        } catch (IOException e) {
            st.saveError = String.valueOf(e.getMessage());
        }
        st.mirrored = writeGameCopy(text);
        st.values = st.saved ? values : ModConfig.resolve(raw);
        st.exists = new File(Paths.PUBLIC_CONFIG).exists();
        st.owner = queryOwnerPackage(Paths.PUBLIC_CONFIG);
        return st;
    }

    private void writePublic(String text) throws IOException {
        File dir = new File(Paths.PUBLIC_DIR);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("폴더를 만들 수 없습니다: " + dir);
        // FileOutputStream(append=false)는 기존 파일을 지우지 않고 길이만 0으로 만든다 → MediaStore 소유자 유지.
        try (FileOutputStream out = new FileOutputStream(new File(Paths.PUBLIC_CONFIG), false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Android 10 이하에서만 성공한다(남의 Android/data 쓰기, Dead End #5). 실패는 정상. */
    private boolean writeGameCopy(String text) {
        File dir = new File(Paths.GAME_CONFIG).getParentFile();
        if (dir == null || !dir.exists()) return false;
        try (FileOutputStream out = new FileOutputStream(new File(Paths.GAME_CONFIG), false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String queryOwnerPackage(String path) {
        Uri uri = MediaStore.Files.getContentUri("external");
        try (Cursor c = context.getContentResolver().query(uri, new String[]{"owner_package_name"}, "_data=?", new String[]{path}, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static final class State {
        public Map<String, Boolean> values;
        public boolean exists;
        public String owner;
        public boolean saved;
        public boolean mirrored;
        public String saveError;
        public String readError;

        /** 게임이 이 설정을 읽을 수 있는가: 공용 파일 소유자가 게임이거나, 게임 폴더 사본에 썼거나. */
        public boolean gameReadable() {
            return Paths.GAME_PACKAGE.equals(owner) || mirrored;
        }
    }
}
