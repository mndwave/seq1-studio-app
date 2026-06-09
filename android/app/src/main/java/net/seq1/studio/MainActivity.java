package net.seq1.studio;

import android.graphics.Bitmap;
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
        // STUDIO-NO-ACTIONBAR-GAP-2026-05-25 (Kyle screenshot — black gap above header):
        // Force the no-action-bar theme BEFORE super.onCreate runs so the activity
        // window never allocates ActionBar layout space. Belt + braces alongside
        // postSplashScreenTheme in styles.xml.
        setTheme(R.style.AppTheme_NoActionBar);

        // STUDIO-LEGACY-WINDOW-FLAGS-REMOVED-2026-06-09 (healer:e9b3d618):
        // The 2026-05-25 setStatusBarColor/setNavigationBarColor/setSystemUiVisibility
        // block conflicted with Capacitor 8 edge-to-edge after biometric AuthActivity
        // resume — env(safe-area-inset-top) collapsed to 0 in the WebView. System bar
        // appearance is owned by the web layer (html:has(.studio-root) background) and
        // the Capacitor StatusBar config (style: Dark, overlaysWebView: true). Do NOT
        // reintroduce legacy window flags here — see
        // memory/studio-biometric-root-layout-antipattern.md.

        // Register Nostr/Amber bridge plugin before super.onCreate
        registerPlugin(NostrSignerPlugin.class);
        super.onCreate(savedInstanceState);

        // STUDIO-NO-DOUBLE-HEADER-2026-05-25 (Kyle screenshot — double SEQ1 Studio bar):
        // The AppCompat theme inherits an ActionBar somewhere in the post-splash
        // transition and renders the activity label as a title bar above the web
        // header. Defensive: explicitly hide any support ActionBar.
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setTitle("");

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
