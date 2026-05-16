package com.Atom2Universe.app.music.sync.peer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.Atom2Universe.app.music.sync.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Gère l'enregistrement NSD (mDNS) du service local et la découverte des pairs A2U.
 *
 * Protocole :
 *   - Type de service : _a2u._tcp
 *   - Nom             : A2U-[deviceId[:8]] (unique par appareil)
 *   - Port            : PeerSyncServer.PORT
 *
 * À chaque pair découvert et résolu, [onPeerFound] est appelé avec ses coordonnées.
 * À la perte d'un pair, il est retiré de la liste et [onPeerLost] est appelé.
 */
class PeerDiscoveryManager(
    private val context: Context,
    private val onPeerFound: (DiscoveredPeer) -> Unit,
    private val onPeerLost: (String) -> Unit   // deviceId
) {
    companion object {
        private const val TAG = "PeerDiscovery"
        private const val SERVICE_TYPE = "_a2u._tcp"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val deviceId = DeviceIdentity.getDeviceId(context)
    private val serviceName = "A2U-${deviceId.take(8)}"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolvedPeers = ConcurrentHashMap<String, DiscoveredPeer>()

    // Listeners conservés en champs pour pouvoir les dé-enregistrer
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // ==================== Enregistrement ====================

    fun registerService(port: Int = PeerSyncServer.PORT) {
        val info = NsdServiceInfo().apply {
            this.serviceName = this@PeerDiscoveryManager.serviceName
            serviceType = SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Service registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Registration failed: code $code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Unregistration failed: code $code")
            }
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregisterService() {
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (_: Exception) {}
            registrationListener = null
        }
    }

    // ==================== Découverte ====================

    fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                Log.w(TAG, "Discovery start failed: code $code")
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {
                Log.w(TAG, "Discovery stop failed: code $code")
            }
            override fun onDiscoveryStarted(type: String) {
                Log.d(TAG, "Discovery started for $type")
            }
            override fun onDiscoveryStopped(type: String) {
                Log.d(TAG, "Discovery stopped")
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == serviceName) return  // ignorer soi-même
                if (!info.serviceType.contains("_a2u")) return
                Log.d(TAG, "Found service: ${info.serviceName}")
                resolveService(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                Log.d(TAG, "Lost service: ${info.serviceName}")
                val lost = resolvedPeers.entries
                    .firstOrNull { it.value.host.isNotEmpty() }  // match par nom
                lost?.let {
                    resolvedPeers.remove(it.key)
                    onPeerLost(it.key)
                }
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
            discoveryListener = null
        }
    }

    private fun resolveService(info: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Resolve failed for ${info.serviceName}: code $code")
            }
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    resolved.hostAddresses.firstOrNull()?.hostAddress ?: return
                } else {
                    @Suppress("DEPRECATION")
                    resolved.host?.hostAddress ?: return
                }
                val port = resolved.port
                Log.i(TAG, "Resolved: ${resolved.serviceName} → $host:$port")

                scope.launch {
                    // Récupérer le deviceId réel via /info
                    val tempPeer = DiscoveredPeer(deviceId = resolved.serviceName, host = host, port = port)
                    val peer = enrichPeerInfo(tempPeer)
                    if (peer.deviceId != deviceId) {
                        resolvedPeers[peer.deviceId] = peer
                        onPeerFound(peer)
                    }
                }
            }
        }
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsdManager.resolveService(info, Executors.newSingleThreadExecutor(), resolveListener)
        } else {
            nsdManager.resolveService(info, resolveListener)
        }
    }

    private suspend fun enrichPeerInfo(peer: DiscoveredPeer): DiscoveredPeer {
        return try {
            val url = java.net.URL("http://${peer.host}:${peer.port}/info")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            peer.copy(
                deviceId = json.optString("deviceId", peer.deviceId),
                latestEventTimestamp = json.optLong("latestEventAt", 0L),
                latestLyricsTimestamp = json.optLong("latestLyricsAt", 0L)
            )
        } catch (_: Exception) {
            peer
        }
    }

    fun getKnownPeers(): List<DiscoveredPeer> = resolvedPeers.values.toList()
}
