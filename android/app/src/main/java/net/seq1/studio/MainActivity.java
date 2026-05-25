package net.seq1.studio;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {

    private static final String OFFLINE_URL = "file:///android_asset/public/offline.html";

    private boolean showingOffline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set bar colours before super.onCreate so they apply from the first frame.
        // styles.xml alone is overridden by Theme.SplashScreen; Window API is authoritative.
        getWindow().setStatusBarColor(Color.parseColor("#0c0a09"));
        getWindow().setNavigationBarColor(Color.parseColor("#0c0a09"));

        // Register Nostr/Amber bridge plugin before super.onCreate
        registerPlugin(NostrSignerPlugin.class);
        super.onCreate(savedInstanceState);

        // Replace the WebViewClient with one that intercepts main-frame load
        // failures (e.g. no internet) and shows our styled offline page
        // instead of the default white-with-green-robot Android error screen.
        WebView webView = this.bridge.getWebView();
        webView.setWebViewClient(new BridgeWebViewClient(this.bridge) {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!url.startsWith("file:///android_asset/public/offline.html")) {
                    showingOffline = false;
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame() && !showingOffline) {
                    showingOffline = true;
                    view.stopLoading();
                    view.loadUrl(OFFLINE_URL);
                    return;
                }
                super.onReceivedError(view, request, error);
            }
        });
    }
}
