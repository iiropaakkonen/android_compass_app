package com.example.compass_app

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.compass_app.ui.theme.Compass_appTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

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
                        LocationPermissionWrapper(fusedLocationClient = fusedLocationClient, compassHeading = compass.heading)
                    }
                }
            }
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

@Composable
fun LocationPermissionWrapper(
    fusedLocationClient: FusedLocationProviderClient,
    compassHeading: StateFlow<Float>,
    viewModel: NearbyViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
        LaunchedEffect(Unit) { viewModel.startLocationUpdates(fusedLocationClient) }
        MainAppContent(viewModel = viewModel, compassHeading = compassHeading)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("This app needs location access to find points of interest near you.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) {
                    Text("Grant Location Permission")
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: NearbyViewModel, compassHeading: StateFlow<Float>) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    var headerHeight by remember { mutableStateOf(280.dp) }

    // Persist the current color scheme to SharedPreferences so widgets can pick it up.
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    SideEffect {
        ThemePrefs.save(
            context = context,
            backgroundColor = colorScheme.primaryContainer.toArgb(),
            tickColor = android.graphics.Color.WHITE,
            accentColor = colorScheme.primary.toArgb()
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderSection(modifier = Modifier.height(headerHeight), compassHeading = compassHeading, viewModel = viewModel)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount.y / (configuration.densityDpi / 160f)
                        val newHeight = headerHeight + delta.dp
                        if (newHeight > 50.dp && newHeight < screenHeight * 0.7f) {
                            headerHeight = newHeight
                        }
                    }
                }
        )
        NearbyPOIScreen(modifier = Modifier.weight(1f), viewModel = viewModel)
    }
}

@Composable
fun HeaderSection(modifier: Modifier = Modifier, compassHeading: StateFlow<Float>, viewModel: NearbyViewModel) {
    val compassNorth by compassHeading.collectAsState()
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val effectiveHeading = if (viewModel.compassLocked) viewModel.lockedHeading else compassNorth
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val headerDragModifier = if (viewModel.compassLocked) {
        Modifier.pointerInput(viewModel.compassLocked) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                viewModel.adjustLockedHeading(dragAmount.x / density * 0.3f)
            }
        }
    } else Modifier

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth().then(headerDragModifier),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryContainer,
                                secondaryContainer,
                            )
                        )
                    )
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dotColor = Color.White.copy(alpha = 0.08f)
                val spacing = 24.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    var y = 0f
                    while (y < size.height) {
                        drawCircle(
                            color = dotColor,
                            radius = 2.dp.toPx(),
                            center = Offset(x, y)
                        )
                        y += spacing
                    }
                    x += spacing
                }
            }

            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Explore Nearby",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

            Spacer(modifier = Modifier.weight(1f))

            CompassView(
                heading = effectiveHeading,
                pois = viewModel.pois.filter { poi ->
                    val categoryMatch = poi.category in viewModel.activeFilters
                    val favoriteMatch = poi.id in viewModel.favorites
                    if (viewModel.showFavoritesOnly) favoriteMatch && categoryMatch
                    else categoryMatch
                },
                userLocation = viewModel.userLocation,
                maxDistanceM = viewModel.maxCompassDistanceM,
                onPoiClick = { viewModel.selectedPoi = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.5f)
            )

            Text(
                text = "${viewModel.maxCompassDistanceM.toInt()}m",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Weights 1 : 6 : 1 = 12.5% : 75% : 12.5%, matching the compass arc margins.
            // Slider occupies the full compass width; lock button sits in the right margin.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Slider(
                    value = viewModel.maxCompassDistanceM,
                    onValueChange = { viewModel.maxCompassDistanceM = it },
                    valueRange = 100f..1000f,
                    modifier = Modifier.weight(6f)
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { viewModel.toggleCompassLock(compassNorth) }) {
                        Icon(
                            painter = painterResource(
                                if (viewModel.compassLocked) R.drawable.lock_closed
                                else R.drawable.lock_open
                            ),
                            contentDescription = if (viewModel.compassLocked) "Unlock orientation" else "Lock orientation",
                            tint = if (viewModel.compassLocked)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
