package com.example.ilearned;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

public class Todo {
    private String id;
    private String title;
    private String subject;
    private Timestamp dueDate;
    private boolean completed;

    public Todo() {}

    public Todo(String title, String subject, Timestamp dueDate, boolean completed) {
        this.title = title;
        this.subject = subject;
        this.dueDate = dueDate;
        this.completed = completed;
    }

    @Exclude
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public Timestamp getDueDate() { return dueDate; }
    public void setDueDate(Timestamp dueDate) { this.dueDate = dueDate; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
