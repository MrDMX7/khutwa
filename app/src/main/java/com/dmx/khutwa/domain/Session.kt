package com.dmx.khutwa.domain

import kotlin.math.roundToInt

/** One deliberately recorded run or walk. */
data class Session(
    val id: Long = 0,
    val date: String,
    val startMs: Long,
    val endMs: Long,
    val steps: Int = 0,
    val distanceM: Double = 0.0,
    val kcal: Double = 0.0,
    val avgCadence: Int = 0,
    val maxCadence: Int = 0,
    val elevGainM: Double = 0.0,
    val activeMinutes: Int = 0,
    val note: String? = null,
) {
    val durationMs: Long get() = endMs - startMs

    /** Seconds per kilometre — the unit runners actually think in. */
    val paceSecPerKm: Int?
        get() = if (distanceM < 100) null
        else ((durationMs / 1000.0) / (distanceM / 1000.0)).roundToInt()

    fun paceText(): String {
        val p = paceSecPerKm ?: return "—"
        return "${p / 60}:${(p % 60).toString().padStart(2, '0')}"
    }

    val avgSpeedKmh: Double
        get() = if (durationMs <= 0) 0.0 else (distanceM / 1000.0) / (durationMs / 3_600_000.0)
}

/** One GPS fix along a route. */
data class RoutePoint(
    val tsMs: Long,
    val lat: Double,
    val lon: Double,
    val altM: Double = 0.0,
    val accuracyM: Double = 0.0,
    val speedMps: Double = 0.0,
)

/**
 * A route prepared for drawing: points projected to a unit square, plus the
 * per-segment speed used to colour it.
 *
 * Projection uses equirectangular scaling with a cos(latitude) correction on
 * longitude. Over the few kilometres a run covers this is visually
 * indistinguishable from a proper projection, and unlike Mercator it keeps the
 * shape's aspect ratio honest without any library.
 */
data class RouteShape(
    val points: List<Pair<Float, Float>>,   // normalised 0..1
    val speeds: List<Double>,               // m/s, one per point
    val widthMetres: Double,
    val heightMetres: Double,
    val minSpeed: Double,
    val maxSpeed: Double,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    val isEmpty: Boolean get() = points.size < 2
}

object Routes {

    private const val EARTH_R = 6_371_000.0

    fun haversine(a: RoutePoint, b: RoutePoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * EARTH_R * Math.asin(Math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun totalDistance(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var d = 0.0
        for (i in 1 until points.size) d += haversine(points[i - 1], points[i])
        return d
    }

    /**
     * Project a route into a unit square for drawing.
     *
     * Preserves aspect ratio: a route that is long and thin must not be
     * stretched into a square, or the shape stops being recognisable as the
     * place the user actually ran.
     */
    fun shape(points: List<RoutePoint>): RouteShape {
        val usable = points.filter { it.accuracyM <= 0 || it.accuracyM < 40 }
        if (usable.size < 2) {
            return RouteShape(emptyList(), emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val minLat = usable.minOf { it.lat }
        val maxLat = usable.maxOf { it.lat }
        val minLon = usable.minOf { it.lon }
        val maxLon = usable.maxOf { it.lon }
        val midLatRad = Math.toRadians((minLat + maxLat) / 2)

        val metresPerDegLat = 111_320.0
        val metresPerDegLon = 111_320.0 * Math.cos(midLatRad)

        val widthM = (maxLon - minLon) * metresPerDegLon
        val heightM = (maxLat - minLat) * metresPerDegLat
        val span = maxOf(widthM, heightM, 1.0)

        // Centre the smaller axis so the shape sits in the middle of the canvas.
        val xOffset = (span - widthM) / 2.0
        val yOffset = (span - heightM) / 2.0

        val projected = usable.map { p ->
            val x = ((p.lon - minLon) * metresPerDegLon + xOffset) / span
            // Screen y grows downward; latitude grows northward. Flip it.
            val y = 1.0 - ((p.lat - minLat) * metresPerDegLat + yOffset) / span
            x.toFloat() to y.toFloat()
        }

        // Prefer the fix's own speed where the GPS reports one; fall back to
        // distance over time, which is noisier but always available.
        val speeds = usable.mapIndexed { i, p ->
            when {
                p.speedMps > 0 -> p.speedMps
                i == 0 -> 0.0
                else -> {
                    val dt = (p.tsMs - usable[i - 1].tsMs) / 1000.0
                    if (dt <= 0) 0.0 else haversine(usable[i - 1], p) / dt
                }
            }
        }
        val moving = speeds.filter { it > 0.3 }

        return RouteShape(
            points = projected,
            speeds = speeds,
            widthMetres = widthM,
            heightMetres = heightM,
            minSpeed = moving.minOrNull() ?: 0.0,
            maxSpeed = moving.maxOrNull() ?: 0.0,
            minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon,
        )
    }

    /** Positive elevation gain along the route, ignoring GPS altitude jitter. */
    fun elevationGain(points: List<RoutePoint>, noiseFloorM: Double = 1.5): Double {
        if (points.size < 2) return 0.0
        var gain = 0.0
        var reference = points.first().altM
        for (p in points.drop(1)) {
            val delta = p.altM - reference
            if (delta > noiseFloorM) {
                gain += delta
                reference = p.altM
            } else if (delta < -noiseFloorM) {
                reference = p.altM
            }
        }
        return gain
    }
}
