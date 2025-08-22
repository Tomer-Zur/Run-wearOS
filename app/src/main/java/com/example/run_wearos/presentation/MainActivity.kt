/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.run_wearos.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.run_wearos.R
import com.example.run_wearos.presentation.theme.RunwearOsTheme
// Remove direct import of LocationRequest, Priority if fully qualifying
// import com.google.android.gms.location.LocationRequest
// import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val CREATE_ACTIVITY_URL = "https://runfuncionapp.azurewebsites.net/api/createActivity"
const val CREATE_TRACK_URL = "https://runfuncionapp.azurewebsites.net/api/createTrack"

// Create a trust manager that trusts all certificates (for development only)
private fun createTrustAllCerts(): Array<TrustManager> {
    return arrayOf(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
}

suspend fun createTrack(
    userId: String,
    path: List<Map<String, Double?>>,
    authToken: String? = null
): String? = withContext(Dispatchers.IO) {
    try {
        val json = JSONObject().apply {
            put("userId", userId)
            put("path", JSONArray(path))
            put("name", "Wear OS Run")
            put("timestamp", System.currentTimeMillis().toString())
        }

        Log.d("RunBackend", "Creating track for userId: $userId")

        val url = URL(CREATE_TRACK_URL)
        val conn = url.openConnection() as HttpsURLConnection
        
        // Configure SSL to trust all certificates (for development)
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, createTrustAllCerts(), java.security.SecureRandom())
            conn.sslSocketFactory = sslContext.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.w("RunBackend", "Failed to configure SSL, using default: ${e.message}")
        }
        
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer $authToken")
        }
        conn.doOutput = true
        conn.connectTimeout = 10000 // 10 seconds
        conn.readTimeout = 10000 // 10 seconds
        OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }
        val responseCode = conn.responseCode
        val responseBody = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.use { it.readText() }
        }
        conn.disconnect()

        Log.d("RunBackend", "Create track response: $responseCode, body: $responseBody")

        if (responseCode in 200..299 && responseBody != null) {
            val responseJson = JSONObject(responseBody)
            responseJson.optString("trackId", null) // Use optString for safety
        } else {
            Log.e("RunBackend", "Create track failed with code $responseCode")
            null
        }
    } catch (e: Exception) {
        Log.e("RunBackend", "Create track exception: ${e.localizedMessage}", e)
        null
    }
}

suspend fun sendRunToBackend(
    userId: String,
    trackId: String,
    startTime: String,
    stopTime: String,
    timestamp: String,
    distance: Float,
    duration: Int,
    calories: Float,
    averagePace: Float,
    averageSpeed: Float,
    authToken: String? = null,
    eventId: String? = null
): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
    try {
        val json = JSONObject().apply {
            put("userId", userId)
            put("trackId", trackId)
            put("start_time", startTime)
            put("stop_time", stopTime)
            put("timestamp", timestamp)
            put("distance", distance)
            put("duration", duration)
            put("calories", calories)
            put("averagePace", averagePace)
            put("averageSpeed", averageSpeed)
            put("type", "Wear OS Run")
            if (eventId != null) put("eventId", eventId)
        }

        Log.d("RunBackend", "Sending run data: userId=$userId, trackId=$trackId, distance=$distance, duration=$duration")
        Log.d("RunBackend", "Full JSON payload: ${json.toString()}")

        val url = URL(CREATE_ACTIVITY_URL)
        val conn = url.openConnection() as HttpsURLConnection
        
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, createTrustAllCerts(), java.security.SecureRandom())
            conn.sslSocketFactory = sslContext.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.w("RunBackend", "Failed to configure SSL, using default: ${e.message}")
        }
        
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer $authToken")
        }
        conn.doOutput = true
        conn.connectTimeout = 10000 
        conn.readTimeout = 10000 
        OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }
        val responseCode = conn.responseCode
        val responseMessage = conn.responseMessage
        val responseBody = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.use { it.readText() }
        }
        conn.disconnect()
        Log.d("RunBackend", "Response code: $responseCode, message: $responseMessage, body: $responseBody")
        if (responseCode in 200..299) {
            true to responseBody
        } else {
            false to ("HTTP $responseCode $responseMessage: $responseBody")
        }
    } catch (e: Exception) {
        Log.e("RunBackend", "Exception: ${e.localizedMessage}", e)
        false to e.localizedMessage
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        try {
            val intent = Intent(this, Class.forName("com.example.run_wearos.TokenListenerService"))
            startService(intent)
        } catch (e: ClassNotFoundException) {
            Log.e("MainActivity", "TokenListenerService class not found", e)
        }
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    RunwearOsTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            AuthenticationWrapper { onLogout ->
                LocationPermissionWrapper {
                    RunScreen(onLogout = onLogout)
                }
            }
        }
    }
}

@Composable
fun AuthenticationWrapper(content: @Composable (onLogout: () -> Unit) -> Unit) {
    val context = LocalContext.current
    var isAuthenticated by remember { mutableStateOf(false) }
    var isCheckingAuth by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val checkAuth = suspend {
        val storedToken = getStoredToken(context)
        if (storedToken != null) {
            val isValid = validateToken(storedToken)
            isAuthenticated = isValid
            if (!isValid) {
                clearCredentials(context)
            }
        } else {
            isAuthenticated = false
        }
        isCheckingAuth = false
    }

    val handleLogout = {
        clearCredentials(context)
        isAuthenticated = false
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            checkAuth()
        }
    }

    if (isCheckingAuth) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (isAuthenticated) {
        content(handleLogout)
    } else {
        LoginScreen(onLoginSuccess = {
            isAuthenticated = true
        })
    }
}

@Composable
fun LocationPermissionWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var permissionRequested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
         if (!isGranted) {
            Log.w("LocationPermission", "Fine location permission denied by user.")
            // Optionally, show a message to the user explaining why the permission is needed
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted && !permissionRequested) {
            Log.d("LocationPermission", "Requesting Fine Location permission.")
            permissionRequested = true
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    if (permissionGranted) {
        content()
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Location permission is required to track your run.", style = MaterialTheme.typography.body1)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                Text("Grant Permission")
            }
        }
    }
}


@Composable
fun RunScreen(onLogout: () -> Unit) {
    var runStarted by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf<String?>(null) }
    // var endTime by remember { mutableStateOf<String?>(null) } // Not used at this level
    val dateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!runStarted) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                StartRunButton(onStart = {
                    startTime = dateFormat.format(Date())
                    runStarted = true
                })
                Button(onClick = onLogout) {
                    Text("Logout")
                }
            }
        } else {
            RunInfo(
                onStop = {
                    runStarted = false
                },
                startTime = startTime,
                dateFormat = dateFormat 
            )
        }
    }
}

@Composable
fun StartRunButton(onStart: () -> Unit) {
    val context = LocalContext.current
    Button(onClick = {
        Toast.makeText(context, context.getString(R.string.start_run_message), Toast.LENGTH_SHORT).show()
        onStart()
    }) {
        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Start Run")
    }
}

@Composable
fun RunInfo(
    onStop: () -> Unit, 
    startTime: String?,
    dateFormat: SimpleDateFormat
) {
    val context = LocalContext.current

    var bpm by remember { mutableIntStateOf(0) }
    var bodySensorsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var bodySensorsPermissionRequested by remember { mutableStateOf(false) }

    val bodySensorsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("RunInfo", "Body Sensors permission result: $isGranted")
        bodySensorsPermissionGranted = isGranted
        if (!isGranted) {
            Log.w("RunInfo", "Body Sensors permission denied by user.")
        } else {
            Log.d("RunInfo", "Body Sensors permission granted!")
        }
    }

    LaunchedEffect(Unit) { 
        if (!bodySensorsPermissionGranted && !bodySensorsPermissionRequested) {
            Log.d("RunInfo", "Requesting Body Sensors permission.")
            bodySensorsPermissionRequested = true
            bodySensorsPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }
    
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val heartRateSensor: Sensor? = remember { sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) }

    val heartRateListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
                    val newBpmValue = event.values.firstOrNull()?.toInt()
                    if (newBpmValue != null && newBpmValue > 0) { 
                        bpm = newBpmValue
                        Log.d("RunInfo", "BPM Updated: $bpm")
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d("RunInfo", "Heart rate sensor accuracy changed to: $accuracy")
            }
        }
    }

    DisposableEffect(heartRateSensor, bodySensorsPermissionGranted) {
        if (bodySensorsPermissionGranted && heartRateSensor != null) {
            val registered = sensorManager.registerListener(
                heartRateListener,
                heartRateSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (registered) {
                Log.d("RunInfo", "Heart rate sensor listener registered.")
            } else {
                Log.e("RunInfo", "Failed to register heart rate sensor listener.")
                bpm = -1 // Error state
            }
        } else {
            if (!bodySensorsPermissionGranted) Log.w("RunInfo", "Body Sensors permission not granted. Cannot read BPM.")
            if (heartRateSensor == null) Log.w("RunInfo", "Heart rate sensor not available on this device.")
            bpm = 0 // Default/unavailable state
        }
        onDispose {
            sensorManager.unregisterListener(heartRateListener)
            Log.d("RunInfo", "Heart rate sensor listener unregistered.")
        }
    }

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var internalEndTime by remember { mutableStateOf<String?>(null) } 

    var previousLocation by remember { mutableStateOf<Location?>(null) }
    var totalDistanceMeters by remember { mutableFloatStateOf(0f) } 
    val locationHistory = remember { LinkedList<Pair<Location, Long>>() }
    var rollingPace by remember { mutableStateOf(0.0) }
    val runPath = remember { mutableListOf<Map<String, Double?>>() }
    var elevationGain by remember { mutableFloatStateOf(0.0f) } 
    var maxElevation by remember { mutableFloatStateOf(0.0f) }  
    var minElevation by remember { mutableFloatStateOf(Float.MAX_VALUE) } 

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    if (!paused) {
                        if (previousLocation != null) {
                            val distance = previousLocation!!.distanceTo(location)
                            if (distance > 1.0f) { 
                                totalDistanceMeters += distance
                            }
                        }
                        
                        val currentAltitude = if(location.hasAltitude()) location.altitude else 0.0
                        
                        if (currentAltitude != 0.0) {
                            if (currentAltitude.toFloat() > maxElevation) maxElevation = currentAltitude.toFloat()
                            if (currentAltitude.toFloat() < minElevation) minElevation = currentAltitude.toFloat()
                            if (previousLocation != null && previousLocation!!.hasAltitude()) {
                                val prevAltitude = previousLocation!!.altitude
                                if (prevAltitude != 0.0) {
                                     val elevationDiff = currentAltitude - prevAltitude
                                     if (elevationDiff > 0) elevationGain += elevationDiff.toFloat()
                                }
                            }
                        }
                        previousLocation = location 

                        val now = System.currentTimeMillis()
                        locationHistory.add(Pair(location, now))
                        runPath.add(mapOf(
                            "latitude" to location.latitude,
                            "longitude" to location.longitude,
                            "altitude" to (currentAltitude.takeIf { it != 0.0 } ?: null)
                        ))

                        while (locationHistory.isNotEmpty() && now - locationHistory.first.second > 30_000) {
                            locationHistory.removeFirst()
                        }

                        var rollingDistance = 0.0
                        var prevLoc: Location? = null
                        for ((loc, _) in locationHistory) {
                            if (prevLoc != null) rollingDistance += prevLoc.distanceTo(loc)
                            prevLoc = loc
                        }

                        if (rollingDistance > 10 && locationHistory.size > 1) {
                            val timeSpan = (locationHistory.last.second - locationHistory.first.second) / 1000.0
                            val minutes = timeSpan / 60.0
                            val km = rollingDistance / 1000.0
                            if (km > 0 && minutes > 0) rollingPace = minutes / km
                            else rollingPace = 0.0
                        } else {
                            rollingPace = 0.0
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 2000L).apply {
            setMinUpdateIntervalMillis(1000L)
            setMinUpdateDistanceMeters(2.0f)
        }.build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastKnownLocation ->
                    if (lastKnownLocation != null) previousLocation = lastKnownLocation
                }
            } catch (e: SecurityException) { Log.e("RunInfo", "SecurityException getting last location: ${e.message}") }
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper())
                Log.d("RunInfo", "Location updates requested.")
            } catch (e: SecurityException) { Log.e("RunInfo", "SecurityException requesting location updates: ${e.message}") }
        } else {
             Log.w("RunInfo", "Location permission not granted. Cannot request location updates.")
        }
        onDispose {
             if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                Log.d("RunInfo", "Location updates removed.")
            }
        }
    }

    LaunchedEffect(paused) {
        // Check runStarted from the parent composable's state if it dictates the timer should run
        // However, RunInfo is only shown if runStarted is true, so this check might be redundant here
        // For clarity, let's assume elapsedSeconds should tick as long as RunInfo is composed and not paused.
        while (!paused) { 
            delay(1000)
            elapsedSeconds++
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp)) 
        Spacer(modifier = Modifier.weight(1f))

            // Show BPM reading
    Text(text = when {
        !bodySensorsPermissionGranted -> "BPM: no permission"
        heartRateSensor == null -> "BPM: N/A (No Sensor)"
        bpm == -1 -> "BPM: Error"
        bpm == 0 && bodySensorsPermissionGranted -> "BPM: ... " 
        else -> "BPM: $bpm"
    })
        
        
        
        Text(text = "Distance: %.2f km".format(totalDistanceMeters / 1000f))
        Text(text = "Time: %02d:%02d".format(minutes, seconds))
        Text(text = if (rollingPace > 0.0) "Pace: %.2f min/km".format(rollingPace) else "Pace: --")
        if (elevationGain > 0) {
            Text(text = "Elevation: +%.0f m".format(elevationGain))
        }
        
        Spacer(modifier = Modifier.weight(1f)) 
        
        val coroutineScope = rememberCoroutineScope()
        val userWeightKg = 70f
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("userId", "") ?: ""
        val authToken = prefs.getString("auth_token", null)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { paused = !paused }) {
                Icon(imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = if (paused) "Resume" else "Pause")
            }
            Button(onClick = {
                internalEndTime = dateFormat.format(Date()) 
                val currentStartTime = startTime ?: dateFormat.format(Date(System.currentTimeMillis() - elapsedSeconds * 1000))
                val calories = userWeightKg * (totalDistanceMeters / 1000f) * 1.036f
                val avgPace = if (totalDistanceMeters > 0 && elapsedSeconds > 0) (elapsedSeconds / 60.0) / (totalDistanceMeters / 1000.0) else 0.0
                val avgSpeed = if (elapsedSeconds > 0) (totalDistanceMeters / elapsedSeconds.toFloat()) else 0f


                coroutineScope.launch {
                    val trackId = if (runPath.isNotEmpty()) {
                        createTrack(userId, runPath, authToken)
                    } else {
                        Log.w("RunBackend", "Run path is empty, not creating track.")
                        null
                    }

                    if (trackId != null || runPath.isEmpty()) { 
                        Log.d("RunBackend", "Proceeding to send run data. Track ID: $trackId")
                        val (success, errorMsg) = sendRunToBackend(
                            userId = userId,
                            trackId = trackId ?: "", 
                            startTime = currentStartTime,
                            stopTime = internalEndTime!!,
                            timestamp = dateFormat.format(Date()), 
                            distance = totalDistanceMeters,
                            duration = elapsedSeconds,
                            calories = calories,
                            averagePace = avgPace.toFloat(),
                            averageSpeed = avgSpeed,
                            authToken = authToken
                        )
                         Toast.makeText(
                            context,
                            if (success) "Run logged successfully!" else "Failed to log run: $errorMsg",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                         Log.e("RunBackend", "Failed to create track and track is considered essential.")
                        Toast.makeText(
                            context,
                            "Failed to create track for the run",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onStop() 
                }
            }) {
                Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop")
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}


