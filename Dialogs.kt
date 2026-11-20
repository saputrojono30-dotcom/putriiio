package id.dlms.dsp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PresetDialog(slots: String, onAction: (Char, Int) -> Unit, onDismiss: () -> Unit) {
    // (aksi, slot) menunggu konfirmasi; sama dengan pertanyaan di aplikasi laptop.
    var ask by remember { mutableStateOf<Pair<Char, Int>?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preset") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in 1..8) {
                    val filled = slots.getOrElse(n - 1) { '0' } == '1'
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Slot $n" + if (filled) "  ●" else "", modifier = Modifier.weight(1f), color = if (filled) TEAL else TXT)
                        TextButton(onClick = { if (filled) ask = 'S' to n else onAction('S', n) },
                            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Simpan", fontSize = 13.sp) }
                        TextButton(onClick = { ask = 'L' to n }, enabled = filled,
                            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Muat", fontSize = 13.sp) }
                        TextButton(onClick = { ask = 'D' to n }, enabled = filled,
                            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Hapus", fontSize = 13.sp, color = if (filled) RED else DIM) }
                    }
                }
                Text("Setelan yang berjalan disimpan otomatis di papan (jeda 5 detik).", color = DIM, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
    ask?.let { (what, n) ->
        val (judul, teks) = when (what) {
            'S' -> "Timpa slot $n?" to "Slot $n sudah berisi. Isinya akan ditimpa setelan yang berjalan sekarang."
            'L' -> "Muat slot $n?" to "Setelan yang berjalan sekarang akan diganti. Perubahan yang belum disimpan ke slot akan hilang."
            else -> "Hapus slot $n?" to "Isi slot $n dihapus permanen."
        }
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(judul) }, text = { Text(teks) },
            confirmButton = { TextButton(onClick = { onAction(what, n); ask = null }) { Text("Ya") } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Tidak") } }
        )
    }
}

@Composable
fun CopyDialog(src: Int, onCopy: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salin OUT ${src + 1} ke…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0 until 4) if (d != src) {
                    TextButton(onClick = { onCopy(listOf(d)); onDismiss() }) { Text("OUT ${src + 1}  →  OUT ${d + 1}") }
                }
                TextButton(onClick = { onCopy((0 until 4).filter { it != src }); onDismiss() }) { Text("OUT ${src + 1}  →  semua kanal lain") }
                Text("Penyalinan dikerjakan papan (satu rebuild). Status mute kanal tujuan dipertahankan.", color = DIM, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Ganti nama + password WiFi papan. Dikirim lewat sambungan yang sudah terenkripsi WPA2. */
@Composable
fun SecurityDialog(
    currentSsid: String,
    validate: (String, String) -> String?,
    onApply: (String, String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var ssid by remember { mutableStateOf(currentSsid) }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val err = when {
        pass.isEmpty() -> null
        validate(ssid, pass) != null -> validate(ssid, pass)
        pass2.isNotEmpty() && pass != pass2 -> "Konfirmasi password tidak sama"
        else -> null
    }
    val ready = pass.isNotEmpty() && pass == pass2 && validate(ssid, pass) == null
    val vt = if (show) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keamanan WiFi papan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Jaringan WiFi papan dilindungi WPA2. Password bawaan pabrik sebaiknya diganti.", color = DIM, fontSize = 12.sp)
                OutlinedTextField(ssid, { ssid = it.filter { c -> c.code in 33..126 }.take(32) }, label = { Text("Nama WiFi (SSID)") }, singleLine = true)
                OutlinedTextField(pass, { pass = it.filter { c -> c.code in 33..126 }.take(63) }, label = { Text("Password baru (8-63)") },
                    singleLine = true, visualTransformation = vt,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(pass2, { pass2 = it.filter { c -> c.code in 33..126 }.take(63) }, label = { Text("Ulangi password") },
                    singleLine = true, visualTransformation = vt,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(show, { show = it }); Text("Tampilkan password", fontSize = 13.sp)
                }
                if (err != null) Text(err, color = RED, fontSize = 12.sp)
                Text("Setelah diterapkan, WiFi papan dimulai ulang (audio tidak terganggu) dan aplikasi menyambung ulang otomatis. " +
                     "HP lain yang masih memakai password lama harus memasukkan yang baru.", color = DIM, fontSize = 11.sp)
                TextButton(onClick = { confirmReset = true }) { Text("Kembalikan ke bawaan pabrik", color = RED) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(ssid, pass); onDismiss() }, enabled = ready) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Kembalikan WiFi ke bawaan?") },
        text = { Text("Nama jadi \"DLMS-S3\" dan password \"dlms1234\".") },
        confirmButton = { TextButton(onClick = { onReset(); confirmReset = false; onDismiss() }) { Text("Ya") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Tidak") } }
    )
}
