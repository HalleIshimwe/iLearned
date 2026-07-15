package com.example.ilearned;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


//Handles all Firestore read/write operations for chat history.

public class FirestoreHelper {

    private static final String TAG = "FirestoreHelper";

    private static final String COLLECTION_USERS    = "users";
    private static final String COLLECTION_SESSIONS = "sessions";
    private static final String COLLECTION_MESSAGES = "messages";

    private final FirebaseFirestore db;
    private final String            userId;

    private String  currentSessionId = null;
    private boolean titleHasBeenSet  = false;

    // ── Callbacks

    public interface MessagesCallback {
        void onSuccess(List<ChatMessage> messages);
        void onError(String error);
    }

    public interface SessionsCallback {
        void onSuccess(List<ChatSession> sessions);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }

    // ── Constructor

    public FirestoreHelper() {
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = (user != null) ? user.getUid() : null;
    }

    // ── Sessions

    /**
     * Create a new session with a temporary placeholder title.
     * The real title is set automatically when the first user message arrives.
     */
    public void createNewSession(String placeholderTitle, SimpleCallback callback) {
        if (userId == null) { callback.onError("Not logged in"); return; }

        titleHasBeenSet = false;

        Map<String, Object> session = new HashMap<>();
        session.put("title",     placeholderTitle);
        session.put("createdAt", new Date());
        session.put("updatedAt", new Date());

        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SESSIONS)
                .add(session)
                .addOnSuccessListener(ref -> {
                    currentSessionId = ref.getId();
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Load all sessions for the current user, most recent first.
     */
    public void loadSessions(SessionsCallback callback) {
        if (userId == null) { callback.onError("Not logged in"); return; }

        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SESSIONS)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<ChatSession> sessions = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        sessions.add(new ChatSession(
                                doc.getId(),
                                doc.getString("title"),
                                doc.getDate("updatedAt")));
                    }
                    callback.onSuccess(sessions);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void setCurrentSession(String sessionId) {
        this.currentSessionId = sessionId;
        this.titleHasBeenSet  = true; // old sessions already have a real title
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    // ── Messages ──────────────────────────────────────────────

    /**
     * Save a message.
     * If this is the first USER message in a brand new session,
     * the session title is automatically updated to that message text.
     */
    public void saveMessage(ChatMessage message) {
        if (userId == null || currentSessionId == null) return;

        // Auto-generate title from first user message
        if (!titleHasBeenSet && message.getType() == ChatMessage.TYPE_USER) {
            titleHasBeenSet = true;
            String autoTitle = message.getText().length() > 60
                    ? message.getText().substring(0, 60) + "…"
                    : message.getText();
            updateSessionTitle(autoTitle);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("text",      message.getText());
        data.put("type",      message.getType());
        data.put("timestamp", new Date());

        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SESSIONS)
                .document(currentSessionId)
                .collection(COLLECTION_MESSAGES)
                .add(data)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save message", e));

        updateSessionTimestamp();
    }

    /**
     * Load all messages for a session in chronological order.
     */
    public void loadMessages(String sessionId, MessagesCallback callback) {
        if (userId == null) { callback.onError("Not logged in"); return; }

        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SESSIONS)
                .document(sessionId)
                .collection(COLLECTION_MESSAGES)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<ChatMessage> messages = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String text = doc.getString("text");
                        Long   type = doc.getLong("type");
                        if (text != null && type != null) {
                            messages.add(new ChatMessage(text, type.intValue()));
                        }
                    }
                    callback.onSuccess(messages);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Searches across ALL sessions by both title AND message content.
    public void searchSessions(String query, SessionsCallback callback) {
        if (userId == null) { callback.onError("Not logged in"); return; }

        // Empty query = show all sessions normally
        if (query == null || query.trim().isEmpty()) {
            loadSessions(callback);
            return;
        }

        final String lowerQuery = query.toLowerCase().trim();

        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SESSIONS)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(sessionSnapshots -> {

                    // Collect all sessions
                    List<ChatSession> allSessions = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : sessionSnapshots) {
                        allSessions.add(new ChatSession(
                                doc.getId(),
                                doc.getString("title"),
                                doc.getDate("updatedAt")));
                    }

                    if (allSessions.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    // LinkedHashMap preserves insertion order and deduplicates by id
                    Map<String, ChatSession> resultMap = new LinkedHashMap<>();

                    // Pass 1: title matches (cheap — no extra Firestore reads)
                    for (ChatSession session : allSessions) {
                        if (session.title != null
                                && session.title.toLowerCase().contains(lowerQuery)) {
                            resultMap.put(session.id, session);
                        }
                    }

                    // Pass 2: message content search for sessions not yet matched
                    List<ChatSession> unmatched = new ArrayList<>();
                    for (ChatSession s : allSessions) {
                        if (!resultMap.containsKey(s.id)) unmatched.add(s);
                    }

                    if (unmatched.isEmpty()) {
                        // All matches came from titles — return immediately
                        callback.onSuccess(new ArrayList<>(resultMap.values()));
                        return;
                    }

                    AtomicInteger pending = new AtomicInteger(unmatched.size());

                    for (ChatSession session : unmatched) {
                        db.collection(COLLECTION_USERS)
                                .document(userId)
                                .collection(COLLECTION_SESSIONS)
                                .document(session.id)
                                .collection(COLLECTION_MESSAGES)
                                .get()
                                .addOnSuccessListener(msgSnapshots -> {
                                    for (QueryDocumentSnapshot msgDoc : msgSnapshots) {
                                        String text = msgDoc.getString("text");
                                        if (text != null
                                                && text.toLowerCase().contains(lowerQuery)) {
                                            // Build a readable snippet around the match
                                            String snippet = buildSnippet(text, lowerQuery);
                                            synchronized (resultMap) {
                                                resultMap.put(session.id,
                                                        new ChatSession(session.id,
                                                                session.title,
                                                                session.updatedAt,
                                                                snippet));
                                            }
                                            break; // one match per session is enough
                                        }
                                    }
                                    if (pending.decrementAndGet() == 0) {
                                        callback.onSuccess(new ArrayList<>(resultMap.values()));
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (pending.decrementAndGet() == 0) {
                                        callback.onSuccess(new ArrayList<>(resultMap.values()));
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ── Private helpers ───────────────────────────────────────

    private void updateSessionTitle(String title) {
        if (userId == null || currentSessionId == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put("title", title);
        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SESSIONS)
                .document(currentSessionId)
                .update(update)
                .addOnFailureListener(e -> Log.w(TAG, "Could not update title", e));
    }

    private void updateSessionTimestamp() {
        if (userId == null || currentSessionId == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put("updatedAt", new Date());
        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SESSIONS)
                .document(currentSessionId)
                .update(update)
                .addOnFailureListener(e -> Log.w(TAG, "Could not update timestamp", e));
    }

    /**
     * Extract ~60 characters of context around the matched keyword.
     * Adds "…" at edges when text is trimmed.
     */
    private String buildSnippet(String text, String query) {
        String lower      = text.toLowerCase();
        int    matchIndex = lower.indexOf(query);
        if (matchIndex < 0)
            return text.length() > 80 ? text.substring(0, 80) + "…" : text;

        int start   = Math.max(0, matchIndex - 25);
        int end     = Math.min(text.length(), matchIndex + query.length() + 35);
        String snip = text.substring(start, end).replace("\n", " ").trim();
        if (start > 0)          snip = "…" + snip;
        if (end < text.length()) snip = snip + "…";
        return snip;
    }

    // ── Data model ────────────────────────────────────────────

    public static class ChatSession {
        public final String id;
        public final String title;
        public final Date   updatedAt;
        public final String snippet; // non-null when match came from message content

        public ChatSession(String id, String title, Date updatedAt) {
            this(id, title, updatedAt, null);
        }

        public ChatSession(String id, String title, Date updatedAt, String snippet) {
            this.id        = id;
            this.title     = title;
            this.updatedAt = updatedAt;
            this.snippet   = snippet;
        }
    }
}