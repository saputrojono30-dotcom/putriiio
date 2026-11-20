package id.dlms.dsp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.dlms.dsp.dsp.*
import id.dlms.dsp.net.DspLink
import id.dlms.dsp.net.LinkEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ConnState { Idle, Connecting, Connected, Failed }

data class Settings(
    val ssid: String = "DLMS-S3",
    val password: String = "",
    val host: String = "192.168.4.1",
    val port: Int = 5555,
    val joinWifi: Boolean = true,
    val remember: Boolean = true,
)

data class DspState(
    val conn: ConnState = ConnState.Idle,
    val message: String = "Belum tersambung",
    val params: Map<String, Double> = defaultParams(),
    val slots: String = "00000000",
    val rvok: Boolean = true,
    val toast: String? = null,
)

class DspViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("dlms", Context.MODE_PRIVATE)
    private val link = DspLink(app, viewModelScope)

    private val _state = MutableStateFlow(DspState())
    val state: StateFlow<DspState> = _state.asStateFlow()

    // Meter dipisah dari state utama: 20 kali per detik tidak boleh menggambar ulang seluruh panel.
    private val _meters = MutableStateFlow(Meters())
    val meters: StateFlow<Meters> = _meters.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var dumpBuf = LinkedHashMap<String, Double>()
    private var dumpSlots: String? = null
    private var dumpRvok: Boolean? = null

    // Pembatas laju seretan: nilai terakhir tiap kunci dikirim paling cepat tiap 50 ms.
    private val pending = LinkedHashMap<String, Double>()
    private var flushJob: Job? = null
    private var keepJob: Job? = null
    private var pendingWifi: Pair<String, String>? = null

    init {
        viewModelScope.launch { link.events.collect(::onEvent) }
    }

    // ------------------------------------------------------------- pengaturan
    private fun loadSettings() = Settings(
        ssid = prefs.getString("ssid", "DLMS-S3") ?: "DLMS-S3",
        password = prefs.getString("pass", "") ?: "",
        host = prefs.getString("host", "192.168.4.1") ?: "192.168.4.1",
        port = prefs.getInt("port", 5555),
        joinWifi = prefs.getBoolean("join", true),
        remember = prefs.getBoolean("remember", true),
    )

    private fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("ssid", s.ssid)
            .putString("pass", if (s.remember) s.password else "")
            .putString("host", s.host).putInt("port", s.port)
            .putBoolean("join", s.joinWifi).putBoolean("remember", s.remember)
            .apply()
    }

    // ------------------------------------------------------------- sambungan
    fun connect(s: Settings) {
        _settings.value = s
        saveSettings(s)
        if (s.joinWifi && s.password.length < 8) {
            _state.update { it.copy(conn = ConnState.Failed, message = "Password WiFi minimal 8 karakter") }
            return
        }
        _state.update { it.copy(conn = ConnState.Connecting, message = "Menyambung…") }
        link.connect(s.host.trim(), s.port, s.ssid.trim(), s.password, s.joinWifi)
    }

    fun disconnect() {
        if (link.running) link.send("M 0")
        keepJob?.cancel()
        // beri "M 0" waktu terkirim sebelum soket ditutup
        viewModelScope.launch { delay(120); link.disconnect() }
        _state.update { it.copy(conn = ConnState.Idle, message = "Terputus") }
        _meters.value = Meters()
    }

    private fun onEvent(e: LinkEvent) {
        when (e) {
            is LinkEvent.Status -> _state.update { it.copy(conn = ConnState.Connecting, message = e.text) }
            LinkEvent.Connected -> {
                _state.update { it.copy(conn = ConnState.Connected, message = "Tersambung ke ${settings.value.host}") }
                // Sama dengan aplikasi laptop: tunggu sebentar, lalu D dan M 1.
                viewModelScope.launch { delay(300); link.send("D"); link.send("M 1") }
                // "M 1" berulang = detak jantung. Papan hanya mengirim data saat meter hidup,
                // dan tanpa data soket dianggap mati (timeout 5 detik).
                keepJob?.cancel()
                keepJob = viewModelScope.launch {
                    while (isActive) { delay(2000); link.send("M 1") }
                }
            }
            is LinkEvent.Lost -> {
                keepJob?.cancel()
                _meters.value = Meters()
                val wifiChange = pendingWifi
                if (wifiChange != null) return      // putus karena ganti password: ditangani onEvent("ok wifi")
                _state.update { it.copy(conn = ConnState.Failed, message = "Terputus: ${e.reason}") }
            }
            is LinkEvent.Line -> onLine(e.text)
        }
    }

    // ---------------------------------------------------------------- protokol
    private fun onLine(s: String) {
        if (s.startsWith("#")) { parseMeter(s); return }
        when (s) {
            "err" -> {
                pendingWifi = null
                _state.update { it.copy(toast = "Papan menolak perintah; menyelaraskan ulang") }
                link.send("D"); return
            }
            "ok wifi" -> { onWifiChanged(); return }
            "." -> {
                val merged = LinkedHashMap(_state.value.params).apply { putAll(dumpBuf) }
                val slots = dumpSlots ?: _state.value.slots
                val rv = dumpRvok ?: _state.value.rvok
                dumpBuf = LinkedHashMap(); dumpSlots = null; dumpRvok = null
                _state.update { it.copy(params = merged, slots = slots, rvok = rv) }
                return
            }
            "!" -> { link.send("D"); return }        // papan: parameter diubah dari encoder
        }
        val i = s.indexOf('=')
        if (i < 0) return
        val k = s.substring(0, i); val v = s.substring(i + 1)
        if (k == "slots") { dumpSlots = (v + "0".repeat(8)).take(8); return }
        val d = v.toDoubleOrNull() ?: return
        when {
            k == "rvok" -> dumpRvok = d != 0.0
            k == "dsp" || k == "v" || k == "npeq" || k == "nslot" -> {
                if (k == "dsp") _meters.update { it.copy(dsp = d.toInt()) }
            }
            else -> dumpBuf[k] = d
        }
    }

    private fun parseMeter(s: String) {
        val n = s.substring(1).split(',').mapNotNull { it.trim().toIntOrNull() }
        if (n.size < 10) return
        _meters.value = Meters(
            level = List(6) { n[it] / 10f },
            gr = List(4) { n[6 + it] / 10f },
            dsp = if (n.size >= 11) n[10] else _meters.value.dsp
        )
    }

    // ------------------------------------------------------------- perintah
    /** Ubah satu parameter. [final]=true: kirim langsung (lepas jari / kontrol diskrit). */
    fun set(key: String, value: Double, final: Boolean = true) {
        _state.update { it.copy(params = it.params + (key to value)) }
        pending[key] = value
        if (final) flushPending()
        else if (flushJob?.isActive != true) {
            flushJob = viewModelScope.launch { delay(50); flushPending() }
        }
    }

    private fun flushPending() {
        flushJob?.cancel()
        for ((k, v) in pending) link.send("S $k ${fmtVal(v)}")
        pending.clear()
    }

    private fun connectedOr(what: String): Boolean {
        if (_state.value.conn == ConnState.Connected) return true
        toast("$what: belum tersambung ke papan")
        return false
    }

    fun refresh() { if (connectedOr("Baca ulang")) link.send("D") }

    fun copyOut(src: Int, dst: List<Int>) {
        if (!connectedOr("Salin")) return
        flushPending()
        dst.forEach { link.send("C ${src + 1} ${it + 1}") }
        viewModelScope.launch { delay(150); link.send("D") }
        toast("OUT ${src + 1} disalin ke " + dst.joinToString { "OUT ${it + 1}" })
    }

    fun preset(what: Char, n: Int) {
        if (!connectedOr("Preset")) return
        flushPending()
        link.send("P $what $n")
        viewModelScope.launch { delay(200); link.send("D") }
        val kata = when (what) { 'S' -> "disimpan ke"; 'L' -> "dimuat dari"; else -> "dihapus dari" }
        toast("Preset $kata slot $n")
    }

    fun toggleRvMaster() {
        val baru = if ((_state.value.params[gKey("rv")] ?: 1.0) != 0.0) 0.0 else 1.0
        set(gKey("rv"), baru)
        toast(if (baru != 0.0) "Reverb dihidupkan kembali" else "Semua reverb dimatikan")
    }

    /** Salin setelan reverb satu output ke tiga lainnya (perintah S biasa, tanpa rebuild filter). */
    fun rvApplyAll(src: Int) {
        val p = _state.value.params
        for (d in 0 until 4) if (d != src) for (k in listOf("rv", "rvm", "rvt", "rvd", "rvp", "rvc")) {
            set(oKey(d, k), p[oKey(src, k)] ?: 0.0)
        }
        toast("Setelan reverb OUT ${src + 1} diterapkan ke semua output")
    }

    // ------------------------------------------------------------- keamanan WiFi
    /** Validasi sama dengan firmware (wifiCredsOk): SSID 1..32, password 8..63, ASCII tanpa spasi. */
    fun validateWifi(ssid: String, pass: String): String? {
        fun ok(t: String) = t.all { it.code in 33..126 }
        return when {
            ssid.isEmpty() || ssid.length > 32 -> "Nama WiFi harus 1-32 karakter"
            pass.length < 8 || pass.length > 63 -> "Password harus 8-63 karakter"
            !ok(ssid) || !ok(pass) -> "Nama dan password: huruf/angka/simbol ASCII, tanpa spasi"
            else -> null
        }
    }

    fun changeWifi(ssid: String, pass: String) {
        if (!connectedOr("Ganti password")) return
        validateWifi(ssid, pass)?.let { toast(it); return }
        pendingWifi = ssid to pass
        link.send("W S $ssid $pass")
        toast("Mengirim nama/password baru ke papan…")
    }

    fun resetWifiOnBoard() {
        if (!connectedOr("Reset WiFi")) return
        pendingWifi = "DLMS-S3" to "dlms1234"
        link.send("W R")
    }

    /** Papan menjawab "ok wifi": AP-nya dimulai ulang dengan kredensial baru; sambungan lama pasti putus. */
    private fun onWifiChanged() {
        val (ssid, pass) = pendingWifi ?: return
        val s = _settings.value.copy(ssid = ssid, password = pass)
        _settings.value = s
        saveSettings(s)
        keepJob?.cancel()
        link.disconnect()
        _meters.value = Meters()
        _state.update { it.copy(conn = ConnState.Connecting, message = "Password diganti. Menyambung ulang…") }
        viewModelScope.launch {
            delay(3500)                       // AP perlu waktu untuk naik kembali
            pendingWifi = null
            connect(s)
        }
    }

    // ------------------------------------------------------------- pesan singkat
    private fun toast(t: String) { _state.update { it.copy(toast = t) } }
    fun toastShown() { _state.update { it.copy(toast = null) } }

    override fun onCleared() {
        link.disconnect()
        super.onCleared()
    }
}
