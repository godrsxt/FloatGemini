package com.example.floatgemini

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        
        startForegroundService()

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_chat, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 100

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, layoutParams)

        setupInteractions()
    }

    private fun startForegroundService() {
        val channelId = "FloatGeminiChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "Floating AI Service", 
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating AI Active")
            .setContentText("Gemini is running in the background")
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupInteractions() {
        val closeBtn = floatingView.findViewById<Button>(R.id.btnClose)
        val dragHandle = floatingView.findViewById<LinearLayout>(R.id.dragHandle)
        val btnResize = floatingView.findViewById<TextView>(R.id.btnResize)
        val etInput = floatingView.findViewById<EditText>(R.id.etInput)
        val btnSend = floatingView.findViewById<Button>(R.id.btnSend)
        val tvChat = floatingView.findViewById<TextView>(R.id.tvChat)
        val rootLayout = floatingView.findViewById<LinearLayout>(R.id.rootLayout)

        closeBtn.setOnClickListener { stopSelf() }

        etInput.setOnTouchListener { _, _ ->
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            windowManager.updateViewLayout(floatingView, layoutParams)
            false
        }

        btnSend.setOnClickListener {
            val query = etInput.text.toString()
            if (query.isNotEmpty()) {
                tvChat.append("\n\nYou: $query")
                etInput.text.clear()
                tvChat.append("\nGemini: Thinking...")
                
                GeminiApiClient.generateResponse(query) { response ->
                    Handler(Looper.getMainLooper()).post {
                        val currentText = tvChat.text.toString()
                        tvChat.text = currentText.replace("Gemini: Thinking...", "Gemini: $response")
                    }
                }
                
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(floatingView, layoutParams)
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }

        var resInitW = 0
        var resInitH = 0
        var resInitX = 0f
        var resInitY = 0f

        btnResize.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resInitW = rootLayout.width
                    resInitH = rootLayout.height
                    resInitX = event.rawX
                    resInitY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.width = resInitW + (event.rawX - resInitX).toInt()
                    layoutParams.height = resInitH + (event.rawY - resInitY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
