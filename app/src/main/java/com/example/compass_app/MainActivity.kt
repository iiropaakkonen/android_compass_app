package com.example.compass_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.compass_app.ui.theme.Compass_appTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices


//                         val heading by compass.heading.collectAsStateWithLifecycle()
//                         Text(text = "Heading: ${"%.1f".format(heading)}°"
class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var compass: CompassSensor
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        enableEdgeToEdge()

        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        compass = CompassSensor(sensorManager)
        compass.start()

        setContent {
            Compass_appTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        LocationPermissionWrapper(
                            fusedLocationClient = fusedLocationClient
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocationPermissionWrapper(
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: NearbyViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    )

    if (hasPermission) {
        LaunchedEffect(Unit) {
            viewModel.startLocationUpdates(fusedLocationClient)
        }
        MainAppContent(viewModel = viewModel)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    "This app needs location access to find points of interest near you.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) {
                    Text("Grant Location Permission")
                }
            }
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: NearbyViewModel
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    // Initial height for the header
    var headerHeight by remember { mutableStateOf(150.dp) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderSection(modifier = Modifier.height(headerHeight))
        
        // The draggable divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Update height based on drag. dragAmount.y is in pixels, 
                        // so we convert to dp (approximate for simplicity here)
                        val delta = dragAmount.y / (configuration.densityDpi / 160f)
                        val newHeight = headerHeight + delta.dp
                        // Constraints to keep UI usable
                        if (newHeight > 50.dp && newHeight < screenHeight * 0.7f) {
                            headerHeight = newHeight
                        }
                    }
                }
        )
        
        NearbyPOIScreen(
            modifier = Modifier.weight(1f),
            viewModel = viewModel
        )
    }
}

@Composable
fun HeaderSection(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Explore Nearby",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Discover interesting places around you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
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
