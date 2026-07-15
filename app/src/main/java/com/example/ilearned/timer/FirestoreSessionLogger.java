package com.example.ilearned.timer;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FirestoreSessionLogger implements TimerEngine.SessionListener {

    @Override
    public void onSessionCompleted(long durationMinutes) {
        writeProgress(durationMinutes, 1);
    }

    @Override
    public void onPartialStop(long elapsedMinutes) {
        writeProgress(elapsedMinutes, 0);
    }

    private void writeProgress(long minutes, int sessionDelta) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || minutes <= 0) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        DocumentReference doc = FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("dailyStats").document(today);

        Map<String, Object> updates = new HashMap<>();
        updates.put("date", today);
        updates.put("minutesStudied", FieldValue.increment(minutes));
        if (sessionDelta > 0) {
            updates.put("sessionsCompleted", FieldValue.increment(sessionDelta));
        }
        doc.set(updates, SetOptions.merge());
    }
}
