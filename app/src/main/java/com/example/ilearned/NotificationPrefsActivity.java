package com.example.ilearned;

import android.Manifest;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Calendar;

/**
 * NotificationPrefsActivity
 *
 * Lets the student:
 *  - Toggle deadline alert notifications on/off
 *  - When enabled: add a custom deadline event to the device calendar
 *
 * Uses:
 *  - READ_CALENDAR / WRITE_CALENDAR permissions
 *  - SharedPreferences to remember the toggle state
 */
public class NotificationPrefsActivity extends AppCompatActivity {

    private static final String PREFS_NAME        = "ilearned_prefs";
    private static final String KEY_DEADLINE_ALERTS = "deadline_alerts_enabled";

    private Switch        switchDeadlineAlerts;
    private LinearLayout  layoutAddDeadline;
    private EditText      editDeadlineTitle;
    private EditText      editDeadlineDate;   // format: DD/MM/YYYY
    private Button        buttonAddToCalendar;
    private TextView      textCalendarStatus;

    // Calendar permission launcher
    private final ActivityResultLauncher<String[]> calendarPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        Boolean read  = result.getOrDefault(Manifest.permission.READ_CALENDAR,  false);
                        Boolean write = result.getOrDefault(Manifest.permission.WRITE_CALENDAR, false);
                        if (Boolean.TRUE.equals(read) && Boolean.TRUE.equals(write)) {
                            textCalendarStatus.setText(
                                    "✓ Calendar access granted. You can now add deadline alerts.");
                            textCalendarStatus.setTextColor(
                                    ContextCompat.getColor(this, R.color.quizCorrectGreen));
                            layoutAddDeadline.setVisibility(View.VISIBLE);
                        } else {
                            textCalendarStatus.setText(
                                    "Calendar permission denied. "
                                            + "Please enable it in device Settings to add deadline alerts.");
                            textCalendarStatus.setTextColor(
                                    ContextCompat.getColor(this, R.color.quizWrongRed));
                            // Revert switch if permission was denied
                            switchDeadlineAlerts.setChecked(false);
                            saveAlertPref(false);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_notification_prefs);

        initViews();
        loadSavedPrefs();
        setupListeners();
    }

    // ─────────────────────────────────────────────────────────
    //  Views
    // ─────────────────────────────────────────────────────────

    private void initViews() {
        switchDeadlineAlerts = findViewById(R.id.switchDeadlineAlerts);
        layoutAddDeadline    = findViewById(R.id.layoutAddDeadline);
        editDeadlineTitle    = findViewById(R.id.editDeadlineTitle);
        editDeadlineDate     = findViewById(R.id.editDeadlineDate);
        buttonAddToCalendar  = findViewById(R.id.buttonAddToCalendar);
        textCalendarStatus   = findViewById(R.id.textCalendarStatus);

        ImageButton btnBack = findViewById(R.id.buttonBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────────────────────
    //  Load saved preference
    // ─────────────────────────────────────────────────────────

    private void loadSavedPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_DEADLINE_ALERTS, false);
        switchDeadlineAlerts.setChecked(enabled);

        if (enabled) {
            // Check if permission is still granted
            boolean hasPermission = hasCalendarPermission();
            layoutAddDeadline.setVisibility(hasPermission ? View.VISIBLE : View.GONE);
            if (hasPermission) {
                textCalendarStatus.setText("✓ Calendar access granted.");
                textCalendarStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.quizCorrectGreen));
            } else {
                textCalendarStatus.setText(
                        "Calendar permission needed. Tap the toggle to request access.");
                textCalendarStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.textSecondary));
            }
        } else {
            layoutAddDeadline.setVisibility(View.GONE);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Listeners
    // ─────────────────────────────────────────────────────────

    private void setupListeners() {
        switchDeadlineAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveAlertPref(isChecked);
            if (isChecked) {
                // Request calendar permission if not already granted
                if (hasCalendarPermission()) {
                    layoutAddDeadline.setVisibility(View.VISIBLE);
                    textCalendarStatus.setText("✓ Calendar access granted.");
                    textCalendarStatus.setTextColor(
                            ContextCompat.getColor(this, R.color.quizCorrectGreen));
                } else {
                    requestCalendarPermission();
                }
            } else {
                layoutAddDeadline.setVisibility(View.GONE);
                textCalendarStatus.setText("");
            }
        });

        buttonAddToCalendar.setOnClickListener(v -> addDeadlineToCalendar());
    }

    // ─────────────────────────────────────────────────────────
    //  Calendar permission
    // ─────────────────────────────────────────────────────────

    private boolean hasCalendarPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCalendarPermission() {
        calendarPermLauncher.launch(new String[]{
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
        });
    }

    // ─────────────────────────────────────────────────────────
    //  Add deadline event to device calendar
    // ─────────────────────────────────────────────────────────

    private void addDeadlineToCalendar() {
        String title    = editDeadlineTitle.getText().toString().trim();
        String dateStr  = editDeadlineDate.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a deadline title", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dateStr.isEmpty()) {
            Toast.makeText(this, "Please enter a date (DD/MM/YYYY)", Toast.LENGTH_SHORT).show();
            return;
        }

        // Parse DD/MM/YYYY
        long eventTimeMs;
        try {
            String[] parts = dateStr.split("/");
            int day   = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1; // Calendar months are 0-based
            int year  = Integer.parseInt(parts[2]);

            Calendar cal = Calendar.getInstance();
            cal.set(year, month, day, 9, 0, 0); // default 9:00 AM
            eventTimeMs = cal.getTimeInMillis();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Invalid date format. Use DD/MM/YYYY", Toast.LENGTH_SHORT).show();
            return;
        }

        // Insert into device calendar
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.TITLE,          title + " — Deadline");
        values.put(CalendarContract.Events.DTSTART,        eventTimeMs);
        values.put(CalendarContract.Events.DTEND,          eventTimeMs + 60 * 60 * 1000); // 1 hr
        values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
        values.put(CalendarContract.Events.DESCRIPTION,    "Deadline alert set by iLearned");
        values.put(CalendarContract.Events.CALENDAR_ID,    1); // primary calendar

        try {
            getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            Toast.makeText(this,
                    "Deadline added to your calendar!", Toast.LENGTH_SHORT).show();
            editDeadlineTitle.setText("");
            editDeadlineDate.setText("");
        } catch (Exception e) {
            Toast.makeText(this,
                    "Could not add to calendar: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Preferences
    // ─────────────────────────────────────────────────────────

    private void saveAlertPref(boolean enabled) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DEADLINE_ALERTS, enabled)
                .apply();
    }
}