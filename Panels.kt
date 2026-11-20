package id.dlms.dsp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dlms.dsp.dsp.*

/** Antarmuka panel ke ViewModel: baca peta parameter, tulis lewat [set]. */
class Ctx(
    val p: Map<String, Double>,
    val rvok: Boolean,
    val set: (key: String, value: Double, final: Boolean) -> Unit,
) {
    fun d(k: String, def: Double = 0.0) = p[k] ?: def
    fun b(k: String) = (p[k] ?: 0.0) != 0.0
    fun i(k: String) = (p[k] ?: 0.0).toInt()
}

private val OUT_PAGES = listOf("DASAR" to AMBER, "X-OVER" to Color(0xFF3FC7FF), "PEQ" to GREEN,
    "LIMITER" to Color(0xFFFF6964), "REVERB" to TEAL)
private val IN_PAGES = listOf("DASAR" to AMBER, "FILTER" to Color(0xFF3FC7FF), "PEQ" to GREEN, "GATE" to TEAL)
private val G_PAGES = listOf("SUMBER" to STEEL, "TAMPILAN" to VIOLET, "MASTER" to AMBER)

fun xoSummary(c: Ctx, pre: String): String {
    val parts = ArrayList<String>()
    if (c.i(pre + "hpt") != 0) parts += "HP ${c.d(pre + "hpf").toInt()}"
    if (c.i(pre + "lpt") != 0) parts += "LP ${c.d(pre + "lpf").toInt()}"
    return if (parts.isEmpty()) "off" else parts.joinToString(" ")
}

fun peqSummary(c: Ctx, pre: String): String {
    val n = (1..N_PEQ).count { c.i(pre + "p$it.t") != 0 }
    return if (n == 0) "off" else "$n band"
}

fun outSummaries(c: Ctx, o: Int): List<String> {
    val pre = "o${o + 1}."
    return listOf(
        if (c.b(pre + "mute")) "MUTE" else String.format("%+.1f dB", c.d(pre + "gain")),
        xoSummary(c, pre), peqSummary(c, pre),
        if (c.b(pre + "lim")) "${c.d(pre + "lth").toInt()} dB" else "off",
        if (c.b(pre + "rv")) RV_PROGS[c.i(pre + "rvp").coerceIn(0, RV_PROGS.size - 1)] else "off")
}

fun inSummaries(c: Ctx, i: Int): List<String> {
    val pre = "i${i + 1}."
    return listOf(String.format("%+.1f dB", c.d(pre + "trim")), xoSummary(c, pre), peqSummary(c, pre),
        if (c.b(pre + "gon")) "${c.d(pre + "gth").toInt()} dB" else "off")
}

fun gSummaries(c: Ctx) = listOf(
    GSRC_NAMES[c.i("g.src").coerceIn(0, 3)], HOME_NAMES[c.i("g.home").coerceIn(0, 2)],
    String.format("%+.1f dB", c.d("g.mast")))

// ---------------------------------------------------------------- bagian bersama
@Composable
private fun XoverGroup(c: Ctx, pre: String, title: String) {
    Group(title) {
        ParamChoice("HPF", XO_NAMES, c.i(pre + "hpt")) { c.set(pre + "hpt", it.toDouble(), true) }
        ParamSlider("Frekuensi HPF", c.d(pre + "hpf", 20.0), 20.0, 20000.0, 1.0, " Hz", { v, f -> c.set(pre + "hpf", v, f) }, log = true, decimals = 0)
        HorizontalDivider(color = GRID)
        ParamChoice("LPF", XO_NAMES, c.i(pre + "lpt")) { c.set(pre + "lpt", it.toDouble(), true) }
        ParamSlider("Frekuensi LPF", c.d(pre + "lpf", 20000.0), 20.0, 20000.0, 1.0, " Hz", { v, f -> c.set(pre + "lpf", v, f) }, log = true, decimals = 0)
    }
}

@Composable
private fun PeqGroup(c: Ctx, pre: String, title: String) {
    for (b in 1..N_PEQ) {
        val on = c.i(pre + "p$b.t") != 0
        Group("$title  —  BAND $b") {
            ParamChoice("Tipe", PQ_NAMES, c.i(pre + "p$b.t")) { c.set(pre + "p$b.t", it.toDouble(), true) }
            ParamSlider("Frekuensi", c.d(pre + "p$b.f", 1000.0), 20.0, 20000.0, 1.0, " Hz", { v, f -> c.set(pre + "p$b.f", v, f) }, log = true, decimals = 0, enabled = on)
            ParamSlider("Gain", c.d(pre + "p$b.g"), -18.0, 18.0, 0.5, " dB", { v, f -> c.set(pre + "p$b.g", v, f) }, decimals = 1, enabled = on)
            ParamSlider("Q", c.d(pre + "p$b.q", 1.0), 0.2, 12.0, 0.05, "", { v, f -> c.set(pre + "p$b.q", v, f) }, log = true, decimals = 2, enabled = on)
        }
    }
}

// ---------------------------------------------------------------- OUT
@Composable
fun OutPanel(c: Ctx, o: Int, page: Int, onRvAll: () -> Unit) {
    val pre = "o${o + 1}."
    when (page) {
        0 -> Group("ROUTING & LEVEL") {
            ParamSwitch("Mute", c.b(pre + "mute")) { c.set(pre + "mute", if (it) 1.0 else 0.0, true) }
            ParamChoice("Sumber", SRC_NAMES, c.i(pre + "src")) { c.set(pre + "src", it.toDouble(), true) }
            ParamSlider("Gain", c.d(pre + "gain"), -60.0, 12.0, 0.5, " dB", { v, f -> c.set(pre + "gain", v, f) }, decimals = 1, color = OUT_COLOR[o])
            ParamSwitch("Balik polaritas", c.b(pre + "pol")) { c.set(pre + "pol", if (it) 1.0 else 0.0, true) }
            ParamSlider("Delay", c.d(pre + "dly"), 0.0, 99.0, 0.05, " ms", { v, f -> c.set(pre + "dly", v, f) }, decimals = 2)
        }
        1 -> XoverGroup(c, pre, "CROSSOVER")
        2 -> PeqGroup(c, pre, "PEQ")
        3 -> Group("LIMITER") {
            ParamSwitch("Aktif", c.b(pre + "lim")) { c.set(pre + "lim", if (it) 1.0 else 0.0, true) }
            ParamSlider("Threshold", c.d(pre + "lth", -6.0), -40.0, -1.0, 0.5, " dBFS", { v, f -> c.set(pre + "lth", v, f) }, decimals = 1)
            ParamSlider("Release", c.d(pre + "lrl", 100.0), 10.0, 1000.0, 10.0, " ms", { v, f -> c.set(pre + "lrl", v, f) }, decimals = 0)
            Text("Limiter selalu blok terakhir sebelum DAC.", color = DIM, fontSize = 11.sp)
        }
        else -> Group("REVERB") {
            val en = c.rvok
            ParamSwitch("Reverb output ini", c.b(pre + "rv"), en) { c.set(pre + "rv", if (it) 1.0 else 0.0, true) }
            ParamChoice("Program", RV_PROGS, c.i(pre + "rvp"), en) { c.set(pre + "rvp", it.toDouble(), true) }
            ParamSlider("Mix", c.d(pre + "rvm", 25.0), 0.0, 100.0, 1.0, " %", { v, f -> c.set(pre + "rvm", v, f) }, decimals = 0, enabled = en)
            ParamSlider("Waktu (RT60)", c.d(pre + "rvt", 1.5), 0.2, 4.0, 0.1, " s", { v, f -> c.set(pre + "rvt", v, f) }, decimals = 1, enabled = en)
            ParamSlider("Redam", c.d(pre + "rvd", 50.0), 0.0, 100.0, 5.0, " %", { v, f -> c.set(pre + "rvd", v, f) }, decimals = 0, enabled = en)
            ParamSlider("Renyah", c.d(pre + "rvc", 40.0), 0.0, 100.0, 5.0, " %", { v, f -> c.set(pre + "rvc", v, f) }, decimals = 0, enabled = en)
            Text("Renyah: dorongan desis \"s\" pada ekor. 40 = bawaan.", color = DIM, fontSize = 11.sp)
            BigButton("Terapkan ke semua OUT", onRvAll, Modifier.fillMaxWidth(), TEAL, en)
            if (!en) Text("Memori reverb di papan tidak cukup — reverb dinonaktifkan.", color = RED, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- IN
@Composable
fun InPanel(c: Ctx, i: Int, page: Int) {
    val pre = "i${i + 1}."
    when (page) {
        0 -> Group("LEVEL & POLARITAS") {
            ParamSlider("Trim", c.d(pre + "trim"), -20.0, 20.0, 0.5, " dB", { v, f -> c.set(pre + "trim", v, f) }, decimals = 1, color = IN_COLOR[i])
            ParamSwitch("Balik polaritas", c.b(pre + "pol")) { c.set(pre + "pol", if (it) 1.0 else 0.0, true) }
            Text("Meter input membaca sinyal mentah dari ADC, sebelum trim. Itu satu-satunya tempat clip yang benar-benar berbahaya.", color = DIM, fontSize = 11.sp)
        }
        1 -> XoverGroup(c, pre, "PENYARING")
        2 -> PeqGroup(c, pre, "PEQ INPUT")
        else -> Group("NOISE GATE") {
            ParamSwitch("Gate aktif", c.b(pre + "gon")) { c.set(pre + "gon", if (it) 1.0 else 0.0, true) }
            ParamSlider("Ambang", c.d(pre + "gth", -65.0), -90.0, -20.0, 1.0, " dB", { v, f -> c.set(pre + "gth", v, f) }, decimals = 0)
            ParamSlider("Attack", c.d(pre + "gat", 2.0), 0.1, 50.0, 0.1, " ms", { v, f -> c.set(pre + "gat", v, f) }, decimals = 1)
            ParamSlider("Hold", c.d(pre + "gho", 50.0), 0.0, 500.0, 10.0, " ms", { v, f -> c.set(pre + "gho", v, f) }, decimals = 0)
            ParamSlider("Release", c.d(pre + "grl", 200.0), 10.0, 2000.0, 10.0, " ms", { v, f -> c.set(pre + "grl", v, f) }, decimals = 0)
            ParamSlider("Range", c.d(pre + "grg", -80.0), -90.0, -20.0, 1.0, " dB", { v, f -> c.set(pre + "grg", v, f) }, decimals = 0)
        }
    }
}

// ---------------------------------------------------------------- GLOBAL
@Composable
fun GlobalPanel(c: Ctx, page: Int) {
    when (page) {
        0 -> Group("SUMBER") {
            ParamChoice("Sumber", GSRC_NAMES, c.i("g.src")) { c.set("g.src", it.toDouble(), true) }
            ParamSlider("Tone", c.d("g.tf", 1000.0), 20.0, 20000.0, 10.0, " Hz", { v, f -> c.set("g.tf", v, f) }, log = true, decimals = 0)
            ParamSlider("Level tone", c.d("g.tl", -12.0), -60.0, 0.0, 1.0, " dBFS", { v, f -> c.set("g.tl", v, f) }, decimals = 0)
            Text("Tone generator menggantikan kedua input, bukan menimpa.", color = DIM, fontSize = 11.sp)
        }
        1 -> Group("TAMPILAN PERANGKAT") {
            ParamChoice("Layar HOME", HOME_NAMES, c.i("g.home")) { c.set("g.home", it.toDouble(), true) }
            ParamSlider("Sensitivitas enc", c.d("g.enc", 4.0), 1.0, 8.0, 1.0, "", { v, f -> c.set("g.enc", v, f) }, decimals = 0)
            ParamSwitch("Meter bergerak di layar", c.b("g.lcd")) { c.set("g.lcd", if (it) 1.0 else 0.0, true) }
        }
        else -> Group("MASTER") {
            ParamSlider("Gain master", c.d("g.mast"), -60.0, 12.0, 0.5, " dB", { v, f -> c.set("g.mast", v, f) }, decimals = 1, color = AMBER)
            ParamSwitch("XSMT aktif (DAC bunyi)", c.b("g.xsmt")) { c.set("g.xsmt", if (it) 1.0 else 0.0, true) }
            Text("Master masuk ke gain output sebelum limiter. Jika limiter dimatikan, puncak dapat terpotong secara digital. XSMT memutus keluaran DAC.", color = DIM, fontSize = 11.sp)
        }
    }
}

val outPages get() = OUT_PAGES
val inPages get() = IN_PAGES
val gPages get() = G_PAGES
