package com.dmx.khutwa.data

import android.content.Context

/**
 * `play` edition: no root, no su, nothing to probe. The constant lets the
 * compiler drop the root section of Settings entirely, so the Play build
 * contains no reference to root at all.
 */
object RootFeatures {
    const val AVAILABLE = false

    fun requestBackgroundExemption(context: Context, onResult: (RootOutcome) -> Unit) =
        onResult(RootOutcome(false, "unavailable"))

    fun backupDatabase(context: Context, onResult: (RootOutcome) -> Unit) =
        onResult(RootOutcome(false, "unavailable"))
}
