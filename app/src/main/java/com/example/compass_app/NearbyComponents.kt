package com.example.compass_app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun NearbyPOIScreen(
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val allCategories = PoiCategory.entries
    val allSelected = viewModel.activeFilters.size == allCategories.size

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(
                        onClick = { filterMenuExpanded = true },
                        colors = if (!allSelected)
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else
                            ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Filter")
                    }
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = allSelected,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (allSelected) "Exclude All" else "Include All",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            onClick = {
                                viewModel.activeFilters = if (allSelected) setOf(allCategories.first())
                                else allCategories.toSet()
                            }
                        )
                        HorizontalDivider()
                        allCategories.forEach { category ->
                            val checked = viewModel.activeFilters.contains(category)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category.displayName)
                                    }
                                },
                                onClick = { viewModel.toggleFilter(category) }
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.refreshPOIs() }) {
                    Text("🔄")
                }
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
                    .filter { it.category in viewModel.activeFilters }
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
                                bearing = bearingTo(loc, poi.location),
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
fun POI_Item(poi: PointOfInterest, distance: Float, bearing: String, onClick: () -> Unit) {
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
            Column(horizontalAlignment = Alignment.End) {
                DistanceDisplay(result = distance)
                Text(
                    text = bearing,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
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
            val usefulKeys = linkedMapOf(
                "Type" to poi.locationType.substringAfter(":").replace("_", " ").capitalize(),
                "Address" to listOfNotNull(
                    poi.tags["addr:street"]?.let { street ->
                        poi.tags["addr:housenumber"]?.let { num -> "$street $num" } ?: street
                    }
                ).firstOrNull(),
                "Opening hours" to (poi.tags["opening_hours"]),
                "Phone" to (poi.tags["phone"] ?: poi.tags["contact:phone"]),
                "Website" to (poi.tags["website"] ?: poi.tags["contact:website"])
            ).filterValues { it != null }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = poi.category.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val uriHandler = LocalUriHandler.current
                usefulKeys.forEach { (label, value) ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = "$label: ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(110.dp)
                        )
                        if (label == "Website") {
                            val url = if (value!!.startsWith("http")) value else "https://$value"
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.weight(1f).clickable { uriHandler.openUri(url) }
                            )
                        } else {
                            Text(
                                text = value!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val context = LocalContext.current
            Button(onClick = {
                openGoogleMapsCoordinates(context, poi.location.lat, poi.location.lon, poi.name)
            }) {
                Text("Open in Google Maps")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
