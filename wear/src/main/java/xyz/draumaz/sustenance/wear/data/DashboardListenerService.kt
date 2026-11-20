package xyz.draumaz.sustenance.wear.data

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService
import xyz.draumaz.sustenance.wear.tile.MacroTileService

/**
 * Woken by Play services when the phone publishes a new snapshot, even with the app closed,
 * so the tile re-renders straight away instead of waiting for its freshness interval.
 */
class DashboardListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        TileService.getUpdater(this).requestUpdate(MacroTileService::class.java)
    }
}
