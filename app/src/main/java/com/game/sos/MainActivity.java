package com.game.sos;

import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private AdView adView;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;

    // ID Uji Coba Resmi dari Google AdMob
    private static final String TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921";
    private static final String TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Inisialisasi SDK AdMob & Muat Iklan
        MobileAds.initialize(this, initializationStatus -> {
            loadAppOpenAd();      // Iklan tayang saat aplikasi pertama dibuka
            loadInterstitialAd(); // Preload iklan untuk transisi/pindah level
            loadRewardedAd();     // Preload iklan untuk reward
        });

        // 2. Banner Ad (Tampil di bagian bawah)
        adView = findViewById(R.id.adView);
        adView.loadAd(new AdRequest.Builder().build());

        // 3. Konfigurasi WebView
        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        // Menghubungkan JavaScript Blogspot ke Java Android
        webView.addJavascriptInterface(this, "AndroidApp");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://gameedukatif17.blogspot.com/");
    }

    // --- 1. APP OPEN AD (Saat Pertama Kali Membuka Aplikasi) ---
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

    // --- 2. INTERSTITIAL AD (Saat Pindah / Pilih Level) ---
    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, TEST_INTERSTITIAL_ID, adRequest,
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd ad) {
                    interstitialAd = ad;
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
                loadInterstitialAd(); // Memuat ulang untuk transisi level berikutnya
            } else {
                loadInterstitialAd();
            }
        });
    }

    // --- 3. REWARDED AD (Saat Klaim Hadiah / Buka Level Terkunci) ---
    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, TEST_REWARDED_ID, adRequest,
            new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    rewardedAd = null;
                }

                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    rewardedAd = ad;
                }
            });
    }

    @JavascriptInterface
    public void panggilIklanReward() {
        runOnUiThread(() -> {
            if (rewardedAd != null) {
                rewardedAd.show(MainActivity.this, rewardItem -> {
                    webView.loadUrl("javascript:onRewardSuccess()");
                    loadRewardedAd(); // Memuat ulang iklan reward
                });
            } else {
                loadRewardedAd();
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
