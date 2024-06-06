package com.example.gps_kotlin

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.gps_kotlin.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.HandlerThread
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val CODIGO_PERMISO_SEGUNDO_PLANO = 100
    private var isPermisos = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var telephonyCallback: TelephonyCallback
    private lateinit var handlerThread: HandlerThread
    private var globalSignalStrengthDbm: Double = 0.0

    private val client = OkHttpClient()

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verificarPermisos()
        val isMobileDataEnabled = isMobileDataEnabled(this)
        Toast.makeText(this, "Datos móviles habilitados: $isMobileDataEnabled", Toast.LENGTH_LONG)
            .show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            handlerThread = HandlerThread("SignalStrengthThread").apply { start() }
            val handler = Handler(handlerThread.looper)
            val executor = Executor { command -> handler.post(command) }
            telephonyCallback = @RequiresApi(Build.VERSION_CODES.S)
            object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val signalStrengthDbm =
                        signalStrength.getCellSignalStrengths().minOfOrNull { it.dbm } ?: return
                    globalSignalStrengthDbm = signalStrengthDbm.toDouble()
                    runOnUiThread {
                        binding.tvSignalStrength.text =
                            "Intensidad de la señal: ${signalStrengthDbm} dBm"
                        Log.d("SIGNAL", "Intensidad de la señal: ${signalStrengthDbm} dBm")
                    }
                }
            }
            telephonyManager.registerTelephonyCallback(executor, telephonyCallback)
        }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDestroy() {
        super.onDestroy()
        // Dejar de escuchar los cambios en la intensidad de la señal cuando la actividad se destruye
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback)
        }
        handlerThread.quitSafely()
    }

    private fun verificarPermisos() {
        val permisos = arrayListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permisos.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val permisosArray = permisos.toTypedArray()
        if (tienePermisos(permisosArray)) {
            isPermisos = true
            onPermisosConcedidos()
        } else {
            solicitarPermisos(permisosArray)
        }
    }

    private fun tienePermisos(permisos: Array<String>): Boolean {
        return permisos.all {
            return ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun onPermisosConcedidos() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if (it != null) {
                    imprimirUbicacion(it)
                } else {
                    Toast.makeText(this, "No se puede obtener la ubicacion", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                30000
            ).apply {
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(true)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    super.onLocationResult(p0)

                    for (location in p0.locations) {
                        imprimirUbicacion(location)
                        postLocationAndSignalStrength(
                            location.latitude,
                            location.longitude,
                            globalSignalStrengthDbm
                        )
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {

        }
    }

    private fun solicitarPermisos(permisos: Array<String>) {
        requestPermissions(
            permisos,
            CODIGO_PERMISO_SEGUNDO_PLANO
        )
    }

    private fun imprimirUbicacion(ubicacion: Location) {
        binding.tvLatitud.text = "${ubicacion.latitude}"
        binding.tvLongitud.text = "${ubicacion.longitude}"
        Log.d("GPS", "LAT: ${ubicacion.latitude} - LON: ${ubicacion.longitude}")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CODIGO_PERMISO_SEGUNDO_PLANO) {
            val todosPermisosConcedidos =
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (grantResults.isNotEmpty() && todosPermisosConcedidos) {
                isPermisos = true
                onPermisosConcedidos()
            }
        }
    }

    fun isMobileDataEnabled(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun postLocationAndSignalStrength(latitude: Double, longitude: Double, signalStrength: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            val json = JSONObject().apply {
                put("latitud", latitude)
                put("longitud", longitude)
                put("potencia", signalStrength)
            }

            val requestBody =
                json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://backmovil-7.onrender.com/users")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Handle the error
                }

                // Process the response
            }
        }
    }
}