package com.example.passivetracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.NotificationManager;
import android.util.Log;

public class MoodNotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_MOOD_SELECTED = "com.example.passivetracker.MOOD_SELECTED";
    public static final String EXTRA_MOOD = "extra_mood";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_MOOD_SELECTED.equals(intent.getAction())) {
            String selectedMood = intent.getStringExtra(EXTRA_MOOD);
            
            if (selectedMood != null) {
                // Store the mood in SharedPreferences as notification_state
                SharedPreferences prefs = context.getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE);
                prefs.edit().putString("notification_state", selectedMood).apply();
                Log.d("MoodReceiver", "Mood stored: " + selectedMood);
            }

            // Dismiss the notification
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(1005);
        }
    }
}
