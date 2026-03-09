package com.example.passivetracker;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final Map<String, String> SOCIAL_APPS = new LinkedHashMap<>();
    private static final int EVENT_KEYGUARD_DISMISSED = 7;
    private static final int PERMISSION_REQUEST_CODE = 123;

    static {
        SOCIAL_APPS.put("com.whatsapp", "WhatsApp");
        SOCIAL_APPS.put("com.whatsapp.w4b", "WhatsApp Business");
        SOCIAL_APPS.put("com.instagram.android", "Instagram");
        SOCIAL_APPS.put("com.facebook.katana", "Facebook");
        SOCIAL_APPS.put("com.linkedin.android", "LinkedIn");
        SOCIAL_APPS.put("com.snapchat.android", "Snapchat");
        SOCIAL_APPS.put("com.indeed.android.jobsearch", "Indeed");
        SOCIAL_APPS.put("com.twitter.android", "X (Twitter)");
        SOCIAL_APPS.put("com.google.android.youtube", "YouTube");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Please grant Usage Stats permission", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CALL_LOG}, PERMISSION_REQUEST_CODE);
        } else {
            loadData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadData();
            } else {
                Toast.makeText(this, "Permission to read call logs is required for call stats.", Toast.LENGTH_SHORT).show();
                loadData();
            }
        }
    }

    private void loadData() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000L;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfToday = cal.getTimeInMillis();

        Map<Integer, Float> weekHours = new HashMap<>();
        String[] daysLabels = new String[7];
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault());

        long totalUnlocksWeek = 0, todayUnlocks = 0;
        long totalNightUnlocksWeek = 0, todayNightUnlocks = 0;

        int incomingToday = 0, missedToday = 0, outgoingToday = 0;
        long durationToday = 0;
        int incomingWeek = 0, missedWeek = 0, outgoingWeek = 0;
        long durationWeek = 0;

        for (int i = 0; i < 7; i++) {
            long start = startOfToday - (6 - i) * dayMs;
            long end = start + dayMs;
            if (i == 6) end = now;

            float totalMins = 0f;
            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(start, end);
            for (UsageStats u : stats.values()) {
                totalMins += u.getTotalTimeInForeground() / 60000f;
            }
            weekHours.put(i, totalMins / 60f);

            int dailyUnlocks = getUnlockCount(usm, start, end);
            totalUnlocksWeek += dailyUnlocks;
            if (i == 6) todayUnlocks = dailyUnlocks;

            int nightUnlocks = getNightUnlockCount(usm, start, end);
            totalNightUnlocksWeek += nightUnlocks;
            if (i == 6) todayNightUnlocks = nightUnlocks;

            CallStats dailyCalls = getCallStats(start, end);
            incomingWeek += dailyCalls.incoming;
            missedWeek += dailyCalls.missed;
            outgoingWeek += dailyCalls.outgoing;
            durationWeek += dailyCalls.duration;

            if (i == 6) {
                incomingToday = dailyCalls.incoming;
                missedToday = dailyCalls.missed;
                outgoingToday = dailyCalls.outgoing;
                durationToday = dailyCalls.duration;
            }

            Calendar labelCal = Calendar.getInstance();
            labelCal.setTimeInMillis(start);
            daysLabels[i] = sdf.format(labelCal.getTime());
        }

        float todayMins = weekHours.containsKey(6) ? weekHours.get(6) * 60 : 0;
        float totalWeekMins = 0;
        for (float h : weekHours.values()) totalWeekMins += (h * 60);

        ((TextView) findViewById(R.id.tvAvg)).setText(String.format(Locale.getDefault(), "%d min", (int) (totalWeekMins / 7)));
        ((TextView) findViewById(R.id.tvToday)).setText(String.format(Locale.getDefault(), "%d min", (int) todayMins));
        setupWeeklyBar(findViewById(R.id.weeklyChart), weekHours, daysLabels);

        displaySleepStats(usm, now, dayMs);

        showSocialMedia(usm, startOfToday, now, R.id.todaySocial, false);
        showSocialMedia(usm, startOfToday - (6 * dayMs), now, R.id.weekSocial, true);

        ((TextView) findViewById(R.id.tvTotalUnlocks)).setText(String.valueOf(todayUnlocks));
        ((TextView) findViewById(R.id.tvAvgUnlocks)).setText("Avg: " + (totalUnlocksWeek / 7));
        ((TextView) findViewById(R.id.tvNightUnlocks)).setText(String.valueOf(todayNightUnlocks));
        ((TextView) findViewById(R.id.tvAvgNightUnlocks)).setText("Avg: " + (totalNightUnlocksWeek / 7));

        ((TextView) findViewById(R.id.tvIncomingToday)).setText(String.valueOf(incomingToday));
        ((TextView) findViewById(R.id.tvIncomingAvg)).setText("Avg: " + (incomingWeek / 7));
        ((TextView) findViewById(R.id.tvMissedToday)).setText(String.valueOf(missedToday));
        ((TextView) findViewById(R.id.tvMissedAvg)).setText("Avg: " + (missedWeek / 7));
        ((TextView) findViewById(R.id.tvOutgoingToday)).setText(String.valueOf(outgoingToday));
        ((TextView) findViewById(R.id.tvOutgoingAvg)).setText("Avg: " + (outgoingWeek / 7));
        ((TextView) findViewById(R.id.tvDurationToday)).setText((durationToday / 60) + "m");
        ((TextView) findViewById(R.id.tvDurationAvg)).setText("Avg: " + (durationWeek / 7 / 60) + "m");
    }

    private void displaySleepStats(UsageStatsManager usm, long now, long dayMs) {
        Map<Integer, Float> nightUsage = new HashMap<>();
        float nightTotalTime = 0f;
        Map<String, UsageStats> nightStats = usm.queryAndAggregateUsageStats(now - dayMs, now);
        for (UsageStats u : nightStats.values()) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(u.getLastTimeUsed());
            int h = c.get(Calendar.HOUR_OF_DAY);
            if (h >= 21 || h <= 6) {
                float m = u.getTotalTimeInForeground() / 60000f;
                float currentVal = nightUsage.getOrDefault(h, 0f);
                nightUsage.put(h, currentVal + m);
                nightTotalTime += m;
            }
        }
        setupSleepChart(findViewById(R.id.sleepChart), nightUsage);
        ((TextView) findViewById(R.id.tvSleepQuality)).setText("Sleep Quality: " + sleepState(nightTotalTime));
    }

    private CallStats getCallStats(long start, long end) {
        CallStats stats = new CallStats();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return stats;
        }

        String selection = CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE + " <= ?";
        String[] selectionArgs = {String.valueOf(start), String.valueOf(end)};

        try (Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, selection, selectionArgs, null)) {
            if (cursor != null) {
                int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);
                while (cursor.moveToNext()) {
                    int type = cursor.getInt(typeIdx);
                    long duration = cursor.getLong(durationIdx);

                    if (type == CallLog.Calls.INCOMING_TYPE) {
                        stats.incoming++;
                        stats.duration += duration;
                    } else if (type == CallLog.Calls.OUTGOING_TYPE) {
                        stats.outgoing++;
                        stats.duration += duration;
                    } else if (type == CallLog.Calls.MISSED_TYPE) {
                        stats.missed++;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stats;
    }

    private static class CallStats {
        int incoming = 0, missed = 0, outgoing = 0;
        long duration = 0;
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private int getUnlockCount(UsageStatsManager usm, long start, long end) {
        int count = 0;
        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == EVENT_KEYGUARD_DISMISSED) count++;
        }
        return count;
    }

    private int getNightUnlockCount(UsageStatsManager usm, long start, long end) {
        int count = 0;
        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event event = new UsageEvents.Event();
        Calendar cal = Calendar.getInstance();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == EVENT_KEYGUARD_DISMISSED) {
                cal.setTimeInMillis(event.getTimeStamp());
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                if (hour >= 21 || hour <= 6) count++;
            }
        }
        return count;
    }

    private void showSocialMedia(UsageStatsManager usm, long start, long end, int layoutId, boolean isAvg) {
        LinearLayout layout = findViewById(layoutId);
        layout.removeAllViews();
        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(start, end);
        PackageManager pm = getPackageManager();
        boolean found = false;

        for (Map.Entry<String, String> entry : SOCIAL_APPS.entrySet()) {
            String pkg = entry.getKey();
            if (stats.containsKey(pkg)) {
                UsageStats u = stats.get(pkg);
                if (u == null) continue;
                long mins = u.getTotalTimeInForeground() / 60000;
                if (isAvg) mins /= 7;
                if (mins <= 0) continue;

                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    Drawable icon = pm.getApplicationIcon(ai);
                    View row = LayoutInflater.from(this).inflate(R.layout.item_app_usage, layout, false);
                    ((ImageView) row.findViewById(R.id.icon)).setImageDrawable(icon);
                    ((TextView) row.findViewById(R.id.text)).setText(entry.getValue() + " : " + mins + " mins");
                    layout.addView(row);
                    found = true;
                } catch (Exception ignored) {}
            }
        }
        if (!found) {
            TextView tv = new TextView(this);
            tv.setText("No usage detected.");
            tv.setPadding(16, 8, 16, 8);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
            layout.addView(tv);
        }
    }

    private void setupWeeklyBar(BarChart chart, Map<Integer, Float> data, String[] labels) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            float val = data.getOrDefault(i, 0f);
            entries.add(new BarEntry(i, val));
        }
        BarDataSet ds = new BarDataSet(entries, "Hours Used");
        ds.setColor(ContextCompat.getColor(this, R.color.primary_color));
        ds.setValueTextSize(10f);
        ds.setValueTextColor(ContextCompat.getColor(this, R.color.text_main));
        chart.setData(new BarData(ds));
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.animateY(1000);
        chart.invalidate();
    }

    private void setupSleepChart(LineChart chart, Map<Integer, Float> data) {
        List<Entry> entries = new ArrayList<>();
        final String[] timeLabels = new String[10];
        int index = 0;
        for (int h = 21; h <= 30; h++) {
            int realHour = h % 24;
            float val = data.getOrDefault(realHour, 0f);
            entries.add(new Entry(index, val));
            String label = (realHour == 0) ? "12 AM" : (realHour == 12) ? "12 PM" : (realHour > 12) ? (realHour - 12) + " PM" : realHour + " AM";
            timeLabels[index++] = label;
        }
        LineDataSet ds = new LineDataSet(entries, "Night Usage");
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setDrawFilled(true);
        ds.setLineWidth(3f);
        ds.setCircleRadius(5f);
        ds.setColor(ContextCompat.getColor(this, R.color.card_purple_start));
        ds.setCircleColor(ContextCompat.getColor(this, R.color.card_purple_start));
        ds.setFillColor(ContextCompat.getColor(this, R.color.card_purple_start));
        ds.setFillAlpha(50);
        ds.setDrawValues(false);
        
        chart.setData(new LineData(ds));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return (idx >= 0 && idx < timeLabels.length) ? timeLabels[idx] : "";
            }
        });
        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.animateX(1000);
        chart.invalidate();
    }

    private String sleepState(float m) {
        if (m <= 15) return "Good";
        if (m <= 60) return "Moderate";
        return "Poor";
    }
}
