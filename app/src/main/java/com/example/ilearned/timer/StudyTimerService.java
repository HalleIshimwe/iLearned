package com.example.ilearned.timer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Observer;

import com.example.ilearned.R;

import java.util.Locale;

/**
 * StudyTimerService
 *
 * Foreground service that keeps the countdown visible while the screen is off.
 *
 * Changes from the original:
 *  ─────────────────────────────────────────────────────────
 *  ALARM FIX
 *  The original triggerCompleteFeedback() used RingtoneManager which plays
 *  on the NOTIFICATION audio stream — this stream is often muted separately
 *  from the alarm stream and plays fire-and-forget with no way to stop it.
 *
 *  The fix uses MediaPlayer routed to AudioManager.STREAM_ALARM which:
 *    • Respects the device's alarm volume (always audible unless alarm is muted)
 *    • Loops continuously until the student explicitly dismisses it
 *    • Is stopped by tapping "Dismiss" on the notification OR by starting
 *      a new timer session
 *
 *  A separate high-importance notification channel "Timer Done" is shown
 *  when the alarm fires. It contains a "Dismiss" action that broadcasts
 *  ACTION_STOP_ALARM back to this service.
 *  ─────────────────────────────────────────────────────────
 */
public class StudyTimerService extends Service {

    // ── Notification channels ─────────────────────────────────
    private static final String CHANNEL_TIMER   = "study_timer_channel";
    private static final String CHANNEL_ALARM   = "study_alarm_channel";

    // ── Notification IDs ──────────────────────────────────────
    private static final int NOTIF_TIMER = 1001;
    private static final int NOTIF_ALARM = 1002;

    // ── Intent actions ────────────────────────────────────────
    /** Sent by TimerAlarmReceiver when the student taps Dismiss */
    public static final String ACTION_STOP_ALARM = "com.example.ilearned.STOP_ALARM";

    // ── State ─────────────────────────────────────────────────
    private TimerEngine engine;
    private MediaPlayer alarmPlayer;   // non-null while alarm is ringing

    // ── Observers ─────────────────────────────────────────────
    private final Observer<Long> remainingObserver = this::updateTimerNotification;

    private final Observer<TimerEngine.State> stateObserver = state -> {
        if (state == TimerEngine.State.FINISHED) {
            triggerAlarm();
        } else if (state == TimerEngine.State.RUNNING || state == TimerEngine.State.IDLE) {
            // New session started — stop any lingering alarm
            stopAlarm();
        }
    };

    // ─────────────────────────────────────────────────────────
    //  Service lifecycle
    // ─────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        engine = TimerEngine.get(this);
        startForeground(NOTIF_TIMER,
                buildTimerNotification(engine.remainingMs().getValue()));
        engine.remainingMs().observeForever(remainingObserver);
        engine.state().observeForever(stateObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle the Dismiss action sent from the alarm notification
        if (intent != null && ACTION_STOP_ALARM.equals(intent.getAction())) {
            stopAlarm();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (engine != null) {
            engine.remainingMs().removeObserver(remainingObserver);
            engine.state().removeObserver(stateObserver);
        }
        stopAlarm();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─────────────────────────────────────────────────────────
    //  Notification channels
    // ─────────────────────────────────────────────────────────

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            // Countdown channel — silent, low importance (just shows remaining time)
            if (nm.getNotificationChannel(CHANNEL_TIMER) == null) {
                NotificationChannel timerCh = new NotificationChannel(
                        CHANNEL_TIMER,
                        "Study Timer",
                        NotificationManager.IMPORTANCE_LOW);
                timerCh.setDescription("Shows the remaining focus time");
                timerCh.setSound(null, null);
                nm.createNotificationChannel(timerCh);
            }

            // Alarm channel — high importance so the heads-up notification appears
            if (nm.getNotificationChannel(CHANNEL_ALARM) == null) {
                NotificationChannel alarmCh = new NotificationChannel(
                        CHANNEL_ALARM,
                        "Timer Done",
                        NotificationManager.IMPORTANCE_HIGH);
                alarmCh.setDescription("Alerts you when your focus session ends");
                // Sound handled by MediaPlayer, not the channel
                alarmCh.setSound(null, null);
                alarmCh.enableVibration(false);
                nm.createNotificationChannel(alarmCh);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Countdown notification (low-importance, ongoing)
    // ─────────────────────────────────────────────────────────

    private Notification buildTimerNotification(Long remainingMs) {
        long ms  = remainingMs == null ? 0L : remainingMs;
        long min = ms / 60000;
        long sec = (ms / 1000) % 60;
        String time = String.format(Locale.getDefault(), "%02d:%02d", min, sec);
        return new NotificationCompat.Builder(this, CHANNEL_TIMER)
                .setContentTitle("Focus session")
                .setContentText(time + " remaining")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateTimerNotification(Long ms) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_TIMER, buildTimerNotification(ms));
    }

    // ─────────────────────────────────────────────────────────
    //  Alarm — triggered when session finishes
    // ─────────────────────────────────────────────────────────

    /**
     * Start the alarm sound and show a heads-up "Session Complete!" notification
     * with a Dismiss button that stops the sound.
     *
     * Uses STREAM_ALARM so the sound respects the device's alarm volume level.
     * Loops continuously until stopAlarm() is called.
     */
    private void triggerAlarm() {
        // ── Vibrate first ──
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vib != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 500, 300, 500}, -1)); // two pulses, no repeat
            } else {
                //noinspection deprecation
                vib.vibrate(new long[]{0, 500, 300, 500}, -1);
            }
        }

        // ── Start looping alarm sound on STREAM_ALARM ──
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                // Fallback to notification sound if no alarm is set
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

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

            alarmPlayer.setDataSource(getApplicationContext(), alarmUri);
            alarmPlayer.setLooping(true);   // keep ringing until dismissed
            alarmPlayer.prepare();
            alarmPlayer.start();

        } catch (Exception e) {
            // If MediaPlayer fails for any reason, at least vibrate
            if (alarmPlayer != null) {
                alarmPlayer.release();
                alarmPlayer = null;
            }
        }

        // ── Show heads-up "Session Complete!" notification with Dismiss button ──
        showAlarmNotification();
    }

    /**
     * Shows a high-importance notification that appears as a heads-up banner.
     * Contains a "Dismiss" action that broadcasts ACTION_STOP_ALARM.
     */
    private void showAlarmNotification() {
        // Build the Dismiss PendingIntent
        Intent dismissIntent = new Intent(this, TimerAlarmReceiver.class);
        dismissIntent.setAction(TimerAlarmReceiver.ACTION_DISMISS_ALARM);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent dismissPi = PendingIntent.getBroadcast(
                this, 0, dismissIntent, flags);

        Notification alarmNotif = new NotificationCompat.Builder(this, CHANNEL_ALARM)
                .setContentTitle("🎯 Session Complete!")
                .setContentText("Great work! Your focus session has ended.")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setOngoing(true)                       // can't swipe away
                .addAction(R.drawable.baseline_close_24,
                        "Dismiss", dismissPi)           // tap to stop sound
                .build();

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ALARM, alarmNotif);
    }

    /**
     * Stop the alarm sound and cancel the alarm notification.
     * Called when:
     *  - Student taps "Dismiss" on the notification
     *  - A new timer session starts
     *  - The service is destroyed
     */
    public void stopAlarm() {
        if (alarmPlayer != null) {
            if (alarmPlayer.isPlaying()) alarmPlayer.stop();
            alarmPlayer.release();
            alarmPlayer = null;
        }

        // Cancel the alarm heads-up notification
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ALARM);
    }
}