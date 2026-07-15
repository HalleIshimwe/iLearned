package com.example.ilearned;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class QuizSessionManager {

    // ── Singleton ─────────────────────────────────────────────

    private static QuizSessionManager instance;

    public static QuizSessionManager get() {
        if (instance == null) instance = new QuizSessionManager();
        return instance;
    }

    private QuizSessionManager() {}

    // ── Session state ─────────────────────────────────────────

    public enum SessionStatus {
        NONE,        // no attempt yet — show "Access Quiz →"
        IN_PROGRESS, // started but not finished — show "In Progress →"
        COMPLETED    // reached result screen — show "Completed →"
    }

    public static class QuizSession {
        public List<QuizQuestion> questions;   // with answers filled in
        public int                lastIndex;   // question student was on
        public SessionStatus      status;
        public int                correctCount; // MCQ correct answers at completion

        QuizSession(List<QuizQuestion> questions, int lastIndex,
                    SessionStatus status, int correctCount) {
            this.questions    = questions;
            this.lastIndex    = lastIndex;
            this.status       = status;
            this.correctCount = correctCount;
        }
    }

    private final Map<String, QuizSession> sessions = new HashMap<>();

    // ── Public API ────────────────────────────────────────────

    /**
     * Called by QuizScreenActivity after every answer and on back press.
     * Saves the current state of the questions list and current index.
     */
    public void saveProgress(String quizId,
                             List<QuizQuestion> questions,
                             int currentIndex,
                             int correctCount) {
        if (quizId == null) return;

        // Preserve COMPLETED status if already set — don't downgrade
        SessionStatus existing = getStatus(quizId);
        SessionStatus status   = existing == SessionStatus.COMPLETED
                ? SessionStatus.COMPLETED
                : SessionStatus.IN_PROGRESS;

        sessions.put(quizId, new QuizSession(questions, currentIndex, status, correctCount));
    }

    /**
     * Called by QuizResultActivity when the student reaches the result screen.
     * Marks the quiz as COMPLETED and locks the correct count.
     */
    public void markCompleted(String quizId,
                              List<QuizQuestion> questions,
                              int correctCount) {
        if (quizId == null) return;
        sessions.put(quizId,
                new QuizSession(questions, 0, SessionStatus.COMPLETED, correctCount));
    }

    /**
     * Called by QuizActivity's Retake button to fully wipe state for a quiz.
     */
    public void clearSession(String quizId) {
        if (quizId != null) sessions.remove(quizId);
    }

    /**
     * Returns the saved session for a quiz, or null if none exists.
     */
    public QuizSession getSession(String quizId) {
        if (quizId == null) return null;
        return sessions.get(quizId);
    }

    /**
     * Returns the status of a quiz (NONE / IN_PROGRESS / COMPLETED).
     */
    public SessionStatus getStatus(String quizId) {
        QuizSession s = getSession(quizId);
        return s != null ? s.status : SessionStatus.NONE;
    }

    /**
     * Returns true if any session exists for this quiz ID.
     */
    public boolean hasSession(String quizId) {
        return quizId != null && sessions.containsKey(quizId);
    }
}