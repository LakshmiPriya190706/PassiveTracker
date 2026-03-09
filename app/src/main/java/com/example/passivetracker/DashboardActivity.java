package com.example.passivetracker;

import android.Manifest;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.widget.TimePicker;

import com.google.android.material.button.MaterialButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvOverallMentalState;
    private View cardSuggestions;
    private TextView tvSuggestionQuote, tvSuggestionPoints;
    private LinearLayout layoutResources;
    private LinearLayout layoutWellnessMap;
    private VideoView videoAvatar;
    private FirebaseFirestore db;

    // Scoring Logic Variables
    private float scoreSad = 0, scoreHappy = 0, scoreIrritated = 0, scoreStressed = 0, scoreLonely = 0, scoreCalm = 0;

    private static final Map<String, String> SOCIAL_APPS = new LinkedHashMap<>();
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MaterialButton btnCallSchedule = findViewById(R.id.btnCallSchedule);

        btnCallSchedule.setOnClickListener(v -> showTimePicker());

        SharedPreferences prefs = getSharedPreferences("CallSchedule", MODE_PRIVATE);
        String savedTime = prefs.getString("daily_call_time", null);

        if(savedTime != null){
            updateCallButton(savedTime);
        }

        tvOverallMentalState = findViewById(R.id.tvOverallMentalState);
        cardSuggestions = findViewById(R.id.cardSuggestions);
        tvSuggestionQuote = findViewById(R.id.tvSuggestionQuote);
        tvSuggestionPoints = findViewById(R.id.tvSuggestionPoints);
        layoutResources = findViewById(R.id.layoutResources);
        layoutWellnessMap = findViewById(R.id.layoutWellnessMap);
        videoAvatar = findViewById(R.id.videoAvatar);

        ExtendedFloatingActionButton fabAI = findViewById(R.id.fabAI);
        db = FirebaseFirestore.getInstance();

        setupWellnessChart();

        // Show the mood check-in notification immediately for testing
        NotificationHelper.showMoodCheckNotification(this);

        findViewById(R.id.cardPassive).setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, MainActivity.class)));

        findViewById(R.id.cardActive).setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, ActiveAnalysisActivity.class)));

        fabAI.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, ChatActivity.class)));

        // Trigger the logic calculation
        fetchDataAndCalculate();
    }

    private void fetchDataAndCalculate() {
        scoreSad = 0; scoreHappy = 0; scoreIrritated = 0; scoreStressed = 0; scoreLonely = 0; scoreCalm = 0;

        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000L;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfToday = cal.getTimeInMillis();

        float totalMinsWeek = 0, todayMins = 0;
        int totalUnlocksWeek = 0, todayUnlocks = 0;
        float totalSocialMinsWeek = 0, todaySocialMins = 0;

        int incomingWeek = 0, incomingToday = 0;
        int missedWeek = 0, missedToday = 0;
        int outgoingWeek = 0, outgoingToday = 0;
        long durationWeek = 0, durationToday = 0;

        float nightTotalTimeToday = 0;

        for (int i = 0; i < 7; i++) {
            long start = startOfToday - (6 - i) * dayMs;
            long end = start + dayMs;
            if (i == 6) end = now;

            float dailyMins = 0;
            float dailySocialMins = 0;
            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(start, end);
            for (UsageStats u : stats.values()) {
                float mins = u.getTotalTimeInForeground() / 60000f;
                dailyMins += mins;
                if (SOCIAL_APPS.containsKey(u.getPackageName())) {
                    dailySocialMins += mins;
                }
            }
            totalMinsWeek += dailyMins;
            totalSocialMinsWeek += dailySocialMins;
            if (i == 6) { todayMins = dailyMins; todaySocialMins = dailySocialMins; }

            int dailyUnlocks = getUnlockCount(usm, start, end);
            totalUnlocksWeek += dailyUnlocks;
            if (i == 6) todayUnlocks = dailyUnlocks;

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

            if (i == 6) {
                for (UsageStats u : stats.values()) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(u.getLastTimeUsed());
                    int h = c.get(Calendar.HOUR_OF_DAY);
                    if (h >= 21 || h <= 6) {
                        nightTotalTimeToday += u.getTotalTimeInForeground() / 60000f;
                    }
                }
            }
        }

        // Scoring Logic
        float avgMins = totalMinsWeek / 7f;
        if (avgMins > 0) {
            float ratio = todayMins / avgMins;
            if (ratio < 0.6f) { scoreSad += 0.1f; scoreLonely += 0.2f; }
            else if (ratio >= 1.3f && ratio <= 1.8f) { scoreIrritated += 0.1f; }
            else if (ratio > 1.8f) { scoreStressed += 0.1f; }
        }

        float avgUnlocks = totalUnlocksWeek / 7f;
        if (avgUnlocks > 0) {
            float ratio = (float) todayUnlocks / avgUnlocks;
            if (ratio < 0.6f) { scoreSad += 0.1f; scoreLonely += 0.2f; }
            else if (ratio >= 1.3f && ratio <= 1.8f) { scoreIrritated += 0.1f; }
            else if (ratio > 1.8f) { scoreStressed += 0.1f; }
        }

        float avgSocial = totalSocialMinsWeek / 7f;
        if (avgSocial > 0) {
            float ratio = todaySocialMins / avgSocial;
            if (ratio < 0.6f) { scoreSad += 0.1f; scoreLonely += 0.2f; }
            else if (ratio >= 1.3f && ratio <= 1.8f) { scoreCalm += 0.1f; }
            else if (ratio > 1.8f) { scoreHappy += 0.1f; }
        }

        String sleepStateStr = getSleepState(nightTotalTimeToday);
        if (sleepStateStr.equals("Good")) { scoreHappy += 0.6f; scoreCalm += 0.5f; }
        else if (sleepStateStr.equals("Moderate")) { scoreLonely += 0.1f; scoreSad += 0.1f; scoreStressed += 0.1f; }
        else { scoreLonely += 0.2f; scoreSad += 0.2f; scoreStressed += 0.2f; }

        if (incomingWeek > 0 && (float) incomingToday / (incomingWeek / 7f) < 1f) scoreSad += 0.2f;
        if (missedWeek > 0 && (float) missedToday / (missedWeek / 7f) > 1f) { scoreIrritated += 0.2f; scoreLonely += 0.2f; scoreSad += 0.1f; }
        if (outgoingWeek > 0 && (float) outgoingToday / (outgoingWeek / 7f) < 1f) scoreLonely += 0.3f;
        if (durationWeek > 0 && (float) durationToday / (durationWeek / 7f) < 1f) { scoreIrritated += 0.2f; scoreSad += 0.2f; }

        applyFaceScanScore();
        applySharedPreferencesScores();
        fetchFirestoreAndFinish();
    }

    private void applyFaceScanScore() {
        String scanResult = getIntent().getStringExtra("face_scan");
        if (scanResult == null) return;
        if (scanResult.equalsIgnoreCase("Happy")) scoreHappy += 0.1f;
        else if (scanResult.equalsIgnoreCase("Lonely/Sad")) { scoreLonely += 0.1f; scoreSad += 0.1f; }
        else if (scanResult.equalsIgnoreCase("Irritated")) scoreIrritated += 0.1f;
        else if (scanResult.equalsIgnoreCase("Stressed")) scoreStressed += 0.1f;
        else if (scanResult.equalsIgnoreCase("Sad")) scoreSad += 0.1f;
        else if (scanResult.equalsIgnoreCase("Lonely")) scoreLonely += 0.1f;
        else if (scanResult.equalsIgnoreCase("Calm")) scoreCalm += 0.1f;
    }

    private void applySharedPreferencesScores() {
        SharedPreferences prefs = getSharedPreferences("WellnessPrefs", MODE_PRIVATE);
        addScore(prefs.getString("llm_state", "NA"), 0.2f);
        addScore(prefs.getString("notification_state", "NA"), 0.7f);
    }

    private void addScore(String state, float val) {
        if (state == null || state.equals("NA")) return;
        if (state.equalsIgnoreCase("Happy")) scoreHappy += val;
        else if (state.equalsIgnoreCase("Sad")) scoreSad += val;
        else if (state.equalsIgnoreCase("Irritated")) scoreIrritated += val;
        else if (state.equalsIgnoreCase("Stressed")) scoreStressed += val;
        else if (state.equalsIgnoreCase("Lonely")) scoreLonely += val;
        else if (state.equalsIgnoreCase("Calm")) scoreCalm += val;
    }

    private void fetchFirestoreAndFinish() {
        db.collection("call_analysis").document("call").get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String state = documentSnapshot.getString("overall emotional state");
                        addScore(state, 0.7f);
                    }
                    determineFinalState();
                })
                .addOnFailureListener(e -> determineFinalState());
    }

    private void determineFinalState() {
        String finalState = "NA";
        float max = 0;
        if (scoreHappy > max) { max = scoreHappy; finalState = "Happy"; }
        if (scoreSad > max) { max = scoreSad; finalState = "Sad"; }
        if (scoreIrritated > max) { max = scoreIrritated; finalState = "Irritated"; }
        if (scoreStressed > max) { max = scoreStressed; finalState = "Stressed"; }
        if (scoreLonely > max) { max = scoreLonely; finalState = "Lonely"; }
        if (scoreCalm > max) { max = scoreCalm; finalState = "Calm"; }

        String finalStateText = finalState;
        runOnUiThread(() -> {
            tvOverallMentalState.setText(finalStateText);
            updateSuggestions(finalStateText);
            updateAvatar(finalStateText);
        });
    }

    private void updateAvatar(String state) {
        int resId = 0;
        switch (state) {
            case "Happy":
                resId = getResources().getIdentifier("happy", "raw", getPackageName());
                break;
            case "Sad":
                resId = getResources().getIdentifier("sad", "raw", getPackageName());
                break;
            case "Irritated":
                resId = getResources().getIdentifier("irritaed", "raw", getPackageName());
                break;
            case "Stressed":
                resId = getResources().getIdentifier("stressed", "raw", getPackageName());
                break;
            case "Lonely":
                resId = getResources().getIdentifier("lonely", "raw", getPackageName());
                break;
            case "Calm":
                resId = getResources().getIdentifier("happy", "raw", getPackageName()); // fallback to happy for calm
                break;
        }

        if (resId != 0) {
            videoAvatar.setVisibility(View.VISIBLE);
            videoAvatar.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + resId));
            videoAvatar.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                videoAvatar.start();
            });
        } else {
            videoAvatar.setVisibility(View.GONE);
        }
    }

    private void updateSuggestions(String state) {
        if (state.equals("NA")) {
            cardSuggestions.setVisibility(View.GONE);
            return;
        }
        cardSuggestions.setVisibility(View.VISIBLE);
        layoutResources.removeAllViews();

        String quote;
        String points;
        String[] links;

        switch (state) {
            case "Sad":
                quote = "\"It’s okay to feel broken sometimes. Every tear you shed waters the seeds of tomorrow’s joy. Your heart will heal, and the light will find you again.\"";
                points = "✔️ Go for a walk in sunlight 🌞\n✔️ Listen to calming music 🎵\n✔️ Write your thoughts (journaling)\n✔️ Watch something light / funny\n✔️ Maintain regular sleep";
                links = new String[]{"https://www.youtube.com/watch?v=tqOrmigV9f0", "https://www.youtube.com/watch?v=HM7oTRPwtUQ", "https://www.youtube.com/watch?v=KTgPsB2ukjc", "https://www.youtube.com/watch?v=4EaMJOo1jks"};
                break;
            case "Happy":
                quote = "\"Treasure this moment of joy, for it is a gift. Let your laughter echo, your smile shine, and your happiness inspire everyone around you.\"";
                points = "✔️ Continue healthy habits\n✔️ Share positivity with others\n✔️ Practice gratitude\n✔️ Engage in hobbies\n✔️ Set new goals";
                links = new String[]{"https://www.youtube.com/watch?v=UPkMkIOzej8", "https://www.youtube.com/playlist?list=PLKPi39tTpkdpjBVQZo5oFLWjFjlOMkd2A"};
                break;
            case "Stressed":
                quote = "\"The weight you carry today will shape your strength tomorrow. Breathe, take one step at a time, and remember—storms never last forever.\"";
                points = "✔️ Deep breathing / meditation 🧘\n✔️ Short breaks from work\n✔️ Light exercise or stretching\n✔️ Time management planning\n✔️ Reduce caffeine and screen time";
                links = new String[]{"https://www.youtube.com/watch?v=tqOrmigV9f0", "https://www.onegreenplanet.org/natural-health/five-meditation-videos-for-depression", "https://www.youtube.com/@cedarrose_counseling/videos"};
                break;
            case "Irritated":
                quote = "\"Anger is a flame that burns the soul. Step back, breathe, and let patience guide you; peace is the victory that lasts.\"";
                points = "✔️ Take a pause before reacting\n✔️ Walk or physical activity 🚶\n✔️ Slow breathing (4-7-8 method)\n✔️ Avoid arguments when emotional\n✔️ Listen to relaxing audio";
                links = new String[]{"https://www.youtube.com/watch?v=tqOrmigV9f0", "https://www.youtube.com/watch?v=149tYQEhqvY"};
                break;
            case "Lonely":
                quote = "\"Being alone doesn’t mean you’re lost. It’s in solitude that you find your true self, your voice, and the courage to shine even brighter.\"";
                points = "✔️ Talk to a friend or family member\n✔️ Join a club / group activity\n✔️ Volunteer or help someone\n✔️ Go outside (park, café, library)\n✔️ Limit excessive phone scrolling";
                links = new String[]{"https://www.youtube.com/watch?v=n2L_ynaW5CE", "https://www.youtube.com/watch?v=WmpXj-P_vCA", "https://www.youtube.com/watch?v=UjYSOXswsmI", "https://www.youtube.com/watch?v=6GNN6YkY70M"};
                break;
            default:
                cardSuggestions.setVisibility(View.GONE);
                return;
        }

        tvSuggestionQuote.setText(quote);
        tvSuggestionPoints.setText(points);

        for (int i = 0; i < links.length; i++) {
            final String url = links[i];
            TextView tv = new TextView(this);
            tv.setText(String.format("• Resource %d", i + 1));
            tv.setTextColor(ContextCompat.getColor(this, R.color.primary_color));
            tv.setPadding(0, 12, 0, 12);
            tv.setTextSize(14);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
            layoutResources.addView(tv);
        }
    }

    private int getUnlockCount(UsageStatsManager usm, long start, long end) {
        int count = 0;
        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == 7) count++;
        }
        return count;
    }

    private CallStats getCallStats(long start, long end) {
        CallStats stats = new CallStats();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return stats;
        String selection = CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE + " <= ?";
        String[] selectionArgs = {String.valueOf(start), String.valueOf(end)};
        try (Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, selection, selectionArgs, null)) {
            if (cursor != null) {
                int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);
                while (cursor.moveToNext()) {
                    int type = cursor.getInt(typeIdx);
                    if (type == CallLog.Calls.INCOMING_TYPE) stats.incoming++;
                    else if (type == CallLog.Calls.OUTGOING_TYPE) stats.outgoing++;
                    else if (type == CallLog.Calls.MISSED_TYPE) stats.missed++;
                    if (type != CallLog.Calls.MISSED_TYPE) stats.duration += cursor.getLong(durationIdx);
                }
            }
        } catch (Exception ignored) {}
        return stats;
    }

    private String getSleepState(float m) {
        if (m <= 15) return "Good";
        if (m <= 60) return "Moderate";
        return "Poor";
    }

    private static class CallStats { int incoming = 0, missed = 0, outgoing = 0; long duration = 0; }

    @Override
    protected void onResume() {
        super.onResume();
        fetchDataAndCalculate();
    }

    private void setupWellnessChart() {
        if (layoutWellnessMap == null) return;
        layoutWellnessMap.removeAllViews();

        String[] states = {"Irritated", "Stressed", "Sad", "Calm", "Happy"};
        String[] emojis = {"😠", "😰", "😢", "😌", "😊"};
        Random random = new Random();

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault());

        int boxWidthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110, getResources().getDisplayMetrics());
        int marginEndPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        int paddingVerticalPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        int paddingHorizontalPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());

        for (int i = 6; i >= 0; i--) {
            Calendar tempCal = (Calendar) cal.clone();
            tempCal.add(Calendar.DAY_OF_YEAR, -i);
            String dayName = sdf.format(tempCal.getTime());
            int randIdx = random.nextInt(states.length);
            String randomState = states[randIdx];
            String randomEmoji = emojis[randIdx];

            LinearLayout dayBox = new LinearLayout(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(boxWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, marginEndPx, 0);
            dayBox.setLayoutParams(params);
            dayBox.setOrientation(LinearLayout.VERTICAL);
            dayBox.setGravity(Gravity.CENTER);
            dayBox.setBackgroundResource(R.drawable.card_bg_white);
            dayBox.setPadding(paddingHorizontalPx, paddingVerticalPx, paddingHorizontalPx, paddingVerticalPx);
            dayBox.setElevation(4f);

            TextView tvDay = new TextView(this);
            tvDay.setText(dayName);
            tvDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvDay.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTypeface(null, android.graphics.Typeface.BOLD);
            dayBox.addView(tvDay);

            TextView tvEmoji = new TextView(this);
            tvEmoji.setText(randomEmoji);
            tvEmoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
            tvEmoji.setGravity(Gravity.CENTER);
            tvEmoji.setPadding(0, 8, 0, 8);
            dayBox.addView(tvEmoji);

            TextView tvState = new TextView(this);
            tvState.setText(randomState);
            tvState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tvState.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
            tvState.setGravity(Gravity.CENTER);
            dayBox.addView(tvState);

            layoutWellnessMap.addView(dayBox);
        }
    }
    private void showTimePicker() {

        Calendar calendar = Calendar.getInstance();

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {

                    String time = String.format(Locale.getDefault(),
                            "%02d:%02d", selectedHour, selectedMinute);

                    SharedPreferences prefs = getSharedPreferences("CallSchedule", MODE_PRIVATE);
                    prefs.edit().putString("daily_call_time", time).apply();

                    updateCallButton(time);

                },
                hour,
                minute,
                true
        );

        dialog.show();
    }
    private void updateCallButton(String time) {

        MaterialButton btn = findViewById(R.id.btnCallSchedule);

        btn.setText(time);   // shows selected time on button

        Toast.makeText(this,
                "Daily Call Set: " + time,
                Toast.LENGTH_SHORT).show();
    }
}
