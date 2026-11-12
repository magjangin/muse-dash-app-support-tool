package com.hwa.musedash.assistant;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hwa.musedash.assistant.songs.SongLibrary;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public class MainActivity extends Activity {
    /** 곡 자켓을 WebView에 보여 주는 가짜 주소. 실제 네트워크로 나가지 않고 shouldInterceptRequest에서 파일로 답한다. */
    static final String LOCAL_HOST = "assistant.local";

    WebView webView;
    private AndroidBridge bridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // 화면은 assets 안에서만 읽는다. 기기 파일은 브리지와 assistant.local 로만 넘긴다.
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d("WebViewConsole", cm.message() + " -- " + cm.sourceId() + ":" + cm.lineNumber());
                return true;
            }
        });
        webView.setWebViewClient(new LocalContentClient(new SongLibrary()));
        bridge = new AndroidBridge(this);
        webView.addJavascriptInterface(bridge, "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        SongProvider.grantToGame(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 게임을 강제 종료하거나 기기를 재부팅하면 URI 권한이 사라지므로, 비서 앱이 앞에 올 때마다 다시 준다.
        SongProvider.grantToGame(this);
        callJs("onAppResume");
    }

    @Override
    public void onBackPressed() {
        // 열린 창(확인 모달)이 있으면 닫고, 없으면 기본 동작.
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript("window.onBackPressed ? window.onBackPressed() : false", handled -> {
            if (!"true".equals(handled)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) bridge.shutdown();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    /** window[fn](args...)를 UI 스레드에서 부른다. 인자는 JSON으로 인코딩해 따옴표·줄바꿈에 안전하다. */
    void callJs(String fn, Object... args) {
        StringBuilder sb = new StringBuilder("if(window.").append(fn).append(")window.").append(fn).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(',');
            Object a = args[i];
            if (a == null) sb.append("null");
            else if (a instanceof Number || a instanceof Boolean) sb.append(a);
            else sb.append(JSONObject.quote(String.valueOf(a)));
        }
        sb.append(");");
        String js = sb.toString();
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    /** https://assistant.local/cover/<곡 폴더> → 곡 자켓 이미지. */
    static final class LocalContentClient extends WebViewClient {
        private final SongLibrary library;

        LocalContentClient(SongLibrary library) {
            this.library = library;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!LOCAL_HOST.equals(url.getHost())) return null;
            List<String> seg = url.getPathSegments();
            if (seg.size() == 2 && "cover".equals(seg.get(0))) {
                File cover = library.coverOf(seg.get(1));
                if (cover != null && cover.isFile()) {
                    try {
                        return new WebResourceResponse(SongLibrary.mimeOf(cover), null, new FileInputStream(cover));
                    } catch (Exception ignored) {
                    }
                }
            }
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // 앱 화면 밖으로 나가는 링크는 WebView에서 열지 않는다.
            return !"file".equals(request.getUrl().getScheme());
        }
    }
}
