package id.dlms.dsp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dlms.dsp.DspViewModel
import id.dlms.dsp.dsp.Meters
import id.dlms.dsp.dsp.gKey

private val TAB_NAMES = listOf("IN 1", "IN 2", "OUT 1", "OUT 2", "OUT 3", "OUT 4", "GLOBAL")

@Composable
fun MainScreen(vm: DspViewModel, meters: Meters) {
    val st by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()
    var tab by rememberSaveable { mutableStateOf(2) }
    var page by rememberSaveable { mutableStateOf(0) }
    var dlg by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    val ctx = remember(st.params, st.rvok) { Ctx(st.params, st.rvok) { k, v, f -> vm.set(k, v, f) } }
    val active: Active? = when {
        tab < 2 -> Active(false, tab)
        tab < 6 -> Active(true, tab - 2)
        else -> null
    }
    val pages = when { tab < 2 -> inPages; tab < 6 -> outPages; else -> gPages }
    val summaries = when {
        tab < 2 -> inSummaries(ctx, tab)
        tab < 6 -> outSummaries(ctx, tab - 2)
        else -> gSummaries(ctx)
    }
    val rvOn = (st.params[gKey("rv")] ?: 1.0) != 0.0

    Column(Modifier.fillMaxSize()) {
        // ----- bar atas
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("DLMS-S3", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("  SISTEM", color = AMBER, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).background(GREEN, CircleShape))
            val dsp = meters.dsp
            Text("  DSP $dsp%", color = if (dsp >= 95) RED else if (dsp >= 80) AMBER else DIM,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Box {
                TextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("⋮", color = TXT, fontSize = 22.sp)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Preset…") }, onClick = { menu = false; dlg = "preset" })
                    DropdownMenuItem(
                        text = { Text(if (tab in 2..5) "Salin OUT ${tab - 1}…" else "Salin kanal (buka tab OUT)") },
                        enabled = tab in 2..5, onClick = { menu = false; dlg = "copy" })
                    DropdownMenuItem(text = { Text("Baca ulang dari papan") }, onClick = { menu = false; vm.refresh() })
                    DropdownMenuItem(text = { Text("Keamanan WiFi (password)…") }, onClick = { menu = false; dlg = "security" })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Putuskan", color = RED) }, onClick = { menu = false; vm.disconnect() })
                }
            }
        }

        // ----- tab kanal
        ScrollableTabRow(
            selectedTabIndex = tab, edgePadding = 8.dp, containerColor = Color.Transparent, contentColor = TXT,
            divider = {}, indicator = { pos ->
                TabRowDefaults.SecondaryIndicator(
                    with(TabRowDefaults) { Modifier.tabIndicatorOffset(pos[tab]) }, color = SEL)
            }
        ) {
            TAB_NAMES.forEachIndexed { i, n ->
                Tab(selected = tab == i, onClick = { tab = i; page = 0 }, text = {
                    Text(n, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = when {
                            tab != i -> DIM
                            i < 2 -> IN_COLOR[i]
                            i < 6 -> OUT_COLOR[i - 2]
                            else -> TXT
                        })
                })
            }
        }

        // ----- isi
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            ResponsePlot(st.params, active, { k, v, f -> vm.set(k, v, f) })
            MeterRow(meters)

            // tombol MASTER reverb: selalu terlihat di semua tab
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Reverb master", color = DIM, fontSize = 12.sp, modifier = Modifier.weight(1f))
                BigButton(
                    if (rvOn) "Matikan reverb" else "Reverb MATI — hidupkan",
                    { vm.toggleRvMaster() }, Modifier.height(36.dp),
                    if (rvOn) Color(0xFF7A2E2C) else Color(0xFFB3402C), enabled = st.rvok
                )
            }

            ChipRow(pages, summaries, page.coerceIn(0, pages.size - 1)) { page = it }
            val pg = page.coerceIn(0, pages.size - 1)
            when {
                tab < 2 -> InPanel(ctx, tab, pg)
                tab < 6 -> OutPanel(ctx, tab - 2, pg) { vm.rvApplyAll(tab - 2) }
                else -> GlobalPanel(ctx, pg)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dlg) {
        "preset" -> PresetDialog(st.slots, { w, n -> vm.preset(w, n) }) { dlg = "" }
        "copy" -> CopyDialog(tab - 2, { vm.copyOut(tab - 2, it) }) { dlg = "" }
        "security" -> SecurityDialog(settings.ssid, vm::validateWifi, vm::changeWifi, vm::resetWifiOnBoard) { dlg = "" }
    }
}
