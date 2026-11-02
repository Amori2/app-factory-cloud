package com.studio.appfactory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.provider.MediaStore;
import org.json.JSONArray;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private FrameLayout rootLayout;
    private WebView webView;
    private WebView browserWebView;
    private TextView nativeFabBtn;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_CODE = 1001;
    private static final int PERMISSION_REQ_CODE = 1002;
    private static final String MOBILE_CHROME_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String DESKTOP_4K_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private PowerManager.WakeLock downloadWakeLock;
    private WifiManager.WifiLock downloadWifiLock;
    private final Map<String, NativeHlsJob> nativeHlsJobs = new HashMap<String, NativeHlsJob>();
    private final Map<String, File> activeSaveTempFiles = new HashMap<String, File>();

    public static class NativeHlsJob {
        public String jobId;
        public String[] segmentUrls;
        public String initMapUrl;
        public String fileName;
        public String subFolder;
        public String refererUrl;
        public int totalSegments;
        public volatile int currentSegmentIndex = 0;
        public volatile long downloadedBytes = 0;
        public volatile boolean initDownloaded = false;
        public volatile boolean isFmp4 = false;
        public volatile boolean running = false;
        public volatile boolean paused = false;
        public volatile boolean canceled = false;
        public File tempFile;
    }
    private final Map<String, FileOutputStream> activeSaveStreams = new HashMap<String, FileOutputStream>();
    private volatile boolean nativeAdBlockEnabled = true;
    private volatile String currentBrowserUrl = "";
    private volatile String currentBrowserTitle = "";
    private boolean browserDesiredVisible = false;
    private int lastDetectedCount = 0;

    private static final String[] AD_DOMAINS = new String[]{
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "adnxs.com",
        "adsrvr.org", "popads.net", "popcash.net", "propellerads.com", "adsterra.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "plugrush.com", "hilltopads.net",
        "clickadu.com", "admaven.com", "revcontent.com", "taboola.com", "outbrain.com",
        "mgid.com", "criteo.com", "criteo.net", "rubiconproject.com", "pubmatic.com",
        "openx.net", "casalemedia.com", "smartadserver.com", "advertising.com", "adform.net",
        "bidswitch.net", "moatads.com", "scorecardresearch.com", "quantserve.com", "hotjar.com",
        "mouseflow.com", "clarity.ms", "zedo.com", "adroll.com", "teads.tv", "outbrainimg.com",
        "trc.taboola.com", "tsyndicate.com", "realsrv.com", "exdynsrv.com", "aucdn.net",
        "onclkds.com", "onclickalgo.com", "adsco.re", "popunder.net", "adcash.com", "bidvertiser.com"
    };

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private boolean isNativeAdOrTrackerUrl(String rawUrl) {
        if (!nativeAdBlockEnabled || rawUrl == null) return false;
        String s = rawUrl.trim().toLowerCase();
        if (s.startsWith("intent://") || s.startsWith("market://")) return true;
        try {
            Uri uri = Uri.parse(s);
            String host = uri.getHost();
            if (host != null) {
                host = host.toLowerCase();
                for (String d : AD_DOMAINS) {
                    if (host.equals(d) || host.endsWith("." + d)) return true;
                }
            }
        } catch (Exception ignored) {}
        return s.contains("/popunder") || s.contains("/adserver/") || s.contains("/popads/");
    }

    private boolean isSniffableMediaUrl(String rawUrl) {
        if (rawUrl == null) return false;
        String s = rawUrl.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) return false;
        String lower = s.toLowerCase();
        if (lower.contains("preview") || lower.contains("thumb") || lower.contains("sprite") ||
            lower.contains("storyboard") || lower.contains("hover") || lower.contains("bumper") ||
            lower.contains("preroll") || lower.contains("/ads/") || lower.contains("favicon")) {
            return false;
        }
        int qIdx = lower.indexOf('?');
        String pathOnly = qIdx >= 0 ? lower.substring(0, qIdx) : lower;
        int hIdx = pathOnly.indexOf('#');
        if (hIdx >= 0) pathOnly = pathOnly.substring(0, hIdx);
        // Ignore individual HLS/DASH segment chunks (.ts, .m4s) and non-media assets
        if (pathOnly.endsWith(".ts") || pathOnly.endsWith(".m4s") || pathOnly.endsWith(".aac") ||
            pathOnly.endsWith(".vtt") || pathOnly.endsWith(".srt") || pathOnly.endsWith(".jpg") ||
            pathOnly.endsWith(".jpeg") || pathOnly.endsWith(".png") || pathOnly.endsWith(".gif") ||
            pathOnly.endsWith(".webp") || pathOnly.endsWith(".svg") || pathOnly.endsWith(".js") ||
            pathOnly.endsWith(".css") || pathOnly.endsWith(".woff") || pathOnly.endsWith(".woff2")) {
            return false;
        }
        if (pathOnly.endsWith(".m3u8") || pathOnly.endsWith(".mpd") || pathOnly.endsWith(".mp4") ||
            pathOnly.endsWith(".webm") || pathOnly.endsWith(".mkv") || pathOnly.endsWith(".mp3") || pathOnly.endsWith(".m4a")) {
            return true;
        }
        if (lower.contains(".m3u8?") || lower.contains(".mpd?") || lower.contains(".mp4?") ||
            lower.contains(".webm?") || lower.contains(".mkv?") || lower.contains(".mp3?") || lower.contains(".m4a?") ||
            lower.contains("application/x-mpegurl") || lower.contains("application/vnd.apple.mpegurl") ||
            lower.contains("mime=video/mp4") || lower.contains("mime=video%2fmp4")) {
            return true;
        }
        return false;
    }

    private void notifyUiAdBlocked() {
        if (webView == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    webView.evaluateJavascript("if(window.incrementBlockedAdsCount)window.incrementBlockedAdsCount(1);", null);
                } catch (Exception ignored) {}
            }
        });
    }

    public void notifyUiMediaDetected(final String mediaUrl, final String pageTitle, final String qualityHint) {
        if (webView == null || mediaUrl == null) return;
        if (isNativeAdOrTrackerUrl(mediaUrl)) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String safeTitle = (pageTitle != null && !pageTitle.trim().isEmpty()) ? pageTitle.trim() : currentBrowserTitle;
                    String js = "if(window.onNativeMediaSniffed)window.onNativeMediaSniffed(" +
                        JSONObject.quote(mediaUrl) + "," +
                        JSONObject.quote(safeTitle != null ? safeTitle : "") + "," +
                        JSONObject.quote(qualityHint != null ? qualityHint : "") + "," +
                        JSONObject.quote(currentBrowserUrl != null ? currentBrowserUrl : "") + ");";
                    webView.evaluateJavascript(js, null);
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifyUiBrowserState(final String url, final String title) {
        if (webView == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean canBack = browserWebView != null && browserWebView.canGoBack();
                    String js = "if(window.onNativeBrowserStateChanged)window.onNativeBrowserStateChanged(" +
                        JSONObject.quote(url != null ? url : "") + "," +
                        JSONObject.quote(title != null ? title : "") + "," +
                        (canBack ? "true" : "false") + ");";
                    webView.evaluateJavascript(js, null);
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifyUiNewInternalTab(final String targetUrl) {
        if (webView == null || targetUrl == null) return;
        if (isNativeAdOrTrackerUrl(targetUrl)) {
            notifyUiAdBlocked();
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (browserWebView != null && browserWebView.getVisibility() == View.VISIBLE) {
                        browserWebView.loadUrl(targetUrl);
                    }
                    String js = "if(window.onNativeNewTabRequested)window.onNativeNewTabRequested(" + JSONObject.quote(targetUrl) + ");";
                    webView.evaluateJavascript(js, null);
                } catch (Exception ignored) {}
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureCommonWebSettings(WebSettings ws) {
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setUserAgentString(MOBILE_CHROME_UA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }

    private void injectDomMediaScannerIntoBrowser() {
        if (browserWebView == null) return;
        String js = "(function(){try{" +
            "var pt='';" +
            "var ms=document.getElementsByTagName('meta');for(var m=0;m<ms.length;m++){var p=ms[m].getAttribute('property')||ms[m].getAttribute('name')||'';if(p==='og:title'||p==='twitter:title'){pt=(ms[m].getAttribute('content')||'').trim();if(pt)break;}}" +
            "if(!pt&&document.title)pt=document.title.trim();" +
            "if(window.AndroidBridge&&window.AndroidBridge.reportPageTitle&&pt)window.AndroidBridge.reportPageTitle(pt,location.href);" +
            "var els=document.querySelectorAll('video,audio,video source,audio source,source');" +
            "for(var i=0;i<els.length;i++){" +
            "var e=els[i];var u=e.currentSrc||e.src||e.getAttribute('src')||e.getAttribute('data-src')||'';" +
            "if(u&&window.AndroidBridge&&window.AndroidBridge.reportDetectedMediaFromJs){" +
            "var q=e.getAttribute('label')||e.getAttribute('res')||e.getAttribute('size')||'';" +
            "window.AndroidBridge.reportDetectedMediaFromJs(u,pt,q);" +
            "}}" +
            "if(!window._snapLpBound){window._snapLpBound=true;" +
            "document.addEventListener('contextmenu',function(ev){try{" +
            "var t=ev.target;if(!t||!t.closest)return;" +
            "var vid=t.closest('video,audio');" +
            "var lnk=t.closest('a[href]');" +
            "var targetUrl='';var labelTxt='';" +
            "if(vid){targetUrl=vid.currentSrc||vid.src||vid.getAttribute('src')||location.href;labelTxt=document.title||'Video';}" +
            "else if(lnk){var h=lnk.getAttribute('href')||'';if(h&&h.charAt(0)!=='#'&&h.indexOf('javascript:')!==0){targetUrl=new URL(h,location.href).href;labelTxt=(lnk.textContent||lnk.title||document.title||'').trim();}}" +
            "if(targetUrl&&window.AndroidBridge&&window.AndroidBridge.onLongPressPageElement){ev.preventDefault();window.AndroidBridge.onLongPressPageElement(targetUrl,labelTxt);}" +
            "}catch(ex){}},true);}" +
            "}catch(err){}})();";
        browserWebView.evaluateJavascript(js, null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        
        String initBgHex = "#090D16";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try { getWindow().setStatusBarColor(Color.parseColor(initBgHex)); } catch (Exception ignored) { getWindow().setStatusBarColor(Color.parseColor("#090D16")); }
        }
        try { getWindow().getDecorView().setBackgroundColor(Color.parseColor(initBgHex)); } catch (Exception ignored) {}

        requestNeededPermissions();

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Primary App UI WebView (overrides onWindowVisibilityChanged so background downloads NEVER pause when switching apps!)
        webView = new WebView(this) {
            @Override
            protected void onWindowVisibilityChanged(int visibility) {
                super.onWindowVisibilityChanged(View.VISIBLE);
            }
        };
        try { webView.setBackgroundColor(Color.parseColor(initBgHex)); } catch (Exception ignored) { webView.setBackgroundColor(Color.parseColor("#090D16")); }
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Real Top-Level Browser WebView for Websites (Zero iframe / Zero ERR_BLOCKED_BY_RESPONSE!)
        browserWebView = new WebView(this);
        browserWebView.setBackgroundColor(Color.WHITE);
        browserWebView.setFocusable(true);
        browserWebView.setFocusableInTouchMode(true);
        FrameLayout.LayoutParams browserParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        browserParams.topMargin = dpToPx(92);
        browserParams.bottomMargin = dpToPx(56);
        browserWebView.setLayoutParams(browserParams);
        browserWebView.setVisibility(View.GONE);
        browserWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (browserWebView == null) return false;
                WebView.HitTestResult hr = browserWebView.getHitTestResult();
                if (hr == null) return false;
                int type = hr.getType();
                String extra = hr.getExtra();
                if (extra != null && (extra.startsWith("http://") || extra.startsWith("https://"))) {
                    if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                        type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                        type == WebView.HitTestResult.IMAGE_TYPE) {
                        showLongPressLinkDialog(extra, currentBrowserTitle);
                        return true;
                    }
                }
                return false;
            }
        });
        rootLayout.addView(browserWebView);

        // 3. Native Floating Download Button on top of Browser WebView when media is detected
        nativeFabBtn = new TextView(this);
        nativeFabBtn.setText("⬇ 1");
        nativeFabBtn.setTextColor(Color.parseColor("#111827"));
        nativeFabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        nativeFabBtn.setGravity(Gravity.CENTER);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(Color.parseColor("#D99B26"));
        fabBg.setStroke(dpToPx(2), Color.WHITE);
        nativeFabBtn.setBackground(fabBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            nativeFabBtn.setElevation(dpToPx(8));
        }
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(dpToPx(48), dpToPx(48));
        fabParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        fabParams.bottomMargin = dpToPx(68);
        fabParams.leftMargin = dpToPx(14);
        nativeFabBtn.setLayoutParams(fabParams);
        nativeFabBtn.setVisibility(View.GONE);
        nativeFabBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                browserWebView.setVisibility(View.GONE);
                nativeFabBtn.setVisibility(View.GONE);
                if (webView != null) {
                    webView.evaluateJavascript("if(window.openDetectedMediaSheet)window.openDetectedMediaSheet();", null);
                }
            }
        });
        rootLayout.addView(nativeFabBtn);

        setContentView(rootLayout);

        configureCommonWebSettings(webView.getSettings());
        configureCommonWebSettings(browserWebView.getSettings());

        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cm.setAcceptThirdPartyCookies(webView, true);
                cm.setAcceptThirdPartyCookies(browserWebView, true);
            }
        } catch (Exception ignored) {}

        WebAppBridge bridge = new WebAppBridge(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.addJavascriptInterface(bridge, "AndroidBridge");
        webView.addJavascriptInterface(bridge, "Android");

        browserWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        browserWebView.addJavascriptInterface(bridge, "AndroidBridge");

        // Setup Primary UI WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                    String reqUrl = request.getUrl().toString();
                    if (isNativeAdOrTrackerUrl(reqUrl)) {
                        notifyUiAdBlocked();
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                    }
                    if (isSniffableMediaUrl(reqUrl)) {
                        notifyUiMediaDetected(reqUrl, currentBrowserTitle, "");
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return handleUiUri(request.getUrl().toString());
                }
                return false;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUiUri(url);
            }
            private boolean handleUiUri(String url) {
                if (url == null) return false;
                if (isNativeAdOrTrackerUrl(url)) {
                    notifyUiAdBlocked();
                    return true;
                }
                if (url.endsWith(".apk") || url.contains("/releases/download/")) {
                    startNativeDownload(url, "app-release.apk", "application/vnd.android.package-archive");
                    return true;
                }
                if (url.startsWith("file://") || url.startsWith("about:") ||
                    url.startsWith("javascript:") || url.startsWith("data:") ||
                    url.startsWith("blob:") || url.startsWith("http://") ||
                    url.startsWith("https://")) {
                    return false;
                }
                // Block non-http external schemes (like intent:// ad redirects) from auto-opening system browser!
                return true;
            }
        });

        // Setup Native Browser WebViewClient (1DM-style Network Sniffer + Ad Blocker + Zero External Redirects)
        browserWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                    String reqUrl = request.getUrl().toString();
                    if (isNativeAdOrTrackerUrl(reqUrl)) {
                        notifyUiAdBlocked();
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                    }
                    if (isSniffableMediaUrl(reqUrl)) {
                        notifyUiMediaDetected(reqUrl, currentBrowserTitle, "");
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                if (url != null && isSniffableMediaUrl(url)) {
                    notifyUiMediaDetected(url, currentBrowserTitle, "");
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                    return handleBrowserUri(view, request.getUrl().toString());
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleBrowserUri(view, url);
            }

            private boolean handleBrowserUri(WebView view, String url) {
                if (url == null) return true;
                if (isNativeAdOrTrackerUrl(url)) {
                    notifyUiAdBlocked();
                    return true;
                }
                if (url.startsWith("intent://") || url.startsWith("market://")) {
                    notifyUiAdBlocked();
                    return true;
                }
                if (isSniffableMediaUrl(url)) {
                    notifyUiMediaDetected(url, currentBrowserTitle, "");
                    if (webView != null) {
                        browserWebView.setVisibility(View.GONE);
                        nativeFabBtn.setVisibility(View.GONE);
                        webView.evaluateJavascript("if(window.openDetectedMediaSheet)window.openDetectedMediaSheet();", null);
                    }
                    return true;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    currentBrowserUrl = url;
                    Map<String, String> extraHeaders = new HashMap<String, String>();
                    extraHeaders.put("X-Requested-With", "");
                    view.loadUrl(url, extraHeaders);
                    notifyUiBrowserState(url, currentBrowserTitle);
                    return true;
                }
                // Block any non-http scheme from escaping to the system browser
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    if (!url.equals(currentBrowserUrl)) {
                        currentBrowserUrl = url;
                        currentBrowserTitle = "";
                        if (webView != null) {
                            final String startUrl = url;
                            webView.post(new Runnable() {
                                @Override
                                public void run() {
                                    webView.evaluateJavascript("if(window.onNativeBrowserPageStarted)window.onNativeBrowserPageStarted(" + JSONObject.quote(startUrl) + ");", null);
                                }
                            });
                        }
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    currentBrowserUrl = url;
                }
                String t = view.getTitle();
                if (t != null && !t.trim().isEmpty() && !t.startsWith("http")) {
                    currentBrowserTitle = t.trim();
                }
                notifyUiBrowserState(currentBrowserUrl, currentBrowserTitle);
                injectDomMediaScannerIntoBrowser();
            }
        });

        WebChromeClient sharedChromeClient = new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (view == browserWebView && title != null && !title.trim().isEmpty()) {
                    currentBrowserTitle = title.trim();
                    notifyUiBrowserState(currentBrowserUrl, currentBrowserTitle);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                if (resultMsg == null) return false;
                final WebView tempWebView = new WebView(MainActivity.this);
                tempWebView.getSettings().setJavaScriptEnabled(true);
                tempWebView.getSettings().setUserAgentString(MOBILE_CHROME_UA);
                tempWebView.setWebViewClient(new WebViewClient() {
                    private boolean handled = false;
                    private void interceptPopupUrl(String url) {
                        if (handled || url == null) return;
                        handled = true;
                        if (isNativeAdOrTrackerUrl(url) || url.startsWith("intent://") || url.startsWith("market://")) {
                            notifyUiAdBlocked();
                        } else if (url.startsWith("http://") || url.startsWith("https://")) {
                            notifyUiNewInternalTab(url);
                        }
                        try { tempWebView.destroy(); } catch (Exception ignored) {}
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && req != null && req.getUrl() != null) {
                            interceptPopupUrl(req.getUrl().toString());
                        }
                        return true;
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        interceptPopupUrl(url);
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(tempWebView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                if (isFinishing()) { result.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("حسناً", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { result.confirm(); }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override public void onCancel(DialogInterface d) { result.cancel(); }
                    }).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                if (isFinishing()) { result.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("نعم", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { result.confirm(); }
                    })
                    .setNegativeButton("إلغاء", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { result.cancel(); }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override public void onCancel(DialogInterface d) { result.cancel(); }
                    }).show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
                if (isFinishing()) { result.cancel(); return true; }
                final EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue != null ? defaultValue : "");
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("موافق", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { result.confirm(input.getText().toString()); }
                    })
                    .setNegativeButton("إلغاء", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { result.cancel(); }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override public void onCancel(DialogInterface d) { result.cancel(); }
                    }).show();
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) { return true; }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    String mimeType = "*/*";
                    if (fileChooserParams != null && fileChooserParams.getAcceptTypes() != null) {
                        String[] types = fileChooserParams.getAcceptTypes();
                        boolean wantsImagesOnly = false;
                        boolean wantsPdfOnly = false;
                        for (String t : types) {
                            if (t == null) continue;
                            String clean = t.trim().toLowerCase();
                            if (clean.startsWith("image/") || clean.contains(".png") || clean.contains(".jpg") || clean.contains(".jpeg")) {
                                wantsImagesOnly = true;
                            } else if (clean.equals("application/pdf") || clean.equals(".pdf")) {
                                wantsPdfOnly = true;
                            }
                        }
                        if (wantsImagesOnly && !wantsPdfOnly) {
                            mimeType = "image/*";
                        } else if (wantsPdfOnly && !wantsImagesOnly) {
                            mimeType = "application/pdf";
                        }
                    }
                    i.setType(mimeType);
                    startActivityForResult(Intent.createChooser(i, "اختر الملفات أو الصور (يدعم التحديد المتعدد)"), FILE_CHOOSER_CODE);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        };

        webView.setWebChromeClient(sharedChromeClient);
        browserWebView.setWebChromeClient(sharedChromeClient);

        DownloadListener sharedDownloadListener = new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (isSniffableMediaUrl(url)) {
                    notifyUiMediaDetected(url, currentBrowserTitle, "");
                    if (webView != null) {
                        browserWebView.setVisibility(View.GONE);
                        nativeFabBtn.setVisibility(View.GONE);
                        webView.evaluateJavascript("if(window.openDetectedMediaSheet)window.openDetectedMediaSheet();", null);
                    }
                    return;
                }
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                startNativeDownload(url, fileName, mimetype);
            }
        };

        webView.setDownloadListener(sharedDownloadListener);
        browserWebView.setDownloadListener(sharedDownloadListener);

        webView.loadUrl("file:///android_asset/index.html");
    }

    public void startNativeDownload(String url, String fileName, String mimeType) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) req.addRequestHeader("cookie", cookies);
                req.addRequestHeader("User-Agent", MOBILE_CHROME_UA);
                req.setDescription("Downloading file...");
                req.setTitle(fileName);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(req);
                    Toast.makeText(getApplicationContext(), "جاري تنزيل الملف في التنزيلات: " + fileName, Toast.LENGTH_LONG).show();
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        String[] perms = new String[]{  };
        ArrayList<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), PERMISSION_REQ_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_CODE && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                ClipData clipData = data.getClipData();
                if (clipData != null && clipData.getItemCount() > 0) {
                    int count = clipData.getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = clipData.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{ data.getData() };
                } else if (data.getDataString() != null) {
                    results = new Uri[]{ Uri.parse(data.getDataString()) };
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            try { webView.resumeTimers(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (webView != null) {
            try { webView.resumeTimers(); } catch (Exception ignored) {}
        }
    }

    public void showLongPressLinkDialog(final String targetUrl, final String labelTitle) {
        if (targetUrl == null || isFinishing()) return;
        if (isNativeAdOrTrackerUrl(targetUrl)) {
            notifyUiAdBlocked();
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                final String[] options = new String[]{
                    "فتح في تبويب بالخلفية (Open in background tab)",
                    "فتح في تبويب جديد الآن (Open in new tab)",
                    "فحص وتحميل الفيديو من الرابط",
                    "نسخ الرابط (Copy link)"
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(labelTitle != null && !labelTitle.isEmpty() ? labelTitle : targetUrl);
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            if (webView != null) {
                                String js = "if(window.openUrlInBackgroundTab)window.openUrlInBackgroundTab(" +
                                    JSONObject.quote(targetUrl) + "," +
                                    JSONObject.quote(labelTitle != null ? labelTitle : "") + ");";
                                webView.evaluateJavascript(js, null);
                            }
                            Toast.makeText(MainActivity.this, "تم الفتح في تبويب بالخلفية", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            notifyUiNewInternalTab(targetUrl);
                        } else if (which == 2) {
                            if (isSniffableMediaUrl(targetUrl)) {
                                notifyUiMediaDetected(targetUrl, labelTitle, "");
                            } else if (webView != null) {
                                String js = "if(window.openUrlInBackgroundTab)window.openUrlInBackgroundTab(" +
                                    JSONObject.quote(targetUrl) + "," +
                                    JSONObject.quote(labelTitle != null ? labelTitle : "") + ",true);";
                                webView.evaluateJavascript(js, null);
                            }
                            browserWebView.setVisibility(View.GONE);
                            nativeFabBtn.setVisibility(View.GONE);
                            if (webView != null) {
                                webView.evaluateJavascript("if(window.openDetectedMediaSheet)window.openDetectedMediaSheet();", null);
                            }
                        } else if (which == 3) {
                            try {
                                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                if (cb != null) {
                                    cb.setPrimaryClip(ClipData.newPlainText("url", targetUrl));
                                    Toast.makeText(MainActivity.this, "تم نسخ الرابط", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                });
                builder.setNegativeButton("إلغاء", null);
                builder.show();
            }
        });
    }

    @SuppressLint("WakelockTimeout")
    public void setDownloadWakeLock(boolean active) {
        try {
            if (active) {
                Intent svc = new Intent(this, DownloadKeepAliveService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try { startForegroundService(svc); } catch (Exception e) { startService(svc); }
                } else {
                    startService(svc);
                }
                if (downloadWakeLock == null) {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        downloadWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Snap1DM:BgDownloadLock");
                        downloadWakeLock.setReferenceCounted(false);
                    }
                }
                if (downloadWakeLock != null && !downloadWakeLock.isHeld()) {
                    downloadWakeLock.acquire(4 * 60 * 60 * 1000L);
                }
                if (downloadWifiLock == null) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        downloadWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Snap1DM:BgWifiLock");
                        downloadWifiLock.setReferenceCounted(false);
                    }
                }
                if (downloadWifiLock != null && !downloadWifiLock.isHeld()) {
                    downloadWifiLock.acquire();
                }
            } else {
                boolean anyRunning = false;
                synchronized (nativeHlsJobs) {
                    for (NativeHlsJob j : nativeHlsJobs.values()) {
                        if (j.running) { anyRunning = true; break; }
                    }
                }
                if (!anyRunning) {
                    try { stopService(new Intent(this, DownloadKeepAliveService.class)); } catch (Exception ignored) {}
                    if (downloadWakeLock != null && downloadWakeLock.isHeld()) {
                        downloadWakeLock.release();
                    }
                    if (downloadWifiLock != null && downloadWifiLock.isHeld()) {
                        downloadWifiLock.release();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void emitNativeHlsProgressToUi(final String jobId, final int doneSegs, final int totalSegs, final long bytes, final long speedBps, final String status, final String savedResult) {
        if (webView == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String js = "if(window.onNativeHlsJobProgress)window.onNativeHlsJobProgress(" +
                        JSONObject.quote(jobId) + "," +
                        doneSegs + "," +
                        totalSegs + "," +
                        bytes + "," +
                        speedBps + "," +
                        JSONObject.quote(status != null ? status : "") + "," +
                        JSONObject.quote(savedResult != null ? savedResult : "") + ");";
                    webView.evaluateJavascript(js, null);
                } catch (Exception ignored) {}
            }
        });
    }

    private byte[] downloadSegmentBytesWithRetry(String segUrl, String refererUrl) throws Exception {
        Exception lastErr = null;
        for (int attempt = 0; attempt <= 3; attempt++) {
            HttpURLConnection conn = null;
            InputStream is = null;
            try {
                URL u = new URL(segUrl);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(25000);
                conn.setReadTimeout(35000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", MOBILE_CHROME_UA);
                if (refererUrl != null && !refererUrl.isEmpty()) {
                    conn.setRequestProperty("Referer", refererUrl);
                }
                String cookies = CookieManager.getInstance().getCookie(segUrl);
                if (cookies != null && !cookies.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookies);
                }
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new Exception("HTTP " + code);
                }
                is = conn.getInputStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[32768];
                int r;
                while ((r = is.read(buf)) != -1) {
                    baos.write(buf, 0, r);
                }
                byte[] data = baos.toByteArray();
                if (data.length == 0) throw new Exception("Empty segment");
                return data;
            } catch (Exception e) {
                lastErr = e;
                try { Thread.sleep(1200L * (attempt + 1)); } catch (Exception ignored) {}
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }
        throw (lastErr != null ? lastErr : new Exception("Segment failed"));
    }

    public void startOrResumeNativeHlsWorker(final NativeHlsJob job) {
        if (job == null || job.running) return;
        job.running = true;
        job.paused = false;
        job.canceled = false;
        setDownloadWakeLock(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(job.tempFile, true);
                    if (job.initMapUrl != null && !job.initMapUrl.isEmpty() && !job.initDownloaded && job.currentSegmentIndex == 0) {
                        byte[] initBytes = downloadSegmentBytesWithRetry(job.initMapUrl, job.refererUrl);
                        fos.write(initBytes);
                        fos.flush();
                        job.downloadedBytes += initBytes.length;
                        job.initDownloaded = true;
                        job.isFmp4 = true;
                    }

                    long lastTickTime = System.currentTimeMillis();
                    long bytesAtLastTick = job.downloadedBytes;
                    long currentSpeed = 0;

                    for (int i = job.currentSegmentIndex; i < job.totalSegments; i++) {
                        if (!job.running || job.paused || job.canceled) {
                            break;
                        }
                        byte[] segBytes = downloadSegmentBytesWithRetry(job.segmentUrls[i], job.refererUrl);
                        if (!job.running || job.paused || job.canceled) {
                            break;
                        }
                        if (i == 0 && !job.isFmp4 && segBytes.length >= 8) {
                            String box = new String(segBytes, 4, 4, "ISO-8859-1");
                            if ("ftyp".equals(box) || "styp".equals(box) || "moof".equals(box) || "sidx".equals(box)) {
                                job.isFmp4 = true;
                            }
                        }
                        fos.write(segBytes);
                        fos.flush();
                        job.downloadedBytes += segBytes.length;
                        job.currentSegmentIndex = i + 1;

                        long now = System.currentTimeMillis();
                        if (now - lastTickTime >= 500) {
                            currentSpeed = ((job.downloadedBytes - bytesAtLastTick) * 1000L) / Math.max(1L, now - lastTickTime);
                            lastTickTime = now;
                            bytesAtLastTick = job.downloadedBytes;
                        }

                        int pct = (job.currentSegmentIndex * 100) / Math.max(1, job.totalSegments);
                        showOrUpdateLockScreenTimer("جاري التحميل (" + pct + "%)", job.currentSegmentIndex + "/" + job.totalSegments, job.fileName, true);
                        emitNativeHlsProgressToUi(job.jobId, job.currentSegmentIndex, job.totalSegments, job.downloadedBytes, currentSpeed, "Downloading", "");
                    }

                    fos.flush();
                    fos.close();
                    fos = null;

                    if (job.canceled) {
                        try { job.tempFile.delete(); } catch (Exception ignored) {}
                        emitNativeHlsProgressToUi(job.jobId, job.currentSegmentIndex, job.totalSegments, job.downloadedBytes, 0, "Canceled", "");
                    } else if (job.paused || !job.running) {
                        emitNativeHlsProgressToUi(job.jobId, job.currentSegmentIndex, job.totalSegments, job.downloadedBytes, 0, "Paused", "");
                    } else if (job.currentSegmentIndex >= job.totalSegments) {
                        job.running = false;
                        emitNativeHlsProgressToUi(job.jobId, job.totalSegments, job.totalSegments, job.downloadedBytes, 0, "Saving", "");
                        String finalName = job.fileName;
                        String finalMime = job.isFmp4 ? "video/mp4" : "video/mp2t";
                        if (job.isFmp4 && finalName.endsWith(".ts")) {
                            finalName = finalName.substring(0, finalName.length() - 3) + ".mp4";
                        } else if (!job.isFmp4 && finalName.endsWith(".mp4")) {
                            // Keep .mp4 if user player prefers or .ts based on container
                        }
                        final String savedRes = moveTempFileToPublicDownloads(job.tempFile, finalName, finalMime, job.subFolder);
                        showOrUpdateLockScreenTimer("", "00:00", "", false);
                        if (savedRes.startsWith("OK:")) {
                            final String toastName = finalName;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "اكتمل التحميل والحفظ: " + toastName, Toast.LENGTH_LONG).show();
                                }
                            });
                            emitNativeHlsProgressToUi(job.jobId, job.totalSegments, job.totalSegments, job.downloadedBytes, 0, "Completed", savedRes);
                        } else {
                            emitNativeHlsProgressToUi(job.jobId, job.totalSegments, job.totalSegments, job.downloadedBytes, 0, "SaveFailed", savedRes);
                        }
                    }
                } catch (Exception e) {
                    job.running = false;
                    job.paused = true;
                    emitNativeHlsProgressToUi(job.jobId, job.currentSegmentIndex, job.totalSegments, job.downloadedBytes, 0, "Failed", e.getMessage());
                } finally {
                    try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                    job.running = false;
                    setDownloadWakeLock(false);
                }
            }
        }).start();
    }

    public String moveTempFileToPublicDownloads(File tempFile, String fileName, String mimeType, String subFolder) {
        if (tempFile == null || !tempFile.exists() || tempFile.length() == 0) {
            return "ERROR:Empty temp file";
        }
        String safeName = (fileName != null && !fileName.trim().isEmpty()) ? fileName.trim() : ("Video_" + System.currentTimeMillis() + ".mp4");
        safeName = safeName.replace('/', '_').replace(':', '_').replace('*', '_').replace('?', '_').replace('"', '_').replace('<', '_').replace('>', '_').replace('|', '_');
        String safeMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : (safeName.endsWith(".ts") ? "video/mp2t" : "video/mp4");
        String cleanSub = "";
        if (subFolder != null && !subFolder.trim().isEmpty()) {
            String candidate = subFolder.trim().replace("..", "").replace('/', '_').replace(':', '_');
            if (!candidate.equalsIgnoreCase("Downloads") && !candidate.equalsIgnoreCase("Download")) {
                cleanSub = candidate;
            }
        }

        // Method A: Official Android 10+ (API 29+) MediaStore.Downloads (works on Android 10, 11, 12, 13, 14 without storage permission issues!)
        if (Build.VERSION.SDK_INT >= 29) {
            FileInputStream fis = null;
            OutputStream os = null;
            try {
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, safeMime);
                String relPath = Environment.DIRECTORY_DOWNLOADS + (cleanSub.isEmpty() ? "" : ("/" + cleanSub));
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri itemUri = resolver.insert(collection, values);
                if (itemUri != null) {
                    os = resolver.openOutputStream(itemUri);
                    if (os != null) {
                        fis = new FileInputStream(tempFile);
                        byte[] buf = new byte[65536];
                        int read;
                        while ((read = fis.read(buf)) != -1) {
                            os.write(buf, 0, read);
                        }
                        os.flush();
                        os.close();
                        os = null;
                        fis.close();
                        fis = null;

                        ContentValues doneValues = new ContentValues();
                        doneValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        resolver.update(itemUri, doneValues, null, null);
                        try { tempFile.delete(); } catch (Exception ignored) {}
                        return "OK:" + relPath + "/" + safeName;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try { if (fis != null) fis.close(); } catch (Exception ignored) {}
                try { if (os != null) os.close(); } catch (Exception ignored) {}
            }
        }

        // Method B: Direct FileOutputStream in DIRECTORY_DOWNLOADS + MediaScannerConnection (Android 6 - 9 & fallback)
        FileInputStream fis2 = null;
        FileOutputStream fos2 = null;
        try {
            File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File targetDir = cleanSub.isEmpty() ? baseDir : new File(baseDir, cleanSub);
            if (!targetDir.exists()) targetDir.mkdirs();
            File outFile = new File(targetDir, safeName);
            fis2 = new FileInputStream(tempFile);
            fos2 = new FileOutputStream(outFile);
            byte[] buf = new byte[65536];
            int read;
            while ((read = fis2.read(buf)) != -1) {
                fos2.write(buf, 0, read);
            }
            fos2.flush();
            fos2.close();
            fos2 = null;
            fis2.close();
            fis2 = null;
            try { tempFile.delete(); } catch (Exception ignored) {}
            try {
                MediaScannerConnection.scanFile(this, new String[]{ outFile.getAbsolutePath() }, new String[]{ safeMime }, null);
            } catch (Exception ignored) {}
            return "OK:" + outFile.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR:" + e.getMessage();
        } finally {
            try { if (fis2 != null) fis2.close(); } catch (Exception ignored) {}
            try { if (fos2 != null) fos2.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView != null) {
                webView.evaluateJavascript(
                    "(function(){if(window.handleAndroidHardwareBack)return window.handleAndroidHardwareBack()?1:0;if(window.handleAppBackNavigation)return window.handleAppBackNavigation()?1:0;return 0;})()",
                    new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
                                return;
                            }
                            if (browserWebView != null && browserWebView.getVisibility() == View.VISIBLE && browserWebView.canGoBack()) {
                                browserWebView.goBack();
                                return;
                            }
                            if (webView.canGoBack()) {
                                webView.goBack();
                                return;
                            }
                            finish();
                        }
                    }
                );
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public static class WebAppBridge {
        private final MainActivity activity;
        public WebAppBridge(MainActivity activity) { this.activity = activity; }

        @JavascriptInterface
        public void showToast(String msg) {
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void openExternal(final String url) {
            if (url == null || url.trim().isEmpty()) return;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
                        activity.startActivity(i);
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void openNativeBrowser(final String url, final int topDp, final int bottomDp) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (activity.browserWebView == null || url == null) return;
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) activity.browserWebView.getLayoutParams();
                    lp.topMargin = activity.dpToPx(topDp > 0 ? topDp : 92);
                    lp.bottomMargin = activity.dpToPx(bottomDp > 0 ? bottomDp : 56);
                    activity.browserWebView.setLayoutParams(lp);
                    activity.browserDesiredVisible = true;
                    activity.browserWebView.setVisibility(View.VISIBLE);
                    if (!url.equals(activity.currentBrowserUrl)) {
                        activity.currentBrowserUrl = url;
                        activity.currentBrowserTitle = "";
                        Map<String, String> extraHeaders = new HashMap<String, String>();
                        extraHeaders.put("X-Requested-With", "");
                        activity.browserWebView.loadUrl(url, extraHeaders);
                    }
                    if (activity.lastDetectedCount > 0 && activity.nativeFabBtn != null) {
                        activity.nativeFabBtn.setVisibility(View.VISIBLE);
                    }
                }
            });
        }

        @JavascriptInterface
        public void setNativeBrowserVisible(final boolean visible) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.browserDesiredVisible = visible;
                    if (activity.browserWebView != null) {
                        activity.browserWebView.setVisibility(visible ? View.VISIBLE : View.GONE);
                    }
                    if (activity.nativeFabBtn != null) {
                        activity.nativeFabBtn.setVisibility((visible && activity.lastDetectedCount > 0) ? View.VISIBLE : View.GONE);
                    }
                }
            });
        }

        @JavascriptInterface
        public void reloadNativeBrowser() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (activity.browserWebView != null) {
                        activity.browserWebView.reload();
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean goBackNativeBrowser() {
            final boolean[] wentBack = new boolean[]{ false };
            if (activity.browserWebView != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.browserWebView.canGoBack()) {
                            activity.browserWebView.goBack();
                        }
                    }
                });
                wentBack[0] = true;
            }
            return wentBack[0];
        }

        @JavascriptInterface
        public void scanNativePage() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.injectDomMediaScannerIntoBrowser();
                }
            });
        }

        @JavascriptInterface
        public void setNativeAdBlockEnabled(final boolean enabled) {
            activity.nativeAdBlockEnabled = enabled;
        }

        @JavascriptInterface
        public void updateNativeFab(final int count, final boolean visible) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.lastDetectedCount = count;
                    if (activity.nativeFabBtn != null) {
                        activity.nativeFabBtn.setText("⬇ " + count);
                        boolean showNow = visible && count > 0 && activity.browserDesiredVisible &&
                            activity.browserWebView != null && activity.browserWebView.getVisibility() == View.VISIBLE;
                        activity.nativeFabBtn.setVisibility(showNow ? View.VISIBLE : View.GONE);
                    }
                }
            });
        }

        @JavascriptInterface
        public void reportPageTitle(final String title, final String url) {
            if (title != null && !title.trim().isEmpty()) {
                activity.currentBrowserTitle = title.trim();
                activity.notifyUiBrowserState(url != null ? url : activity.currentBrowserUrl, activity.currentBrowserTitle);
            }
        }

        @JavascriptInterface
        public void reportDetectedMediaFromJs(final String url, final String title, final String quality) {
            if (url != null && activity.isSniffableMediaUrl(url)) {
                activity.notifyUiMediaDetected(url, title, quality);
            }
        }

        private String executeHttpGetText(String urlStr, String userAgent) {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(urlStr);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", userAgent);
                if (activity.currentBrowserUrl != null && !activity.currentBrowserUrl.isEmpty()) {
                    conn.setRequestProperty("Referer", activity.currentBrowserUrl);
                }
                String cookies = CookieManager.getInstance().getCookie(urlStr);
                if (cookies != null && !cookies.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookies);
                }
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append((char) 10);
                    }
                    br.close();
                    return sb.toString();
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            return "";
        }

        @JavascriptInterface
        public String fetchTextWithCookies(String urlStr) {
            if (urlStr == null || (!urlStr.startsWith("http://") && !urlStr.startsWith("https://"))) return "";
            String mobileRes = executeHttpGetText(urlStr, MOBILE_CHROME_UA);
            // Problem 3: If this is an M3U8 master playlist and does not yet contain 2160/4K, also probe with a 4K Desktop User-Agent!
            if (mobileRes.contains("#EXT-X-STREAM-INF") && !mobileRes.contains("2160") && !mobileRes.contains("3840")) {
                String desktopRes = executeHttpGetText(urlStr, DESKTOP_4K_UA);
                if (desktopRes.contains("#EXT-X-STREAM-INF") &&
                    (desktopRes.contains("2160") || desktopRes.contains("3840") || desktopRes.length() > mobileRes.length())) {
                    return desktopRes;
                }
            }
            if (mobileRes.isEmpty()) {
                return executeHttpGetText(urlStr, DESKTOP_4K_UA);
            }
            return mobileRes;
        }

        @JavascriptInterface
        public void onLongPressPageElement(final String url, final String labelTitle) {
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                activity.showLongPressLinkDialog(url, labelTitle);
            }
        }

        @JavascriptInterface
        public boolean startNativeHlsJob(String jobId, String segmentUrlsJson, String initMapUrl, String fileName, String subFolder) {
            try {
                JSONArray arr = new JSONArray(segmentUrlsJson);
                if (arr.length() == 0) return false;
                String[] urls = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    urls[i] = arr.getString(i);
                }
                NativeHlsJob job;
                synchronized (activity.nativeHlsJobs) {
                    job = activity.nativeHlsJobs.get(jobId);
                    if (job == null) {
                        job = new NativeHlsJob();
                        job.jobId = jobId;
                        job.segmentUrls = urls;
                        job.initMapUrl = initMapUrl != null ? initMapUrl : "";
                        job.fileName = fileName;
                        job.subFolder = subFolder != null ? subFolder : "";
                        job.refererUrl = activity.currentBrowserUrl;
                        job.totalSegments = urls.length;
                        File tempDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (tempDir == null || !tempDir.exists()) tempDir = activity.getCacheDir();
                        job.tempFile = new File(tempDir, "hls_" + jobId.replaceAll("[^a-zA-Z0-9_-]", "_") + ".tmp");
                        if (job.tempFile.exists()) job.tempFile.delete();
                        activity.nativeHlsJobs.put(jobId, job);
                    }
                }
                activity.startOrResumeNativeHlsWorker(job);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean pauseNativeHlsJob(String jobId) {
            synchronized (activity.nativeHlsJobs) {
                NativeHlsJob job = activity.nativeHlsJobs.get(jobId);
                if (job != null) {
                    job.paused = true;
                    job.running = false;
                    return true;
                }
            }
            return false;
        }

        @JavascriptInterface
        public boolean resumeNativeHlsJob(String jobId) {
            NativeHlsJob job;
            synchronized (activity.nativeHlsJobs) {
                job = activity.nativeHlsJobs.get(jobId);
            }
            if (job != null && job.currentSegmentIndex < job.totalSegments) {
                activity.startOrResumeNativeHlsWorker(job);
                return true;
            }
            return false;
        }

        @JavascriptInterface
        public boolean cancelNativeHlsJob(String jobId) {
            synchronized (activity.nativeHlsJobs) {
                NativeHlsJob job = activity.nativeHlsJobs.remove(jobId);
                if (job != null) {
                    job.canceled = true;
                    job.running = false;
                    try { if (job.tempFile != null && job.tempFile.exists()) job.tempFile.delete(); } catch (Exception ignored) {}
                    return true;
                }
            }
            return false;
        }

        @JavascriptInterface
        public void setBackgroundDownloadActive(final boolean active) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.setDownloadWakeLock(active);
                    if (active && activity.webView != null) {
                        try { activity.webView.resumeTimers(); } catch (Exception ignored) {}
                    }
                }
            });
        }

        @JavascriptInterface
        public String beginSaveFile(String fileName) {
            try {
                String sessionId = "save_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 10000);
                File tempDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (tempDir == null || !tempDir.exists()) tempDir = activity.getCacheDir();
                File tempFile = new File(tempDir, sessionId + ".tmp");
                FileOutputStream fos = new FileOutputStream(tempFile);
                synchronized (activity.activeSaveStreams) {
                    activity.activeSaveTempFiles.put(sessionId, tempFile);
                    activity.activeSaveStreams.put(sessionId, fos);
                }
                return sessionId;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean appendSaveFileChunk(String sessionId, String base64Chunk) {
            if (sessionId == null || base64Chunk == null) return false;
            try {
                FileOutputStream fos;
                synchronized (activity.activeSaveStreams) {
                    fos = activity.activeSaveStreams.get(sessionId);
                }
                if (fos == null) return false;
                String cleanB64 = base64Chunk;
                int comma = cleanB64.indexOf(',');
                if (comma >= 0) cleanB64 = cleanB64.substring(comma + 1);
                byte[] bytes = Base64.decode(cleanB64, Base64.DEFAULT);
                fos.write(bytes);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String finishSaveFile(String sessionId, final String fileName, String mimeType, String subFolder) {
            File tempFile = null;
            FileOutputStream fos = null;
            synchronized (activity.activeSaveStreams) {
                fos = activity.activeSaveStreams.remove(sessionId);
                tempFile = activity.activeSaveTempFiles.remove(sessionId);
            }
            try {
                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
            } catch (Exception ignored) {}
            if (tempFile == null || !tempFile.exists()) {
                return "ERROR:Temp file not found";
            }
            final String result = activity.moveTempFileToPublicDownloads(tempFile, fileName, mimeType, subFolder);
            if (result.startsWith("OK:")) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "تم حفظ الفيديو في التنزيلات: " + fileName, Toast.LENGTH_LONG).show();
                    }
                });
            }
            return result;
        }

        @JavascriptInterface
        public void downloadUrl(String url, String fileName) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.startNativeDownload(url, fileName, "*/*");
                }
            });
        }

        @JavascriptInterface
        public boolean saveBase64File(String base64Data, String fileName) {
            return saveBase64File(base64Data, fileName, "*/*");
        }

        @JavascriptInterface
        public boolean saveBase64File(String base64Data, String fileName, String mimeType) {
            return saveBase64FileInFolder(base64Data, fileName, "", mimeType);
        }

        @JavascriptInterface
        public boolean saveBase64FileInFolder(String base64Data, final String fileName, String folderName, String mimeType) {
            try {
                String sId = beginSaveFile(fileName);
                if (sId == null || sId.isEmpty()) return false;
                if (!appendSaveFileChunk(sId, base64Data)) return false;
                String res = finishSaveFile(sId, fileName, mimeType, folderName);
                return res != null && res.startsWith("OK:");
            } catch (final Exception e) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "خطأ أثناء حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
                return false;
            }
        }

        @JavascriptInterface
        public void updateWidget(String jsonPayload) {
            try {
                JSONObject obj = new JSONObject(jsonPayload);
                SharedPreferences prefs = activity.getSharedPreferences("af_widget_prefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor ed = prefs.edit();
                if (obj.has("title")) ed.putString("title", obj.optString("title", ""));
                if (obj.has("body")) ed.putString("body", obj.optString("body", ""));
                if (obj.has("primaryColor")) ed.putString("primaryColor", obj.optString("primaryColor", "#458AFF"));
                if (obj.has("bgColor")) ed.putString("bgColor", obj.optString("bgColor", "#141C2B"));
                ed.apply();
                
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void updateTimerNotification(final String modeTitle, final String timeStr, final String taskName, final boolean isRunning) {
            
        }
    }

    public void showOrUpdateLockScreenTimer(String modeTitle, String timeStr, String taskName, boolean isRunning) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String channelId = "af_live_timer_channel";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "المؤقت وحاسب الجلسات",
                    NotificationManager.IMPORTANCE_LOW
                );
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
            if (!isRunning && ("00:00".equals(timeStr) || "00:00:00".equals(timeStr))) {
                nm.cancel(7701);
                return;
            }
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(this, 7701, intent, flags);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, channelId);
            } else {
                builder = new Notification.Builder(this);
            }
            builder.setSmallIcon(R.mipmap.ic_launcher)
                   .setContentTitle(timeStr + " • " + (modeTitle != null ? modeTitle : "المؤقت"))
                   .setContentText(taskName != null && !taskName.isEmpty() ? taskName : "جلسة تركيز نشطة")
                   .setContentIntent(pi)
                   .setOngoing(isRunning)
                   .setOnlyAlertOnce(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            }
            nm.notify(7701, builder.build());
        } catch (Exception ignored) {}
    }
}