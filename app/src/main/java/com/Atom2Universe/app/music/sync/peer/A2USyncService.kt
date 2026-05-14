package com.Atom2Universe.app.music.sync.peer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service background gérant la synchronisation P2P LAN.
 * Pas de notification — le service tourne silencieusement.
 * Ne synce que si le réseau Wi-Fi actuel est de confiance.
 */
class A2USyncService : Service() {

    companion object {
        private const val TAG = "A2USyncService"
        private const val RESYNC_INTERVAL_MS = 60 * 60 * 1000L   // 1 heure

        private const val ACTION_START = "com.Atom2Universe.app.LAN_SYNC_START"
        private const val ACTION_STOP  = "com.Atom2Universe.app.LAN_SYNC_STOP"

        fun startLanSync(context: Context) {
            context.startService(
                Intent(context, A2USyncService::class.java).apply { action = ACTION_START }
            )
        }

        fun stopLanSync(context: Context) {
            context.startService(
                Intent(context, A2USyncService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resyncJob: Job? = null

    private lateinit var server: PeerSyncServer
    private lateinit var discovery: PeerDiscoveryManager
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ==================== Lifecycle ====================

    override fun onCreate() {
        super.onCreate()
        server = PeerSyncServer(this)
        discovery = PeerDiscoveryManager(
            context     = this,
            onPeerFound = { peer -> onPeerFound(peer) },
            onPeerLost  = { deviceId -> Log.d(TAG, "Peer lost: ${deviceId.take(8)}") }
        )
        connectivityManager = getSystemService(ConnectivityManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> {
                registerNetworkCallback()
                applyTrustedNetworkState()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        stopComponents()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ==================== Réseau de confiance ====================

    private fun applyTrustedNetworkState() {
        if (TrustedNetworkManager.isCurrentNetworkTrusted(this)) {
            startComponents()
        } else {
            stopComponents()
            Log.d(TAG, "Réseau non fiable — sync inactive")
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    delay(1000)
                    applyTrustedNetworkState()
                }
            }
            override fun onLost(network: Network) {
                stopComponents()
                Log.d(TAG, "Wi-Fi perdu — sync arrêtée")
            }
        }.also { connectivityManager.registerNetworkCallback(request, it) }
    }

    // ==================== Composants sync ====================

    private fun startComponents() {
        if (server.isRunning()) return
        server.start()
        discovery.registerService(server.port)
        discovery.startDiscovery()
        resyncJob = scope.launch {
            while (isActive) {
                delay(RESYNC_INTERVAL_MS)
                resyncKnownPeers()
            }
        }
        Log.i(TAG, "LAN sync démarrée")
    }

    private fun stopComponents() {
        resyncJob?.cancel()
        resyncJob = null
        try { discovery.stopDiscovery() }     catch (_: Exception) {}
        try { discovery.unregisterService() } catch (_: Exception) {}
        try { server.stop() }                 catch (_: Exception) {}
    }

    private fun onPeerFound(peer: DiscoveredPeer) {
        scope.launch {
            val events = PeerSyncClient.syncWithPeer(peer, applicationContext)
            val lyrics = PeerSyncClient.syncLyricsWith(peer, applicationContext)
            if (events > 0 || lyrics > 0) {
                Log.i(TAG, "Sync depuis ${peer.host}: $events events, $lyrics paroles")
            }
        }
    }

    private suspend fun resyncKnownPeers() {
        val peers = discovery.getKnownPeers()
        if (peers.isEmpty()) return
        Log.d(TAG, "Resync horaire → ${peers.size} pair(s)")
        for (peer in peers) {
            PeerSyncClient.syncWithPeer(peer, applicationContext)
            PeerSyncClient.syncLyricsWith(peer, applicationContext)
        }
    }
}
