package com.phonepilot.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.TaskEntity

class MainActivity : Activity() {

    private var isRecording = false
    private var taskList: List<String> = emptyList()
    private lateinit var db: TaskDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TaskDatabaseHelper.getInstance(this)

        try {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Log.e("MainActivity", "Cannot open accessibility settings", e)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "PhonePilot AI"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }

        val btnTasks = Button(this).apply {
            text = "قائمة المهام"
            textSize = 18f
            setOnClickListener { showTasksList() }
        }

        val btnRecord = Button(this).apply {
            text = if (isRecording) "إيقاف التسجيل" else "ابدأ التسجيل"
            textSize = 18f
            setOnClickListener {
                isRecording = !isRecording
                text = if (isRecording) "إيقاف التسجيل" else "ابدأ التسجيل"
                Toast.makeText(this@MainActivity, if (isRecording) "بدأت تسجيل المهمة" else "تم إيقاف التسجيل", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(title)
        layout.addView(btnTasks)
        layout.addView(btnRecord)

        setContentView(layout)
    }

    private fun showTasksList() {
        val tasks: List<TaskEntity> = db.getAllTasks()
        taskList = if (tasks.isEmpty()) {
            listOf("No tasks yet")
        } else {
            tasks.map { it.name }
        }

        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, taskList)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = taskList[position]
            Toast.makeText(this, "Selected: $selected", Toast.LENGTH_SHORT).show()
        }

        setContentView(listView)
    }
}
