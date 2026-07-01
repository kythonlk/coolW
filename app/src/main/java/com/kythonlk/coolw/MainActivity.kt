package com.kythonlk.coolw

import android.Manifest
import android.media.AudioManager
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.kythonlk.coolw.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: TodoDatabaseHelper
    private val activityScope = CoroutineScope(Dispatchers.Main)

    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.contains(HealthConnectHelper.stepsReadPermission)) {
            StepCounterService.start(this)
            refreshStepsUI()
            Toast.makeText(this, "Health Connect steps enabled", Toast.LENGTH_SHORT).show()
        }
    }

    // Broadcast receiver to update UI when alarms trigger or music updates
    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTodoList()
            updateMusicInfoText()
            refreshStepsUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = TodoDatabaseHelper(this)

        // 1. Render Header Logo in Dot-Matrix style using the JNI string!
        val jniTitle = try {
            stringFromJNI()
        } catch (e: Exception) {
            "COOLW"
        }
        val logoBitmap = DotMatrixRenderer.renderText(
            text = jniTitle.substringBefore(" ").uppercase(Locale.getDefault()), // grab first word
            activeColor = Color.WHITE,
            inactiveColor = Color.parseColor("#121212"),
            dotRadius = 4f,
            dotSpacing = 12f,
            charSpacing = 8f,
            drawInactive = false
        )
        binding.headerLogo.setImageBitmap(logoBitmap)

        // 2. Start background sync services
        requestPermissionsIfNeeded()
        if (hasStepPermissions()) {
            StepCounterService.start(this)
        }
        if (hasBluetoothPermissions()) {
            BluetoothHeadphoneService.start(this)
        }

        // 3. UI Buttons Actions
        binding.btnImportJson.setOnClickListener {
            importJsonTodos()
        }

        binding.btnAddManual.setOnClickListener {
            showAddManualDialog()
        }

        binding.btnSyncSteps.setOnClickListener {
            requestHealthConnectPermission()
        }

        binding.btnBluetoothPermission.setOnClickListener {
            requestBluetoothPermission()
        }

        binding.btnPlayMusic.setOnClickListener {
            toggleMusicPlayback()
        }

        binding.btnToggleNotifListener.setOnClickListener {
            toggleNotificationListenerPermission()
        }

        binding.btnGrantAlarmPermission.setOnClickListener {
            checkExactAlarmPermission()
        }

        // 4. Initial Displays
        refreshStepsUI()
        updateMusicInfoText()
        refreshTodoList()
        updatePermissionButtonsState()

        // 5. Register receivers
        val filter = IntentFilter().apply {
            addAction("com.kythonlk.coolw.TODO_REFRESH")
            addAction("com.kythonlk.coolw.ACTION_MUSIC_UPDATE")
            addAction(NothingStepsWidget.ACTION_STEP_UPDATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiUpdateReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(uiUpdateReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStepsUI()
        updateMusicInfoText()
        refreshTodoList()
        updatePermissionButtonsState()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(uiUpdateReceiver)
    }

    // --- TODO PLANNERS & JSON IMPORT ---

    private fun refreshTodoList() {
        binding.todoListContainer.removeAllViews()
        val todos = dbHelper.getAllTodos()

        if (todos.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No active tasks. Add manually or import JSON above."
                setTextColor(Color.parseColor("#55FFFFFF"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 24)
            }
            binding.todoListContainer.addView(emptyTv)
            return
        }

        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

        for (todo in todos) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(6, 6, 6, 6)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 8)
                layoutParams = params
                
                // Styling mimicking dark theme card
                val backgroundDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.nothing_widget_background)
                background = backgroundDrawable
            }

            // Checkbox
            val checkbox = CheckBox(this).apply {
                isChecked = todo.isCompleted
                buttonTintList = ContextCompat.getColorStateList(this@MainActivity, android.R.color.white)
                setOnCheckedChangeListener { _, isChecked ->
                    dbHelper.updateCompletion(todo.id, isChecked)
                    refreshTodoList()
                }
            }
            itemLayout.addView(checkbox)

            // Text Layout (Title + Time)
            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(10, 0, 10, 0)
                }
            }

            val titleTv = TextView(this).apply {
                text = todo.title
                setTextColor(if (todo.isCompleted) Color.GRAY else Color.WHITE)
                textSize = 14f
                paintFlags = if (todo.isCompleted) paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            textLayout.addView(titleTv)

            val timeTv = TextView(this).apply {
                text = sdf.format(Date(todo.timeInMillis))
                setTextColor(Color.parseColor("#88FFFFFF"))
                textSize = 10f
            }
            textLayout.addView(timeTv)
            
            if (todo.description.isNotEmpty()) {
                val descTv = TextView(this).apply {
                    text = todo.description
                    setTextColor(Color.parseColor("#55FFFFFF"))
                    textSize = 11f
                    setPadding(0, 2, 0, 0)
                }
                textLayout.addView(descTv)
            }

            itemLayout.addView(textLayout)

            // Alarm status bell
            if (todo.alarmScheduled && todo.timeInMillis > System.currentTimeMillis()) {
                val bellTv = TextView(this).apply {
                    text = "🔔"
                    textSize = 12f
                    setPadding(8, 8, 8, 8)
                }
                itemLayout.addView(bellTv)
            }

            // Delete action button
            val deleteBtn = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor("#FFFF3B30")) // Nothing red
                textSize = 16f
                setPadding(12, 12, 12, 12)
                isClickable = true
                focusable = View.FOCUSABLE
                setOnClickListener {
                    cancelTodoAlarm(todo.id)
                    dbHelper.deleteTodo(todo.id)
                    refreshTodoList()
                    Toast.makeText(this@MainActivity, "Task deleted", Toast.LENGTH_SHORT).show()
                }
            }
            itemLayout.addView(deleteBtn)

            binding.todoListContainer.addView(itemLayout)
        }
    }

    private fun importJsonTodos() {
        val jsonStr = binding.etJsonImport.text.toString().trim()
        if (jsonStr.isEmpty()) {
            Toast.makeText(this, "Please paste a JSON string first!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val jsonArray = JSONArray(jsonStr)
            var count = 0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val desc = obj.optString("description", obj.optString("desc", ""))
                val timeStr = obj.getString("time")
                
                val timeInMillis = parseDateTime(timeStr)
                if (timeInMillis == 0L) {
                    Toast.makeText(this, "Could not parse time for: $title", Toast.LENGTH_SHORT).show()
                    continue
                }

                // Add to database
                val todoId = dbHelper.addTodo(title, desc, timeInMillis, true)
                
                // Schedule alarm
                val todoItem = TodoItem(todoId.toInt(), title, desc, timeInMillis, false, true)
                scheduleTodoAlarm(todoItem)
                count++
            }
            binding.etJsonImport.text.clear()
            refreshTodoList()
            triggerWidgetUpdate()
            Toast.makeText(this, "Successfully imported $count tasks!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid JSON format: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseDateTime(timeStr: String): Long {
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                return sdf.parse(timeStr)?.time ?: 0L
            } catch (e: Exception) {
                // Try next
            }
        }
        return timeStr.toLongOrNull() ?: 0L
    }

    private fun showAddManualDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_todo, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.dialog_et_title)
        val etDesc = dialogView.findViewById<EditText>(R.id.dialog_et_desc)
        val tvSelectedTime = dialogView.findViewById<TextView>(R.id.dialog_tv_selected_time)
        val btnSelectTime = dialogView.findViewById<TextView>(R.id.dialog_btn_select_time)

        val calendar = Calendar.getInstance()
        var selectedTimeInMillis = calendar.timeInMillis

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        tvSelectedTime.text = sdf.format(Date(selectedTimeInMillis))

        btnSelectTime.setOnClickListener {
            // Show Date picker then Time picker
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    
                    TimePickerDialog(
                        this,
                        { _, hourOfDay, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(Calendar.MINUTE, minute)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            
                            selectedTimeInMillis = calendar.timeInMillis
                            tvSelectedTime.text = sdf.format(Date(selectedTimeInMillis))
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Add New Task")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val todoId = dbHelper.addTodo(title, desc, selectedTimeInMillis, true)
                val todoItem = TodoItem(todoId.toInt(), title, desc, selectedTimeInMillis, false, true)
                scheduleTodoAlarm(todoItem)
                
                refreshTodoList()
                triggerWidgetUpdate()
                Toast.makeText(this, "Task Added & Alarm Scheduled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scheduleTodoAlarm(todo: TodoItem) {
        if (todo.timeInMillis < System.currentTimeMillis()) return

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("todo_id", todo.id)
            putExtra("todo_title", todo.title)
            putExtra("todo_desc", todo.description)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, todo.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, todo.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            // Fallback for newer Android versions
            alarmManager.set(AlarmManager.RTC_WAKEUP, todo.timeInMillis, pendingIntent)
        }
    }

    private fun cancelTodoAlarm(todoId: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, todoId, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    // --- STEPS ---

    private fun refreshStepsUI() {
        val prefs = CoolWPrefs.prefs(this)
        val steps = prefs.getInt(CoolWPrefs.STEPS_TODAY, 0)
        val source = prefs.getString(CoolWPrefs.STEPS_SOURCE, CoolWPrefs.SOURCE_NONE)
            ?: CoolWPrefs.SOURCE_NONE
        binding.tvStepCount.text = String.format("%,d Steps", steps)
        binding.tvStepSource.text = HealthConnectHelper.sourceLabel(source)
    }

    private fun requestHealthConnectPermission() {
        if (!HealthConnectHelper.isAvailable(this)) {
            Toast.makeText(
                this,
                "Install Health Connect to sync Google Fit steps",
                Toast.LENGTH_LONG
            ).show()
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                    }
                )
            } catch (_: Exception) {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                    }
                )
            }
            return
        }

        activityScope.launch {
            if (HealthConnectHelper.hasStepsPermission(this@MainActivity)) {
                StepCounterService.start(this@MainActivity)
                refreshStepsUI()
                Toast.makeText(this@MainActivity, "Steps already synced from Health Connect", Toast.LENGTH_SHORT).show()
            } else {
                healthPermissionLauncher.launch(setOf(HealthConnectHelper.stepsReadPermission))
            }
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    201
                )
                return
            }
        }

        BluetoothHeadphoneService.start(this)
        Toast.makeText(this, "Bluetooth headphone sync active", Toast.LENGTH_SHORT).show()
        updatePermissionButtonsState()
    }

    // --- MUSIC CONTROLLERS ---

    private fun updateMusicInfoText() {
        val prefs = getSharedPreferences("CoolWPrefs", Context.MODE_PRIVATE)
        val title = prefs.getString("music_title", "Nothing Track")
        val artist = prefs.getString("music_artist", "Nothing OS")
        val isPlaying = prefs.getBoolean("music_is_playing", false)
        
        binding.tvMusicInfo.text = "$title — $artist"
        binding.btnPlayMusic.text = if (isPlaying) "PAUSE" else "PLAY"
    }

    private fun toggleMusicPlayback() {
        // Toggle local state
        val prefs = getSharedPreferences("CoolWPrefs", Context.MODE_PRIVATE)
        val isPlaying = prefs.getBoolean("music_is_playing", false)
        val nextPlaying = !isPlaying
        prefs.edit().putBoolean("music_is_playing", nextPlaying).apply()
        
        // Send actual play/pause broadcast to system media sessions
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = System.currentTimeMillis()
        val downEvent = android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        val upEvent = android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)

        updateMusicInfoText()
        
        // Trigger music widget redraw
        val intent = Intent(this, NothingMusicWidget::class.java).apply {
            action = NothingMusicWidget.ACTION_MUSIC_UPDATE
        }
        sendBroadcast(intent)
    }

    // --- PERMISSIONS AND SETTINGS ---

    private fun requestPermissionsIfNeeded() {
        val permissions = ArrayList<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 200)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            StepCounterService.start(this)
            refreshStepsUI()
        }
        if (requestCode == 201 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            BluetoothHeadphoneService.start(this)
            updatePermissionButtonsState()
        }
    }

    private fun toggleNotificationListenerPermission() {
        val cn = ComponentName(this, MediaNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabled = flat != null && flat.contains(cn.flattenToString())
        
        if (isEnabled) {
            Toast.makeText(this, "Notification Listener is already enabled!", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Please enable 'CoolW Media Sync' listener service", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkExactAlarmPermission() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Exact alarm permission is already granted!", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } else {
            Toast.makeText(this, "Exact alarm permission is granted by default on this OS version.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePermissionButtonsState() {
        val cn = ComponentName(this, MediaNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isNotifEnabled = flat != null && flat.contains(cn.flattenToString())
        binding.btnToggleNotifListener.text = if (isNotifEnabled) "SYNCED" else "ENABLE"
        binding.btnToggleNotifListener.isEnabled = !isNotifEnabled

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        binding.btnGrantAlarmPermission.text = if (canScheduleExact) "GRANTED" else "GRANT"
        binding.btnGrantAlarmPermission.isEnabled = !canScheduleExact

        val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        binding.btnBluetoothPermission.text = if (hasBluetooth) "GRANTED" else "GRANT"
        binding.btnBluetoothPermission.isEnabled = !hasBluetooth

        activityScope.launch {
            val hasHealth = HealthConnectHelper.hasStepsPermission(this@MainActivity)
            binding.btnSyncSteps.text = if (hasHealth) "SYNCED" else "SYNC FIT"
        }
    }

    private fun triggerWidgetUpdate() {
        val intent = Intent(this, NothingClockWidget::class.java).apply {
            action = "com.kythonlk.coolw.UPDATE_ALL_WIDGETS"
        }
        sendBroadcast(intent)
    }

    private fun hasStepPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    external fun stringFromJNI(): String

    companion object {
        init {
            System.loadLibrary("coolw")
        }
    }
}