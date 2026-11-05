package com.phonepilot.app

import android.app.Activity
import android.content.Intent
import os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

public class MainActivity extends Activity {

    private static final String[] TASK_DUMMIES = {"Task 1", "Task 2", "Task 3"};
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Try to open accessibility settings directly
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Log.e("MainActivity", "Cannot open accessibility settings", e);
        }

        // Build UI linearly
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("PhonePilot AI");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 24);

        // Tasks list button
        Button btnTasks = new Button(this);
        btnTasks.setText("قائمة المهام");
        btnTasks.setTextSize(18);
        btnTasks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTasksList();
            }
        });

        // Record button - toggles between start/stop
        Button btnRecord = new Button(this);
        btnRecord.setText(isRecording ? "إيقاف التسجيل" : "ابدأ التسجيل");
        btnRecord.setTextSize(18);
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRecording = !isRecording;
                btnRecord.setText(isRecording ? "إيقاف التسجيل" : "ابدأ التسجيل");
                Toast.makeText(MainActivity.this, isRecording ? "بدأت تسجيل المهمة" : "تم إيقاف التسجيل", Toast.LENGTH_SHORT).show();
            }
        });

        layout.addView(title);
        layout.addView(btnTasks);
        layout.addView(btnRecord);

        setContentView(layout);
    }

    private void showTasksList() {
        // Simple list dialog
        taskList = new java.util.ArrayList<>();
        for (String task : TASK_DUMMIES) {
            taskList.add(task);
        }

        // Create list view
        android.widget.ListView listView = new android.widget.ListView(this);

        // Custom adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                taskList
        ) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                if (view instanceof android.widget.TextView) {
                    ((android.widget.TextView) view).setTextColor(0xFF6200EE);
                }
                return view;
            }
        };

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String selectedTask = taskList.get(position);
                Toast.makeText(MainActivity.this, "Selected: " + selectedTask, Toast.LENGTH_SHORT).show();
            }
        });

        // Set content to the list view
        setContentView(listView);
    }
}
