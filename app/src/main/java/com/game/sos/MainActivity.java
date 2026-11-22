package com.game.sos;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {

    private static final String BLOGGER_GAME_URL = "https://gameedukatif17.blogspot.com/?m=1";

    private WebView webView;
    private FrameLayout nativeAdContainer;
    private AdManager adManager;
    private boolean isInGameSession = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        enableImmersiveFullscreen();

        webView = findViewById(R.id.game_webview);
        nativeAdContainer = findViewById(R.id.native_ad_container);

        adManager = new AdManager(this, webView, nativeAdContainer);

        setupWebView();
        setupBackNavigation();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            loadGameUrl();
        }
    }

    private void loadGameUrl() {
        if (isNetworkAvailable()) {
            webView.loadUrl(BLOGGER_GAME_URL);
        } else {
            showOfflineFallbackPage();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void showOfflineFallbackPage() {
        String offlineHtml = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'>"
                + "<style>body{margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;"
                + "background:#F0F9FF;font-family:sans-serif;text-align:center;padding:24px;color:#0F172A;}"
                + "img{max-width:180px;margin-bottom:16px;}h2{margin:0 0 8px;font-size:20px;}"
                + "p{margin:0 0 20px;color:#475569;font-size:14px;}"
                + "button{background:#0284C7;color:#fff;border:none;padding:12px 24px;border-radius:10px;font-size:15px;font-weight:bold;}</style>"
                + "</head><body>"
                + "<img src='file:///android_asset/no_internet.png' alt='Offline'/>"
                + "<h2>Koneksi Internet Terputus</h2>"
                + "<p>Pastikan perangkat terhubung ke internet untuk memainkan Game SOS.</p>"
                + "<button onclick='AndroidAdMob.retryConnection()'>Coba Lagi</button>"
                + "</body></html>";
        webView.loadDataWithBaseURL("file:///android_asset/", offlineHtml, "text/html", "UTF-8", null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.parseColor("#F0F9FF"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebAppAdMobInterface jsBridge = new WebAppAdMobInterface();
        webView.addJavascriptInterface(jsBridge, "AndroidAdMob");
        webView.addJavascriptInterface(jsBridge, "AndroidApp");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.contains("blogspot.com")
                        || host.contains("blogger.com")
                        || host.contains("script.google.com")
                        || host.contains("googleusercontent.com"))) {
                    return false;
                }
                // Buka tautan eksternal (misalnya Kebijakan Privasi) di browser eksternal
                if ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("https://")) {
                    adManager.onContentReady();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showOfflineFallbackPage();
                }
            }
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isInGameSession && webView != null) {
                    webView.evaluateJavascript("if(typeof confirmQuitLevel==='function'){confirmQuitLevel();}", null);
                } else if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    private void enableImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveFullscreen();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (adManager != null) adManager.onDestroy();
        if (webView != null) {
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }

    /**
     * Jembatan metode JavaScript yang dipanggil oleh skrip Blogspot.
     * Disederhanakan khusus untuk App Open, Native, dan Interstitial.
     */
    private class WebAppAdMobInterface {

        @JavascriptInterface
        public void retryConnection() {
            runOnUiThread(() -> loadGameUrl());
        }

        @JavascriptInterface
        public void configureAdPolicy(boolean childDirected, String maxAdContentRating) {
            String rating = (maxAdContentRating != null && !maxAdContentRating.isEmpty())
                    ? maxAdContentRating : "PG";
            adManager.configureAdPolicy(childDirected, rating);
        }

        @JavascriptInterface
        public void onGameContentReady() {
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void onContentLoaded() {
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void setGameplayState(boolean active) {
            isInGameSession = active;
            adManager.setGameplayActive(active);
        }

        @JavascriptInterface
        public void setNativeAdVisible(boolean visible) {
            adManager.setNativeAdVisible(visible);
        }

        @JavascriptInterface
        public void showInterstitial(String placement) {
            adManager.showInterstitialAd(placement != null ? placement : "level_transition");
        }

        @JavascriptInterface
        public void panggilIklanInterstisial() {
            adManager.showInterstitialAd("level_transition");
        }

        // Kompatibilitas mundur jika tema web masih memanggil fungsi banner lama
        @JavascriptInterface
        public int getBannerHeightPx() {
            return 0;
        }
    }
}
