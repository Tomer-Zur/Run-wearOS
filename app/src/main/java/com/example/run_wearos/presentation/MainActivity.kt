/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.run_wearos.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
//import androidx.compose.ui.res.stringResource
//import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.run_wearos.R
import com.example.run_wearos.presentation.theme.RunwearOsTheme
import android.widget.Toast
import androidx.wear.compose.material.Button
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.wear.compose.material.Icon
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import android.location.Location
import java.util.LinkedList
import java.util.Iterator
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.content.Intent
import android.content.Context

const val CREATE_ACTIVITY_URL = "https://runfuncionapp.azurewebsites.net/api/createActivity"

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
            if (eventId != null) put("eventId", eventId)
        }
        val url = URL(CREATE_ACTIVITY_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer $authToken")
        }
        conn.doOutput = true
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

        // Start the TokenListenerService
        val intent = Intent(this, Class.forName("com.example.run_wearos.TokenListenerService"))
        startService(intent)

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
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    // Call rememberLauncherForActivityResult directly in the composable's scope
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        permissionGranted = granted
        if (!granted && !permissionRequested) {
            permissionRequested = true
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    if (permissionGranted) {
        content()
    } else {
        Text(text = "Location permission required to track your run.")
    }
}


@Composable
fun RunScreen(onLogout: () -> Unit) {
    var runStarted by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf<String?>(null) }
    var endTime by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Main run content
        if (!runStarted) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                StartRunButton(onStart = {
                    startTime = dateFormat.format(Date())
                    runStarted = true
                })
                
                Button(
                    onClick = onLogout
                ) {
                    Text("Logout")
                }
            }
        } else {
            RunInfo(
                onStop = {
                    endTime = dateFormat.format(Date())
                    runStarted = false
                },
                startTime = startTime,
                endTime = endTime
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
    endTime: String?
) {
    // Mock values
    val bpm = 120
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    var previousLocation by remember { mutableStateOf<Location?>(null) }
    var totalDistanceMeters by remember { mutableStateOf(0f) }
    val locationHistory = remember { LinkedList<Pair<Location, Long>>() }
    var rollingPace by remember { mutableStateOf(0.0) }

    // 1. Location state and client
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }

    // 2. LocationCallback
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    if (previousLocation != null) {
                        val distance = previousLocation!!.distanceTo(location) // in meters
                        if (distance > 1.0) { // Filter out noise and small movements
                            totalDistanceMeters += distance
                        }
                    }
                    
                    previousLocation = location
                    lastLocation = location
                    
                    // Add to history
                    val now = System.currentTimeMillis()
                    locationHistory.add(Pair(location, now))
                    
                    // Prune history to last 30 seconds
                    while (locationHistory.isNotEmpty() && now - locationHistory.first.second > 30_000) {
                        locationHistory.removeFirst()
                    }
                    
                    // Calculate rolling distance
                    var rollingDistance = 0.0
                    var prev: Location? = null
                    for ((loc, _) in locationHistory) {
                        if (prev != null) {
                            rollingDistance += prev.distanceTo(loc)
                        }
                        prev = loc
                    }
                    
                    // Calculate pace (min/km) if enough distance and time
                    if (rollingDistance > 10 && locationHistory.size > 1) {
                        val timeSpan = (locationHistory.last.second - locationHistory.first.second) / 1000.0 // seconds
                        val minutes = timeSpan / 60.0
                        val km = rollingDistance / 1000.0
                        if (km > 0 && minutes > 0) {
                            rollingPace = minutes / km
                        } else {
                            rollingPace = 0.0
                        }
                    } else {
                        rollingPace = 0.0
                    }
                }
            }
        }
    }

    // 3. Start/stop location updates
    DisposableEffect(Unit) {
        val locationRequest = LocationRequest.create().apply {
            interval = 2000 // 2 seconds between updates
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 2.0f // Request updates for movements of 2+ meters
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Try to get last known location first
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastKnownLocation ->
                    if (lastKnownLocation != null) {
                        previousLocation = lastKnownLocation
                        lastLocation = lastKnownLocation
                    }
                }
            } catch (e: Exception) {
                // Ignore errors for last known location
            }
            
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }

        onDispose {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    LaunchedEffect(paused) {
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
        Spacer(modifier = Modifier.height(32.dp))
        // Move stats lower
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "BPM: $bpm")
        Text(text = "Distance: %.2f km".format(totalDistanceMeters / 1000f))
        Text(text = "Time: %02d:%02d".format(minutes, seconds))
        Text(text = if (rollingPace > 0.0) "Pace: %.2f min/km".format(rollingPace) else "Pace: --")
        Spacer(modifier = Modifier.height(32.dp))
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val userWeightKg = 70f // Default user weight
        // Read user info from SharedPreferences
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("userId", "") ?: ""
        val username = prefs.getString("username", "") ?: ""
        val authToken = prefs.getString("auth_token", null)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { paused = !paused }) {
                if (paused) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Play")
                } else {
                    Icon(imageVector = Icons.Filled.Pause, contentDescription = "Pause")
                }
            }
            Button(onClick = {
                val startTimeStr = startTime ?: ""
                val endTimeStr = endTime ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
                val timestamp = System.currentTimeMillis().toString()
                val calories = userWeightKg * (totalDistanceMeters / 1000f) * 1.036f
                val avgPace = rollingPace.toFloat()
                val avgSpeed = if (elapsedSeconds > 0) (totalDistanceMeters / elapsedSeconds) else 0f
                coroutineScope.launch {
                    val (success, errorMsg) = sendRunToBackend(
                        userId = userId,
                        trackId = "demoTrack", // TODO: Replace with actual track ID
                        startTime = startTimeStr,
                        stopTime = endTimeStr,
                        timestamp = timestamp,
                        distance = totalDistanceMeters,
                        duration = elapsedSeconds,
                        calories = calories,
                        averagePace = avgPace,
                        averageSpeed = avgSpeed,
                        authToken = authToken
                    )
                    Toast.makeText(
                        context,
                        if (success) "Run logged successfully!" else "Failed to log run: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
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