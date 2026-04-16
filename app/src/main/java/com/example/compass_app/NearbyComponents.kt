package com.example.compass_app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NearbyPOIScreen(
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby Locations",
                style = MaterialTheme.typography.headlineSmall
            )
            IconButton(onClick = { viewModel.refreshPOIs() }) {
                Text("🔄")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (viewModel.isLoading && viewModel.pois.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            viewModel.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            viewModel.userLocation?.let { loc ->
                val sortedPois = viewModel.pois
                    .map { it to distanceTo(loc, it.location) }
                    .sortedBy { it.second }

                if (sortedPois.isEmpty() && !viewModel.isLoading) {
                    Text(text = "No locations found nearby.")
                } else {
                    LazyColumn {
                        items(sortedPois) { (poi, distance) ->
                            POI_Item(
                                poi = poi,
                                distance = distance,
                                onClick = { viewModel.selectedPoi = poi }
                            )
                        }
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Searching for your location...")
            }
        }
    }

    // Info Dialog
    viewModel.selectedPoi?.let { poi ->
        PoiInfoDialog(
            poi = poi,
            onDismiss = { viewModel.selectedPoi = null }
        )
    }
}

@Composable
fun POI_Item(poi: PointOfInterest, distance: Float, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = poi.name, style = MaterialTheme.typography.titleMedium)
                Text(text = poi.category.displayName, style = MaterialTheme.typography.bodySmall)
            }
            DistanceDisplay(result = distance)
        }
    }
}

@Composable
fun DistanceDisplay(result: Float, modifier: Modifier = Modifier) {
    val text = if (result >= 1.0f) {
        "${"%.2f".format(result)} km"
    } else {
        "${(result * 1000).toInt()} m"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
fun PoiInfoDialog(poi: PointOfInterest, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = poi.name)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Category: ${poi.category.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Type: ${poi.locationType}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                if (poi.tags.isNotEmpty()) {
                    Text(text = "Details:", style = MaterialTheme.typography.titleSmall)
                    poi.tags.forEach { (key, value) ->
                        val displayKey = key.replace("addr:", "").replace("_", " ").capitalize()
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "$displayKey: ",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(100.dp)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Text(text = "No additional information available.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
