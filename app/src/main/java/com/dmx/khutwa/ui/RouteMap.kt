package com.dmx.khutwa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.dmx.khutwa.data.MapTiles
import com.dmx.khutwa.domain.RoutePoint
import com.dmx.khutwa.domain.RouteShape
import com.dmx.khutwa.domain.Routes
import com.dmx.khutwa.ui.theme.Neon

/**
 * The route, drawn from the GPS trace itself rather than over a map.
 *
 * This works with no network, no API key and no tile downloads, which is why
 * it is the default — the rest of the app makes no network calls at all, and a
 * map would be the single thing that broke that. It also shows something a
 * plain map line does not: the path is **coloured by speed**, so where you sped
 * up and where you struggled is visible at a glance.
 *
 * The optional OpenStreetMap layer sits behind this same path when the user
 * asks for streets; see MapTiles.
 */
@Composable
fun RouteTrace(
    points: List<RoutePoint>,
    arabicDigits: Boolean,
    modifier: Modifier = Modifier,
    showScale: Boolean = true,
    tiles: List<MapTiles.PlacedTile> = emptyList(),
) {
    val shape = remember(points) { Routes.shape(points) }

    if (shape.isEmpty) {
        Box(
            modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "لا يوجد مسار — لم يُلتقط إشارة GPS كافية",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                val w = size.width
                val h = size.height
                fun px(i: Int) = Offset(shape.points[i].first * w, shape.points[i].second * h)

                // Map tiles, when the user asked for them, share the route's own
                // projection so the streets line up with the path exactly.
                for (t in tiles) {
                    val dstLeft = (t.left * w).toInt()
                    val dstTop = (t.top * h).toInt()
                    val dstSize = (t.size * w).toInt().coerceAtLeast(1)
                    drawImage(
                        image = t.bitmap.asImageBitmap(),
                        dstOffset = androidx.compose.ui.unit.IntOffset(dstLeft, dstTop),
                        dstSize = androidx.compose.ui.unit.IntSize(dstSize, dstSize),
                        alpha = 0.85f,
                    )
                }

                // A dark casing under the path keeps it readable wherever it
                // crosses itself or, with tiles on, passes over pale ground.
                val casing = Path().apply {
                    moveTo(px(0).x, px(0).y)
                    for (i in 1 until shape.points.size) lineTo(px(i).x, px(i).y)
                }
                drawPath(
                    casing,
                    color = Color.Black.copy(alpha = 0.45f),
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // Per-segment colour: slow → fast across the run's own range,
                // so the gradient is meaningful for this route rather than
                // against some absolute pace the runner may never hit.
                val span = (shape.maxSpeed - shape.minSpeed).takeIf { it > 0.2 } ?: 1.0
                for (i in 1 until shape.points.size) {
                    val speed = shape.speeds[i]
                    val t = ((speed - shape.minSpeed) / span).coerceIn(0.0, 1.0).toFloat()
                    drawLine(
                        color = lerpSpeedColor(t),
                        start = px(i - 1),
                        end = px(i),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // Start and end markers.
                drawCircle(Neon.Active, radius = 7.dp.toPx(), center = px(0))
                drawCircle(Color.Black.copy(alpha = 0.6f), radius = 3.dp.toPx(), center = px(0))
                val last = shape.points.lastIndex
                drawCircle(Neon.Run, radius = 7.dp.toPx(), center = px(last))
                drawCircle(Color.Black.copy(alpha = 0.6f), radius = 3.dp.toPx(), center = px(last))
            }
        }

        if (showScale) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedLegend(shape, arabicDigits)
                ScaleBar(shape, arabicDigits)
            }
        }
    }
}

@Composable
private fun SpeedLegend(shape: RouteShape, arabicDigits: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${Format.decimal(shape.minSpeed * 3.6, 1, arabicDigits)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Row {
            for (i in 0..5) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 6.dp)
                        .background(lerpSpeedColor(i / 5f))
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "${Format.decimal(shape.maxSpeed * 3.6, 1, arabicDigits)} كم/س",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A distance scale, so the drawing conveys real size rather than just shape. */
@Composable
private fun ScaleBar(shape: RouteShape, arabicDigits: Boolean) {
    val span = maxOf(shape.widthMetres, shape.heightMetres)
    val nice = niceScale(span / 3.0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(36.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (nice >= 1000) "${Format.decimal(nice / 1000.0, 1, arabicDigits)} كم"
            else "${Format.number(nice.toInt(), arabicDigits)} م",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Round a distance to a human-friendly 1/2/5 × 10ⁿ value. */
private fun niceScale(raw: Double): Double {
    if (raw <= 0) return 100.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val normalized = raw / magnitude
    val step = when {
        normalized < 1.5 -> 1.0
        normalized < 3.5 -> 2.0
        normalized < 7.5 -> 5.0
        else -> 10.0
    }
    return step * magnitude
}

/** Slow (violet) → moderate (cyan) → fast (amber). */
private fun lerpSpeedColor(t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return if (clamped < 0.5f) {
        lerp(Neon.Distance, Neon.Steps, clamped * 2f)
    } else {
        lerp(Neon.Steps, Neon.Calories, (clamped - 0.5f) * 2f)
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/**
 * A route with an opt-in street map behind it.
 *
 * The toggle is per-view and starts off. Turning it on is the only action in
 * the entire app that causes a network request, which is why it is a visible
 * switch rather than something that just happens.
 */
@Composable
fun RouteCard(
    points: List<RoutePoint>,
    arabicDigits: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showMap by remember { mutableStateOf(false) }
    var tiles by remember(points) { mutableStateOf<List<MapTiles.PlacedTile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val shape = remember(points) { Routes.shape(points) }

    LaunchedEffect(showMap, points.size) {
        if (!showMap || shape.isEmpty || tiles.isNotEmpty()) return@LaunchedEffect
        loading = true
        failed = false
        MapTiles.load(
            context, shape.minLat, shape.maxLat, shape.minLon, shape.maxLon,
        ) { result ->
            tiles = result
            loading = false
            failed = result.isEmpty()
        }
    }

    Column(modifier.fillMaxWidth()) {
        RouteTrace(points, arabicDigits, tiles = if (showMap) tiles else emptyList())
        if (!shape.isEmpty) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        loading -> "يحمّل الخريطة…"
                        failed -> "تعذّر تحميل الخريطة"
                        showMap -> "خريطة OpenStreetMap"
                        else -> "عرض الشوارع يحتاج تحميل من الإنترنت"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = showMap, onCheckedChange = { showMap = it })
            }
        }
    }
}

/** Elevation profile along the route — where the climbing actually was. */
@Composable
fun ElevationProfile(
    points: List<RoutePoint>,
    arabicDigits: Boolean,
    modifier: Modifier = Modifier,
) {
    val alts = points.map { it.altM }.filter { it != 0.0 }
    if (alts.size < 3) return
    val min = alts.min()
    val max = alts.max()
    val span = (max - min).takeIf { it > 1.0 } ?: 1.0

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val stepX = size.width / (alts.size - 1).coerceAtLeast(1)
            val path = Path()
            path.moveTo(0f, size.height)
            alts.forEachIndexed { i, a ->
                val y = size.height - ((a - min) / span * size.height).toFloat()
                path.lineTo(i * stepX, y)
            }
            path.lineTo(size.width, size.height)
            path.close()
            drawPath(path, color = Neon.Floors.copy(alpha = 0.28f))

            val line = Path()
            alts.forEachIndexed { i, a ->
                val y = size.height - ((a - min) / span * size.height).toFloat()
                if (i == 0) line.moveTo(0f, y) else line.lineTo(i * stepX, y)
            }
            drawPath(line, color = Neon.Floors, style = Stroke(width = 2.dp.toPx()))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "الارتفاع: ${Format.number((max - min).toInt(), arabicDigits)} متر فرق  ·  " +
                "صعود ${Format.number(Routes.elevationGain(points).toInt(), arabicDigits)} متر",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
