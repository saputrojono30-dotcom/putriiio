package id.dlms.dsp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dlms.dsp.dsp.*
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private const val DB_MIN = -42.0
private const val DB_MAX = 18.0

/** Saluran yang sedang dibuka: ("in",0..1), ("out",0..3) atau null untuk tab GLOBAL. */
data class Active(val isOut: Boolean, val idx: Int)

private class Handle(val id: String, val keyF: String, val keyG: String?, val x: Float, val y: Float, val label: String)

@Composable
fun ResponsePlot(
    params: Map<String, Double>,
    active: Active?,
    onEdit: (key: String, value: Double, final: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val curves = remember(params) { computeCurves(params) }
    var showOut by remember { mutableStateOf(listOf(true, true, true, true)) }
    var showSum by remember { mutableStateOf(true) }
    var dragId by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            for (o in 0 until 4) {
                FilterChip(
                    selected = showOut[o], onClick = { showOut = showOut.toMutableList().also { l -> l[o] = !l[o] } },
                    label = { Text("OUT ${o + 1}", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OUT_COLOR[o].copy(alpha = 0.22f),
                        selectedLabelColor = OUT_COLOR[o], labelColor = DIM)
                )
            }
            FilterChip(
                selected = showSum, onClick = { showSum = !showSum },
                label = { Text("Σ", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.White.copy(alpha = 0.18f), selectedLabelColor = Color.White, labelColor = DIM)
            )
        }

        val density = LocalDensity.current
        val padL = with(density) { 34.dp.toPx() }
        val padB = with(density) { 16.dp.toPx() }
        val padT = with(density) { 6.dp.toPx() }
        val padR = with(density) { 6.dp.toPx() }
        val hitR = with(density) { 26.dp.toPx() }

        fun xOf(f: Double, w: Float): Float {
            val lo = log10(F_MIN); val hi = log10(F_MAX)
            return padL + ((log10(f) - lo) / (hi - lo)).toFloat() * (w - padL - padR)
        }
        fun yOf(db: Double, h: Float): Float {
            val d = db.coerceIn(DB_MIN, DB_MAX)
            return padT + (1f - ((d - DB_MIN) / (DB_MAX - DB_MIN)).toFloat()) * (h - padT - padB)
        }
        fun fOf(x: Float, w: Float): Double {
            val lo = log10(F_MIN); val hi = log10(F_MAX)
            val t = ((x - padL) / (w - padL - padR)).coerceIn(0f, 1f)
            return 10.0.pow(lo + t * (hi - lo))
        }
        fun dbOf(y: Float, h: Float): Double {
            val t = 1f - (y - padT) / (h - padT - padB)
            return DB_MIN + t * (DB_MAX - DB_MIN)
        }

        // Kurva yang jadi tokoh: kanal aktif, atau penjumlahan di tab GLOBAL.
        val sumCx = remember(curves, showOut, params) {
            var acc = Cx.zeros(N_POINTS)
            for (o in 0 until 4) {
                if (showOut[o] && (params[oKey(o, "mute")] ?: 1.0) == 0.0) acc = acc.plus(curves.out[o])
            }
            acc
        }
        val activeCx: Cx? = when {
            active == null -> if (showSum) sumCx else null
            active.isOut -> if (showOut[active.idx]) curves.out[active.idx] else null
            else -> curves.inp[active.idx]
        }
        val heroColor = when {
            active == null -> Color.White
            active.isOut -> OUT_COLOR[active.idx]
            else -> IN_COLOR[active.idx]
        }

        fun handles(w: Float, h: Float): List<Handle> {
            if (active == null || activeCx == null) return emptyList()
            val pre = if (active.isOut) "o${active.idx + 1}." else "i${active.idx + 1}."
            fun g(k: String) = params[pre + k] ?: 0.0
            fun curveDb(f: Double): Double {
                val t = (log10(f) - log10(F_MIN)) / (log10(F_MAX) - log10(F_MIN))
                return activeCx.db((t * (N_POINTS - 1)).roundToInt().coerceIn(0, N_POINTS - 1))
            }
            val hs = ArrayList<Handle>()
            if (g("hpt") != 0.0) { val f = g("hpf"); hs += Handle("hpf", pre + "hpf", null, xOf(f, w), yOf(curveDb(f), h), "HPF ${f.roundToInt()} Hz") }
            if (g("lpt") != 0.0) { val f = g("lpf"); hs += Handle("lpf", pre + "lpf", null, xOf(f, w), yOf(curveDb(f), h), "LPF ${f.roundToInt()} Hz") }
            for (b in 1..N_PEQ) {
                if (g("p$b.t") == 0.0) continue
                val f = g("p$b.f")
                hs += Handle("p$b", pre + "p$b.f", pre + "p$b.g", xOf(f, w), yOf(curveDb(f), h),
                    "B$b ${f.roundToInt()} Hz ${String.format("%+.1f", g("p$b.g"))} dB Q ${String.format("%.2f", g("p$b.q"))}")
            }
            return hs
        }

        val paramsNow by rememberUpdatedState(params)
        val activeNow by rememberUpdatedState(active)
        val handlesFn by rememberUpdatedState<(Float, Float) -> List<Handle>>({ w, h -> handles(w, h) })
        val onEditNow by rememberUpdatedState(onEdit)
        val xf: (Double, Float) -> Float = { f, w -> xOf(f, w) }
        val yf: (Double, Float) -> Float = { d, h -> yOf(d, h) }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color(0xFF0A1226), RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        // Pegangan terdekat dalam jari-radius; di luar itu biarkan halaman menggulung.
                        val hit = handlesFn(w, h)
                            .minByOrNull { (it.x - down.position.x).let { dx -> dx * dx } + (it.y - down.position.y).let { dy -> dy * dy } }
                            ?.takeIf { (it.x - down.position.x).let { dx -> dx * dx } + (it.y - down.position.y).let { dy -> dy * dy } <= hitR * hitR }
                            ?: return@awaitEachGesture
                        down.consume()
                        dragId = hit.id
                        // selisih jari terhadap gain saat ini, supaya pegangan tidak melompat
                        val dy0 = if (hit.keyG != null) dbOf(down.position.y, h) - (paramsNow[hit.keyG] ?: 0.0) else 0.0
                        var lastF = paramsNow[hit.keyF] ?: 1000.0
                        var lastG = hit.keyG?.let { paramsNow[it] ?: 0.0 }
                        drag(down.id) { ch ->
                            ch.consume()
                            lastF = fOf(ch.position.x, w).roundToInt().toDouble()
                            onEditNow(hit.keyF, lastF, false)
                            if (hit.keyG != null) {
                                lastG = ((dbOf(ch.position.y, h) - dy0).coerceIn(-18.0, 18.0) * 10).roundToInt() / 10.0
                                onEditNow(hit.keyG, lastG!!, false)
                            }
                        }
                        // Kirim lagi tanpa pembatas laju: nilai terakhir harus sampai.
                        onEditNow(hit.keyF, lastF, true)
                        if (hit.keyG != null) onEditNow(hit.keyG, lastG!!, true)
                        dragId = null
                    }
                }
        ) {
            val w = size.width; val h = size.height
            drawGrid(w, h, xf, yf, padL, padR, padT, padB)

            // kurva output (redup) di belakang tokoh
            for (o in 0 until 4) {
                if (!showOut[o]) continue
                if (active != null && active.isOut && active.idx == o) continue
                drawCurve(curves.out[o], w, h, xf, yf, OUT_COLOR[o].copy(alpha = if (active == null) 0.55f else 0.35f), 1.4f, null)
            }
            if (active != null && showSum) drawCurve(sumCx, w, h, xf, yf, Color.White.copy(alpha = 0.45f), 1.4f, null)
            if (activeCx != null) drawCurve(activeCx, w, h, xf, yf, heroColor, 3.2f, heroColor.copy(alpha = 0.10f))

            for (hd in handles(w, h)) {
                val on = hd.id == dragId
                drawCircle(Color(0xFF0A1226), 11.dp.toPx(), Offset(hd.x, hd.y))
                drawCircle(heroColor, 9.dp.toPx(), Offset(hd.x, hd.y), style = Stroke(width = if (on) 4.dp.toPx() else 2.5.dp.toPx()))
                if (on) {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE; textSize = 12.sp.toPx(); isAntiAlias = true
                    }
                    val tw = paint.measureText(hd.label)
                    val tx = (hd.x - tw / 2).coerceIn(padL, w - tw - 4.dp.toPx())
                    val ty = if (hd.y > 40.dp.toPx()) hd.y - 18.dp.toPx() else hd.y + 30.dp.toPx()
                    drawRect(Color(0xCC000000), Offset(tx - 4.dp.toPx(), ty - 14.dp.toPx()), Size(tw + 8.dp.toPx(), 20.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText(hd.label, tx, ty, paint)
                }
            }
        }
        if (active != null) {
            Text(
                "Seret titik di kurva: kiri-kanan = frekuensi, atas-bawah = gain (PEQ). Ketuk angka di panel untuk nilai persis.",
                color = DIM, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

private fun DrawScope.drawGrid(
    w: Float, h: Float, xOf: (Double, Float) -> Float, yOf: (Double, Float) -> Float,
    padL: Float, padR: Float, padT: Float, padB: Float
) {
    val paint = android.graphics.Paint().apply {
        color = 0xFF7C8CA8.toInt(); textSize = 10.sp.toPx(); isAntiAlias = true
    }
    // garis frekuensi
    val minor = listOf(30, 40, 60, 70, 80, 90, 300, 400, 600, 700, 800, 900, 3000, 4000, 6000, 7000, 8000, 9000)
    for (f in minor) {
        val x = xOf(f.toDouble(), w)
        drawLine(GRIDSUB, Offset(x, padT), Offset(x, h - padB), 1f)
    }
    for ((f, t) in listOf(20 to "20", 50 to "50", 100 to "100", 200 to "200", 500 to "500", 1000 to "1k",
        2000 to "2k", 5000 to "5k", 10000 to "10k", 20000 to "20k")) {
        val x = xOf(f.toDouble(), w)
        drawLine(GRID, Offset(x, padT), Offset(x, h - padB), 1f)
        val tw = paint.measureText(t)
        drawContext.canvas.nativeCanvas.drawText(t, (x - tw / 2).coerceIn(padL - 10.dp.toPx(), w - tw), h - 3.dp.toPx(), paint)
    }
    // garis dB
    var db = -36
    while (db <= 18) {
        val y = yOf(db.toDouble(), h)
        drawLine(if (db == 0) Color(0xFF4C6290) else GRID, Offset(padL, y), Offset(w - padR, y), if (db == 0) 1.6f else 1f)
        val t = "$db"
        drawContext.canvas.nativeCanvas.drawText(t, padL - 4.dp.toPx() - paint.measureText(t), y + 3.dp.toPx(), paint)
        db += 12
    }
}

private fun DrawScope.drawCurve(
    cx: Cx, w: Float, h: Float, xOf: (Double, Float) -> Float, yOf: (Double, Float) -> Float,
    color: Color, widthDp: Float, fill: Color?
) {
    val path = Path()
    for (i in 0 until N_POINTS) {
        val x = xOf(FREQS[i], w); val y = yOf(cx.db(i), h)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (fill != null) {
        val area = Path().apply {
            addPath(path)
            lineTo(xOf(F_MAX, w), yOf(DB_MIN, h)); lineTo(xOf(F_MIN, w), yOf(DB_MIN, h)); close()
        }
        drawPath(area, Brush.verticalGradient(listOf(fill, Color.Transparent)))
    }
    drawPath(path, color, style = Stroke(width = widthDp.dp.toPx()))
}
