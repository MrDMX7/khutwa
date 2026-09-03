package com.dmx.khutwa.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Optional OpenStreetMap raster tiles behind a route.
 *
 * Written by hand rather than pulling in a map library, for three reasons: the
 * whole job is ~100 lines of Web Mercator arithmetic plus an HTTP GET; a map
 * SDK would drag in a large dependency for one screen; and doing it directly
 * means **this file is the only place in the app that touches the network**,
 * so the "no network unless you ask" property stays auditable.
 *
 * Nothing here runs unless the user explicitly turns the map on for a route.
 * Tiles are cached on disk, so revisiting a run costs no data at all.
 *
 * OSM's tile policy requires an identifying User-Agent and rules out heavy
 * automated use; occasional personal viewing with an on-disk cache is within it.
 */
object MapTiles {

    private const val TILE_SIZE = 256
    private const val USER_AGENT = "Khutwa/2.1 (personal step tracker; contact via device owner)"
    private const val MAX_TILES = 24
    private const val CACHE_DIR = "osm-tiles"

    private val io = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "khutwa-tiles").apply { isDaemon = true }
    }

    data class TileKey(val z: Int, val x: Int, val y: Int)

    /** Where a tile sits, in the same 0..1 space the route is projected into. */
    data class PlacedTile(val key: TileKey, val bitmap: Bitmap, val left: Float, val top: Float, val size: Float)

    // ---- Web Mercator -----------------------------------------------------

    fun lonToTileX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * (1 shl z)

    fun latToTileY(lat: Double, z: Int): Double {
        val rad = Math.toRadians(lat)
        return (1.0 - asinh(tan(rad)) / PI) / 2.0 * (1 shl z)
    }

    fun tileXToLon(x: Double, z: Int): Double = x / (1 shl z) * 360.0 - 180.0

    fun tileYToLat(y: Double, z: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl z)
        return Math.toDegrees(atan(sinh(n)))
    }

    /**
     * Highest zoom whose tile count for this bounding box stays under
     * [MAX_TILES] — more detail than that is wasted on a small route card and
     * would mean a lot of downloading.
     */
    fun chooseZoom(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Int {
        for (z in 17 downTo 2) {
            val x0 = floor(lonToTileX(minLon, z)).toInt()
            val x1 = floor(lonToTileX(maxLon, z)).toInt()
            val y0 = floor(latToTileY(maxLat, z)).toInt()
            val y1 = floor(latToTileY(minLat, z)).toInt()
            val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1).toLong()
            if (count in 1..MAX_TILES) return z
        }
        return 12
    }

    // ---- fetching ---------------------------------------------------------

    /**
     * Load the tiles covering a bounding box, cache-first.
     *
     * [onResult] receives tiles placed in the same normalised square the route
     * uses, so the caller can draw them without repeating the projection.
     */
    fun load(
        context: Context,
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double,
        onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> },
        onResult: (List<PlacedTile>) -> Unit,
    ) {
        io.execute {
            val z = chooseZoom(minLat, maxLat, minLon, maxLon)

            // The route is projected into a square whose side is the larger of
            // its two spans, so the tile placement has to use the same square
            // or the map and the path will not line up.
            val midLatRad = Math.toRadians((minLat + maxLat) / 2)
            val mPerDegLat = 111_320.0
            val mPerDegLon = 111_320.0 * Math.cos(midLatRad)
            val widthM = (maxLon - minLon) * mPerDegLon
            val heightM = (maxLat - minLat) * mPerDegLat
            val spanM = maxOf(widthM, heightM, 1.0)
            val padLon = (spanM - widthM) / 2.0 / mPerDegLon
            val padLat = (spanM - heightM) / 2.0 / mPerDegLat

            val westLon = minLon - padLon
            val eastLon = maxLon + padLon
            val southLat = minLat - padLat
            val northLat = maxLat + padLat

            val x0 = floor(lonToTileX(westLon, z)).toInt()
            val x1 = floor(lonToTileX(eastLon, z)).toInt()
            val y0 = floor(latToTileY(northLat, z)).toInt()
            val y1 = floor(latToTileY(southLat, z)).toInt()

            val keys = mutableListOf<TileKey>()
            for (x in x0..x1) for (y in y0..y1) keys += TileKey(z, x, y)
            if (keys.size > MAX_TILES * 2) {
                onResult(emptyList())
                return@execute
            }

            // Fractional tile coordinates of the square's corners, used to map
            // each tile into 0..1.
            val fx0 = lonToTileX(westLon, z)
            val fx1 = lonToTileX(eastLon, z)
            val fy0 = latToTileY(northLat, z)
            val fy1 = latToTileY(southLat, z)
            val tilesW = (fx1 - fx0).takeIf { it > 0 } ?: 1.0
            val tilesH = (fy1 - fy0).takeIf { it > 0 } ?: 1.0

            val out = mutableListOf<PlacedTile>()
            keys.forEachIndexed { index, key ->
                val bmp = fetch(context, key)
                if (bmp != null) {
                    out += PlacedTile(
                        key = key,
                        bitmap = bmp,
                        left = ((key.x - fx0) / tilesW).toFloat(),
                        top = ((key.y - fy0) / tilesH).toFloat(),
                        size = (1.0 / tilesW).toFloat(),
                    )
                }
                onProgress(index + 1, keys.size)
            }
            onResult(out)
        }
    }

    private fun cacheFile(context: Context, key: TileKey): File {
        val dir = File(context.cacheDir, "$CACHE_DIR/${key.z}/${key.x}").apply { mkdirs() }
        return File(dir, "${key.y}.png")
    }

    private fun fetch(context: Context, key: TileKey): Bitmap? {
        val file = cacheFile(context, key)
        if (file.exists() && file.length() > 0) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
        }
        return try {
            val url = URL("https://tile.openstreetmap.org/${key.z}/${key.x}/${key.y}.png")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                // OSM rejects requests without an identifying agent.
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 12_000
                readTimeout = 12_000
            }
            conn.inputStream.use { input ->
                val bytes = input.readBytes()
                file.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Size of the on-disk tile cache, so Settings can show and clear it. */
    fun cacheSizeBytes(context: Context): Long =
        File(context.cacheDir, CACHE_DIR).walkBottomUp()
            .filter { it.isFile }.sumOf { it.length() }

    fun clearCache(context: Context) {
        File(context.cacheDir, CACHE_DIR).deleteRecursively()
    }
}
