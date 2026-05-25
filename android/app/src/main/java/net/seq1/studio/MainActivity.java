package net.seq1.studio;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
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
        // STUDIO-LIGHT-MODE-2026-05-25 (Kyle voice dictation):
        // System bars match the cream Studio surface so the header logo never
        // disappears under a dark status bar. SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        // forces dark battery/clock glyphs so they remain readable on cream.
        getWindow().setStatusBarColor(Color.parseColor("#F4F1E8"));
        getWindow().setNavigationBarColor(Color.parseColor("#F4F1E8"));
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);

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
