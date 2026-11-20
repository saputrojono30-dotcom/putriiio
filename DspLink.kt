package id.dlms.dsp.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface LinkEvent {
    data class Status(val text: String) : LinkEvent
    data object Connected : LinkEvent
    data class Line(val text: String) : LinkEvent
    data class Lost(val reason: String) : LinkEvent
}

/**
 * Sambungan TCP ke papan DLMS-S3 lewat WiFi.
 *
 * Dua cara menyambung:
 *  - joinWifi = true  : aplikasi sendiri meminta Android bergabung ke WiFi [ssid] dengan
 *                       password WPA2 [pass] (WifiNetworkSpecifier). Android menampilkan satu
 *                       dialog persetujuan. Soket dipaksa lewat jaringan itu, jadi tetap jalan
 *                       walaupun WiFi papan "tanpa internet".
 *  - joinWifi = false : pengguna sudah bergabung lewat Pengaturan Android; aplikasi mencari
 *                       jaringan WiFi aktif dan memakainya untuk soket.
 */
class DspLink(context: Context, private val scope: CoroutineScope) {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 1024)
    val events: SharedFlow<LinkEvent> = _events

    private var job: Job? = null
    private var socket: Socket? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var queue: Channel<String>? = null

    val running: Boolean get() = job?.isActive == true

    fun connect(host: String, port: Int, ssid: String, pass: String, joinWifi: Boolean) {
        disconnect()
        val q = Channel<String>(Channel.UNLIMITED)
        queue = q
        job = scope.launch(Dispatchers.IO) {
            var lostReason: String
            try {
                val net: Network? = if (joinWifi) {
                    emit(LinkEvent.Status("Bergabung ke WiFi \"$ssid\"…"))
                    requestWifi(ssid, pass)
                } else {
                    emit(LinkEvent.Status("Mencari jaringan WiFi aktif…"))
                    cm.allNetworks.firstOrNull {
                        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    }
                }
                emit(LinkEvent.Status("Membuka sambungan ke $host:$port…"))
                val s = net?.socketFactory?.createSocket() ?: Socket()
                socket = s
                s.tcpNoDelay = true
                s.soTimeout = 5000
                s.connect(InetSocketAddress(host, port), 4000)
                emit(LinkEvent.Connected)

                coroutineScope {
                    launch {                                   // penulis
                        val out = s.getOutputStream()
                        for (m in q) {
                            out.write((m + "\n").toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                    }
                    val rd = s.getInputStream().bufferedReader(Charsets.UTF_8)   // pembaca
                    while (true) {
                        val ln = try { rd.readLine() } catch (e: SocketTimeoutException) {
                            throw IOException("papan tidak menjawab (timeout)")
                        } ?: throw IOException("papan menutup sambungan")
                        val t = ln.trim()
                        if (t.isNotEmpty()) emit(LinkEvent.Line(t))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lostReason = e.message ?: e.javaClass.simpleName
                // Putus karena disconnect() disengaja: jangan dilaporkan sebagai kegagalan.
                if (currentCoroutineContext().isActive) emit(LinkEvent.Lost(lostReason))
            } finally {
                runCatching { socket?.close() }
                socket = null
                q.close()
                releaseWifi()
            }
        }
    }

    private suspend fun requestWifi(ssid: String, pass: String): Network =
        suspendCancellableCoroutine { cont ->
            val spec = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(pass)
                .build()
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(spec)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (cont.isActive) cont.resume(network)
                }
                override fun onUnavailable() {
                    if (cont.isActive) cont.resumeWithException(
                        IOException("WiFi \"$ssid\" tidak ditemukan, password salah, atau sambungan ditolak"))
                }
                override fun onLost(network: Network) {
                    _events.tryEmit(LinkEvent.Lost("WiFi terputus"))
                    runCatching { socket?.close() }    // membangunkan pembaca yang sedang menunggu
                }
            }
            netCb = cb
            cm.requestNetwork(req, cb, 30_000)
            cont.invokeOnCancellation { releaseWifi() }
        }

    private fun releaseWifi() {
        netCb?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        netCb = null
    }

    private suspend fun emit(e: LinkEvent) = _events.emit(e)

    fun send(line: String) { queue?.trySend(line) }

    fun disconnect() {
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        releaseWifi()
    }
}
