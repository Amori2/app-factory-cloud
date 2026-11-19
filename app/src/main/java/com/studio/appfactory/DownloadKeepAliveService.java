package com.studio.appfactory;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class DownloadKeepAliveService extends Service {
    private static final String CHANNEL_ID = "af_bg_dl_keepalive";
    private static final int NOTIF_ID = 7702;
    private PowerManager.WakeLock svcWakeLock;
    private WifiManager.WifiLock svcWifiLock;

    @SuppressLint("WakelockTimeout")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && svcWakeLock == null) {
                svcWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CruzDM:SvcWakeLock");
                svcWakeLock.setReferenceCounted(false);
            }
            if (svcWakeLock != null && !svcWakeLock.isHeld()) {
                svcWakeLock.acquire(10 * 60 * 60 * 1000L);
            }
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && svcWifiLock == null) {
                svcWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CruzDM:SvcWifiLock");
                svcWifiLock.setReferenceCounted(false);
            }
            if (svcWifiLock != null && !svcWifiLock.isHeld()) {
                svcWifiLock.acquire();
            }

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "CruzDM Background Downloads", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            Intent tapIntent = new Intent(this, MainActivity.class);
            tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                piFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(this, NOTIF_ID, tapIntent, piFlags);
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
            b.setSmallIcon(R.mipmap.ic_launcher)
             .setContentTitle("App Factory")
             .setContentText("التحميل مستمر في الخلفية")
             .setContentIntent(pi)
             .setOngoing(true);
            Notification notif = b.build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, notif);
            }
        } catch (Exception ignored) {}
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (svcWakeLock != null && svcWakeLock.isHeld()) svcWakeLock.release();
            if (svcWifiLock != null && svcWifiLock.isHeld()) svcWifiLock.release();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}