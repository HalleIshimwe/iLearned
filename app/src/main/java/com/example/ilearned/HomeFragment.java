package com.example.ilearned;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.material.snackbar.Snackbar;
import com.example.ilearned.timer.TimerEngine;

public class HomeFragment extends Fragment {

    private TextView greetingText;
    private TextView subtitleText;
    private Button quickStudyButton;
    private RecyclerView todoRecycler;
    private View addToListButton;

    private final List<Todo> todos = new ArrayList<>();
    private TodoAdapter adapter;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration todosListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        greetingText = view.findViewById(R.id.greetingText);
        subtitleText = view.findViewById(R.id.subtitleText);
        quickStudyButton = view.findViewById(R.id.quickStudyButton);
        todoRecycler = view.findViewById(R.id.todoRecycler);
        addToListButton = view.findViewById(R.id.addToListButton);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new TodoAdapter(requireContext(), todos, this::onTodoChecked);
        todoRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        todoRecycler.setAdapter(adapter);
        todoRecycler.setNestedScrollingEnabled(false);

        renderGreeting(null);
        loadUserName();
        attachSwipeToDelete();

        quickStudyButton.setOnClickListener(v ->{
            Toast.makeText(getContext(), "Timer started", Toast.LENGTH_SHORT).show();
            TimerEngine.get(requireContext()).start();
                });


        addToListButton.setOnClickListener(v -> showAddTodoDialog());

        listenForTodos();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (todosListener != null) todosListener.remove();
    }

    private void loadUserName() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            renderGreeting(null);
            return;
        }
        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(this::renderGreetingFromUser)
                .addOnFailureListener(e -> renderGreeting(null));
    }

    private void renderGreetingFromUser(DocumentSnapshot snap) {
        String name = snap != null ? snap.getString("name") : null;
        renderGreeting(name);
    }

    private void renderGreeting(@Nullable String fullName) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String prefix;
        if (hour < 12) prefix = "Good morning";
        else if (hour < 17) prefix = "Good afternoon";
        else prefix = "Good evening";

        String firstName = (fullName == null || fullName.trim().isEmpty())
                ? "there"
                : fullName.trim().split("\\s+")[0];

        greetingText.setText(prefix + ",\n" + firstName + ".");
    }

    private CollectionReference todosRef() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return null;
        return db.collection("users").document(user.getUid()).collection("todos");
    }

    private void listenForTodos() {
        CollectionReference ref = todosRef();
        if (ref == null) return;
        todosListener = ref.orderBy("dueDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    todos.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Todo t = doc.toObject(Todo.class);
                        t.setId(doc.getId());
                        todos.add(t);
                    }
                    adapter.notifyDataSetChanged();
                    updateDeadlinesSubtitle();
                });
    }

    private void updateDeadlinesSubtitle() {
        long now = System.currentTimeMillis();
        long weekEnd = now + TimeUnit.DAYS.toMillis(7);
        int count = 0;
        for (Todo t : todos) {
            if (t.isCompleted() || t.getDueDate() == null) continue;
            long dueMs = t.getDueDate().toDate().getTime();
            if (dueMs >= now - TimeUnit.DAYS.toMillis(1) && dueMs <= weekEnd) count++;
        }
        String text = "Your mindful study space is ready. You have " + count
                + (count == 1 ? " deadline" : " deadlines") + " approaching this week.";
        subtitleText.setText(text);
    }

    private void onTodoChecked(Todo todo, boolean isChecked) {
        CollectionReference ref = todosRef();
        if (ref == null || todo.getId() == null) return;
        ref.document(todo.getId()).update("completed", isChecked);
    }
    private void attachSwipeToDelete() {
        final Drawable icon = ContextCompat.getDrawable(
                requireContext(), R.drawable.baseline_delete_24);
        final ColorDrawable background = new ColorDrawable(Color.parseColor("#E53935"));
        ItemTouchHelper.SimpleCallback callback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }
                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int pos = viewHolder.getBindingAdapterPosition();
                        if (pos < 0 || pos >= todos.size()) return;
                        Todo removed = todos.get(pos);
                        deleteWithUndo(removed);
                    }
                    @Override
                    public void onChildDraw(@NonNull Canvas c,
                                            @NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            float dX, float dY,
                                            int actionState, boolean isCurrentlyActive) {
                        View itemView = viewHolder.itemView;
                        int top = itemView.getTop();
                        int bottom = itemView.getBottom();
                        int right = itemView.getRight();
                        int height = bottom - top;
                        if (dX < 0) {
                            background.setBounds(right + (int) dX, top, right, bottom);
                            background.draw(c);
                            if (icon != null) {
                                int iconSize = (int) (height * 0.4f);
                                int margin = (int) (height * 0.3f);
                                int iconTop = top + (height - iconSize) / 2;
                                int iconRight = right - margin;
                                int iconLeft = iconRight - iconSize;
                                int iconBottom = iconTop + iconSize;
                                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                                icon.draw(c);
                            }
                        } else {
                            background.setBounds(0, 0, 0, 0);
                        }
                        super.onChildDraw(c, recyclerView, viewHolder,
                                dX, dY, actionState, isCurrentlyActive);
                    }
                };
        new ItemTouchHelper(callback).attachToRecyclerView(todoRecycler);
    }
    private void deleteWithUndo(Todo todo) {
        CollectionReference ref = todosRef();
        if (ref == null || todo.getId() == null) return;
        final String id = todo.getId();
        final Todo snapshot = todo;
        ref.document(id).delete();
        Snackbar sb = Snackbar.make(requireView(), "Task deleted", Snackbar.LENGTH_LONG);
        sb.setAction("Undo", v -> {
            CollectionReference r = todosRef();
            if (r == null) return;
            r.document(id).set(snapshot);
        });
        sb.show();
    }

    private void showAddTodoDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_todo, null);

        EditText titleInput = dialogView.findViewById(R.id.inputTitle);
        EditText subjectInput = dialogView.findViewById(R.id.inputSubject);
        Button dateButton = dialogView.findViewById(R.id.btnPickDate);

        Calendar selected = Calendar.getInstance();
        dateButton.setText(formatDateButton(selected.getTime()));

        dateButton.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(requireContext(),
                    (picker, year, month, day) -> {
                        selected.set(year, month, day);
                        dateButton.setText(formatDateButton(selected.getTime()));
                    },
                    selected.get(Calendar.YEAR),
                    selected.get(Calendar.MONTH),
                    selected.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Task")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String subject = subjectInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(getContext(),
                                "Title is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addTodo(title, subject, selected.getTime());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String formatDateButton(Date d) {
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(d);
    }

    private void addTodo(String title, String subject, Date dueDate) {
        CollectionReference ref = todosRef();
        if (ref == null) {
            Toast.makeText(getContext(), "Please sign in first", Toast.LENGTH_SHORT).show();
            return;
        }
        Todo t = new Todo(title, subject, new Timestamp(dueDate), false);
        ref.add(t).addOnFailureListener(e ->
                Toast.makeText(getContext(), "Failed to add task", Toast.LENGTH_SHORT).show());
    }
}
