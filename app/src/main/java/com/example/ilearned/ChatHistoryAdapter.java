package com.example.ilearned;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(FirestoreHelper.ChatSession session);
    }

    private List<FirestoreHelper.ChatSession> displayedSessions = new ArrayList<>();
    private String                             activeSessionId   = null;
    private final OnSessionClickListener       listener;

    public ChatHistoryAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    public void setSessions(List<FirestoreHelper.ChatSession> sessions) {
        displayedSessions = new ArrayList<>(sessions);
        notifyDataSetChanged();
    }

    public void setActiveSessionId(String sessionId) {
        this.activeSessionId = sessionId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_session, parent, false);
        return new SessionViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(displayedSessions.get(position), activeSessionId, listener);
    }

    @Override
    public int getItemCount() {
        return displayedSessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {

        private final View     itemRoot;
        private final TextView textTitle;
        private final TextView textDate;
        private final TextView textSnippet;

        SessionViewHolder(View v) {
            super(v);
            itemRoot    = v;
            textTitle   = v.findViewById(R.id.textSessionTitle);
            textDate    = v.findViewById(R.id.textSessionDate);
            textSnippet = v.findViewById(R.id.textSessionSnippet);
        }

        void bind(FirestoreHelper.ChatSession session,
                  String activeSessionId,
                  OnSessionClickListener listener) {

            textTitle.setText(session.title != null ? session.title : "Chat");

            if (session.updatedAt != null) {
                String date = (String) android.text.format.DateFormat
                        .format("dd MMM, HH:mm", session.updatedAt);
                textDate.setText(date);
                textDate.setVisibility(View.VISIBLE);
            } else {
                textDate.setVisibility(View.GONE);
            }

            // Show snippet only when match came from message content
            if (session.snippet != null && !session.snippet.isEmpty()) {
                textSnippet.setText(session.snippet);
                textSnippet.setVisibility(View.VISIBLE);
            } else {
                textSnippet.setVisibility(View.GONE);
            }

            boolean isActive = session.id != null && session.id.equals(activeSessionId);
            itemRoot.setSelected(isActive);
            itemRoot.setOnClickListener(v -> listener.onSessionClick(session));
        }
    }
}