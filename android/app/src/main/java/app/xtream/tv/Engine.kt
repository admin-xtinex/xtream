package app.xtream.tv

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object Engine {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime {
        runtime?.let { return it }
        synchronized(this) {
            runtime?.let { return it }
            val blocking = ContentBlocking.Settings.Builder()
                .antiTracking(
                    ContentBlocking.AntiTracking.AD or
                        ContentBlocking.AntiTracking.ANALYTIC or
                        ContentBlocking.AntiTracking.SOCIAL or
                        ContentBlocking.AntiTracking.CONTENT or
                        ContentBlocking.AntiTracking.CRYPTOMINING or
                        ContentBlocking.AntiTracking.FINGERPRINTING,
                )
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
                .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                .build()
            val settings = GeckoRuntimeSettings.Builder()
                .contentBlocking(blocking)
                .consoleOutput(false)
                .build()
            val created = GeckoRuntime.create(context.applicationContext, settings)
            runtime = created
            return created
        }
    }
}
