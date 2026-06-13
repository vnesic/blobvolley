package rs.vn.blobvolley.network

import android.os.Handler
import android.os.Looper
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDP-based network manager for LAN multiplayer.
 * Handles discovery, connection, and game state exchange.
 */
class NetworkManager(private val listener: NetworkListener) {

    interface NetworkListener {
        fun onHostDiscovered(host: HostInfo)
        fun onClientConnected(clientName: String, clientAddress: InetAddress)
        fun onClientDisconnected()
        fun onConnectionAccepted()
        fun onConnectionRejected()
        fun onRemoteInput(seq: Int, left: Boolean, right: Boolean, jump: Boolean)
        fun onGameStateReceived(snapshot: GameStateSnapshot)
        fun onGameStart(servingLeft: Boolean)
        fun onPingUpdate(latencyMs: Int)
        fun onError(message: String)
    }

    data class HostInfo(
        val name: String,
        val address: InetAddress,
        val port: Int
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var socket: DatagramSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var receiveThread: Thread? = null
    private var discoveryThread: Thread? = null
    private var announceThread: Thread? = null

    private val running = AtomicBoolean(false)
    private var isHost = false
    private var remoteAddress: InetAddress? = null
    private var remotePort: Int = Protocol.PORT

    var hostName: String = "Player"
    var latencyMs: Int = 0
        private set

    // ==================== Host Mode ====================

    fun startHost(name: String) {
        if (running.get()) stop()

        hostName = name
        isHost = true
        running.set(true)

        try {
            socket = DatagramSocket(Protocol.PORT).apply {
                soTimeout = 100
                broadcast = true
            }
            discoverySocket = DatagramSocket(Protocol.DISCOVERY_PORT).apply {
                soTimeout = 100
                broadcast = true
            }
        } catch (e: Exception) {
            postError("Failed to start host: ${e.message}")
            stop()
            return
        }

        startReceiveThread()
        startAnnounceThread()
        startDiscoveryListenThread()
    }

    private fun startAnnounceThread() {
        announceThread = Thread {
            val packet = Protocol.createAnnouncePacket(hostName)
            while (running.get() && remoteAddress == null) {
                try {
                    val broadcastAddresses = getBroadcastAddresses()
                    for (addr in broadcastAddresses) {
                        socket?.send(DatagramPacket(packet, packet.size, addr, Protocol.DISCOVERY_PORT))
                    }
                } catch (_: Exception) {}
                Thread.sleep(1000)
            }
        }.apply {
            name = "HostAnnounce"
            isDaemon = true
            start()
        }
    }

    private fun startDiscoveryListenThread() {
        discoveryThread = Thread {
            val buffer = ByteArray(256)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    discoverySocket?.receive(packet)

                    val msg = Protocol.parsePacket(buffer, packet.length)
                    if (msg is ParsedMessage.Discover && remoteAddress == null) {
                        // Send direct announce to the discovering client
                        val announce = Protocol.createAnnouncePacket(hostName)
                        socket?.send(DatagramPacket(announce, announce.size, packet.address, Protocol.PORT))
                    }
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {}
            }
        }.apply {
            name = "DiscoveryListen"
            isDaemon = true
            start()
        }
    }

    // ==================== Client Mode ====================

    fun startClient(name: String) {
        if (running.get()) stop()

        hostName = name
        isHost = false
        running.set(true)

        try {
            socket = DatagramSocket().apply {
                soTimeout = 100
                broadcast = true
            }
            discoverySocket = DatagramSocket(Protocol.DISCOVERY_PORT).apply {
                soTimeout = 100
                broadcast = true
            }
        } catch (e: Exception) {
            postError("Failed to start client: ${e.message}")
            stop()
            return
        }

        startReceiveThread()
        startDiscoveryThread()
    }

    private fun startDiscoveryThread() {
        discoveryThread = Thread {
            val discoverPacket = Protocol.createDiscoverPacket()
            val buffer = ByteArray(256)

            while (running.get() && remoteAddress == null) {
                try {
                    // Broadcast discovery
                    val broadcastAddresses = getBroadcastAddresses()
                    for (addr in broadcastAddresses) {
                        socket?.send(DatagramPacket(discoverPacket, discoverPacket.size, addr, Protocol.DISCOVERY_PORT))
                    }

                    // Listen for announces on discovery socket
                    val packet = DatagramPacket(buffer, buffer.size)
                    discoverySocket?.receive(packet)

                    val msg = Protocol.parsePacket(buffer, packet.length)
                    if (msg is ParsedMessage.Announce) {
                        mainHandler.post {
                            listener.onHostDiscovered(HostInfo(msg.hostName, packet.address, Protocol.PORT))
                        }
                    }
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {}

                Thread.sleep(500)
            }
        }.apply {
            name = "DiscoveryThread"
            isDaemon = true
            start()
        }
    }

    fun connectToHost(host: HostInfo) {
        remoteAddress = host.address
        remotePort = host.port

        val joinPacket = Protocol.createJoinPacket(hostName)
        sendPacket(joinPacket)
    }

    // ==================== Communication ====================

    private fun startReceiveThread() {
        receiveThread = Thread {
            val buffer = ByteArray(1024)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val msg = Protocol.parsePacket(buffer, packet.length) ?: continue
                    handleMessage(msg, packet.address, packet.port)
                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    if (running.get()) {
                        postError("Network error: ${e.message}")
                    }
                }
            }
        }.apply {
            name = "ReceiveThread"
            isDaemon = true
            start()
        }
    }

    private fun handleMessage(msg: ParsedMessage, fromAddress: InetAddress, fromPort: Int) {
        when (msg) {
            is ParsedMessage.Announce -> {
                if (!isHost) {
                    mainHandler.post {
                        listener.onHostDiscovered(HostInfo(msg.hostName, fromAddress, fromPort))
                    }
                }
            }
            is ParsedMessage.Join -> {
                if (isHost && remoteAddress == null) {
                    remoteAddress = fromAddress
                    remotePort = fromPort
                    sendPacket(Protocol.createAcceptPacket())
                    mainHandler.post { listener.onClientConnected(msg.playerName, fromAddress) }
                } else if (isHost) {
                    // Already have a client
                    val rejectPacket = Protocol.createRejectPacket()
                    socket?.send(DatagramPacket(rejectPacket, rejectPacket.size, fromAddress, fromPort))
                }
            }
            is ParsedMessage.Accept -> {
                if (!isHost) {
                    mainHandler.post { listener.onConnectionAccepted() }
                }
            }
            is ParsedMessage.Reject -> {
                if (!isHost) {
                    remoteAddress = null
                    mainHandler.post { listener.onConnectionRejected() }
                }
            }
            is ParsedMessage.Input -> {
                mainHandler.post { listener.onRemoteInput(msg.seq, msg.left, msg.right, msg.jump) }
            }
            is ParsedMessage.State -> {
                mainHandler.post { listener.onGameStateReceived(msg.snapshot) }
            }
            is ParsedMessage.Start -> {
                mainHandler.post { listener.onGameStart(msg.servingLeft) }
            }
            is ParsedMessage.Ping -> {
                sendPacket(Protocol.createPongPacket(msg.timestamp))
            }
            is ParsedMessage.Pong -> {
                val now = System.currentTimeMillis()
                latencyMs = ((now - msg.timestamp) / 2).toInt()
                mainHandler.post { listener.onPingUpdate(latencyMs) }
            }
            is ParsedMessage.Leave -> {
                remoteAddress = null
                mainHandler.post { listener.onClientDisconnected() }
            }
            else -> {}
        }
    }

    fun sendInput(seq: Int, left: Boolean, right: Boolean, jump: Boolean) {
        sendPacket(Protocol.createInputPacket(seq, left, right, jump))
    }

    fun sendGameState(snapshot: GameStateSnapshot) {
        sendPacket(Protocol.createStatePacket(snapshot))
    }

    fun sendGameStart(servingLeft: Boolean) {
        sendPacket(Protocol.createStartPacket(servingLeft))
    }

    fun sendPing() {
        sendPacket(Protocol.createPingPacket(System.currentTimeMillis()))
    }

    private fun sendPacket(data: ByteArray) {
        val addr = remoteAddress ?: return
        try {
            socket?.send(DatagramPacket(data, data.size, addr, remotePort))
        } catch (_: Exception) {}
    }

    // ==================== Lifecycle ====================

    fun stop() {
        running.set(false)

        try {
            if (remoteAddress != null) {
                val leavePacket = Protocol.createLeavePacket()
                socket?.send(DatagramPacket(leavePacket, leavePacket.size, remoteAddress, remotePort))
            }
        } catch (_: Exception) {}

        remoteAddress = null

        try { socket?.close() } catch (_: Exception) {}
        try { discoverySocket?.close() } catch (_: Exception) {}

        socket = null
        discoverySocket = null
        receiveThread = null
        discoveryThread = null
        announceThread = null
    }

    fun isConnected(): Boolean = remoteAddress != null

    fun isHostMode(): Boolean = isHost

    // ==================== Utilities ====================

    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                for (interfaceAddress in iface.interfaceAddresses) {
                    interfaceAddress.broadcast?.let { addresses.add(it) }
                }
            }
        } catch (_: Exception) {}

        // Fallback
        if (addresses.isEmpty()) {
            try {
                addresses.add(InetAddress.getByName("255.255.255.255"))
            } catch (_: Exception) {}
        }

        return addresses
    }

    private fun postError(message: String) {
        mainHandler.post { listener.onError(message) }
    }
}
