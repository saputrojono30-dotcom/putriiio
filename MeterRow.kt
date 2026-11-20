package id.dlms.dsp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dlms.dsp.dsp.Meters

private const val MIN_DB = -60f

private fun dbColor(db: Float): Color = when {
    db > -3f -> RED
    db > -12f -> AMBER
    else -> GREEN
}

/** Enam batang vertikal: IN 1, IN 2, OUT 1..4. Garis merah dari atas = gain reduction limiter. */
@Composable
fun MeterRow(m: Meters, modifier: Modifier = Modifier) {
    val names = listOf("IN1", "IN2", "O1", "O2", "O3", "O4")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 6) {
            val db = m.level[i].coerceIn(MIN_DB, 6f)
            val gr = if (i >= 2) m.gr[i - 2] else 0f
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                    val r = CornerRadius(3.dp.toPx())
                    drawRoundRect(Color(0xFF0A1226), Offset.Zero, size, r)
                    val frac = ((db - MIN_DB) / (0f - MIN_DB + 6f)).coerceIn(0f, 1f)
                    val bh = size.height * frac
                    drawRoundRect(dbColor(db), Offset(0f, size.height - bh), Size(size.width, bh), r)
                    if (gr < -0.1f) {
                        val gh = size.height * ((-gr).coerceAtMost(20f) / 20f)
                        drawRect(RED.copy(alpha = 0.9f), Offset(0f, 0f), Size(size.width, gh))
                    }
                }
                Text(names[i], color = if (i < 2) DIM else OUT_COLOR[i - 2], fontSize = 10.sp)
                Text(if (db <= MIN_DB + 0.5f) "—" else String.format("%.0f", db), color = DIM, fontSize = 10.sp)
            }
        }
    }
}
