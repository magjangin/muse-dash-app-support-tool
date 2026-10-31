#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import sys
import subprocess
import shutil
import zipfile

ROOT = os.path.abspath(os.path.dirname(__file__))
SDK_DIR = os.path.join(ROOT, "sdk")
BUILD_TOOLS = os.path.join(SDK_DIR, "build-tools", "android-14")
AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
D8_BAT = os.path.join(BUILD_TOOLS, "d8.bat")
ANDROID_JAR = os.path.join(SDK_DIR, "platforms", "android-35", "android.jar")
UBER_SIGNER = os.path.join(ROOT, "tools", "uber-apk-signer.jar")

SRC_DIR = os.path.join(ROOT, "src", "main")
RES_DIR = os.path.join(SRC_DIR, "res")
ASSETS_DIR = os.path.join(SRC_DIR, "assets")
JAVA_SRC_DIR = os.path.join(SRC_DIR, "java")
MANIFEST = os.path.join(SRC_DIR, "AndroidManifest.xml")

BUILD_DIR = os.path.join(ROOT, "build")
OBJ_DIR = os.path.join(BUILD_DIR, "obj")
GEN_DIR = os.path.join(BUILD_DIR, "gen")
CLASSES_DIR = os.path.join(OBJ_DIR, "classes")
BIN_DIR = os.path.join(BUILD_DIR, "bin")

def run(cmd, desc):
    print(f"[BUILD] {desc}...")
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=ROOT)
    if res.returncode != 0:
        print(f"FAILED: {desc}")
        print("STDOUT:", res.stdout)
        print("STDERR:", res.stderr)
        sys.exit(1)
    return res.stdout

def clean_and_prepare():
    os.makedirs(OBJ_DIR, exist_ok=True)
    os.makedirs(GEN_DIR, exist_ok=True)
    os.makedirs(CLASSES_DIR, exist_ok=True)
    os.makedirs(BIN_DIR, exist_ok=True)

def step1_compile_res():
    flat_zip = os.path.join(OBJ_DIR, "res.zip")
    cmd = f'"{AAPT2}" compile --dir "{RES_DIR}" -o "{flat_zip}"'
    run(cmd, "Compiling Android resources with AAPT2")
    return flat_zip

def step2_link_res(flat_zip):
    unaligned_apk = os.path.join(OBJ_DIR, "unaligned_res.apk")
    cmd = (
        f'"{AAPT2}" link "{flat_zip}" '
        f'-I "{ANDROID_JAR}" '
        f'--manifest "{MANIFEST}" '
        f'--java "{GEN_DIR}" '
        f'-A "{ASSETS_DIR}" '
        f'-o "{unaligned_apk}" '
        f'--auto-add-overlay'
    )
    run(cmd, "Linking resources, assets and AndroidManifest with AAPT2")
    return unaligned_apk

def step3_compile_java():
    java_files = []
    for base in [JAVA_SRC_DIR, GEN_DIR]:
        for root, _, files in os.walk(base):
            for f in files:
                if f.endswith(".java"):
                    rel_p = os.path.relpath(os.path.join(root, f), ROOT).replace("\\", "/")
                    java_files.append(rel_p)
    
    file_list_txt = os.path.join(OBJ_DIR, "sources.txt")
    with open(file_list_txt, "w", encoding="utf-8") as f:
        for jf in java_files:
            f.write(f"{jf}\n")

    cmd = (
        f'javac -source 8 -target 8 -g:none -encoding UTF-8 '
        f'-cp "sdk/platforms/android-35/android.jar" '
        f'-d "build/obj/classes" '
        f'@build/obj/sources.txt'
    )
    run(cmd, "Compiling Java sources with javac")

def step4_dex():
    class_files = []
    for root, _, files in os.walk(CLASSES_DIR):
        for f in files:
            if f.endswith(".class"):
                rel_p = os.path.relpath(os.path.join(root, f), ROOT).replace("\\", "/")
                class_files.append(rel_p)

    class_list_txt = os.path.join(OBJ_DIR, "classes.txt")
    with open(class_list_txt, "w", encoding="utf-8") as f:
        for cf in class_files:
            f.write(f"{cf}\n")

    cmd = (
        f'"{D8_BAT}" '
        f'--lib "sdk/platforms/android-35/android.jar" '
        f'--output "build/obj" '
        f'@build/obj/classes.txt'
    )
    run(cmd, "Generating classes.dex with D8")

def step5_merge_apk(unaligned_apk):
    merged_apk = os.path.join(OBJ_DIR, "unsigned.apk")
    shutil.copy2(unaligned_apk, merged_apk)

    dex_file = os.path.join(OBJ_DIR, "classes.dex")
    with zipfile.ZipFile(merged_apk, "a") as z:
        z.write(dex_file, "classes.dex")
    print(f"[BUILD] classes.dex added into {merged_apk}")
    return merged_apk

def step6_sign(unsigned_apk):
    cmd = (
        f'java -jar "{UBER_SIGNER}" '
        f'--apks "{unsigned_apk}" '
        f'--out "{BIN_DIR}" '
        f'--allowResign'
    )
    run(cmd, "Signing APK with uber-apk-signer (debug key)")
    
    # Rename output
    for f in os.listdir(BIN_DIR):
        if f.endswith(".apk") and "aligned" in f:
            final_path = os.path.join(BIN_DIR, "MuseDash_Mod_Assistant.apk")
            src_path = os.path.join(BIN_DIR, f)
            if os.path.exists(final_path):
                os.remove(final_path)
            os.rename(src_path, final_path)
            print(f"\n[SUCCESS] Final APK ready: {final_path}")
            return final_path
    return None

if __name__ == "__main__":
    clean_and_prepare()
    flat_zip = step1_compile_res()
    unaligned_apk = step2_link_res(flat_zip)
    step3_compile_java()
    step4_dex()
    unsigned_apk = step5_merge_apk(unaligned_apk)
    final_apk = step6_sign(unsigned_apk)
    if final_apk and os.path.exists(final_apk):
        size = os.path.getsize(final_apk)
        print(f"APK Size: {size:,} bytes ({size / 1024:.1f} KB)")
