package com.dmx.khutwa.data

/**
 * Result of an optional root-assisted action. Shared by both editions so the
 * UI can be written once; only the `personal` flavor ever produces `ok = true`.
 */
data class RootOutcome(val ok: Boolean, val output: String)
