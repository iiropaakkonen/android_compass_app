package com.example.compass_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.compass_app.ui.theme.Compass_appTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        enableEdgeToEdge()
        setContent {
            Compass_appTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Apply the innerPadding from Scaffold here to handle status bars
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
    // Column will stack Header and the Screen
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderSection()
        
        // We pass a modifier with weight(1f) to NearbyPOIScreen
        // This tells it to only take the REMAINING space.
        NearbyPOIScreen(
            modifier = Modifier.weight(1f),
            viewModel = viewModel
        )
    }
}

@Composable
fun HeaderSection() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
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
