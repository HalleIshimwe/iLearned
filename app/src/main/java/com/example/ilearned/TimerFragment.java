package com.example.ilearned;

import static android.app.ProgressDialog.show;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ilearned.timer.TimerEngine;
import com.example.ilearned.views.CircularTimerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;

public class TimerFragment extends Fragment {

    private CircularTimerView ringView;
    private TextView timeText;
    private TextView dailyGoalText;
    private TextView sessionText;
    private Button startButton;
    private View resetButton;
    private View goalContainer;
    private View sessionContainer;

    private TimerEngine engine;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private int dailyGoalHours = 8;
    private int sessionTarget = 4;
    private int sessionLengthMinutes = 25;
    private long minutesStudiedToday = 0;
    private int sessionsCompletedToday = 0;

    private ListenerRegistration userListener;
    private ListenerRegistration statsListener;

    //new tab views
    private TextView tabFocusMode;
    private TextView tabBreak;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_timer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ringView         = view.findViewById(R.id.timerRing);
        timeText         = view.findViewById(R.id.timerText);
        dailyGoalText    = view.findViewById(R.id.dailyGoalText);
        sessionText      = view.findViewById(R.id.sessionText);
        startButton      = view.findViewById(R.id.startButton);
        resetButton      = view.findViewById(R.id.resetButton);
        goalContainer    = view.findViewById(R.id.goalContainer);
        sessionContainer = view.findViewById(R.id.sessionContainer);
        tabFocusMode = view.findViewById(R.id.tabFocusMode);
        tabBreak = view.findViewById(R.id.tabBreak);


        engine = TimerEngine.get(requireContext());
        db     = FirebaseFirestore.getInstance();
        auth   = FirebaseAuth.getInstance();

        engine.remainingMs().observe(getViewLifecycleOwner(), this::renderTime);
        engine.totalMs().observe(getViewLifecycleOwner(), ms -> renderProgress());
        engine.state().observe(getViewLifecycleOwner(), this::renderState);

        startButton.setOnClickListener(v -> toggleStart());
        resetButton.setOnClickListener(v -> engine.reset());
        goalContainer.setOnClickListener(v -> showHourPicker());
        sessionContainer.setOnClickListener(v -> showSessionTargetPicker());

        setupTabListener();


        // ── NEW: Tapping the timer display opens the custom duration picker ──
        // Only allowed when the timer is not actively running
        timeText.setOnClickListener(v -> {
            TimerEngine.State currentState = engine.state().getValue();
            if (currentState == TimerEngine.State.RUNNING) {
                // Do not allow changing while running — show a hint
                android.widget.Toast.makeText(requireContext(),
                        "Pause or reset the timer to change the duration.",
                        android.widget.Toast.LENGTH_SHORT).show();
            } else {
                showCustomDurationPicker();
            }
        });

        // Visual hint: underline the timer text to signal it is tappable
        timeText.setPaintFlags(
                timeText.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);

        listenForUserPrefs();
        listenForTodayStats();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) userListener.remove();
        if (statsListener != null) statsListener.remove();
        tabFocusMode = null;
        tabBreak = null;
    }

    //TAB NAVIGATION
    private void setupTabListener(){
        if(tabFocusMode != null){
            tabFocusMode.setOnClickListener(v -> {
                //already on focus mode
            });
        }
        //break tab ceck if timer is running
        if(tabBreak != null){
            tabBreak.setOnClickListener(v-> handleBreakTap());
        }
    }

    //CALLED WHEN STUDENT TAPS THE BREAK TAB
    private void handleBreakTap(){
        TimerEngine.State currentState = engine.state().getValue();

        if(currentState == TimerEngine.State.RUNNING){
            //timer is running warn student
            new AlertDialog.Builder(requireContext())
                    .setTitle("Session Still Running")
                    .setMessage("Your focus session is still running." + "switching to break will pause it.\n\n"
                    + "Switch to break anyway?")
                    .setPositiveButton("Switch to break", (dialog, which) -> {
                        //pause the focus timer then naviagate
                        engine.pause();
                        goToBreak();
                    })
                    .setNegativeButton("Keep Focusing", null).show();
        }else{
            //timer idle or paused
            goToBreak();
        }
    }

    private void goToBreak(){
        if(!isAdded()) return;
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.frameLayout, new BreakFragment())
                .addToBackStack(null)
                .commit();
    }

    // ─────────────────────────────────────────────────────────
    //  Custom duration picker — hours + minutes
    // ─────────────────────────────────────────────────────────

    /**
     * Shows a dialog with two NumberPickers: one for hours (0–5)
     * and one for minutes (0–59). Default values are derived from
     * the current sessionLengthMinutes so the picker opens pre-filled.
     *
     * On Save: converts hours+minutes to total minutes, saves to Firebase
     * under "sessionLengthMinutes", and calls engine.setSessionLengthMinutes()
     * so the ring resets immediately.
     */
    private void showCustomDurationPicker() {
        int currentHours   = sessionLengthMinutes / 60;
        int currentMinutes = sessionLengthMinutes % 60;

        // Inflate a two-picker layout
        View dlg = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_duration_picker, null);

        NumberPicker hourPicker   = dlg.findViewById(R.id.pickerHours);
        NumberPicker minutePicker = dlg.findViewById(R.id.pickerMinutes);
        TextView     titleView    = dlg.findViewById(R.id.durationPickerTitle);

        titleView.setText("Set Focus Duration");

        // Hours: 0–5
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(5);
        hourPicker.setValue(currentHours);
        hourPicker.setWrapSelectorWheel(false);

        // Minutes: 0, 5, 10 … 55 (5-minute steps for usability)
        String[] minuteLabels = {"00", "05", "10", "15", "20", "25",
                "30", "35", "40", "45", "50", "55"};
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(minuteLabels.length - 1);
        minutePicker.setDisplayedValues(minuteLabels);
        // Map current minutes to nearest 5-minute slot
        minutePicker.setValue(Math.min(currentMinutes / 5,
                minuteLabels.length - 1));
        minutePicker.setWrapSelectorWheel(true);

        new AlertDialog.Builder(requireContext())
                .setView(dlg)
                .setPositiveButton("Set Timer", (d, w) -> {
                    int hours       = hourPicker.getValue();
                    int minuteIndex = minutePicker.getValue();
                    int minutes     = minuteIndex * 5;   // convert index → actual minutes
                    int totalMins   = hours * 60 + minutes;

                    // Enforce minimum 1 minute
                    if (totalMins < 1) totalMins = 1;

                    applyCustomDuration(totalMins);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Applies the student's chosen duration:
     *  1. Updates the engine so the ring and countdown reset immediately
     *  2. Saves to Firebase under sessionLengthMinutes so it persists
     */
    private void applyCustomDuration(int totalMinutes) {
        sessionLengthMinutes = totalMinutes;
        engine.setSessionLengthMinutes(totalMinutes);
        saveUserPref("sessionLengthMinutes", totalMinutes);
    }

    // ─────────────────────────────────────────────────────────
    //  Rendering
    // ─────────────────────────────────────────────────────────

    private void renderTime(Long ms) {
        long totalSeconds = ms / 1000;
        long hours        = totalSeconds / 3600;
        long minutes      = (totalSeconds % 3600) / 60;
        long seconds      = totalSeconds % 60;

        // Show H:MM:SS if the duration is 1 hour or more, otherwise MM:SS
        if (hours > 0) {
            timeText.setText(String.format(Locale.getDefault(),
                    "%d:%02d:%02d", hours, minutes, seconds));
        } else {
            timeText.setText(String.format(Locale.getDefault(),
                    "%02d:%02d", minutes, seconds));
        }
        renderProgress();
    }

    private void renderProgress() {
        Long total     = engine.totalMs().getValue();
        Long remaining = engine.remainingMs().getValue();
        if (total == null || remaining == null || total == 0) return;
        float p = (float) remaining / (float) total;
        ringView.setProgress(p);
    }

    private void renderState(TimerEngine.State state) {
        if (state == TimerEngine.State.RUNNING) {
            startButton.setText("  Pause");
            startButton.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.baseline_pause_24, 0, 0, 0);
            // Remove underline while running — not tappable
            timeText.setPaintFlags(
                    timeText.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        } else {
            startButton.setText("  Start");
            startButton.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.baseline_play_arrow_24, 0, 0, 0);
            // Restore underline — tappable again
            timeText.setPaintFlags(
                    timeText.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }
    }

    private void toggleStart() {
        TimerEngine.State s = engine.state().getValue();
        if (s == TimerEngine.State.RUNNING) {
            engine.pause();
        } else {
            engine.start();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Existing goal/session pickers
    // ─────────────────────────────────────────────────────────

    private void showHourPicker() {
        showNumberPicker("Daily goal (hours)", 1, 12, dailyGoalHours,
                value -> saveUserPref("dailyGoalHours", value));
    }

    private void showSessionTargetPicker() {
        showNumberPicker("Session target", 1, 20, sessionTarget,
                value -> saveUserPref("dailySessionTarget", value));
    }

    private interface IntCallback { void onValue(int value); }

    private void showNumberPicker(String title, int min, int max, int current,
                                  IntCallback onSave) {
        View dlg = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_number_picker, null);
        NumberPicker picker    = dlg.findViewById(R.id.numberPicker);
        TextView     titleView = dlg.findViewById(R.id.pickerTitle);
        titleView.setText(title);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(current);
        picker.setWrapSelectorWheel(false);

        new AlertDialog.Builder(requireContext())
                .setView(dlg)
                .setPositiveButton("Save", (d, w) -> onSave.onValue(picker.getValue()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveUserPref(String key, int value) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;
        db.collection("users").document(u.getUid())
                .set(Collections.singletonMap(key, value), SetOptions.merge());
    }

    // ─────────────────────────────────────────────────────────
    //  Firebase listeners
    // ─────────────────────────────────────────────────────────

    private void listenForUserPrefs() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;
        userListener = db.collection("users").document(u.getUid())
                .addSnapshotListener((snap, err) -> {
                    if (snap == null || err != null) return;
                    Long g  = snap.getLong("dailyGoalHours");
                    Long t  = snap.getLong("dailySessionTarget");
                    Long sl = snap.getLong("sessionLengthMinutes");
                    if (g  != null) dailyGoalHours  = g.intValue();
                    if (t  != null) sessionTarget    = t.intValue();
                    if (sl != null && sl.intValue() != sessionLengthMinutes) {
                        sessionLengthMinutes = sl.intValue();
                        engine.setSessionLengthMinutes(sessionLengthMinutes);
                    }
                    renderStats();
                });
    }

    private void listenForTodayStats() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());
        statsListener = db.collection("users").document(u.getUid())
                .collection("dailyStats").document(today)
                .addSnapshotListener((snap, err) -> {
                    if (snap == null || err != null) return;
                    Long m  = snap.getLong("minutesStudied");
                    Long sc = snap.getLong("sessionsCompleted");
                    minutesStudiedToday    = m  == null ? 0 : m;
                    sessionsCompletedToday = sc == null ? 0 : sc.intValue();
                    renderStats();
                });
    }

    private void renderStats() {
        long hoursStudied = minutesStudiedToday / 60;
        dailyGoalText.setText(hoursStudied + " / " + dailyGoalHours + " hrs");
        sessionText.setText(String.format(Locale.getDefault(), "%02d / %02d",
                sessionsCompletedToday, sessionTarget));
    }
}