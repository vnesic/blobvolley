package rs.vn.blobvolley

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rs.vn.blobvolley.network.GameStateSnapshot
import rs.vn.blobvolley.network.NetworkManager
import java.net.InetAddress
import kotlin.random.Random

class LobbyActivity : ComponentActivity(), NetworkManager.NetworkListener {

    companion object {
        const val EXTRA_IS_HOST = "is_host"
        const val EXTRA_PLAYER_NAME = "player_name"
    }

    private lateinit var networkManager: NetworkManager
    private var isHost = false
    private var playerName = "Player"

    private lateinit var statusText: TextView
    private lateinit var hostListLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var startButton: Button
    private lateinit var backButton: Button

    private val discoveredHosts = mutableMapOf<String, NetworkManager.HostInfo>()
    private var connectedOpponent: String? = null
    private var opponentAddress: InetAddress? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        isHost = intent.getBooleanExtra(EXTRA_IS_HOST, false)
        playerName = intent.getStringExtra(EXTRA_PLAYER_NAME) ?: "Player"

        networkManager = NetworkManager(this)

        val dp = resources.displayMetrics.density

        // Create UI
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(0xFF1C2733.toInt())
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * dp).toInt(), (32 * dp).toInt(), (32 * dp).toInt(), (32 * dp).toInt())
        }

        val titleText = TextView(this).apply {
            text = if (isHost) "Hosting Game" else "Finding Games"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        contentLayout.addView(titleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (24 * dp).toInt() })

        statusText = TextView(this).apply {
            text = if (isHost) "Waiting for opponent..." else "Searching for hosts..."
            textSize = 16f
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
        }
        contentLayout.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * dp).toInt() })

        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }
        contentLayout.addView(progressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (24 * dp).toInt() })

        hostListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (isHost) View.GONE else View.VISIBLE
        }
        contentLayout.addView(hostListLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (24 * dp).toInt() })

        startButton = Button(this).apply {
            text = "Start Game"
            visibility = View.GONE
            setOnClickListener { startGame() }
        }
        contentLayout.addView(startButton, LinearLayout.LayoutParams(
            (200 * dp).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (16 * dp).toInt() })

        backButton = Button(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        }
        contentLayout.addView(backButton, LinearLayout.LayoutParams(
            (200 * dp).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        rootLayout.addView(contentLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        setContentView(rootLayout)

        // Start networking
        if (isHost) {
            networkManager.startHost(playerName)
        } else {
            networkManager.startClient(playerName)
        }
    }

    override fun onDestroy() {
        networkManager.stop()
        super.onDestroy()
    }

    private fun startGame() {
        val servingLeft = Random.nextBoolean()
        networkManager.sendGameStart(servingLeft)

        val intent = Intent(this, MultiplayerActivity::class.java).apply {
            putExtra(MultiplayerActivity.EXTRA_IS_HOST, isHost)
            putExtra(MultiplayerActivity.EXTRA_SERVING_LEFT, servingLeft)
            putExtra(MultiplayerActivity.EXTRA_OPPONENT_NAME, connectedOpponent ?: "Opponent")
        }
        startActivity(intent)
        finish()
    }

    private fun addHostButton(host: NetworkManager.HostInfo) {
        val dp = resources.displayMetrics.density
        val button = Button(this).apply {
            text = "${host.name}\n${host.address.hostAddress}"
            setOnClickListener {
                statusText.text = "Connecting to ${host.name}..."
                progressBar.visibility = View.VISIBLE
                networkManager.connectToHost(host)
            }
        }
        hostListLayout.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (8 * dp).toInt() })
    }

    // ================= NetworkManager.NetworkListener =================

    override fun onHostDiscovered(host: NetworkManager.HostInfo) {
        val key = host.address.hostAddress ?: return
        if (!discoveredHosts.containsKey(key)) {
            discoveredHosts[key] = host
            addHostButton(host)
        }
    }

    override fun onClientConnected(clientName: String, clientAddress: InetAddress) {
        connectedOpponent = clientName
        opponentAddress = clientAddress
        statusText.text = "$clientName connected!"
        progressBar.visibility = View.GONE
        startButton.visibility = View.VISIBLE
    }

    override fun onClientDisconnected() {
        connectedOpponent = null
        opponentAddress = null
        statusText.text = "Opponent disconnected. Waiting..."
        progressBar.visibility = View.VISIBLE
        startButton.visibility = View.GONE
    }

    override fun onConnectionAccepted() {
        // Client connected to host - start game
        val intent = Intent(this, MultiplayerActivity::class.java).apply {
            putExtra(MultiplayerActivity.EXTRA_IS_HOST, false)
            putExtra(MultiplayerActivity.EXTRA_SERVING_LEFT, true) // Will be overridden by host's start
        }
        startActivity(intent)
        finish()
    }

    override fun onConnectionRejected() {
        Toast.makeText(this, "Connection rejected - host already has a player", Toast.LENGTH_SHORT).show()
    }

    override fun onRemoteInput(seq: Int, left: Boolean, right: Boolean, jump: Boolean) {}

    override fun onGameStateReceived(snapshot: GameStateSnapshot) {}

    override fun onGameStart(servingLeft: Boolean) {
        // Client receives start signal
        val intent = Intent(this, MultiplayerActivity::class.java).apply {
            putExtra(MultiplayerActivity.EXTRA_IS_HOST, false)
            putExtra(MultiplayerActivity.EXTRA_SERVING_LEFT, servingLeft)
        }
        startActivity(intent)
        finish()
    }

    override fun onPingUpdate(latencyMs: Int) {}

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}