package com.example.compass_app

import android.location.Geocoder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NearbyPOIScreen(
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
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

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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

                when {
                    viewModel.pois.isEmpty() && viewModel.userLocation == null && !viewModel.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Searching for your location...")
                        }
                    }
                    viewModel.pois.isEmpty() && !viewModel.isLoading -> {
                        Text("No locations found nearby.")
                    }
                    else -> {
                        val userLoc = viewModel.userLocation
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(viewModel.pois) { poi ->
                                val distance = userLoc?.let { distanceTo(it, poi.location) }
                                POI_Item(
                                    poi = poi,
                                    distance = distance,
                                    isFavorited = poi.id in viewModel.favorites,
                                    isCustom = poi.locationType.startsWith("custom:"),
                                    onFavoriteClick = { viewModel.toggleFavorite(poi) },
                                    onClick = { viewModel.selectedPoi = poi }
                                )
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
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }

    if (showAddDialog) {
        AddCustomPoiDialog(
            currentLocation = viewModel.userLocation,
            onAdd = { name, category, lat, lon ->
                viewModel.addCustomPoi(name, category, lat, lon)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    viewModel.selectedPoi?.let { poi ->
        val isCustom = poi.locationType.startsWith("custom:")
        PoiInfoDialog(
            poi = poi,
            isFavorited = poi.id in viewModel.favorites,
            isCustom = isCustom,
            onToggleFavorite = { viewModel.toggleFavorite(poi) },
            onDelete = {
                viewModel.deleteCustomPoi(poi.id)
                viewModel.selectedPoi = null
            },
            onDismiss = { viewModel.selectedPoi = null }
        )
    }
}

@Composable
fun POI_Item(
    poi: PointOfInterest,
    distance: Float?,
    isFavorited: Boolean,
    isCustom: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
     bearing: String
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCustom) {
                        Text(
                            text = "📍 ",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(text = poi.name, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = poi.category.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onFavoriteClick) {
                Text(
                    text = if (isFavorited) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isFavorited) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
fun DistanceDisplay(result: Float?, modifier: Modifier = Modifier) {
    val text = when {
        result == null -> "--"
        result >= 1.0f -> "${"%.2f".format(result)} km"
        else -> "${(result * 1000).toInt()} m"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
fun PoiInfoDialog(
    poi: PointOfInterest,
    isFavorited: Boolean,
    isCustom: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = poi.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isFavorited) "★  Unfavorite" else "☆  Favorite")
                    }
                    if (isCustom) {
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = poi.category.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val uriHandler = LocalUriHandler.current
                val infoFields = buildList {
                    if (!isCustom) {
                        add("Type" to poi.locationType.substringAfter(":").replace("_", " ").capitalize())
                    }
                    val address = poi.tags["addr:street"]?.let { street ->
                        poi.tags["addr:housenumber"]?.let { num -> "$street $num" } ?: street
                    }
                    if (address != null) add("Address" to address)
                    val hours = poi.tags["opening_hours"]
                    if (hours != null) add("Opening hours" to hours)
                    val phone = poi.tags["phone"] ?: poi.tags["contact:phone"]
                    if (phone != null) add("Phone" to phone)
                    val website = poi.tags["website"] ?: poi.tags["contact:website"]
                    if (website != null) add("Website" to website)
                }

                infoFields.forEach { (label, value) ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = "$label: ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(110.dp)
                        )
                        if (label == "Website") {
                            val url = if (value.startsWith("http")) value else "https://$value"
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.weight(1f).clickable { uriHandler.openUri(url) }
                            )
                        } else {
                            Text(
                                text = value,
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
                Text("Open in Maps")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private enum class LocationSource { CURRENT, ADDRESS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomPoiDialog(
    currentLocation: Location?,
    onAdd: (name: String, category: PoiCategory, lat: Float, lon: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PoiCategory.OTHER) }
    var categoryExpanded by remember { mutableStateOf(false) }

    var locationSource by remember { mutableStateOf(LocationSource.CURRENT) }
    var addressQuery by remember { mutableStateOf("") }
    var resolvedLocation by remember { mutableStateOf<Location?>(null) }
    var resolvedLabel by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val effectiveLocation = if (locationSource == LocationSource.CURRENT) currentLocation else resolvedLocation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Location") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        PoiCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.displayName) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Text("Location", style = MaterialTheme.typography.labelLarge)

                LocationSource.entries.forEach { source ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = locationSource == source,
                                onClick = { locationSource = source }
                            )
                    ) {
                        RadioButton(
                            selected = locationSource == source,
                            onClick = { locationSource = source }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (source == LocationSource.CURRENT) "My current location"
                                   else "Search by address",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (locationSource == LocationSource.CURRENT) {
                    if (currentLocation == null) {
                        Text(
                            "Waiting for GPS — try again in a moment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Will be pinned at your current location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = addressQuery,
                            onValueChange = {
                                addressQuery = it
                                searchError = null
                            },
                            label = { Text("Address") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isSearching = true
                                    searchError = null
                                    resolvedLocation = null
                                    resolvedLabel = null
                                    withContext(Dispatchers.IO) {
                                        try {
                                            @Suppress("DEPRECATION")
                                            val results = Geocoder(context)
                                                .getFromLocationName(addressQuery.trim(), 5)
                                            if (!results.isNullOrEmpty()) {
                                                val r = results[0]
                                                resolvedLocation = Location(
                                                    r.latitude.toFloat(),
                                                    r.longitude.toFloat()
                                                )
                                                resolvedLabel = r.getAddressLine(0)
                                            } else {
                                                searchError = "Address not found"
                                            }
                                        } catch (e: Exception) {
                                            searchError = "Search failed: ${e.message}"
                                        }
                                    }
                                    isSearching = false
                                }
                            },
                            enabled = addressQuery.isNotBlank() && !isSearching
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("🔍")
                            }
                        }
                    }

                    resolvedLabel?.let { label ->
                        Text(
                            text = "📍 $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    searchError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val loc = effectiveLocation ?: return@Button
                    if (name.isNotBlank()) onAdd(name.trim(), selectedCategory, loc.lat, loc.lon)
                },
                enabled = name.isNotBlank() && effectiveLocation != null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
