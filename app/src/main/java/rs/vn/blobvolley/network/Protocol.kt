package rs.vn.blobvolley.network

import java.nio.ByteBuffer

/**
 * Lightweight UDP protocol for LAN multiplayer.
 * All messages are prefixed with a 1-byte message type.
 */
object Protocol {
    const val PORT = 19847
    const val DISCOVERY_PORT = 19848
    const val MAGIC = 0x424C4F42 // "BLOB"

    // Message types
    const val MSG_DISCOVER = 0x01.toByte()      // Broadcast to find hosts
    const val MSG_ANNOUNCE = 0x02.toByte()      // Host announces presence
    const val MSG_JOIN = 0x03.toByte()          // Client requests to join
    const val MSG_ACCEPT = 0x04.toByte()        // Host accepts client
    const val MSG_REJECT = 0x05.toByte()        // Host rejects client
    const val MSG_INPUT = 0x06.toByte()         // Input state update
    const val MSG_STATE = 0x07.toByte()         // Full game state sync
    const val MSG_START = 0x08.toByte()         // Game start signal
    const val MSG_PING = 0x09.toByte()          // Ping for latency
    const val MSG_PONG = 0x0A.toByte()          // Pong response
    const val MSG_LEAVE = 0x0B.toByte()         // Player leaves

    fun createDiscoverPacket(): ByteArray {
        return ByteBuffer.allocate(5).apply {
            putInt(MAGIC)
            put(MSG_DISCOVER)
        }.array()
    }

    fun createAnnouncePacket(hostName: String): ByteArray {
        val nameBytes = hostName.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(6 + nameBytes.size).apply {
            putInt(MAGIC)
            put(MSG_ANNOUNCE)
            put(nameBytes.size.toByte())
            put(nameBytes)
        }.array()
    }

    fun createJoinPacket(playerName: String): ByteArray {
        val nameBytes = playerName.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(6 + nameBytes.size).apply {
            putInt(MAGIC)
            put(MSG_JOIN)
            put(nameBytes.size.toByte())
            put(nameBytes)
        }.array()
    }

    fun createAcceptPacket(): ByteArray {
        return ByteBuffer.allocate(5).apply {
            putInt(MAGIC)
            put(MSG_ACCEPT)
        }.array()
    }

    fun createRejectPacket(): ByteArray {
        return ByteBuffer.allocate(5).apply {
            putInt(MAGIC)
            put(MSG_REJECT)
        }.array()
    }

    fun createInputPacket(seq: Int, left: Boolean, right: Boolean, jump: Boolean): ByteArray {
        val flags = (if (left) 1 else 0) or
                    (if (right) 2 else 0) or
                    (if (jump) 4 else 0)
        return ByteBuffer.allocate(10).apply {
            putInt(MAGIC)
            put(MSG_INPUT)
            putInt(seq)
            put(flags.toByte())
        }.array()
    }

    fun createStatePacket(state: GameStateSnapshot): ByteArray {
        return ByteBuffer.allocate(65).apply {
            putInt(MAGIC)
            put(MSG_STATE)
            putInt(state.tick)
            // Player blob (16 bytes)
            putFloat(state.playerX)
            putFloat(state.playerY)
            putFloat(state.playerVx)
            putFloat(state.playerVy)
            // Bot/opponent blob (16 bytes)
            putFloat(state.opponentX)
            putFloat(state.opponentY)
            putFloat(state.opponentVx)
            putFloat(state.opponentVy)
            // Ball (16 bytes)
            putFloat(state.ballX)
            putFloat(state.ballY)
            putFloat(state.ballVx)
            putFloat(state.ballVy)
            // Scores and touches (4 bytes)
            put(state.scoreL.toByte())
            put(state.scoreR.toByte())
            put(state.touchesL.toByte())
            put(state.touchesR.toByte())
            // Flags (1 byte)
            val flags = (if (state.frozen) 1 else 0) or
                        (if (state.servingLeft) 2 else 0)
            put(flags.toByte())
        }.array()
    }

    fun createStartPacket(servingLeft: Boolean): ByteArray {
        return ByteBuffer.allocate(6).apply {
            putInt(MAGIC)
            put(MSG_START)
            put(if (servingLeft) 1 else 0)
        }.array()
    }

    fun createPingPacket(timestamp: Long): ByteArray {
        return ByteBuffer.allocate(13).apply {
            putInt(MAGIC)
            put(MSG_PING)
            putLong(timestamp)
        }.array()
    }

    fun createPongPacket(timestamp: Long): ByteArray {
        return ByteBuffer.allocate(13).apply {
            putInt(MAGIC)
            put(MSG_PONG)
            putLong(timestamp)
        }.array()
    }

    fun createLeavePacket(): ByteArray {
        return ByteBuffer.allocate(5).apply {
            putInt(MAGIC)
            put(MSG_LEAVE)
        }.array()
    }

    fun parsePacket(data: ByteArray, length: Int): ParsedMessage? {
        if (length < 5) return null
        val buf = ByteBuffer.wrap(data, 0, length)
        val magic = buf.int
        if (magic != MAGIC) return null
        val type = buf.get()

        return when (type) {
            MSG_DISCOVER -> ParsedMessage.Discover
            MSG_ANNOUNCE -> {
                if (length < 6) return null
                val nameLen = buf.get().toInt() and 0xFF
                if (length < 6 + nameLen) return null
                val nameBytes = ByteArray(nameLen)
                buf.get(nameBytes)
                ParsedMessage.Announce(String(nameBytes, Charsets.UTF_8))
            }
            MSG_JOIN -> {
                if (length < 6) return null
                val nameLen = buf.get().toInt() and 0xFF
                if (length < 6 + nameLen) return null
                val nameBytes = ByteArray(nameLen)
                buf.get(nameBytes)
                ParsedMessage.Join(String(nameBytes, Charsets.UTF_8))
            }
            MSG_ACCEPT -> ParsedMessage.Accept
            MSG_REJECT -> ParsedMessage.Reject
            MSG_INPUT -> {
                if (length < 10) return null
                val seq = buf.int
                val flags = buf.get().toInt()
                ParsedMessage.Input(
                    seq = seq,
                    left = (flags and 1) != 0,
                    right = (flags and 2) != 0,
                    jump = (flags and 4) != 0
                )
            }
            MSG_STATE -> {
                if (length < 65) return null
                ParsedMessage.State(
                    GameStateSnapshot(
                        tick = buf.int,
                        playerX = buf.float,
                        playerY = buf.float,
                        playerVx = buf.float,
                        playerVy = buf.float,
                        opponentX = buf.float,
                        opponentY = buf.float,
                        opponentVx = buf.float,
                        opponentVy = buf.float,
                        ballX = buf.float,
                        ballY = buf.float,
                        ballVx = buf.float,
                        ballVy = buf.float,
                        scoreL = buf.get().toInt(),
                        scoreR = buf.get().toInt(),
                        touchesL = buf.get().toInt(),
                        touchesR = buf.get().toInt(),
                        frozen = (buf.get().toInt() and 1) != 0,
                        servingLeft = (buf.get().toInt() and 2) != 0
                    )
                )
            }
            MSG_START -> {
                if (length < 6) return null
                ParsedMessage.Start(buf.get() == 1.toByte())
            }
            MSG_PING -> {
                if (length < 13) return null
                ParsedMessage.Ping(buf.long)
            }
            MSG_PONG -> {
                if (length < 13) return null
                ParsedMessage.Pong(buf.long)
            }
            MSG_LEAVE -> ParsedMessage.Leave
            else -> null
        }
    }
}

data class GameStateSnapshot(
    val tick: Int,
    val playerX: Float,
    val playerY: Float,
    val playerVx: Float,
    val playerVy: Float,
    val opponentX: Float,
    val opponentY: Float,
    val opponentVx: Float,
    val opponentVy: Float,
    val ballX: Float,
    val ballY: Float,
    val ballVx: Float,
    val ballVy: Float,
    val scoreL: Int,
    val scoreR: Int,
    val touchesL: Int,
    val touchesR: Int,
    val frozen: Boolean,
    val servingLeft: Boolean
)

sealed class ParsedMessage {
    data object Discover : ParsedMessage()
    data class Announce(val hostName: String) : ParsedMessage()
    data class Join(val playerName: String) : ParsedMessage()
    data object Accept : ParsedMessage()
    data object Reject : ParsedMessage()
    data class Input(val seq: Int, val left: Boolean, val right: Boolean, val jump: Boolean) : ParsedMessage()
    data class State(val snapshot: GameStateSnapshot) : ParsedMessage()
    data class Start(val servingLeft: Boolean) : ParsedMessage()
    data class Ping(val timestamp: Long) : ParsedMessage()
    data class Pong(val timestamp: Long) : ParsedMessage()
    data object Leave : ParsedMessage()
}
