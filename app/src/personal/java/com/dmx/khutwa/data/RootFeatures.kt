package com.dmx.khutwa.data

import android.content.Context

/**
 * `personal` edition: the owner's rooted Fold4. Delegates to [RootBridge].
 * The `play` edition ships its own RootFeatures with [AVAILABLE] = false and
 * no su code at all — keep the two files' signatures identical.
 */
object RootFeatures {
    const val AVAILABLE = true

    fun requestBackgroundExemption(context: Context, onResult: (RootOutcome) -> Unit) =
        RootBridge.requestBackgroundExemption(context) { onResult(RootOutcome(it.ok, it.output)) }

    fun backupDatabase(context: Context, onResult: (RootOutcome) -> Unit) =
        RootBridge.backupDatabase(context) { onResult(RootOutcome(it.ok, it.output)) }
}
