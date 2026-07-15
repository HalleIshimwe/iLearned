package com.example.ilearned;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ilearned.R;
import com.example.ilearned.Todo;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.TodoViewHolder> {

    public interface OnTodoCheckedListener {
        void onChecked(Todo todo, boolean isChecked);
    }

    private final Context context;
    private final List<Todo> items;
    private final OnTodoCheckedListener listener;

    public TodoAdapter(Context context, List<Todo> items, OnTodoCheckedListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TodoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_todo, parent, false);
        return new TodoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TodoViewHolder holder, int position) {
        Todo todo = items.get(position);

        holder.title.setText(todo.getTitle());
        holder.subtitle.setText(formatSubtitle(todo));

        Date due = todo.getDueDate() != null ? todo.getDueDate().toDate() : null;
        Priority p = computePriority(due);
        holder.priority.setText(p.label);
        holder.priority.setBackgroundResource(p.bgRes);
        holder.priority.setTextColor(ContextCompat.getColor(context, p.textColorRes));

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(todo.isCompleted());
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onChecked(todo, isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatSubtitle(Todo todo) {
        Date due = todo.getDueDate() != null ? todo.getDueDate().toDate() : null;
        String dueText = formatDue(due);
        String subject = todo.getSubject() != null ? todo.getSubject() : "";
        if (subject.isEmpty()) return "Due: " + dueText;
        return "Due: " + dueText + "  •  " + subject;
    }

    private String formatDue(Date date) {
        if (date == null) return "—";
        Calendar today = startOfDay(Calendar.getInstance());
        Calendar dueCal = startOfDay(toCalendar(date));
        long diffDays = TimeUnit.MILLISECONDS.toDays(
                dueCal.getTimeInMillis() - today.getTimeInMillis());
        if (diffDays == 0) return "Today";
        if (diffDays == 1) return "Tomorrow";
        if (diffDays == -1) return "Yesterday";
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
    }

    private Priority computePriority(Date date) {
        if (date == null) return Priority.LOW;
        Calendar today = startOfDay(Calendar.getInstance());
        Calendar dueCal = startOfDay(toCalendar(date));
        long diffDays = TimeUnit.MILLISECONDS.toDays(
                dueCal.getTimeInMillis() - today.getTimeInMillis());
        if (diffDays <= 1) return Priority.HIGH;
        if (diffDays <= 7) return Priority.MEDIUM;
        return Priority.LOW;
    }

    private Calendar toCalendar(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return c;
    }

    private Calendar startOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    enum Priority {
        HIGH("HIGH", R.drawable.bg_priority_high, R.color.priority_high_text),
        MEDIUM("MEDIUM", R.drawable.bg_priority_medium, R.color.priority_medium_text),
        LOW("LOW", R.drawable.bg_priority_low, R.color.priority_low_text);

        final String label;
        final int bgRes;
        final int textColorRes;

        Priority(String label, int bgRes, int textColorRes) {
            this.label = label;
            this.bgRes = bgRes;
            this.textColorRes = textColorRes;
        }
    }

    static class TodoViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView title;
        TextView subtitle;
        TextView priority;

        TodoViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.todoCheckbox);
            title = itemView.findViewById(R.id.todoTitle);
            subtitle = itemView.findViewById(R.id.todoSubtitle);
            priority = itemView.findViewById(R.id.todoPriority);
        }
    }
}
