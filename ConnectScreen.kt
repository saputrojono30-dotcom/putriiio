package id.dlms.dsp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dlms.dsp.ConnState
import id.dlms.dsp.Settings

@Composable
fun ConnectScreen(initial: Settings, conn: ConnState, message: String, onConnect: (Settings) -> Unit, onCancel: () -> Unit) {
    var ssid by remember { mutableStateOf(initial.ssid) }
    var pass by remember { mutableStateOf(initial.password) }
    var show by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var join by remember { mutableStateOf(initial.joinWifi) }
    var saveIt by remember { mutableStateOf(initial.remember) }
    var advanced by remember { mutableStateOf(false) }
    val busy = conn == ConnState.Connecting

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("DLMS-S3", color = Color_White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("  SISTEM", color = AMBER, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text("Sambungkan ke papan lewat WiFi. Papan membuat jaringan sendiri yang dilindungi password (WPA2).", color = DIM, fontSize = 13.sp)

        Group("WIFI PAPAN") {
            OutlinedTextField(ssid, { ssid = it }, label = { Text("Nama WiFi (SSID)") }, singleLine = true,
                enabled = join && !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it }, label = { Text("Password WiFi") }, singleLine = true,
                enabled = join && !busy, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(show, { show = it }); Text("Tampilkan password", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Checkbox(saveIt, { saveIt = it }); Text("Ingat", fontSize = 13.sp)
            }
            Text("Bawaan pabrik: DLMS-S3 / dlms1234. Android akan meminta persetujuan sekali untuk bergabung ke jaringan ini.",
                color = DIM, fontSize = 11.sp)
        }

        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Sembunyikan lanjutan ▴" else "Lanjutan ▾") }
        if (advanced) Group("LANJUTAN") {
            ParamSwitch("Gabung WiFi otomatis dari aplikasi", join) { join = it }
            Text("Matikan jika Anda sudah bergabung ke WiFi papan lewat Pengaturan Android, atau memakai simulator (host 10.0.2.2 di emulator).",
                color = DIM, fontSize = 11.sp)
            OutlinedTextField(host, { host = it }, label = { Text("Alamat papan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        }

        if (conn == ConnState.Failed || busy) {
            Text(message, color = if (busy) TXT else RED, fontSize = 14.sp)
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            BigButton("Batal", onCancel, Modifier.fillMaxWidth(), GRID)
        } else {
            BigButton("Hubungkan", {
                onConnect(Settings(ssid.trim(), pass, host.trim(), port.toIntOrNull() ?: 5555, join, saveIt))
            }, Modifier.fillMaxWidth().height(52.dp))
        }
    }
}

private val Color_White = androidx.compose.ui.graphics.Color.White
