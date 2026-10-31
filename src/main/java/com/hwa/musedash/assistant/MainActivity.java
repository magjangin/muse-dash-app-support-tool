package com.hwa.musedash.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    public WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebChromeClient(new ChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        SongProvider.grantToGame(this);
    }

    static class ChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage cm) {
            Log.d("WebViewConsole", cm.message() + " -- Line " + cm.lineNumber());
            return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 게임을 강제 종료하거나 기기를 재부팅하면 URI 권한이 사라지므로, 비서 앱이 앞에 올 때마다 다시 준다.
        SongProvider.grantToGame(this);
        if (webView != null) {
            webView.evaluateJavascript("if(window.onAppResume) window.onAppResume();", null);
        }
    }
}
