package com.hwa.musedash.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 앱 화면(WebView)이 뜨고 브리지가 붙는지, 탭 전환과 목록 그리기가 오류 없이 되는지 본다. */
@RunWith(AndroidJUnit4.class)
public class MainActivityDeviceTest {

    private static String eval(ActivityScenario<MainActivity> sc, String js) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> out = new AtomicReference<>();
        sc.onActivity(a -> a.webView.evaluateJavascript(js, v -> {
            out.set(v);
            latch.countDown();
        }));
        assertTrue("JS 응답 없음: " + js, latch.await(10, TimeUnit.SECONDS));
        return out.get();
    }

    private static String waitFor(ActivityScenario<MainActivity> sc, String js, String expected) throws InterruptedException {
        String v = null;
        long end = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < end) {
            v = eval(sc, js);
            if (expected.equals(v)) return v;
            SystemClock.sleep(200);
        }
        return v;
    }

    @Test
    public void screenLoads_bridgeWorks_tabsRender() throws Exception {
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            assertEquals("true", waitFor(sc, "document.readyState==='complete' && typeof AndroidBridge==='object'", "true"));
            assertEquals("5", eval(sc, "document.querySelectorAll('.setting input').length"));
            assertEquals("4", eval(sc, "document.querySelectorAll('.tabbtn').length"));
            // 설정 상태 문구가 그려졌다 (연동 여부와 상관없이)
            assertEquals("true", eval(sc, "document.getElementById('cfgLinkStatus').textContent.length > 0"));
            // 버전 표시
            assertEquals("true", eval(sc, "document.getElementById('appVersion').textContent.startsWith('v')"));

            // 내 곡 탭: 목록 또는 빈 안내가 그려진다
            eval(sc, "document.querySelector('[data-tab=tab-songs]').click()");
            assertEquals("true", waitFor(sc, "document.getElementById('tab-songs').classList.contains('active') && document.getElementById('songList').children.length > 0", "true"));
            assertEquals("true", eval(sc, "document.getElementById('gameStatus').textContent !== '확인 중…'"));

            // XAPK 탭
            eval(sc, "document.querySelector('[data-tab=tab-xapk]').click()");
            assertEquals("true", waitFor(sc, "document.getElementById('packageList').children.length > 0", "true"));

            // 확인 창은 뒤로 가기로 닫힌다
            eval(sc, "confirmBox('t','m')");
            assertEquals("false", eval(sc, "document.getElementById('modal').hidden"));
            assertEquals("true", eval(sc, "window.onBackPressed()"));
            assertEquals("true", eval(sc, "document.getElementById('modal').hidden"));

            // 처음 탭으로 돌려 둔다 (다음 실행의 시작 탭)
            eval(sc, "document.querySelector('[data-tab=tab-config]').click()");
        }
    }
}
