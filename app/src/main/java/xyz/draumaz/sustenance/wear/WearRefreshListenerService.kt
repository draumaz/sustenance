package xyz.draumaz.sustenance.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import xyz.draumaz.sustenance.widget.WidgetUpdateWorker

/**
 * Answers the watch's "I opened with nothing fresh" ping. The refresh worker already re-reads
 * Health Connect for the widget and then publishes to the watch, so it's reused rather than
 * reading here: Play services binds this service only briefly.
 */
class WearRefreshListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearSync.PATH_REFRESH_REQUEST) {
            WidgetUpdateWorker.enqueue(applicationContext)
        }
    }
}
