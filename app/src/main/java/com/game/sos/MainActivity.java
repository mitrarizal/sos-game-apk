package com.game.sos;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private AdView adView;
    private InterstitialAd interstitialAd;
    private ProgressBar progressBar;

    // ID Uji Coba Resmi dari Google AdMob
    private static final String TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921";
    private static final String TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);

        // 1. Inisialisasi SDK AdMob & Muat Iklan
        MobileAds.initialize(this, initializationStatus -> {
            loadAppOpenAd();      // Iklan tayang saat aplikasi pertama dibuka
            loadInterstitialAd(); // Preload iklan untuk transisi/pindah level
        });

        // 2. Banner Ad (Tampil di bagian bawah)
        adView = findViewById(R.id.adView);
        if (adView != null) {
            adView.loadAd(new AdRequest.Builder().build());
        }

        // 3. Konfigurasi WebView
        webView = findViewById(R.id.webview);

        // OPTIMASI VISUAL & AKSELERASI PERANGKAT KERAS
        webView.setBackgroundColor(Color.parseColor("#0f172a")); 
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);    

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        // OPTIMASI CACHE
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Menghubungkan JavaScript Blogspot ke Java Android
        webView.addJavascriptInterface(this, "AndroidApp");

        webView.setWebChromeClient(new WebChromeClient());
        
        // Mencegah layar gelap/blank & Mengubah Tampilan Offline Custom
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("gameedukatif17.blogspot.com")) {
                    return false;
                }
                return true; 
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                webView.setVisibility(View.VISIBLE);
            }

            // GANTI TAMPILAN ERROR DEFAULT ANDROID DENGAN TAMPILAN CUSTOM
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                
                if (request.isForMainFrame()) {
                    // Desain HTML Custom Offline (Tautan Blog Tersembunyi)
                    String customOfflineHtml = "<html><head>" +
                            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                            "<style>" +
                            "body { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); color: #ffffff; font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; padding: 20px; box-sizing: border-box; }" +
                            ".icon { font-size: 60px; margin-bottom: 15px; }" +
                            "h2 { margin: 0 0 10px 0; font-size: 22px; font-weight: 800; color: #f8fafc; }" +
                            "p { font-size: 13px; color: #94a3b8; margin-bottom: 25px; line-height: 1.5; max-width: 280px; }" +
                            ".btn { background-color: #2563eb; color: #ffffff; border: none; padding: 12px 28px; border-radius: 12px; font-weight: bold; font-size: 14px; cursor: pointer; box-shadow: 0 4px 14px rgba(37,99,235,0.4); transition: 0.2s; }" +
                            ".btn:active { transform: scale(0.95); background-color: #1d4ed8; }" +
                            "</style></head><body>" +
                            "<div class='icon'>📡</div>" +
                            "<h2>Koneksi Terputus</h2>" +
                            "<p>Aplikasi membutuhkan koneksi internet untuk memuat game. Silakan periksa jaringan Anda lalu coba lagi.</p>" +
                            "<button class='btn' onclick='window.location.href=\"https://gameedukatif17.blogspot.com/\"'>Coba Lagi</button>" +
                            "</body></html>";

                    // Muat halaman HTML custom
                    view.loadDataWithBaseURL("https://gameedukatif17.blogspot.com/", customOfflineHtml, "text/html", "UTF-8", null);
                }
            }
        });

        // Load URL Game Utama
        webView.loadUrl("https://gameedukatif17.blogspot.com/");
    }

    // --- 1. APP OPEN AD ---
    private void loadAppOpenAd() {
        AdRequest request = new AdRequest.Builder().build();
        AppOpenAd.load(
            this, TEST_APP_OPEN_ID, request,
            new AppOpenAd.AppOpenAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull AppOpenAd ad) {
                    ad.show(MainActivity.this);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    Log.e("AdMob", "App Open Ad gagal dimuat: " + loadAdError.getMessage());
                }
            }
        );
    }

    // --- 2. INTERSTITIAL AD (SIKLUS HIDUP AMAN) ---
    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, TEST_INTERSTITIAL_ID, adRequest,
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd ad) {
                    interstitialAd = ad;
                    
                    // Pasang Callback untuk menangani event saat iklan ditutup atau gagal tampil
                    interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            // Dikelola setelah iklan ditutup pengguna
                            interstitialAd = null;
                            loadInterstitialAd(); // Baru muat iklan berikutnya di sini
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                            interstitialAd = null;
                            loadInterstitialAd();
                        }
                    });
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    interstitialAd = null;
                }
            });
    }

    @JavascriptInterface
    public void panggilIklanInterstisial() {
        runOnUiThread(() -> {
            if (interstitialAd != null) {
                interstitialAd.show(MainActivity.this);
            } else {
                // Jika iklan belum siap, muat untuk kesempatan berikutnya tanpa mengganggu game
                loadInterstitialAd();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
