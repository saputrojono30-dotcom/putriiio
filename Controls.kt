package id.dlms.dsp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dlms.dsp.dsp.fmtVal
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Kartu pengelompokan seperti QGroupBox di aplikasi laptop. */
@Composable
fun Group(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(PANEL, RoundedCornerShape(10.dp))
            .border(1.dp, GRID, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = DIM, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        content()
    }
}

fun fmtUnit(v: Double, decimals: Int, unit: String): String =
    (if (decimals <= 0) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.${decimals}f", v)) + unit

/**
 * Slider dengan label dan nilai. Ketuk angka untuk mengetik nilai persis.
 * [log]=true untuk frekuensi dan Q: gerak slider proporsional dengan oktaf.
 */
@Composable
fun ParamSlider(
    label: String, value: Double, min: Double, max: Double, step: Double, unit: String,
    onChange: (Double, Boolean) -> Unit,
    log: Boolean = false, decimals: Int = if (step < 1) 2 else 0, color: Color = HERO,
    enabled: Boolean = true,
) {
    var editing by remember { mutableStateOf(false) }
    fun snap(v: Double): Double {
        val c = v.coerceIn(min, max)
        return (Math.round(c / step) * step).coerceIn(min, max)
    }
    fun toPos(v: Double) = if (log) ((ln(v.coerceIn(min, max)) - ln(min)) / (ln(max) - ln(min))).toFloat()
                           else ((v.coerceIn(min, max) - min) / (max - min)).toFloat()
    fun fromPos(p: Float) = if (log) exp(ln(min) + p * (ln(max) - ln(min))) else min + p * (max - min)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = DIM, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                fmtUnit(value, decimals, unit), color = if (enabled) TXT else DIM, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = enabled) { editing = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Slider(
            value = toPos(value),
            onValueChange = { onChange(snap(fromPos(it)), false) },
            onValueChangeFinished = { onChange(value, true) },
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = GRID),
            modifier = Modifier.height(28.dp)
        )
    }
    if (editing) {
        var txt by remember { mutableStateOf(fmtVal(value)) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = txt, onValueChange = { txt = it }, singleLine = true,
                    suffix = { Text(unit.trim()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    txt.replace(',', '.').toDoubleOrNull()?.let { onChange(snap(it), true) }
                    editing = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun ParamSwitch(label: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TXT, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = TEAL, checkedThumbColor = Color.White))
    }
}

@Composable
fun ParamChoice(label: String, options: List<String>, selected: Int, enabled: Boolean = true, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DIM, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { open = true }, enabled = enabled,
                border = BorderStroke(1.dp, GRID), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp).widthIn(min = 110.dp)
            ) { Text(options.getOrElse(selected) { "?" } + "  ▾", color = TXT, fontSize = 13.sp) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { i, n ->
                    DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onChange(i) })
                }
            }
        }
    }
}

/** Baris chip halaman (DASAR / X-OVER / PEQ …) dengan ringkasan nilai di bawah nama. */
@Composable
fun ChipRow(items: List<Pair<String, Color>>, summaries: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { i, (name, c) ->
            val sel = i == selected
            Column(
                Modifier
                    .background(if (sel) c.copy(alpha = 0.18f) else PANEL, RoundedCornerShape(10.dp))
                    .border(1.dp, if (sel) c else GRID, RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(name, color = if (sel) c else TXT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(summaries.getOrElse(i) { "" }, color = DIM, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = SEL, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}
