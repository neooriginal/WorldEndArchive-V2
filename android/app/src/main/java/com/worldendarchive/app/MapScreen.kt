package com.worldendarchive.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.*

/** Natural Earth base map (assets/basemap.json): land and lakes as paths in "world pixels" (256 at zoom 0) + cities. */
class BaseMap(val land: Path, val lakes: Path, val cities: List<City>) {
    class City(val name: String, val lon: Double, val lat: Double, val rank: Int, val capital: Boolean)

    companion object {
        @Volatile private var cached: BaseMap? = null

        fun load(ctx: Context): BaseMap = cached ?: synchronized(this) {
            cached ?: run {
                val j = JSONObject(ctx.assets.open("basemap.json").bufferedReader().use { it.readText() })
                val s = j.getDouble("scale")
                fun ring(a: org.json.JSONArray, into: Path) {
                    for (i in 0 until a.length() step 2) {
                        val x = wx(a.getInt(i) / s).toFloat(); val y = wy(a.getInt(i + 1) / s).toFloat()
                        if (i == 0) into.moveTo(x, y) else into.lineTo(x, y)
                    }
                    into.close()
                }
                val land = Path(); val lakes = Path()
                val cs = j.getJSONArray("countries")
                for (i in 0 until cs.length()) { val r = cs.getJSONObject(i).getJSONArray("r"); for (k in 0 until r.length()) ring(r.getJSONArray(k), land) }
                val ls = j.getJSONArray("lakes")
                for (i in 0 until ls.length()) ring(ls.getJSONArray(i), lakes)
                val ct = j.getJSONArray("cities")
                val cities = List(ct.length()) { i ->
                    val c = ct.getJSONArray(i)
                    City(c.getString(0), c.getInt(1) / s, c.getInt(2) / s, c.getInt(3), c.getInt(6) == 1)
                }
                BaseMap(land, lakes, cities).also { cached = it }
            }
        }
    }
}

private const val TILE = 256.0
fun wx(lon: Double) = (lon + 180) / 360 * TILE
fun wy(lat: Double): Double { val s = sin(lat.coerceIn(-85.0, 85.0) * PI / 180); return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * TILE }
fun unx(x: Double) = x / TILE * 360 - 180
fun uny(y: Double) = atan(sinh(PI - 2 * PI * y / TILE)) * 180 / PI

fun distanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
    return 12742 * asin(sqrt(h))
}

fun bearingDeg(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val y = sin(Math.toRadians(bLon - aLon)) * cos(Math.toRadians(bLat))
    val x = cos(Math.toRadians(aLat)) * sin(Math.toRadians(bLat)) - sin(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * cos(Math.toRadians(bLon - aLon))
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

fun fmtKm(d: Double) = when { d < 1 -> "${(d * 1000).roundToInt()} m"; d < 20 -> "%.1f km".format(d); else -> "${d.roundToInt()} km" }
fun compassDir(b: Double) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((b / 45).roundToInt()) % 8]

val PLACE_STYLE = mapOf(
    "hospital" to ("🏥" to Color(0xFFFF5C6C)), "shelter" to ("🛡️" to Color(0xFFFFCF70)), "rescue station" to ("🚑" to Color(0xFFFF8F3D)),
    "hut" to ("🛖" to Color(0xFF7EE081)), "airport" to ("✈️" to Color(0xFF7AA7FF)), "port" to ("⚓" to Color(0xFF5AD1FF)),
    "power plant" to ("⚡" to Color(0xFFE3D26F)), "dam" to ("🌊" to Color(0xFF3FB5FF)), "water works" to ("🚰" to Color(0xFF3FD5FF)),
    "embassy" to ("🏛️" to Color(0xFFC79BFF)),
)
private val CAT_DE = mapOf("hospital" to "Krankenhaus", "shelter" to "Schutzraum", "rescue station" to "Rettungsstation", "hut" to "Hütte",
    "airport" to "Flughafen", "port" to "Hafen", "power plant" to "Kraftwerk", "dam" to "Staudamm", "water works" to "Wasserwerk", "embassy" to "Botschaft")

/** GPS position (works offline) while the screen is open; null until a fix arrives or without permission. */
@SuppressLint("MissingPermission")
@Composable
fun rememberLocation(enabled: Boolean): State<Location?> {
    val ctx = LocalContext.current
    val loc = remember { mutableStateOf<Location?>(null) }
    DisposableEffect(enabled) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { loc.value = it }
        if (enabled) {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                runCatching { lm.getLastKnownLocation(p)?.let { if (loc.value == null) loc.value = it } }
                runCatching { if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 2000L, 2f, listener, Looper.getMainLooper()) }
            }
        }
        onDispose { runCatching { lm.removeUpdates(listener) } }
    }
    return loc
}

/** Compass heading in degrees (0 = north) from the rotation-vector sensor; null if the device has none. */
@Composable
fun rememberHeading(enabled: Boolean): State<Float?> {
    val ctx = LocalContext.current
    val heading = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(enabled) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val rot = FloatArray(9); val ori = FloatArray(3)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val h = ((Math.toDegrees(ori[0].toDouble()) + 360) % 360).toFloat()
                val old = heading.value
                heading.value = if (old == null) h else { var d = h - old; if (d > 180) d -= 360; if (d < -180) d += 360; (old + d * 0.2f + 360) % 360 }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (enabled && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    return heading
}

@Composable
fun MapScreen(a: Archive, lang: String, back: () -> Unit) {
    val de = lang == "de"
    val ctx = LocalContext.current
    var base by remember { mutableStateOf<BaseMap?>(null) }
    LaunchedEffect(Unit) { base = withContext(Dispatchers.Default) { BaseMap.load(ctx) } }

    var lon by rememberSaveable { mutableDoubleStateOf(10.0) }
    var lat by rememberSaveable { mutableDoubleStateOf(30.0) }
    var zoom by rememberSaveable { mutableDoubleStateOf(1.6) }
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<Place?>(null) }
    var places by remember { mutableStateOf(emptyList<Place>()) }
    var showCompass by rememberSaveable { mutableStateOf(false) }
    var followMe by remember { mutableStateOf(false) }

    val granted = { ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    var hasPerm by remember { mutableStateOf(granted()) }
    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasPerm = granted() }
    val me by rememberLocation(hasPerm)
    val heading by rememberHeading(showCompass)
    LaunchedEffect(me, followMe) { val m = me; if (followMe && m != null) { lon = m.longitude; lat = m.latitude; zoom = max(zoom, 9.0); followMe = false } }

    val minZoom = if (size.width > 0f) log2(size.width / TILE) else 0.8
    LaunchedEffect(minZoom) { if (zoom < minZoom) zoom = minZoom }
    val cats = remember(lang) { a.placeCategories(lang) }
    val scale = 2.0.pow(zoom)
    fun toScreen(plon: Double, plat: Double) = Offset(((wx(plon) - wx(lon)) * scale + size.width / 2).toFloat(), ((wy(plat) - wy(lat)) * scale + size.height / 2).toFloat())
    fun toGeo(x: Float, y: Float) = unx(wx(lon) + (x - size.width / 2) / scale) to uny(wy(lat) + (y - size.height / 2) / scale)

    LaunchedEffect(lon, lat, zoom, size, filter, lang) {
        if (!a.hasPlaces || size.width == 0f) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        val (lon0, lat1) = toGeo(0f, 0f); val (lon1, lat0) = toGeo(size.width, size.height)
        val minImp = if (zoom < 3) 5 else if (zoom < 4.5) 4 else 3
        places = withContext(Dispatchers.IO) { a.places(lang, lat0, lat1, max(-180.0, lon0), min(180.0, lon1), minImp, filter) }
    }
    val nearest = remember(me?.latitude?.times(1000)?.roundToInt(), me?.longitude?.times(1000)?.roundToInt(), filter, lang) {
        val m = me ?: return@remember emptyList()
        var rows = emptyList<Place>()
        for (d in listOf(1.0, 3.0, 8.0)) { rows = a.places(lang, m.latitude - d, m.latitude + d, m.longitude - d * 1.5, m.longitude + d * 1.5, 0, filter, 400); if (rows.size >= 8) break }
        rows.sortedBy { distanceKm(m.latitude, m.longitude, it.lat, it.lon) }.take(12)
    }
    val catName = { c: String -> if (de) CAT_DE[c] ?: c else c }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar(lang, back) { Text(if (de) "Karte" else "Map", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (cats.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(if (de) "Alle" else "All", filter == null) { filter = null }
            cats.forEach { (c, _) -> FilterChip("${PLACE_STYLE[c]?.first ?: "📍"} ${catName(c)}", filter == c) { filter = if (filter == c) null else c } }
        }
        val shape = RoundedCornerShape(18.dp)
        Box(Modifier.fillMaxWidth().height(460.dp).clip(shape).border(1.dp, C.border, shape)) {
            val b = base
            Canvas(Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val s = 2.0.pow(zoom)
                        if (gestureZoom != 1f) {
                            val gLon = unx(wx(lon) + (centroid.x - size.width / 2) / s); val gLat = uny(wy(lat) + (centroid.y - size.height / 2) / s)
                            zoom = (zoom + log2(gestureZoom.toDouble())).coerceIn(log2(size.width / TILE), 16.0)
                            val s2 = 2.0.pow(zoom)
                            lon = unx(wx(gLon) - (centroid.x - size.width / 2) / s2); lat = uny(wy(gLat) - (centroid.y - size.height / 2) / s2)
                        }
                        val s3 = 2.0.pow(zoom)
                        lon = unx(wx(lon) - pan.x / s3).let { if (it > 180) it - 360 else if (it < -180) it + 360 else it }
                        lat = uny((wy(lat) - pan.y / s3).coerceIn(1.0, TILE - 1))
                    }
                }
                .pointerInput(places, filter) {
                    detectTapGestures(onDoubleTap = { zoom = min(16.0, zoom + 1) }) { p ->
                        val hit = places.filter { filter == null || it.category == filter }
                            .minByOrNull { val o = toScreen(it.lon, it.lat); (o - p).getDistance() }
                        if (hit != null && (toScreen(hit.lon, hit.lat) - p).getDistance() < 48f) target = a.place(hit.id)
                    }
                }) {
                size = this.size
                drawRect(Color(0xFF04121B))
                if (b != null) {
                    withTransform({
                        translate((size.width / 2 - wx(lon) * scale).toFloat(), (size.height / 2 - wy(lat) * scale).toFloat())
                        scale(scale.toFloat(), scale.toFloat(), Offset.Zero)
                    }) {
                        drawPath(b.land, Color(0xFF0F1A22))
                        drawPath(b.land, Color(0x3800F2FF), style = Stroke(width = (1.2 / scale).toFloat()))
                        drawPath(b.lakes, Color(0xFF04121B))
                    }
                    val maxRank = when { zoom < 2.5 -> 1; zoom < 3.5 -> 3; zoom < 4.5 -> 5; zoom < 6 -> 7; else -> 10 }
                    val paint = android.graphics.Paint().apply { color = 0xC8DCE6F0.toInt(); textSize = 30f; isAntiAlias = true }
                    val taken = mutableListOf<Offset>()
                    for (c in b.cities) {
                        if (c.rank > maxRank) continue
                        val o = toScreen(c.lon, c.lat)
                        if (o.x < -80 || o.y < -40 || o.x > size.width + 80 || o.y > size.height + 40) continue
                        if (taken.any { abs(it.x - o.x) < 160 && abs(it.y - o.y) < 36 }) continue
                        taken += o
                        drawCircle(if (c.capital) Color.White else Color(0x99FFFFFF), if (c.capital) 6f else 4f, o)
                        drawContext.canvas.nativeCanvas.drawText(c.name, o.x + 12, o.y + 10, paint)
                    }
                }
                val r = if (zoom < 4) 13f else 17f
                for (p in places) {
                    if (filter != null && p.category != filter) continue
                    val o = toScreen(p.lon, p.lat)
                    if (o.x < -20 || o.y < -20 || o.x > size.width + 20 || o.y > size.height + 20) continue
                    val col = PLACE_STYLE[p.category]?.second ?: C.cyan
                    drawCircle(Color(0xFF050505), (if (target?.id == p.id) r + 8 else r) + 3, o)
                    drawCircle(col, if (target?.id == p.id) r + 8 else r, o)
                }
                me?.let { m ->
                    val o = toScreen(m.longitude, m.latitude)
                    target?.let { t -> drawLine(C.cyan.copy(alpha = 0.6f), o, toScreen(t.lon, t.lat), 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 18f))) }
                    drawCircle(C.cyan.copy(alpha = 0.2f), 40f, o)
                    drawCircle(Color.White, 17f, o); drawCircle(C.cyan, 13f, o)
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapButton("+") { zoom = min(16.0, zoom + 1) }
                MapButton("−") { zoom = max(minZoom, zoom - 1) }
                MapButton("⌖") { if (!hasPerm) askPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)); followMe = true }
                MapButton("🧭") { showCompass = !showCompass; if (!hasPerm) askPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
            }
            if (showCompass) CompassCard(heading, me, target, de, Modifier.align(Alignment.TopStart).padding(10.dp))
            Text("%.2f, %.2f · z%.1f".format(lat, lon, zoom) + if (a.hasPlaces) " · ${places.size}" else "", fontFamily = Mono, fontSize = 11.sp, color = C.muted,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0x99050505), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        if (!a.hasPlaces) Text(if (de) "Die Kartenebene (Krankenhäuser, Schutzräume, Rettungsstationen…) kommt mit dem nächsten Archiv-Update. Weltkarte, Städte, Position und Kompass funktionieren schon offline."
            else "The map layer (hospitals, shelters, rescue stations…) arrives with the next archive update. The world map, cities, your position and the compass already work offline.",
            color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        target?.let { t ->
            Glass(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Label("${PLACE_STYLE[t.category]?.first ?: "📍"} ${catName(t.category)}" + (t.city?.let { " · $it" } ?: "") + (t.country?.let { " · $it" } ?: ""))
                Text(t.name, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                if (t.summary.isNotBlank()) Text(t.summary, color = C.muted, fontSize = 15.sp, lineHeight = 21.sp)
                t.features.forEach { Text("• $it", color = C.text, fontSize = 14.sp) }
                me?.let { m ->
                    val d = distanceKm(m.latitude, m.longitude, t.lat, t.lon); val bdeg = bearingDeg(m.latitude, m.longitude, t.lat, t.lon)
                    Text("${fmtKm(d)} · ${bdeg.roundToInt()}° ${compassDir(bdeg)}", fontFamily = Mono, color = C.cyan, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Text("%.5f, %.5f".format(t.lat, t.lon), fontFamily = Mono, color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (de) "🧭 Dorthin zeigen" else "🧭 Point me there") {
                        showCompass = true
                        if (!hasPerm) askPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                    Chip(if (de) "Schließen" else "Close") { target = null }
                }
            }
        }
        if (nearest.isNotEmpty()) {
            SectionTitle(if (de) "In deiner Nähe" else "Nearest to you")
            val m = me!!
            nearest.forEach { p ->
                val d = distanceKm(m.latitude, m.longitude, p.lat, p.lon); val bdeg = bearingDeg(m.latitude, m.longitude, p.lat, p.lon)
                val s = RoundedCornerShape(14.dp)
                Column(Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(s).background(C.glass).border(1.dp, C.border, s)
                    .clickable { target = a.place(p.id); lon = p.lon; lat = p.lat; zoom = max(zoom, 11.0) }.padding(14.dp)) {
                    Text("${PLACE_STYLE[p.category]?.first ?: "📍"} ${p.name}", color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Label("${fmtKm(d)} ${compassDir(bdeg)} · ${catName(p.category)}")
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(text, fontFamily = Mono, fontSize = 13.sp, color = if (on) C.bg else C.muted,
        modifier = Modifier.clip(shape).border(1.dp, if (on) C.cyan else C.border, shape).background(if (on) C.cyan else C.glass)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
private fun MapButton(text: String, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xCC050505)).border(1.dp, C.border, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontSize = 20.sp) }
}

@Composable
private fun CompassCard(heading: Float?, me: Location?, target: Place?, de: Boolean, modifier: Modifier) {
    Column(modifier.width(180.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xD9050505)).border(1.dp, C.border, RoundedCornerShape(18.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().rotate(-(heading ?: 0f)).border(2.dp, C.cyan.copy(alpha = 0.45f), CircleShape)) {
                Text("N", color = C.red, fontWeight = FontWeight.Bold, fontFamily = Mono, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
                Text("S", color = C.muted, fontFamily = Mono, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                Text("E", color = C.muted, fontFamily = Mono, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
                Text("W", color = C.muted, fontFamily = Mono, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
            }
            if (target != null && me != null) {
                val b = bearingDeg(me.latitude, me.longitude, target.lat, target.lon)
                Canvas(Modifier.fillMaxSize().rotate((b - (heading ?: 0f)).toFloat())) {
                    val c = center
                    val p = Path().apply { moveTo(c.x, c.y - size.height * 0.36f); lineTo(c.x - 14f, c.y + 6f); lineTo(c.x + 14f, c.y + 6f); close() }
                    drawPath(p, C.cyan)
                    drawCircle(C.cyan, 6f, c)
                }
            }
        }
        val txt = when {
            me == null -> if (de) "Warte auf GPS…" else "Waiting for GPS…"
            target != null -> {
                val d = distanceKm(me.latitude, me.longitude, target.lat, target.lon); val b = bearingDeg(me.latitude, me.longitude, target.lat, target.lon)
                "${fmtKm(d)}\n${target.name.substringBefore(" (")}\n${b.roundToInt()}° ${compassDir(b)}" + if (heading == null) (if (de) "\n(kein Kompass-Sensor)" else "\n(no compass sensor)") else ""
            }
            heading != null -> (if (de) "Richtung " else "Heading ") + "${heading.roundToInt()}° ${compassDir(heading.toDouble())}"
            else -> if (de) "Kein Kompass-Sensor" else "No compass sensor"
        }
        Text(txt, color = C.text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
