package com.example.ilearned;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ilearned.views.StudyBarChartView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * StudyReportActivity — updated version.
 *
 * Changes from v1:
 *  1. MUTED_SAGE (#8BA49A) replaces LIGHT_GREEN (#CBDED3) for below-target bars
 *     — much higher contrast against the cream card background
 *  2. Week label is tappable — opens a DatePickerDialog so the student
 *     can jump to any week. The picked date's Mon–Sun week is loaded.
 *  3. selectedWeekMonday Calendar field drives all date calculations so
 *     a single loadWeekStats() method serves both current and past weeks.
 */
public class StudyReportActivity extends AppCompatActivity {

    // ── Bar colours ───────────────────────────────────────────
    private static final int DARK_GREEN  = 0xFF3B6255;  // ≥ half daily target
    private static final int MUTED_SAGE  = 0xFF8BA49A;  // < half daily target (was CBDED3)

    // ── Views ─────────────────────────────────────────────────
    private StudyBarChartView chartView;
    private TextView          textWeekRange;
    private TextView          textTotalHours;
    private TextView          textSessions;
    private TextView          textGoalProgress;
    private TextView          textMotivation;
    private ProgressBar       progressBar;
    private View              contentLayout;

    // ── Firebase ──────────────────────────────────────────────
    private FirebaseFirestore db;
    private String            userId;

    // ── User settings (loaded once) ───────────────────────────
    private int dailySessionTarget = 6;
    private int dailyGoalHours     = 4;
    private boolean settingsLoaded = false;

    // ── Selected week ─────────────────────────────────────────
    // Always points to MONDAY of the week being displayed.
    // Starts as Monday of the current week; updated when user picks a date.
    private Calendar selectedWeekMonday;

    // ─────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_study_report);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        userId = user.getUid();
        db     = FirebaseFirestore.getInstance();

        // Initialise to current week's Monday
        selectedWeekMonday = getMondayOf(Calendar.getInstance());

        initViews();
        loadReport();
    }

    // ─────────────────────────────────────────────────────────
    //  Views
    // ─────────────────────────────────────────────────────────

    private void initViews() {
        chartView        = findViewById(R.id.studyBarChart);
        textWeekRange    = findViewById(R.id.textWeekRange);
        textTotalHours   = findViewById(R.id.textTotalHours);
        textSessions     = findViewById(R.id.textSessions);
        textGoalProgress = findViewById(R.id.textGoalProgress);
        textMotivation   = findViewById(R.id.textMotivation);
        progressBar      = findViewById(R.id.progressBar);
        contentLayout    = findViewById(R.id.contentLayout);

        ImageButton btnBack = findViewById(R.id.buttonBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        contentLayout.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        // Show week label and make it tappable
        updateWeekLabel();
        textWeekRange.setOnClickListener(v -> openDatePicker());
    }

    // ─────────────────────────────────────────────────────────
    //  Date picker — user taps week label
    // ─────────────────────────────────────────────────────────

    private void openDatePicker() {
        // Pre-select the currently displayed week's Monday in the picker
        int year  = selectedWeekMonday.get(Calendar.YEAR);
        int month = selectedWeekMonday.get(Calendar.MONTH);
        int day   = selectedWeekMonday.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    // User picked a date — find that date's Monday
                    Calendar picked = Calendar.getInstance();
                    picked.set(y, m, d);
                    selectedWeekMonday = getMondayOf(picked);
                    updateWeekLabel();
                    reloadWeekData();
                },
                year, month, day);

        picker.setTitle("Pick any date in the week you want to view");

        // Restrict to dates up to and including today (no future weeks)
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());

        picker.show();
    }

    // ─────────────────────────────────────────────────────────
    //  Data loading
    // ─────────────────────────────────────────────────────────

    private void loadReport() {
        if (settingsLoaded) {
            // Settings already in memory — just load the week stats
            loadWeekStats();
            return;
        }
        // First load: fetch user settings then week stats
        db.collection("users").document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        Long goal   = userDoc.getLong("dailyGoalHours");
                        Long target = userDoc.getLong("dailySessionTarget");
                        if (goal   != null) dailyGoalHours     = goal.intValue();
                        if (target != null) dailySessionTarget = target.intValue();
                    }
                    settingsLoaded = true;
                    loadWeekStats();
                })
                .addOnFailureListener(e -> {
                    settingsLoaded = true;
                    loadWeekStats();
                });
    }

    /** Called after user picks a new week — shows loading state then reloads */
    private void reloadWeekData() {
        contentLayout.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        loadReport();
    }

    private void loadWeekStats() {
        List<String> weekDates = getWeekDates(selectedWeekMonday);

        final float[] hoursPerDay    = new float[7];
        final int[]   sessionsPerDay = new int[7];
        final int[]   loadedCount    = {0};
        final int     total          = weekDates.size();

        for (int i = 0; i < total; i++) {
            final int dayIndex = i;
            String dateStr     = weekDates.get(i);

            db.collection("users")
                    .document(userId)
                    .collection("dailyStats")
                    .document(dateStr)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Long mins     = doc.getLong("minutesStudied");
                            Long sessions = doc.getLong("sessionsCompleted");
                            hoursPerDay[dayIndex]    = mins     != null ? mins / 60f     : 0f;
                            sessionsPerDay[dayIndex] = sessions != null ? sessions.intValue() : 0;
                        }
                        loadedCount[0]++;
                        if (loadedCount[0] == total)
                            runOnUiThread(() -> buildReport(hoursPerDay, sessionsPerDay));
                    })
                    .addOnFailureListener(e -> {
                        loadedCount[0]++;
                        if (loadedCount[0] == total)
                            runOnUiThread(() -> buildReport(hoursPerDay, sessionsPerDay));
                    });
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Build report
    // ─────────────────────────────────────────────────────────

    private void buildReport(float[] hoursPerDay, int[] sessionsPerDay) {
        progressBar.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);

        // Colour threshold: half of dailySessionTarget × 0.5 hrs/session
        float halfTargetHours = (dailySessionTarget / 2f) * 0.5f;

        int[] barColors = new int[7];
        for (int i = 0; i < 7; i++) {
            barColors[i] = hoursPerDay[i] >= halfTargetHours
                    ? DARK_GREEN
                    : MUTED_SAGE;   // ← updated colour
        }

        // Y-axis ceiling
        float maxH = 1f;
        for (float h : hoursPerDay) if (h > maxH) maxH = h;
        maxH = (float) (Math.ceil(maxH / 2f) * 2f);

        chartView.setData(hoursPerDay, barColors, maxH);

        // Summary
        float totalHours    = 0f;
        int   totalSessions = 0;
        for (int i = 0; i < 7; i++) {
            totalHours    += hoursPerDay[i];
            totalSessions += sessionsPerDay[i];
        }

        float weekGoalHours = dailyGoalHours * 7f;
        int   goalPercent   = weekGoalHours > 0
                ? Math.min(100, (int) ((totalHours / weekGoalHours) * 100))
                : 0;

        textTotalHours.setText(String.format(Locale.getDefault(),
                "%.1f hrs studied this week", totalHours));
        textSessions.setText(totalSessions + " sessions completed");
        textGoalProgress.setText("Weekly goal: " + goalPercent + "% achieved");
        textMotivation.setText(getMotivationalMessage(goalPercent, totalHours));
    }

    // ─────────────────────────────────────────────────────────
    //  Motivational message
    // ─────────────────────────────────────────────────────────

    private String getMotivationalMessage(int goalPercent, float totalHours) {
        if (totalHours == 0f)
            return " No study data for this week yet. Every minute of study counts — let's go!";
        if (goalPercent >= 100)
            return " Outstanding! You crushed your weekly goal. Consistency like this builds greatness.";
        if (goalPercent >= 75)
            return " You're so close to your goal! A strong finish will take you all the way.";
        if (goalPercent >= 50)
            return " Great progress. You're past the halfway mark — keep building that momentum!";
        if (goalPercent >= 25)
            return " You've made a start. Small, consistent sessions add up to big results.";
        return " Every session shapes your future. Even one focused hour today makes a difference.";
    }

    // ─────────────────────────────────────────────────────────
    //  Date helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Given any Calendar date, returns a new Calendar set to
     * the Monday of that same week at midnight.
     */
    private Calendar getMondayOf(Calendar cal) {
        Calendar monday = (Calendar) cal.clone();
        monday.set(Calendar.HOUR_OF_DAY, 0);
        monday.set(Calendar.MINUTE, 0);
        monday.set(Calendar.SECOND, 0);
        monday.set(Calendar.MILLISECOND, 0);

        int dow    = monday.get(Calendar.DAY_OF_WEEK);
        int offset = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        monday.add(Calendar.DAY_OF_YEAR, offset);
        return monday;
    }

    /**
     * Returns 7 date strings "YYYY-MM-DD" starting from the given Monday.
     * Index 0 = Monday … index 6 = Sunday.
     */
    private List<String> getWeekDates(Calendar monday) {
        List<String> dates = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = (Calendar) monday.clone();
        for (int i = 0; i < 7; i++) {
            dates.add(sdf.format(cal.getTime()));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return dates;
    }

    /**
     * Updates the week label TextView with an underline to signal it is tappable.
     * Shows "May 12 – May 18, 2026  " with underline.
     */
    private void updateWeekLabel() {
        if (textWeekRange == null) return;

        SimpleDateFormat short_ = new SimpleDateFormat("MMM d", Locale.getDefault());
        SimpleDateFormat long_  = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        Calendar end = (Calendar) selectedWeekMonday.clone();
        end.add(Calendar.DAY_OF_YEAR, 6);

        //WANT A DRAWABLE PICURE OF CALENDER HERE
        String label = short_.format(selectedWeekMonday.getTime())
                + " – " + long_.format(end.getTime()) + "  ";

        SpannableString span = new SpannableString(label);
        span.setSpan(new UnderlineSpan(), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textWeekRange.setText(span);
    }
}