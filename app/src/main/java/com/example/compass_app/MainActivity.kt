package com.example.compass_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.compass_app.ui.theme.Compass_appTheme
import androidx.compose.foundation.layout.Column
import kotlin.math.sqrt
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import android.hardware.SensorManager
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
class MainActivity : ComponentActivity() {
    private lateinit var compass: CompassSensor
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        compass = CompassSensor(sensorManager)
        compass.start()

        setContent {
            Compass_appTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {

                        val loc1 = Location(60.45f, 22.26f)
                        val loc2 = Location(61.0f, 23.0f)
                        val loc3 = Location(20.45f, 22.26f)
                        val loc4 = Location(61.0f, 23.0f)
                        GreetingColumn()
                        DistanceDisplay(Distance(loc1, loc2))
                        DistanceDisplay(Distance(loc3, loc4))
                        val context = LocalContext.current
                        Button(onClick = { openGoogleMaps(context, "Turku, Finland") }) {
                            Text("Open Maps")

                        }
                        val heading by compass.heading.collectAsStateWithLifecycle()
                        Text(text = "Heading: ${"%.1f".format(heading)}°")
                    }
                }
            }
        }
    }

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        val expanded = remember { mutableStateOf(false) }
        val extraPadding = if (expanded.value) 48.dp else 0.dp
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            Row(modifier = Modifier.padding(24.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = extraPadding)

                ) {
                    Text(
                        text = "Hello $name!"
                    )
                    Text(
                        text = "Koira"
                    )
                }
                ElevatedButton(
                    onClick = { expanded.value = !expanded.value }
                ) {
                    Text(if (expanded.value) "Show less" else "Show more")
                }
            }
        }
    }

    @Composable
    fun GreetingColumn(
        modifier: Modifier = Modifier,
        names: List<String> = listOf("Samu", "Antti")
    ) {
        Column(modifier = modifier.padding(vertical = 4.dp)) {
            for (name in names) {
                Greeting(name = name)
            }
        }
    }

    @Composable
    fun DistanceDisplay(result: Float, modifier: Modifier = Modifier) {
        if (result > 1.0f) {
            Text(text = "${"%.2f".format(result)} Kilometriä", modifier = modifier)
        } else {
            Text(text = "${Math.round(result * 1000.0f)} Metriä", modifier = modifier)
        }
    }
    override fun onPause() {
        super.onPause()
        compass.stop()
    }

    override fun onResume() {
        super.onResume()
        compass.start()
    }
}
