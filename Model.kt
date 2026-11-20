package id.dlms.dsp.dsp

import java.util.Locale

val XO_NAMES = listOf("OFF", "BW12", "LR24", "LR48")
val PQ_NAMES = listOf("off", "bell", "lowshelf", "highshelf")
val SRC_NAMES = listOf("IN 1", "IN 2", "MIX")
val GSRC_NAMES = listOf("INPUT", "TONE", "DIAM", "SWEEP")
val HOME_NAMES = listOf("grafik", "ringkas", "meter")
val RV_PROGS = listOf("Room", "Plate", "Hall", "Vokal", "Chamber", "Katedral", "Gate")

fun iKey(i: Int, k: String) = "i${i + 1}.$k"
fun oKey(o: Int, k: String) = "o${o + 1}.$k"
fun gKey(k: String) = "g.$k"

/** Nilai awal sama dengan default_in/default_out/Model.g di aplikasi laptop. Dipakai sebelum dump pertama tiba. */
fun defaultParams(): Map<String, Double> {
    val m = LinkedHashMap<String, Double>()
    for (i in 0 until 2) {
        val d = mapOf("trim" to 0.0, "pol" to 0.0, "hpt" to 0.0, "hpf" to 30.0, "lpt" to 0.0, "lpf" to 18000.0,
            "gon" to 0.0, "gth" to -65.0, "gat" to 2.0, "gho" to 50.0, "grl" to 200.0, "grg" to -80.0)
        d.forEach { (k, v) -> m[iKey(i, k)] = v }
        for (b in 1..N_PEQ) {
            m[iKey(i, "p$b.t")] = 0.0; m[iKey(i, "p$b.f")] = 1000.0
            m[iKey(i, "p$b.g")] = 0.0; m[iKey(i, "p$b.q")] = 1.0
        }
    }
    for (o in 0 until 4) {
        val d = mapOf("mute" to 1.0, "src" to 0.0, "gain" to 0.0, "pol" to 0.0, "dly" to 0.0,
            "hpt" to 0.0, "hpf" to 80.0, "lpt" to 0.0, "lpf" to 2000.0,
            "lim" to 0.0, "lth" to -6.0, "lrl" to 100.0,
            "rv" to 0.0, "rvm" to 25.0, "rvt" to 1.5, "rvd" to 50.0, "rvp" to 3.0, "rvc" to 40.0)
        d.forEach { (k, v) -> m[oKey(o, k)] = v }
        for (b in 1..N_PEQ) {
            m[oKey(o, "p$b.t")] = 0.0; m[oKey(o, "p$b.f")] = 1000.0
            m[oKey(o, "p$b.g")] = 0.0; m[oKey(o, "p$b.q")] = 1.0
        }
    }
    mapOf("src" to 0.0, "tf" to 1000.0, "tl" to -12.0, "t1" to 0.0, "t2" to 0.0, "mast" to 0.0,
        "home" to 0.0, "enc" to 4.0, "lcd" to 1.0, "xsmt" to 1.0, "rv" to 1.0)
        .forEach { (k, v) -> m[gKey(k)] = v }
    return m
}

/** Angka ke teks untuk perintah "S": bilangan bulat tanpa titik, selain itu maksimal 3 desimal. */
fun fmtVal(v: Double): String {
    if (v == Math.rint(v) && kotlin.math.abs(v) < 1e9) return v.toLong().toString()
    return String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
}

data class Meters(
    val level: List<Float> = List(6) { -120f },   // IN1 IN2 OUT1..4 dBFS
    val gr: List<Float> = List(4) { 0f },         // gain reduction OUT1..4 dB
    val dsp: Int = 0
)
