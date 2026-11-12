package com.hwa.musedash.assistant.songs;

import com.hwa.musedash.assistant.Paths;
import com.hwa.musedash.assistant.util.Io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 공용 /MuseDash/hwa 폴더의 커스텀 곡. 이 폴더가 곡 목록의 기준이다.
 * 게임 폴더(files/hwa)의 사본은 게임이 다음 시작 때 SongProvider 목록과 맞춘다(.from_assistant 표시가 있는 폴더만).
 */
public final class SongLibrary {
    private final File baseDir;

    public SongLibrary() {
        this(new File(Paths.HWA_DIR));
    }

    public SongLibrary(File baseDir) {
        this.baseDir = baseDir;
    }

    public File baseDir() {
        return baseDir;
    }

    public List<SongInfo> scan() {
        List<SongInfo> out = new ArrayList<>();
        File[] dirs = baseDir.listFiles();
        if (dirs == null) return out;
        for (File dir : dirs) {
            // 점으로 시작하는 폴더는 받는 중인 임시 폴더다.
            if (!dir.isDirectory() || dir.getName().startsWith(".")) continue;
            SongInfo s = read(dir);
            if (s.isValid()) out.add(s);
        }
        Collections.sort(out, (a, b) -> a.title.compareToIgnoreCase(b.title));
        return out;
    }

    static SongInfo read(File dir) {
        SongInfo s = new SongInfo();
        s.folder = dir.getName();
        s.title = dir.getName();
        s.path = dir.getAbsolutePath();
        File[] files = dir.listFiles();
        if (files == null) return s;
        for (File f : files) {
            if (!f.isFile()) continue;
            s.countFile(f.getName());
            if (SongInfo.isInfoFile(f.getName())) {
                try {
                    s.applyInfoText(new String(Io.readAll(f), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                }
            }
        }
        return s;
    }

    /**
     * 곡 폴더를 지운다. 먼저 폴더 이름이 정확히 같은 것(대소문자 무시)을 찾고,
     * 없을 때만 info.txt 제목이 같은 폴더를 지운다. 지운 폴더 수를 돌려준다.
     */
    public int delete(String folderOrTitle) {
        List<File> hidden = hide(folderOrTitle);
        purge(hidden);
        return hidden.size();
    }

    /**
     * 지울 곡 폴더를 ".del_" 이름으로 바꿔 목록·게임(SongProvider)에서 바로 빼고, 바뀐 폴더를 돌려준다.
     * 곡 하나가 파일 수천 개·100MB를 넘어 실제 삭제는 몇 초 걸리므로 purge()로 따로 지운다.
     * 이름을 못 바꾸면 그 자리에서 지운다.
     */
    public List<File> hide(String folderOrTitle) {
        List<File> out = new ArrayList<>();
        for (File d : find(folderOrTitle)) {
            File trash = new File(baseDir, TRASH_PREFIX + System.nanoTime() + "_" + d.getName());
            if (d.renameTo(trash)) {
                out.add(trash);
            } else {
                Io.deleteRecursive(d);
                if (!d.exists()) out.add(d);
            }
        }
        return out;
    }

    public static void purge(List<File> dirs) {
        for (File d : dirs) Io.deleteRecursive(d);
    }

    /**
     * 앞선 실행이 남긴 받기 임시 폴더(.dl_)와 지우다 만 폴더(.del_)를 지운다.
     * 앱 시작 때만 부른다 (받는 중인 임시 폴더를 지우지 않도록).
     */
    public int cleanupLeftovers() {
        File[] dirs = baseDir.listFiles();
        if (dirs == null) return 0;
        int n = 0;
        for (File d : dirs) {
            String name = d.getName();
            if (d.isDirectory() && (name.startsWith(TRASH_PREFIX) || name.startsWith(SongDownloader.TEMP_PREFIX))) {
                Io.deleteRecursive(d);
                n++;
            }
        }
        return n;
    }

    static final String TRASH_PREFIX = ".del_";

    private List<File> find(String folderOrTitle) {
        List<File> none = new ArrayList<>();
        if (folderOrTitle == null) return none;
        String target = folderOrTitle.trim();
        if (target.isEmpty()) return none;
        File[] dirs = baseDir.listFiles();
        if (dirs == null) return none;

        List<File> matches = new ArrayList<>();
        for (File d : dirs) {
            if (d.isDirectory() && !d.getName().startsWith(".") && d.getName().trim().equalsIgnoreCase(target)) matches.add(d);
        }
        if (matches.isEmpty()) {
            for (File d : dirs) {
                if (!d.isDirectory() || d.getName().startsWith(".")) continue;
                SongInfo s = read(d);
                if (s.title != null && s.title.trim().equalsIgnoreCase(target)) matches.add(d);
            }
        }
        return matches;
    }

    /** 곡의 자켓 파일 (없거나 폴더 밖이면 null). */
    public File coverOf(String folder) {
        try {
            File dir = SafePaths.resolveInside(baseDir, folder);
            if (!dir.isDirectory()) return null;
            SongInfo s = read(dir);
            return s.cover == null ? null : new File(dir, s.cover);
        } catch (IOException e) {
            return null;
        }
    }

    public static String mimeOf(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") ? "image/png" : "image/jpeg";
    }
}
