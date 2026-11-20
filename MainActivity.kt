package id.dlms.dsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import id.dlms.dsp.ui.*

class MainActivity : ComponentActivity() {
    private val vm: DspViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DlmsTheme {
                val st by vm.state.collectAsState()
                val meters by vm.meters.collectAsState()
                val settings by vm.settings.collectAsState()
                val snack = remember { SnackbarHostState() }

                // Layar tetap menyala selama terhubung, supaya meter tidak membeku.
                val view = LocalView.current
                SideEffect { view.keepScreenOn = st.conn == ConnState.Connected }

                LaunchedEffect(st.toast) {
                    st.toast?.let { snack.showSnackbar(it); vm.toastShown() }
                }

                Scaffold(
                    containerColor = BG,
                    snackbarHost = { SnackbarHost(snack) },
                    modifier = Modifier.safeDrawingPadding()
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize().background(BG)) {
                        if (st.conn == ConnState.Connected) {
                            MainScreen(vm, meters)
                        } else {
                            ConnectScreen(settings, st.conn, st.message, vm::connect, vm::disconnect)
                        }
                    }
                }
            }
        }
    }
}
