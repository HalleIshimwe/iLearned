package com.example.ilearned.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * TimerAlarmReceiver
 *
 * Receives the "Dismiss" action broadcast from the timer-done notification.
 * Tells StudyTimerService to stop the alarm sound.
 *
 * Register in AndroidManifest.xml inside <application>:
 *
 *   <receiver
 *       android:name=".timer.TimerAlarmReceiver"
 *       android:exported="false"/>
 */
public class TimerAlarmReceiver extends BroadcastReceiver {

    /** Action sent when the student taps "Dismiss" on the alarm notification */
    public static final String ACTION_DISMISS_ALARM = "com.example.ilearned.DISMISS_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_DISMISS_ALARM.equals(intent.getAction())) {
            // Tell the service to stop the alarm sound
            Intent stopIntent = new Intent(context, StudyTimerService.class);
            stopIntent.setAction(StudyTimerService.ACTION_STOP_ALARM);
            context.startService(stopIntent);
        }
    }
}