package com.example.passivetracker;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageHelper {

    public static long getTodayScreenTime(Context context) {
        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        long start = getStartOfDay();
        long end = System.currentTimeMillis();

        long total = 0;

        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, start, end);

        if (stats == null) return 0;

        for (UsageStats u : stats) {
            total += u.getTotalTimeInForeground();
        }

        return total / 60000; // minutes
    }

    public static Map<Integer, Float> getHourlyUsage(Context context) {
        Map<Integer, Float> hourlyMap = new HashMap<>();

        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        long end = System.currentTimeMillis();
        long start = end - (24 * 60 * 60 * 1000);

        List<UsageStats> stats =
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);

        if (stats == null) return hourlyMap;

        for (UsageStats u : stats) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(u.getLastTimeUsed());
            int hour = c.get(Calendar.HOUR_OF_DAY);

            float mins = u.getTotalTimeInForeground() / 60000f;

            if (!hourlyMap.containsKey(hour)) {
                hourlyMap.put(hour, mins);
            } else {
                hourlyMap.put(hour, hourlyMap.get(hour) + mins);
            }
        }
        return hourlyMap;
    }

    private static long getStartOfDay() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
