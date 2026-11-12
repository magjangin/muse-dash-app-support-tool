package com.hwa.musedash.assistant.xapk;

import java.util.Locale;

/** XAPK 항목을 어디에 둘지 정하는 규칙. 순수 Java라 단위 테스트로 검증한다. */
public final class XapkRules {
    private XapkRules() {}

    public static boolean isValidPackageName(String pkg) {
        return pkg != null && pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }

    /**
     * OBB 항목의 저장 위치. 항목 경로가 "Android/obb/..."이면 그대로(.. 금지), 아니면
     * Android/obb/<패키지>/<파일 이름>.
     */
    public static String obbTarget(String storageRoot, String entryName, String targetPackage) {
        if (entryName.startsWith("Android/obb/") && !entryName.contains("..") && !entryName.contains("\\")) {
            return storageRoot + "/" + entryName;
        }
        String name = entryName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        return storageRoot + "/Android/obb/" + targetPackage + "/" + name;
    }

    /** 이 APK 항목을 설치용으로 뽑을지. 첫 APK를 잡고, 이후엔 base/musedash가 든 이름이 이긴다. */
    public static boolean takeApk(String entryName, boolean alreadyFound) {
        if (!entryName.toLowerCase(Locale.ROOT).endsWith(".apk")) return false;
        return !alreadyFound || entryName.contains("base") || entryName.contains("musedash");
    }

    public static boolean isObb(String entryName) {
        return entryName.toLowerCase(Locale.ROOT).endsWith(".obb");
    }

    /** 파일 이름으로 뮤즈대시 관련 패키지인지 (목록에서 강조용). */
    public static boolean looksLikeMuseDash(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.contains("musedash") || n.contains("muse-dash") || n.contains("muse dash") || n.contains("prpr");
    }
}
