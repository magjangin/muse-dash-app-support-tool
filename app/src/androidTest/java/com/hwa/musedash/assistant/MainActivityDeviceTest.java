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
            assertEquals("7", eval(sc, "document.querySelectorAll('.setting input').length"));
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

    /** hwa.moe 탭 검색·태그·대기열. 네트워크 없이 가짜 앨범 목록으로 그린다. */
    @Test
    public void storeSearch_tags_queue() throws Exception {
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            assertEquals("true", waitFor(sc, "document.readyState==='complete' && typeof AndroidBridge==='object'", "true"));
            eval(sc, "state.albums = ["
                    + "{id:'t1',title:'뮤즈대시 커스텀 5레벨 You’re Under Zz',download_filename:'zz under.zip',difficulty:5,tags:['팝'],created_at:'2026-01-03'},"
                    + "{id:'t2',title:'아기상어 테스트',download_filename:'shark zz.zip',difficulty:7,tags:['동요','팝'],created_at:'2026-01-02'},"
                    + "{id:'t3',title:'Zz Other',download_filename:'zz other.zip',difficulty_easy:3,difficulty_hard:9,difficulty:9,tags:['j-pop'],created_at:'2026-01-01'}];"
                    + "state.albumsLoadedAt = Date.now(); state.albumsError = null; true");
            eval(sc, "document.querySelector('[data-tab=tab-store]').click()");
            String items = "document.querySelectorAll('#albumList .item').length";
            assertEquals("3", waitFor(sc, items, "3"));
            // 머리말을 뗀 이름, 난이도별 레벨
            assertEquals("\"You’re Under Zz\"", eval(sc, "document.querySelector('[data-album=t1] .item-title').textContent"));
            assertEquals("\"Lv 3 · 9\"", eval(sc, "document.querySelector('[data-album=t3] .badge').textContent"));

            String search = "(q => { const i = document.getElementById('storeSearch'); i.value = q; i.dispatchEvent(new Event('input')); return document.querySelectorAll('#albumList .item').length; })";
            assertEquals("1", eval(sc, search + "(\"you're zz\")"));   // 곧은 따옴표로 둥근 따옴표 찾기, 여러 낱말
            assertEquals("1", eval(sc, search + "('ㅇㄱㅅㅇ')"));       // 초성
            assertEquals("1", eval(sc, search + "('shark')"));         // 파일 이름
            assertEquals("1", eval(sc, search + "('jpop')"));          // 태그, 기호 무시
            assertEquals("false", eval(sc, "document.getElementById('storeSearchClear').hidden"));
            eval(sc, "document.getElementById('storeSearchClear').click()");
            assertEquals("3", eval(sc, items));

            // 태그 칩: 많이 쓰인 태그가 먼저, 누르면 거르고 다시 누르면 푼다
            assertEquals("\"#팝 2\"", eval(sc, "document.querySelector('#storeTags .chip').textContent"));
            eval(sc, "document.querySelector('#storeTags .chip').click()");
            assertEquals("2", eval(sc, items));
            eval(sc, "document.querySelector('#storeTags .chip').click()");
            assertEquals("3", eval(sc, items));

            // 정렬: 레벨 높은순
            eval(sc, "(s => { s.value = 'levelDesc'; s.dispatchEvent(new Event('change')); })(document.getElementById('storeSort'))");
            assertEquals("\"t3\"", eval(sc, "document.querySelector('#albumList .item').dataset.album"));
            eval(sc, "(s => { s.value = 'new'; s.dispatchEvent(new Event('change')); })(document.getElementById('storeSort'))");

            // 모두 받기 줄이 보이고, 대기열에 넣었다 빼면 원래대로
            assertEquals("true", eval(sc, "document.getElementById('storeBulk').textContent.includes('3곡 모두 받기')"));
            eval(sc, "enqueue(state.albums[0]); renderStore(); true");
            assertEquals("\"대기 중 ✕\"", eval(sc, "document.querySelector('[data-album=t1] .item-actions button').textContent"));
            eval(sc, "document.querySelector('[data-album=t1] .item-actions button').click()");
            assertEquals("\"⬇ 받기\"", eval(sc, "document.querySelector('[data-album=t1] .item-actions button').textContent"));
            assertEquals("0", eval(sc, "state.downloadQueue.length"));

            eval(sc, "document.querySelector('[data-tab=tab-config]').click()");
        }
    }
}
