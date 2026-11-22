package com.game.sos;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.util.Date;

public class AdManager implements DefaultLifecycleObserver {

    // Ganti dengan Unit ID Produksi AdMob Anda sebelum rilis ke Google Play
    private static final String AD_UNIT_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";
    private static final String AD_UNIT_NATIVE = "ca-app-pub-3940256099942544/2247696110";
    private static final String AD_UNIT_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";

    // Jeda minimal (30 detik) antar iklan layar penuh agar mematuhi kebijakan pengalaman pengguna
    private static final long FULLSCREEN_AD_COOLDOWN_MS = 30_000L;

    private final Activity activity;
    private final WebView webView;
    private final FrameLayout nativeAdContainer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppOpenAd appOpenAd;
    private NativeAd currentNativeAd;
    private InterstitialAd interstitialAd;

    private boolean isInitialized = false;
    private boolean isContentReady = false;
    private boolean isGameplayActive = false;
    private boolean isNativeAllowedByWeb = true;
    private boolean isShowingFullScreenAd = false;
    private boolean isLoadingAppOpen = false;
    private boolean isLoadingInterstitial = false;
    private boolean hasShownInitialAppOpen = false;

    private long appOpenLoadTimeMs = 0L;
    private long lastFullScreenDismissedMs = 0L;

    public AdManager(@NonNull Activity activity,
                     @NonNull WebView webView,
                     @NonNull FrameLayout nativeAdContainer) {
        this.activity = activity;
        this.webView = webView;
        this.nativeAdContainer = nativeAdContainer;

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        // Konfigurasi standar khalayak umum (AD_ID aktif) dengan rating konten aman (PG)
        configureAdPolicy(false, RequestConfiguration.MAX_AD_CONTENT_RATING_PG);
    }

    /**
     * Mengatur kebijakan konten AdMob.
     * Gunakan childDirected = false agar izin AD_ID di Manifest bekerja penuh sesuai aturan Google Play.
     */
    public void configureAdPolicy(boolean childDirected, @NonNull String maxRating) {
        int childTag = childDirected
                ? RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                : RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED;

        int underAgeTag = childDirected
                ? RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                : RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED;

        RequestConfiguration config = new RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(childTag)
                .setTagForUnderAgeOfConsent(underAgeTag)
                .setMaxAdContentRating(maxRating)
                .build();

        MobileAds.setRequestConfiguration(config);

        if (!isInitialized) {
            isInitialized = true;
            MobileAds.initialize(activity.getApplicationContext(), initializationStatus -> {
                mainHandler.post(() -> {
                    loadAppOpenAd(true);
                    loadInterstitialAd();
                    if (isContentReady) {
                        loadNativeAdvancedAd();
                    }
                });
            });
        }
    }

    /**
     * Dipanggil saat halaman/game di Blogspot siap ditampilkan.
     */
    public void onContentReady() {
        mainHandler.post(() -> {
            if (isContentReady) {
                updateNativeContainerVisibility();
                return;
            }
            isContentReady = true;
            loadNativeAdvancedAd();
            if (interstitialAd == null) loadInterstitialAd();
            if (appOpenAd == null) loadAppOpenAd(false);
        });
    }

    public void setGameplayActive(boolean active) {
        mainHandler.post(() -> {
            this.isGameplayActive = active;
            updateNativeContainerVisibility();
        });
    }

    public void setNativeAdVisible(boolean visible) {
        mainHandler.post(() -> {
            this.isNativeAllowedByWeb = visible;
            updateNativeContainerVisibility();
        });
    }

    private void updateNativeContainerVisibility() {
        boolean shouldShow = isContentReady
                && !isGameplayActive
                && isNativeAllowedByWeb
                && !isShowingFullScreenAd
                && currentNativeAd != null;

        nativeAdContainer.setVisibility(shouldShow ? View.VISIBLE : View.GONE);

        if (shouldShow) {
            nativeAdContainer.post(() -> {
                float density = activity.getResources().getDisplayMetrics().density;
                int heightDp = Math.round(nativeAdContainer.getHeight() / density);
                evaluateJs("if(window.setNativeAdHeight){window.setNativeAdHeight(" + heightDp + ");}");
            });
        } else {
            evaluateJs("if(window.setNativeAdHeight){window.setNativeAdHeight(0);}");
        }
    }

    private AdRequest buildAdRequest() {
        return new AdRequest.Builder().build();
    }

    private boolean isCooldownActive() {
        return (System.currentTimeMillis() - lastFullScreenDismissedMs) < FULLSCREEN_AD_COOLDOWN_MS;
    }

    // =========================================================================
    // 1. APP OPEN AD (PEMBUKAAN APLIKASI: COLD START & WARM START)
    // =========================================================================
    public void loadAppOpenAd(boolean showOnInitialLoad) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (isLoadingAppOpen || isAppOpenAdAvailable()) return;

        isLoadingAppOpen = true;
        AppOpenAd.load(activity, AD_UNIT_APP_OPEN, buildAdRequest(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        appOpenAd = ad;
                        isLoadingAppOpen = false;
                        appOpenLoadTimeMs = new Date().getTime();

                        // Tampilkan saat pembukaan pertama (Cold Start) jika pemain belum masuk gameplay
                        if (showOnInitialLoad && !hasShownInitialAppOpen && !isGameplayActive && !isShowingFullScreenAd) {
                            hasShownInitialAppOpen = true;
                            showAppOpenAdIfAvailable();
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        isLoadingAppOpen = false;
                    }
                });
    }

    private boolean isAppOpenAdAvailable() {
        if (appOpenAd == null) return false;
        long fourHoursMs = 4L * 3600L * 1000L;
        return (new Date().getTime() - appOpenLoadTimeMs) < fourHoursMs;
    }

    public void showAppOpenAdIfAvailable() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (isGameplayActive || isShowingFullScreenAd || isCooldownActive()) return;

        if (!isAppOpenAdAvailable()) {
            loadAppOpenAd(false);
            return;
        }

        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true;
                updateNativeContainerVisibility();
                evaluateJs("if(window.onAppOpenAdOpened){window.onAppOpenAdOpened();}");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false;
                appOpenAd = null;
                lastFullScreenDismissedMs = System.currentTimeMillis();
                updateNativeContainerVisibility();
                evaluateJs("if(window.onAppOpenAdClosed){window.onAppOpenAdClosed();}");
                loadAppOpenAd(false);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isShowingFullScreenAd = false;
                appOpenAd = null;
                updateNativeContainerVisibility();
                evaluateJs("if(window.onAppOpenAdClosed){window.onAppOpenAdClosed();}");
                loadAppOpenAd(false);
            }
        });

        appOpenAd.show(activity);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // Warm Start: tampilkan saat pengguna kembali membuka aplikasi (bukan saat sedang bermain)
        if (isContentReady && !isGameplayActive && !isShowingFullScreenAd) {
            hasShownInitialAppOpen = true;
            showAppOpenAdIfAvailable();
        }
    }

    // =========================================================================
    // 2. NATIVE ADVANCED AD
    // =========================================================================
    public void loadNativeAdvancedAd() {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        AdLoader adLoader = new AdLoader.Builder(activity, AD_UNIT_NATIVE)
                .forNativeAd(nativeAd -> {
                    if (activity.isDestroyed() || activity.isFinishing()) {
                        nativeAd.destroy();
                        return;
                    }
                    if (currentNativeAd != null) {
                        currentNativeAd.destroy();
                    }
                    currentNativeAd = nativeAd;

                    NativeAdView adView = (NativeAdView) LayoutInflater.from(activity)
                            .inflate(R.layout.ad_native_layout, nativeAdContainer, false);
                    populateNativeAdView(nativeAd, adView);

                    nativeAdContainer.removeAllViews();
                    nativeAdContainer.addView(adView);
                    updateNativeContainerVisibility();
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        nativeAdContainer.setVisibility(View.GONE);
                        evaluateJs("if(window.setNativeAdHeight){window.setNativeAdHeight(0);}");
                    }
                })
                .withNativeAdOptions(new NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build())
                .build();

        adLoader.loadAd(buildAdRequest());
    }

    private void populateNativeAdView(@NonNull NativeAd nativeAd, @NonNull NativeAdView adView) {
        TextView headlineView = adView.findViewById(R.id.ad_headline);
        TextView bodyView = adView.findViewById(R.id.ad_body);
        TextView advertiserView = adView.findViewById(R.id.ad_advertiser);
        Button ctaView = adView.findViewById(R.id.ad_call_to_action);
        ImageView iconView = adView.findViewById(R.id.ad_app_icon);
        MediaView mediaView = adView.findViewById(R.id.ad_media);

        adView.setHeadlineView(headlineView);
        adView.setBodyView(bodyView);
        adView.setAdvertiserView(advertiserView);
        adView.setCallToActionView(ctaView);
        adView.setIconView(iconView);
        adView.setMediaView(mediaView);

        headlineView.setText(nativeAd.getHeadline());
        if (nativeAd.getMediaContent() != null) {
            mediaView.setMediaContent(nativeAd.getMediaContent());
            mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP);
        }

        if (nativeAd.getBody() != null) {
            bodyView.setVisibility(View.VISIBLE);
            bodyView.setText(nativeAd.getBody());
        } else {
            bodyView.setVisibility(View.GONE);
        }

        if (nativeAd.getCallToAction() != null) {
            ctaView.setVisibility(View.VISIBLE);
            ctaView.setText(nativeAd.getCallToAction());
        } else {
            ctaView.setVisibility(View.INVISIBLE);
        }

        if (nativeAd.getIcon() != null) {
            iconView.setVisibility(View.VISIBLE);
            iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
        } else {
            iconView.setVisibility(View.GONE);
        }

        if (nativeAd.getAdvertiser() != null) {
            advertiserView.setVisibility(View.VISIBLE);
            advertiserView.setText(nativeAd.getAdvertiser());
        } else {
            advertiserView.setVisibility(View.GONE);
        }

        adView.setNativeAd(nativeAd);
    }

    // =========================================================================
    // 3. INTERSTITIAL AD (TRANSISI ANTAR-LEVEL / SESI)
    // =========================================================================
    public void loadInterstitialAd() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (isLoadingInterstitial || interstitialAd != null) return;

        isLoadingInterstitial = true;
        InterstitialAd.load(activity, AD_UNIT_INTERSTITIAL, buildAdRequest(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        isLoadingInterstitial = false;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitialAd = null;
                        isLoadingInterstitial = false;
                    }
                });
    }

    public void showInterstitialAd(@NonNull String placement) {
        mainHandler.post(() -> {
            if (isShowingFullScreenAd || interstitialAd == null || isCooldownActive()) {
                evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
                if (interstitialAd == null) loadInterstitialAd();
                return;
            }

            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    isShowingFullScreenAd = true;
                    updateNativeContainerVisibility();
                    evaluateJs("if(window.onAdOpened){window.onAdOpened();}");
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    isShowingFullScreenAd = false;
                    interstitialAd = null;
                    lastFullScreenDismissedMs = System.currentTimeMillis();
                    updateNativeContainerVisibility();
                    evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
                    loadInterstitialAd();
                    // Segarkan Native Ad setelah sesi permainan selesai
                    loadNativeAdvancedAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isShowingFullScreenAd = false;
                    interstitialAd = null;
                    updateNativeContainerVisibility();
                    evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
                    loadInterstitialAd();
                }
            });

            interstitialAd.show(activity);
        });
    }

    // =========================================================================
    // LIFECYCLE & UTILITAS WEBVIEW
    // =========================================================================
    public void onDestroy() {
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
        if (currentNativeAd != null) {
            currentNativeAd.destroy();
            currentNativeAd = null;
        }
    }

    private void evaluateJs(@NonNull String script) {
        mainHandler.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed() && webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }
}
