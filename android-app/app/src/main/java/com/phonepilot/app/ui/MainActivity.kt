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
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private val TAG = "MainActivity"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var btnRecord: Button? = null
    private var txtServiceStatus: TextView? = null
    private var txtRecordingStatus: TextView? = null
    private var txtEventCount: TextView? = null
    private var layout: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordingRepository.init(this)

        // Mark any recordings left in RECORDING state as INTERRUPTED
        com.phonepilot.app.database.TaskDatabaseHelper.getInstance(this).markInterruptedRecordings()

        try {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open accessibility settings", e)
        }

        buildUI()
        observeRecordingState()
    }

    private fun buildUI() {
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "PhonePilot AI"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }

        txtServiceStatus = TextView(this).apply {
            text = "حالة الخدمة: غير مفعلة"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }

        txtRecordingStatus = TextView(this).apply {
            text = "حالة التسجيل: غير نشط"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }

        txtEventCount = TextView(this).apply {
            text = "الأحداث المسجلة: 0"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }

        val btnRecord = Button(this).apply {
            text = "ابدأ التسجيل"
            textSize = 18f
            setOnClickListener {
                if (RecordingRepository.isRecording()) {
                    RecordingRepository.stopRecording()
                } else {
                    // Verify service is connected before allowing start
                    val state = RecordingRepository.recordingState.value
                    if (state !is RecordingState.Recording) {
                        // Try to start recording - the service will validate
                        RecordingRepository.startRecording(createTaskId(), 1)
                    }
                }
            }
        }
        this.btnRecord = btnRecord

        val btnTasks = Button(this).apply {
            text = "قائمة المهام"
            textSize = 18f
            setOnClickListener { showTasksList() }
        }

        layout!!.addView(title)
        layout!!.addView(txtServiceStatus!!)
        layout!!.addView(txtRecordingStatus!!)
        layout!!.addView(txtEventCount!!)
        layout!!.addView(btnRecord)
        layout!!.addView(btnTasks)

        setContentView(layout)
    }

    private fun createTaskId(): Long {
        // Generate a task ID based on current time
        return System.currentTimeMillis()
    }

    private fun observeRecordingState() {
        RecordingRepository.recordingState.collect { state ->
            scope.launch {
                when (state) {
                    is RecordingState.Recording -> {
                        btnRecord?.text = "إيقاف التسجيل"
                        txtRecordingStatus?.text = "حالة التسجيل: يسجل (المهمة ${state.taskId})"
                        txtEventCount?.text = "الأحداث المسجلة: ${state.eventCount}"
                    }
                    RecordingState.Completed -> {
                        btnRecord?.text = "ابدأ التسجيل"
                        txtRecordingStatus?.text = "حالة التسجيل: مكتمل"
                    }
                    is RecordingState.Interrupted -> {
                        btnRecord?.text = "ابدأ التسجيل"
                        txtRecordingStatus?.text = "حالة التسجيل: مقطوع (${state.eventCount} حدث)"
                    }
                    RecordingState.Idle -> {
                        btnRecord?.text = "ابدأ التسجيل"
                        txtRecordingStatus?.text = "حالة التسجيل: غير نشط"
                        txtEventCount?.text = "الأحداث المسجلة: 0"
                    }
                }
            }
        }
    }

    private fun showTasksList() {
        val tasks = com.phonepilot.app.database.TaskDatabaseHelper.getInstance(this).getAllTasks()
        val taskList = if (tasks.isEmpty()) {
            listOf("لا توجد مهام مسجلة")
        } else {
            tasks.map { it.name }
        }

        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, taskList)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            if (tasks.isNotEmpty()) {
                val selected = tasks[position]
                Toast.makeText(this, "المهمة المختارة: ${selected.name}", Toast.LENGTH_SHORT).show()
            }
        }

        setContentView(listView)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
