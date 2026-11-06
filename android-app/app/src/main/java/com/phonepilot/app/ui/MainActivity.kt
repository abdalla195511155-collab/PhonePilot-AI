package com.phonepilot.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.recording.PhonePilotAccessibilityService
import com.phonepilot.app.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private val TAG = "MainActivity"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var btnRecord: Button? = null
    private var txtServiceStatus: TextView? = null
    private var txtRecordingStatus: TextView? = null
    private var txtEventCount: TextView? = null
    private var txtDroppedCount: TextView? = null
    private var layout: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.phonepilot.app.database.TaskDatabaseHelper.getInstance(this).markInterruptedRecordings()

        buildUI()
        observeRecordingState()
        observeServiceStatus()

        if (!PhonePilotAccessibilityService.isServiceEnabled(this)) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open accessibility settings", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusText()
        updateRecordingStatusText()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildUI() {
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "PhonePilot AI"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        layout?.addView(title)

        txtServiceStatus = TextView(this).apply {
            text = "حالة الخدمة: جاري التحقق..."
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        layout?.addView(txtServiceStatus)

        txtRecordingStatus = TextView(this).apply {
            text = "حالة التسجيل: خامل"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        layout?.addView(txtRecordingStatus)

        txtEventCount = TextView(this).apply {
            text = "عدد الأحداث: 0"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        layout?.addView(txtEventCount)

        txtDroppedCount = TextView(this).apply {
            text = "الأحداث المفقودة: 0"
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }
        layout?.addView(txtDroppedCount)

        btnRecord = Button(this).apply {
            text = "بدء التسجيل"
            textSize = 16f
            setPadding(32, 16, 32, 16)
            isEnabled = false
        }
        btnRecord?.setOnClickListener { onRecordClick() }
        layout?.addView(btnRecord)

        val btnTasks = Button(this).apply {
            text = "عرض المهام"
            textSize = 16f
            setPadding(32, 16, 32, 16)
        }
        btnTasks.setOnClickListener { showTasksList() }
        layout?.addView(btnTasks)

        val btnOpenSettings = Button(this).apply {
            text = "فتح إعدادات إمكانية الوصول"
            textSize = 14f
            setPadding(32, 16, 32, 16)
        }
        btnOpenSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "لا يمكن فتح الإعدادات", Toast.LENGTH_SHORT).show()
            }
        }
        layout?.addView(btnOpenSettings)

        setContentView(layout)
    }

    private fun onRecordClick() {
        if (RecordingRepository.isRecording()) {
            scope.launch {
                val success = RecordingRepository.stopRecording()
                if (success) {
                    Toast.makeText(this@MainActivity, "تم إيقاف التسجيل", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "فشل إيقاف التسجيل", Toast.LENGTH_SHORT).show()
                }
                updateRecordingStatusText()
            }
        } else {
            val serviceEnabled = PhonePilotAccessibilityService.isServiceEnabled(this)
            if (!serviceEnabled) {
                Toast.makeText(this, "خدمة إمكانية الوصول غير مفعلة. يرجى تفعيلها أولاً.", Toast.LENGTH_LONG).show()
                return
            }
            if (!RecordingRepository.isServiceConnected()) {
                Toast.makeText(this, "الخدمة غير متصلة حالياً. يرجى المحاولة بعد لحظات.", Toast.LENGTH_LONG).show()
                return
            }
            val started = RecordingRepository.startRecording(createTaskId(), 1)
            if (started) {
                Toast.makeText(this, "بدأ التسجيل", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "فشل بدء التسجيل", Toast.LENGTH_SHORT).show()
            }
            updateRecordingStatusText()
        }
    }

    private fun createTaskId(): Long {
        return System.currentTimeMillis()
    }

    private fun observeRecordingState() {
        scope.launch {
            RecordingRepository.recordingState.collect { state ->
                updateRecordingStatusText()
                updateEventCountText()
            }
        }
        scope.launch {
            RecordingRepository.eventCount.collect { count ->
                txtEventCount?.text = "عدد الأحداث: $count"
            }
        }
        scope.launch {
            RecordingRepository.droppedCount.collect { count ->
                txtDroppedCount?.text = "الأحداث المفقودة: $count"
            }
        }
    }

    private fun observeServiceStatus() {
        scope.launch {
            RecordingRepository.serviceConnected.collect { connected ->
                updateServiceStatusText()
            }
        }
    }

    private fun updateServiceStatusText() {
        val enabled = PhonePilotAccessibilityService.isServiceEnabled(this)
        val connected = RecordingRepository.isServiceConnected()
        runOnUiThread {
            if (!enabled) {
                txtServiceStatus?.text = "حالة الخدمة: غير مفعلة ⚠️"
                btnRecord?.isEnabled = false
            } else if (!connected) {
                txtServiceStatus?.text = "حالة الخدمة: مفعلة ولكن غير متصلة"
                btnRecord?.isEnabled = false
            } else {
                txtServiceStatus?.text = "حالة الخدمة: مفعلة ومتصلة ✅"
                btnRecord?.isEnabled = true
            }
            txtDroppedCount?.text = "الأحداث المفقودة: ${RecordingRepository.getDroppedCount()}"
        }
    }

    private fun updateRecordingStatusText() {
        runOnUiThread {
            val state = RecordingRepository.recordingState.value
            when (state) {
                is RecordingState.Idle -> {
                    txtRecordingStatus?.text = "حالة التسجيل: خامل"
                    btnRecord?.text = "بدء التسجيل"
                }
                is RecordingState.Recording -> {
                    txtRecordingStatus?.text = "حالة التسجيل: جارٍ (${state.eventCount} حدث)"
                    btnRecord?.text = "إيقاف التسجيل"
                }
                is RecordingState.Completed -> {
                    txtRecordingStatus?.text = "حالة التسجيل: مكتمل"
                    btnRecord?.text = "بدء التسجيل"
                }
                is RecordingState.Interrupted -> {
                    txtRecordingStatus?.text = "حالة التسجيل: مقطوع (${state.eventCount} حدث)"
                    btnRecord?.text = "بدء التسجيل"
                }
            }
        }
    }

    private fun updateEventCountText() {
        txtEventCount?.text = "عدد الأحداث: ${RecordingRepository.getEventCount()}"
    }

    private fun showTasksList() {
        val tasks = com.phonepilot.app.database.TaskDatabaseHelper.getInstance(this).getAllTasks()
        val taskList = if (tasks.isEmpty()) {
            listOf("لا توجد مهام")
        } else {
            tasks.map { "${it.id}: ${it.name}" }
        }
        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, taskList)
        listView.adapter = adapter
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("المهام")
            .setView(listView)
            .setPositiveButton("إغلاق", null)
            .create()
        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = tasks.getOrNull(position)
            if (selected != null) {
                Toast.makeText(this, "المهمة: ${selected.name}", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }
}
