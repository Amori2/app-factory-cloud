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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
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
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_CODE = 1001;
    private static final int PERMISSION_REQ_CODE = 1002;

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

        webView = new WebView(this);
        try { webView.setBackgroundColor(Color.parseColor(initBgHex)); } catch (Exception ignored) { webView.setBackgroundColor(Color.parseColor("#090D16")); }
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.addJavascriptInterface(new WebAppBridge(this), "AndroidBridge");
        webView.addJavascriptInterface(new WebAppBridge(this), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return handleUri(request.getUrl().toString());
                }
                return false;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUri(url);
            }
            private boolean handleUri(String url) {
                if (url == null) return false;
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
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
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
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                startNativeDownload(url, fileName, mimetype);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public void startNativeDownload(String url, String fileName, String mimeType) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) req.addRequestHeader("cookie", cookies);
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
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
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
        public void downloadUrl(String url, String fileName) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.startNativeDownload(url, fileName, "application/vnd.android.package-archive");
                }
            });
        }

        @JavascriptInterface
        public void saveBase64File(String base64Data, String fileName) {
            saveBase64File(base64Data, fileName, "*/*");
        }

        @JavascriptInterface
        public void saveBase64File(String base64Data, String fileName, String mimeType) {
            saveBase64FileInFolder(base64Data, fileName, "", mimeType);
        }

        @JavascriptInterface
        public void saveBase64FileInFolder(String base64Data, String fileName, String folderName, String mimeType) {
            try {
                String cleanB64 = base64Data;
                if (cleanB64 != null && cleanB64.contains(",")) {
                    cleanB64 = cleanB64.substring(cleanB64.indexOf(",") + 1);
                }
                byte[] bytes = Base64.decode(cleanB64, Base64.DEFAULT);
                File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File targetDir = baseDir;
                if (folderName != null && !folderName.trim().isEmpty()) {
                    String safeFolder = folderName.trim().replace("..", "").replace('/', '_').replace(':', '_').replace('*', '_').replace('?', '_').replace('<', '_').replace('>', '_').replace('|', '_');
                    targetDir = new File(baseDir, safeFolder);
                }
                if (!targetDir.exists()) targetDir.mkdirs();
                File outFile = new File(targetDir, fileName);
                FileOutputStream fos = new FileOutputStream(outFile);
                fos.write(bytes);
                fos.flush();
                fos.close();
                String msg = (folderName != null && !folderName.trim().isEmpty())
                    ? ("تم حفظ الملف في Download/" + folderName.trim() + "/" + fileName)
                    : ("تم حفظ الملف في التنزيلات: " + fileName);
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(activity, "خطأ أثناء حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
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