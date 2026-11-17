package com.hwa.musedash.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * 곡을 받는 동안만 떠 있는 포그라운드 서비스. 하는 일은 알림을 띄워 프로세스를 살려 두는 것뿐이고,
 * 받기 자체는 AndroidBridge의 스레드가 한다. 화면을 끄거나 다른 앱으로 가도 Android가 앱을 죽이지 않게 하려는 것이다.
 *
 * <p>받기가 이어질 때 JS 대기열이 다음 곡을 시작하기까지 잠깐 0곡이 되므로, 서비스는 바로 끄지 않고
 * {@link #STOP_DELAY_MS} 뒤에 끈다(그 사이 새 받기가 오면 취소). 백그라운드에서는 서비스를 새로 켤 수 없어서,
 * 받기 도중에 꺼졌다 다시 켜지면 안 된다.
 */
public final class DownloadService extends Service {
    private static final String TAG = "MuseDashAssistant";
    private static final String CHANNEL_ID = "downloads";
    private static final int NOTIFICATION_ID = 1;
    static final long STOP_DELAY_MS = 15_000;
    private static final String EXTRA_COUNT = "count";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingStop;

    /** 받는 곡이 count개로 바뀌었음을 알린다(1 이상이면 서비스를 켜거나 알림을 고치고, 0이면 잠시 뒤 끈다). 어느 스레드에서든 부른다. */
    public static void setActive(Context context, int count) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (pendingStop != null) {
                MAIN.removeCallbacks(pendingStop);
                pendingStop = null;
            }
            if (count > 0) {
                try {
                    app.startForegroundService(new Intent(app, DownloadService.class).putExtra(EXTRA_COUNT, count));
                } catch (RuntimeException e) {
                    // 앱이 백그라운드라 서비스를 못 켜는 경우. 받기는 계속하되 보호는 못 받는다.
                    Log.w(TAG, "download service not started", e);
                }
            } else {
                pendingStop = () -> {
                    pendingStop = null;
                    app.stopService(new Intent(app, DownloadService.class));
                };
                MAIN.postDelayed(pendingStop, STOP_DELAY_MS);
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "곡 받기", NotificationManager.IMPORTANCE_LOW));
        // startForegroundService 뒤 5초 안에 불러야 한다. 개수는 onStartCommand에서 고친다.
        show(0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        show(intent != null ? intent.getIntExtra(EXTRA_COUNT, 0) : 0);
        return START_NOT_STICKY;
    }

    private void show(int count) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("곡 받는 중")
                .setContentText(count > 0 ? count + "곡을 받고 있습니다" : "곡을 받고 있습니다")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
