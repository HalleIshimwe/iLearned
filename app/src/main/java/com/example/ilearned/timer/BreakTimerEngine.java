package com.example.ilearned.timer;

import android.content.Context;
import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * BreakTimerEngine
 *
 * A self-contained singleton countdown timer used exclusively for break sessions.
 * Completely separate from TimerEngine so break time is NEVER saved to Firestore
 * or counted as study time.
 *
 * Default break duration: 5 minutes (configurable by the student).
 * When finished: posts State.FINISHED so BreakFragment can auto-navigate
 * back to Focus Mode and trigger the alarm.
 */
public class BreakTimerEngine {

    // ── Singleton ─────────────────────────────────────────────
    private static BreakTimerEngine instance;

    public static synchronized BreakTimerEngine get(Context ctx) {
        if (instance == null) instance = new BreakTimerEngine(ctx.getApplicationContext());
        return instance;
    }

    // ── State ─────────────────────────────────────────────────
    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

    private final MutableLiveData<Long>  remainingMsLive = new MutableLiveData<>();
    private final MutableLiveData<Long>  totalMsLive     = new MutableLiveData<>();
    private final MutableLiveData<State> stateLive       = new MutableLiveData<>(State.IDLE);

    private CountDownTimer countDownTimer;
    private long           totalMs;
    private long           remainingMs;
    private int            breakMinutes = 5;      // default 5-minute break

    private BreakTimerEngine(Context ctx) {
        applyDuration(breakMinutes);
    }

    // ─────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────

    public LiveData<Long>  remainingMs() { return remainingMsLive; }
    public LiveData<Long>  totalMs()     { return totalMsLive;     }
    public LiveData<State> state()       { return stateLive;       }
    public int             getBreakMinutes() { return breakMinutes; }

    /** Start or resume the break countdown */
    public void start() {
        State current = stateLive.getValue();
        if (current == State.RUNNING) return;

        long startFrom = (current == State.PAUSED && remainingMs > 0)
                ? remainingMs : totalMs;

        countDownTimer = new CountDownTimer(startFrom, 250) {
            @Override
            public void onTick(long msUntilFinished) {
                remainingMs = msUntilFinished;
                remainingMsLive.postValue(remainingMs);
            }

            @Override
            public void onFinish() {
                remainingMs = 0;
                remainingMsLive.postValue(0L);
                stateLive.postValue(State.FINISHED);
            }
        }.start();

        stateLive.setValue(State.RUNNING);
    }

    /** Pause the countdown */
    public void pause() {
        if (countDownTimer != null) countDownTimer.cancel();
        stateLive.setValue(State.PAUSED);
    }

    /** Reset to the full break duration */
    public void reset() {
        if (countDownTimer != null) countDownTimer.cancel();
        applyDuration(breakMinutes);
        stateLive.setValue(State.IDLE);
    }

    /**
     * Set a new break duration in minutes.
     * Resets the timer to the new duration immediately.
     */
    public void setBreakMinutes(int minutes) {
        if (minutes < 1) minutes = 1;
        breakMinutes = minutes;
        if (countDownTimer != null) countDownTimer.cancel();
        applyDuration(breakMinutes);
        stateLive.setValue(State.IDLE);
    }

    // ─────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────

    private void applyDuration(int minutes) {
        totalMs     = minutes * 60 * 1000L;
        remainingMs = totalMs;
        totalMsLive.postValue(totalMs);
        remainingMsLive.postValue(remainingMs);
    }
}