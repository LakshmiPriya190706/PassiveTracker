package com.example.passivetracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;

public class NotificationHelper {
    public static final String CHANNEL_ID = "mood_check_channel";
    public static final int NOTIFICATION_ID = 1005;

    public static void showMoodCheckNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Mood Check-in", NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, null); // Make it silent so it's not annoying every time
            manager.createNotificationChannel(channel);
        }

        // Custom layout to ensure all 5 states are visible
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.notification_mood_check);
        
        remoteViews.setOnClickPendingIntent(R.id.btnLonely, getPendingIntent(context, "lonely"));
        remoteViews.setOnClickPendingIntent(R.id.btnIrritated, getPendingIntent(context, "irritated"));
        remoteViews.setOnClickPendingIntent(R.id.btnHappy, getPendingIntent(context, "happy"));
        remoteViews.setOnClickPendingIntent(R.id.btnSad, getPendingIntent(context, "sad"));
        remoteViews.setOnClickPendingIntent(R.id.btnStressed, getPendingIntent(context, "stressed"));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true) // Keeps it there
                .setAutoCancel(true);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static PendingIntent getPendingIntent(Context context, String mood) {
        Intent intent = new Intent(context, MoodNotificationReceiver.class);
        intent.setAction(MoodNotificationReceiver.ACTION_MOOD_SELECTED);
        intent.putExtra(MoodNotificationReceiver.EXTRA_MOOD, mood);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        return PendingIntent.getBroadcast(context, mood.hashCode(), intent, flags);
    }
}
