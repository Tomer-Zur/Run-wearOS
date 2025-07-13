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
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.wear.compose.material.Icon
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

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
            RunScreen()
        }
    }
}

@Composable
fun RunScreen() {
    var runStarted by remember { mutableStateOf(false) }
    if (!runStarted) {
        StartRunButton(onStart = { runStarted = true })
    } else {
        RunInfo(onStop = { runStarted = false })
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
fun RunInfo(onStop: () -> Unit) {
    // Mock values
    val bpm = 120
    val distance = 0.42 // km
    val pace = 5.0 // min/km
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

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
        Text(text = "Distance: %.2f km".format(distance))
        Text(text = "Time: %02d:%02d".format(minutes, seconds))
        Text(text = "Pace: %.2f min/km".format(pace))
        Spacer(modifier = Modifier.height(32.dp))
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
            Button(onClick = { onStop() }) {
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