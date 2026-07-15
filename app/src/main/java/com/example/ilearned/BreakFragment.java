package com.example.ilearned;

import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ilearned.timer.BreakTimerEngine;
import com.example.ilearned.views.CircularTimerView;

import java.util.Locale;

/**
 * BreakFragment
 *
 * A separate fragment for break sessions. Keeps break time completely
 * isolated from study tracking — nothing here writes to Firestore.
 *
 * Features:
 *  - Countdown ring in warm tan (#D2C49E) to visually distinguish from focus
 *  - Tappable time display opens hours + minutes picker (same UX as focus timer)
 *  - Start / Pause / Reset controls
 *  - When timer finishes: alarm rings, then auto-navigates back to TimerFragment
 *
 * Navigation back to Focus Mode:
 *  - Tapping the "Focus Mode" tab in the header
 *  - Timer finishing (auto-switch after alarm)
 */
public class BreakFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────
    private CircularTimerView ringView;
    private TextView          timeText;
    private TextView          labelText;
    private Button            startButton;
    private View              resetButton;
    private TextView          tabFocusMode;
    private TextView          tabBreak;

    // ── Engine ────────────────────────────────────────────────
    private BreakTimerEngine engine;

    // ── Alarm ─────────────────────────────────────────────────
    private MediaPlayer alarmPlayer;
    private boolean     alarmShown = false;

    // ─────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_break, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        engine = BreakTimerEngine.get(requireContext());

        initViews(view);
        observeEngine();
        setupListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAlarm();
        ringView     = null;
        timeText     = null;
        labelText    = null;
        startButton  = null;
        resetButton  = null;
        tabFocusMode = null;
        tabBreak     = null;
    }

    // ─────────────────────────────────────────────────────────
    //  Views
    // ─────────────────────────────────────────────────────────

    private void initViews(View view) {
        ringView     = view.findViewById(R.id.breakTimerRing);
        timeText     = view.findViewById(R.id.breakTimerText);
        labelText    = view.findViewById(R.id.breakLabel);
        startButton  = view.findViewById(R.id.breakStartButton);
        resetButton  = view.findViewById(R.id.breakResetButton);
        tabFocusMode = view.findViewById(R.id.tabFocusMode);
        tabBreak     = view.findViewById(R.id.tabBreak);

        // Highlight Break tab as active
        tabBreak.setSelected(true);
        tabFocusMode.setSelected(false);

        // Underline timer text to show it is tappable
        timeText.setPaintFlags(
                timeText.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
    }

    // ─────────────────────────────────────────────────────────
    //  Observers
    // ─────────────────────────────────────────────────────────

    private void observeEngine() {
        engine.remainingMs().observe(getViewLifecycleOwner(), ms -> {
            renderTime(ms);
            renderProgress();
        });

        engine.totalMs().observe(getViewLifecycleOwner(), ms -> renderProgress());

        engine.state().observe(getViewLifecycleOwner(), state -> {
            switch (state) {
                case RUNNING:
                    startButton.setText("  Pause");
                    startButton.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.baseline_pause_24, 0, 0, 0);
                    labelText.setText("ON BREAK");
                    // Remove underline — not tappable while running
                    timeText.setPaintFlags(
                            timeText.getPaintFlags()
                                    & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                    break;

                case PAUSED:
                    startButton.setText("  Resume");
                    startButton.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.baseline_play_arrow_24, 0, 0, 0);
                    labelText.setText("PAUSED");
                    timeText.setPaintFlags(
                            timeText.getPaintFlags()
                                    | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                    break;

                case FINISHED:
                    if (!alarmShown) {
                        alarmShown = true;
                        triggerAlarm();
                    }
                    break;

                case IDLE:
                default:
                    startButton.setText("  Start");
                    startButton.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.baseline_play_arrow_24, 0, 0, 0);
                    labelText.setText("BREAK");
                    timeText.setPaintFlags(
                            timeText.getPaintFlags()
                                    | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                    alarmShown = false;
                    break;
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    //  Click listeners=
    // ─────────────────────────────────────────────────────────

    private void setupListeners() {

        // Focus Mode tab → navigate back to TimerFragment
        tabFocusMode.setOnClickListener(v -> goToFocusMode());

        // Break tab is already active — tapping it does nothing
        tabBreak.setOnClickListener(v -> { /* already on Break */ });

        // Start / Pause
        startButton.setOnClickListener(v -> {
            BreakTimerEngine.State s = engine.state().getValue();
            if (s == BreakTimerEngine.State.RUNNING) {
                engine.pause();
            } else {
                alarmShown = false;
                engine.start();
            }
        });

        // Reset
        resetButton.setOnClickListener(v -> {
            stopAlarm();
            alarmShown = false;
            engine.reset();
        });

        // Tap timer display to set custom duration
        timeText.setOnClickListener(v -> {
            BreakTimerEngine.State s = engine.state().getValue();
            if (s == BreakTimerEngine.State.RUNNING) {
                android.widget.Toast.makeText(requireContext(),
                        "Pause or reset the timer to change the duration.",
                        android.widget.Toast.LENGTH_SHORT).show();
            } else {
                showDurationPicker();
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    //  Duration picker — hours + minutes (same UX as focus timer)
    // ─────────────────────────────────────────────────────────

    private void showDurationPicker() {
        int currentMins  = engine.getBreakMinutes();
        int currentHours = currentMins / 60;
        int currentMin   = currentMins % 60;

        View dlg = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_duration_picker, null);

        NumberPicker hourPicker   = dlg.findViewById(R.id.pickerHours);
        NumberPicker minutePicker = dlg.findViewById(R.id.pickerMinutes);
        TextView     titleView    = dlg.findViewById(R.id.durationPickerTitle);

        titleView.setText("Set Break Duration");

        // Hours 0–3 (breaks are typically short)
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(3);
        hourPicker.setValue(currentHours);
        hourPicker.setWrapSelectorWheel(false);

        // Minutes in 5-minute steps
        String[] minuteLabels = {"00", "05", "10", "15", "20", "25",
                "30", "35", "40", "45", "50", "55"};
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(minuteLabels.length - 1);
        minutePicker.setDisplayedValues(minuteLabels);
        minutePicker.setValue(Math.min(currentMin / 5, minuteLabels.length - 1));
        minutePicker.setWrapSelectorWheel(true);

        new AlertDialog.Builder(requireContext())
                .setView(dlg)
                .setPositiveButton("Set Break", (d, w) -> {
                    int hours       = hourPicker.getValue();
                    int minuteIndex = minutePicker.getValue();
                    int minutes     = minuteIndex * 5;
                    int totalMins   = hours * 60 + minutes;
                    if (totalMins < 1) totalMins = 1;
                    engine.setBreakMinutes(totalMins);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────
    //  Alarm — fires when break ends, then returns to Focus Mode
    // ─────────────────────────────────────────────────────────

    private void triggerAlarm() {
        // Vibrate
        Vibrator vib = (Vibrator) requireContext()
                .getSystemService(Context.VIBRATOR_SERVICE);
        if (vib != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 400, 200, 400}, -1));
            } else {
                //noinspection deprecation
                vib.vibrate(new long[]{0, 400, 200, 400}, -1);
            }
        }

        // MediaPlayer on STREAM_ALARM (same fix as focus timer)
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null)
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            alarmPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            } else {
                //noinspection deprecation
                alarmPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }
            alarmPlayer.setDataSource(requireContext(), alarmUri);
            alarmPlayer.setLooping(false); // play once then auto-switch
            alarmPlayer.prepare();
            alarmPlayer.start();

            // When the sound finishes playing, auto-navigate to Focus Mode
            alarmPlayer.setOnCompletionListener(mp -> {
                stopAlarm();
                engine.reset();
                if (isAdded()) {
                    requireActivity().runOnUiThread(this::goToFocusMode);
                }
            });

        } catch (Exception e) {
            // Sound failed — still navigate back after a short delay
            stopAlarm();
            engine.reset();
            if (isAdded()) {
                requireActivity().runOnUiThread(this::goToFocusMode);
            }
        }
    }

    private void stopAlarm() {
        if (alarmPlayer != null) {
            if (alarmPlayer.isPlaying()) alarmPlayer.stop();
            alarmPlayer.release();
            alarmPlayer = null;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Navigation back to Focus Mode
    // ─────────────────────────────────────────────────────────

    private void goToFocusMode() {
        if (!isAdded()) return;
        stopAlarm();
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.frameLayout, new TimerFragment())
                .commit();
    }

    // ─────────────────────────────────────────────────────────
    //  Rendering helpers
    // ─────────────────────────────────────────────────────────

    private void renderTime(Long ms) {
        if (timeText == null) return;
        long totalSeconds = ms / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            timeText.setText(String.format(Locale.getDefault(),
                    "%d:%02d:%02d", hours, minutes, seconds));
        } else {
            timeText.setText(String.format(Locale.getDefault(),
                    "%02d:%02d", minutes, seconds));
        }
    }

    private void renderProgress() {
        if (ringView == null) return;
        Long total     = engine.totalMs().getValue();
        Long remaining = engine.remainingMs().getValue();
        if (total == null || remaining == null || total == 0) return;
        float progress = (float) remaining / (float) total;
        ringView.setProgress(progress);
    }
}