package com.example.compass_app

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

class NearbyListRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NearbyListFactory(applicationContext)
}

private class NearbyListFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var pois: List<WidgetPoi> = emptyList()
    private var userLoc: Location? = null

    override fun onCreate() = reload()
    override fun onDataSetChanged() = reload()
    override fun onDestroy() {}

    private fun reload() {
        userLoc = WidgetDataStore.getUserLocation(context)
        pois = WidgetDataStore.getPois(context)
    }

    override fun getCount(): Int = pois.size

    override fun getViewAt(position: Int): RemoteViews {
        val poi = pois.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_nearby_list_item)

        val views = RemoteViews(context.packageName, R.layout.widget_nearby_list_item)

        views.setTextViewText(R.id.nearby_item_name, poi.name)

        val dist = userLoc?.let { distanceTo(it, Location(poi.lat, poi.lon)) }
        val distText = when {
            dist == null        -> "--"
            dist >= 1f          -> "%.1f km".format(dist)
            else                -> "${(dist * 1000).toInt()} m"
        }
        views.setTextViewText(R.id.nearby_item_distance, distText)

        val category = poi.toCategory()
        val iconBmp = ContextCompat.getDrawable(context, poiCategoryIcon(category))?.let { d ->
            d.mutate()
                .apply { colorFilter = PorterDuffColorFilter(poiCategoryColorInt(category), PorterDuff.Mode.SRC_IN) }
                .toBitmap(40, 40)
        }
        if (iconBmp != null) views.setImageViewBitmap(R.id.nearby_item_icon, iconBmp)

        // Per-row fill-in: carries the POI coordinates so MainActivity can open the info dialog
        val fillIn = Intent().apply {
            putExtra("poi_lat", poi.lat)
            putExtra("poi_lon", poi.lon)
        }
        views.setOnClickFillInIntent(R.id.nearby_item_root, fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
