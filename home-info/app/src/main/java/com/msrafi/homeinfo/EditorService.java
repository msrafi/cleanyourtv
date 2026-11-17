package com.msrafi.homeinfo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the reminders page up while the TV is on something other than Home.
 * The shade entry stays quiet; Home still reads the list itself and never opens a connection.
 */
public final class EditorService extends Service {
    private static final String CHANNEL = "reminders-page";
    private static final int NOTE = 41;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            NotificationManager notes = getSystemService(NotificationManager.class);
            if (notes.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL, "Reminders page", NotificationManager.IMPORTANCE_MIN);
                channel.setShowBadge(false);
                channel.setSound(null, null);
                notes.createNotificationChannel(channel);
            }
            Notification note = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_edit)
                    .setContentTitle("Reminders")
                    .setContentText("A phone on this Wi-Fi can edit the list.")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTE, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTE, note);
            }
        } catch (RuntimeException ignored) {
            // The page still runs in this process. It stops if Android frees the process.
            stopSelf();
        }
        EditorServer.start(this);
        return START_STICKY;
    }
}
