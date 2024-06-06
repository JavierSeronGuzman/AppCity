package com.example.gps_kotlin.services

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

class SignalStrengthService(private val context: Context, private val onSignalStrengthChanged: (Int) -> Unit) {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var telephonyCallback: TelephonyCallback
    private lateinit var handlerThread: HandlerThread

    init {
        setupSignalStrengthListener()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setupSignalStrengthListener() {
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        handlerThread = HandlerThread("SignalStrengthThread").apply { start() }
        val handler = Handler(handlerThread.looper)
        val executor = Executor { command -> handler.post(command)}
        telephonyCallback = @RequiresApi(Build.VERSION_CODES.S)
        object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                val signalStrengthDbm = signalStrength.getCellSignalStrengths().minOfOrNull { it.dbm } ?: return
                onSignalStrengthChanged(signalStrengthDbm)
            }
        }
        telephonyManager.registerTelephonyCallback(executor, telephonyCallback)
    }

    fun cleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback)
        }
        handlerThread.quitSafely()
    }
}