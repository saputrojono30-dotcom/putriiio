package id.dlms.dsp.dsp

import kotlin.math.*

/** Cerminan dsp.h / dlms_app.py: rumus RBJ Audio EQ Cookbook, Butterworth dan Linkwitz-Riley. */
const val FS = 48000.0
const val N_PEQ = 4

data class Bq(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
    Bq(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

fun bqPeaking(f: Double, q: Double, gDb: Double): Bq {
    val a = 10.0.pow(gDb / 40.0)
    val w = 2 * PI * f / FS
    val cw = cos(w); val sw = sin(w)
    val al = sw / (2 * q)
    return norm(1 + al * a, -2 * cw, 1 - al * a, 1 + al / a, -2 * cw, 1 - al / a)
}

fun bqLowShelf(f: Double, gDb: Double): Bq {
    val a = 10.0.pow(gDb / 40.0)
    val w = 2 * PI * f / FS
    val cw = cos(w); val sw = sin(w)
    val al = sw / 2 * sqrt(2.0)
    val ta = 2 * sqrt(a) * al
    return norm(
        a * ((a + 1) - (a - 1) * cw + ta),
        2 * a * ((a - 1) - (a + 1) * cw),
        a * ((a + 1) - (a - 1) * cw - ta),
        (a + 1) + (a - 1) * cw + ta,
        -2 * ((a - 1) + (a + 1) * cw),
        (a + 1) + (a - 1) * cw - ta
    )
}

fun bqHighShelf(f: Double, gDb: Double): Bq {
    val a = 10.0.pow(gDb / 40.0)
    val w = 2 * PI * f / FS
    val cw = cos(w); val sw = sin(w)
    val al = sw / 2 * sqrt(2.0)
    val ta = 2 * sqrt(a) * al
    return norm(
        a * ((a + 1) + (a - 1) * cw + ta),
        -2 * a * ((a - 1) + (a + 1) * cw),
        a * ((a + 1) + (a - 1) * cw - ta),
        (a + 1) - (a - 1) * cw + ta,
        2 * ((a - 1) - (a + 1) * cw),
        (a + 1) - (a - 1) * cw - ta
    )
}

fun bqLowPass(f: Double, q: Double): Bq {
    val w = 2 * PI * f / FS
    val cw = cos(w); val sw = sin(w)
    val al = sw / (2 * q)
    return norm((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + al, -2 * cw, 1 - al)
}

fun bqHighPass(f: Double, q: Double): Bq {
    val w = 2 * PI * f / FS
    val cw = cos(w); val sw = sin(w)
    val al = sw / (2 * q)
    return norm((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + al, -2 * cw, 1 - al)
}

private const val Q_BW2 = 0.70710678
private val Q_BW4 = doubleArrayOf(0.54119610, 1.30656296)

/** Sama dengan xoDesign() di dsp.h. xtype: 0 OFF, 1 BW12, 2 LR24, 3 LR48. */
fun xoverSections(xtype: Int, f: Double, highpass: Boolean): List<Bq> {
    val qs = when (xtype) {
        0 -> return emptyList()
        1 -> doubleArrayOf(Q_BW2)
        2 -> doubleArrayOf(Q_BW2, Q_BW2)
        else -> doubleArrayOf(Q_BW4[0], Q_BW4[1], Q_BW4[0], Q_BW4[1])
    }
    return qs.map { if (highpass) bqHighPass(f, it) else bqLowPass(f, it) }
}

/** Rantai biquad satu kanal. [prefix] = "o1." atau "i2." */
fun channelSections(p: Map<String, Double>, prefix: String): List<Bq> {
    fun g(k: String, d: Double = 0.0) = p[prefix + k] ?: d
    val s = ArrayList<Bq>()
    s += xoverSections(g("hpt").toInt(), g("hpf", 20.0), true)
    s += xoverSections(g("lpt").toInt(), g("lpf", 20000.0), false)
    for (b in 1..N_PEQ) {
        val t = g("p$b.t").toInt()
        val f = g("p$b.f", 1000.0)
        val gain = g("p$b.g")
        val q = g("p$b.q", 1.0)
        when (t) {
            1 -> s += bqPeaking(f, q, gain)
            2 -> s += bqLowShelf(f, gain)
            3 -> s += bqHighShelf(f, gain)
        }
    }
    return s
}

/** Respons kompleks. Fase ikut dihitung supaya penjumlahan antar-output realistis. */
class Cx(val re: DoubleArray, val im: DoubleArray) {
    val size get() = re.size
    fun scaled(k: Double) = Cx(DoubleArray(size) { re[it] * k }, DoubleArray(size) { im[it] * k })
    fun times(o: Cx) = Cx(
        DoubleArray(size) { re[it] * o.re[it] - im[it] * o.im[it] },
        DoubleArray(size) { re[it] * o.im[it] + im[it] * o.re[it] })
    fun plus(o: Cx) = Cx(DoubleArray(size) { re[it] + o.re[it] }, DoubleArray(size) { im[it] + o.im[it] })
    fun db(i: Int): Double {
        val m = hypot(re[i], im[i])
        return if (m > 1e-9) 20 * log10(m) else -200.0
    }
    companion object { fun zeros(n: Int) = Cx(DoubleArray(n), DoubleArray(n)) }
}

fun response(sections: List<Bq>, freqs: DoubleArray): Cx {
    val n = freqs.size
    val re = DoubleArray(n) { 1.0 }
    val im = DoubleArray(n)
    for (i in 0 until n) {
        val w = 2 * PI * freqs[i] / FS
        val c1 = cos(w); val s1 = -sin(w)
        val c2 = cos(2 * w); val s2 = -sin(2 * w)
        var hr = 1.0; var hi = 0.0
        for (s in sections) {
            val nr = s.b0 + s.b1 * c1 + s.b2 * c2
            val ni = s.b1 * s1 + s.b2 * s2
            val dr = 1 + s.a1 * c1 + s.a2 * c2
            val di = s.a1 * s1 + s.a2 * s2
            val den = dr * dr + di * di
            val qr = (nr * dr + ni * di) / den
            val qi = (ni * dr - nr * di) / den
            val tr = hr * qr - hi * qi
            hi = hr * qi + hi * qr
            hr = tr
        }
        re[i] = hr; im[i] = hi
    }
    return Cx(re, im)
}

const val F_MIN = 20.0
const val F_MAX = 20000.0
const val N_POINTS = 360

val FREQS: DoubleArray = DoubleArray(N_POINTS) {
    val lo = log10(F_MIN); val hi = log10(F_MAX)
    10.0.pow(lo + (hi - lo) * it / (N_POINTS - 1))
}

/** Kurva setiap input dan output, termasuk trim/polaritas/gain dan routing -- seperti ResponsePlot.recompute(). */
class Curves(val inp: List<Cx>, val out: List<Cx>)

fun computeCurves(p: Map<String, Double>): Curves {
    val icur = (0 until 2).map { i ->
        val pre = "i${i + 1}."
        val h = response(channelSections(p, pre), FREQS)
        var lin = if ((p[pre + "pol"] ?: 0.0) != 0.0) -1.0 else 1.0
        lin *= 10.0.pow((p[pre + "trim"] ?: 0.0) / 20.0)
        h.scaled(lin)
    }
    val ocur = (0 until 4).map { o ->
        val pre = "o${o + 1}."
        val h = response(channelSections(p, pre), FREQS)
        var lin = 10.0.pow((p[pre + "gain"] ?: 0.0) / 20.0)
        if ((p[pre + "pol"] ?: 0.0) != 0.0) lin = -lin
        val fed = when ((p[pre + "src"] ?: 0.0).toInt()) {
            0 -> icur[0]
            1 -> icur[1]
            else -> icur[0].plus(icur[1]).scaled(0.5)
        }
        h.times(fed).scaled(lin)
    }
    return Curves(icur, ocur)
}
