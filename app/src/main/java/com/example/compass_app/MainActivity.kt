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


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Compass_appTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        val loc1 = Location(60.45f, 22.26f)
                        val loc2 = Location(61.0f, 23.0f)
                        val loc3 = Location(20.45f, 22.26f)
                        val loc4 = Location(61.0f, 23.0f)
                        Greeting("Samu")
                        DistanceDisplay(Distance(loc1, loc2))
                        DistanceDisplay(Distance(loc3, loc4))
                    }
                }
        }
    }
}


 @Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
 fun Distance(location1: Location, location2: Location): Float
    {
        val dx = location1.lat - location2.lat
        val dy = location1.lon - location2.lon
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }
    }
@Composable
fun DistanceDisplay(result: Float, modifier: Modifier = Modifier) {
    if (result > 1.0f) {
        Text(text = "${"%.2f".format(result)} Kilometriä", modifier = modifier)
    } else {
        Text(text = "${Math.round(result*1000.0f)} Metriä" , modifier = modifier)
    }
}
