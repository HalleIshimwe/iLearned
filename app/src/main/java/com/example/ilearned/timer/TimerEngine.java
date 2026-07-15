package com.example.ilearned.timer;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.CopyOnWriteArrayList;

public class TimerEngine {

    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

    public interface SessionListener {
        void onSessionCompleted(long durationMinutes);
        void onPartialStop(long elapsedMinutes);
    }

    private static volatile TimerEngine instance;

    private final Context appContext;
    private final MutableLiveData<Long> remainingMs = new MutableLiveData<>();
    private final MutableLiveData<Long> totalMs = new MutableLiveData<>();
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);

    private CountDownTimer countdown;
    private long currentTotalMs = 25L * 60 * 1000;
    private long currentRemainingMs = 25L * 60 * 1000;

    private final CopyOnWriteArrayList<SessionListener> listeners = new CopyOnWriteArrayList<>();

    private TimerEngine(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        remainingMs.setValue(currentRemainingMs);
        totalMs.setValue(currentTotalMs);
    }

    public static TimerEngine get(Context ctx) {
        if (instance == null) {
            synchronized (TimerEngine.class) {
                if (instance == null) {
                    instance = new TimerEngine(ctx);
                    instance.addListener(new FirestoreSessionLogger());
                }
            }
        }
        return instance;
    }

    public LiveData<Long> remainingMs() { return remainingMs; }
    public LiveData<Long> totalMs() { return totalMs; }
    public LiveData<State> state() { return state; }

    public void addListener(SessionListener l) { listeners.add(l); }
    public void removeListener(SessionListener l) { listeners.remove(l); }

    public void setSessionLengthMinutes(int minutes) {
        if (state.getValue() == State.RUNNING) return;
        long ms = minutes * 60L * 1000;
        currentTotalMs = ms;
        currentRemainingMs = ms;
        totalMs.postValue(ms);
        remainingMs.postValue(ms);
    }

    public void start() {
        if (state.getValue() == State.RUNNING) return;
        ensureService();
        countdown = new CountDownTimer(currentRemainingMs, 250) {
            @Override
            public void onTick(long millisUntilFinished) {
                currentRemainingMs = millisUntilFinished;
                remainingMs.postValue(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                currentRemainingMs = 0;
                remainingMs.postValue(0L);
                state.postValue(State.FINISHED);
                long minutes = currentTotalMs / 60000;
                for (SessionListener l : listeners) l.onSessionCompleted(minutes);
                for (SessionListener l : listeners) l.onSessionCompleted(minutes);
            }
        };
        countdown.start();
        state.postValue(State.RUNNING);
    }

    public void pause() {
        if (state.getValue() != State.RUNNING) return;
        if (countdown != null) countdown.cancel();
        state.postValue(State.PAUSED);
    }

    public void reset() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        if (state.getValue() != State.FINISHED) {
            long elapsed = currentTotalMs - currentRemainingMs;
            long elapsedMinutes = elapsed / 60000;
            if (elapsedMinutes > 0) {
                for (SessionListener l : listeners) l.onPartialStop(elapsedMinutes);
            }
        }
        currentRemainingMs = currentTotalMs;
        remainingMs.postValue(currentTotalMs);
        state.postValue(State.IDLE);
        stopService();
    }

    private void ensureService() {
        Intent i = new Intent(appContext, StudyTimerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(i);
        } else {
            appContext.startService(i);
        }
    }

    private void stopService() {
        Intent i = new Intent(appContext, StudyTimerService.class);
        appContext.stopService(i);
    }
}