package rs.vn.blobvolley

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rs.vn.blobvolley.game.MultiplayerGameView
import rs.vn.blobvolley.network.GameStateSnapshot
import rs.vn.blobvolley.network.NetworkManager
import java.net.InetAddress

class MultiplayerActivity : ComponentActivity(), NetworkManager.NetworkListener {

    companion object {
        const val EXTRA_IS_HOST = "is_host"
        const val EXTRA_SERVING_LEFT = "serving_left"
        const val EXTRA_OPPONENT_NAME = "opponent_name"
    }

    private lateinit var gameView: MultiplayerGameView
    private lateinit var networkManager: NetworkManager
    private var isHost = false

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
        val servingLeft = intent.getBooleanExtra(EXTRA_SERVING_LEFT, true)
        val opponentName = intent.getStringExtra(EXTRA_OPPONENT_NAME) ?: "Opponent"

        networkManager = NetworkManager(this)

        gameView = MultiplayerGameView(this, networkManager, isHost) {
            // On disconnected callback
            runOnUiThread { finish() }
        }
        gameView.opponentName = opponentName

        setContentView(gameView)

        // Start networking in appropriate mode
        if (isHost) {
            networkManager.startHost("Host")
        } else {
            networkManager.startClient("Client")
        }

        // Start the game
        gameView.startGame(servingLeft)
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        gameView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        networkManager.stop()
        super.onDestroy()
    }

    // ================= NetworkManager.NetworkListener (forward to GameView) =================

    override fun onHostDiscovered(host: NetworkManager.HostInfo) {
        gameView.onHostDiscovered(host)
    }

    override fun onClientConnected(clientName: String, clientAddress: InetAddress) {
        gameView.onClientConnected(clientName, clientAddress)
    }

    override fun onClientDisconnected() {
        gameView.onClientDisconnected()
    }

    override fun onConnectionAccepted() {
        gameView.onConnectionAccepted()
    }

    override fun onConnectionRejected() {
        gameView.onConnectionRejected()
    }

    override fun onRemoteInput(seq: Int, left: Boolean, right: Boolean, jump: Boolean) {
        gameView.onRemoteInput(seq, left, right, jump)
    }

    override fun onGameStateReceived(snapshot: GameStateSnapshot) {
        gameView.onGameStateReceived(snapshot)
    }

    override fun onGameStart(servingLeft: Boolean) {
        gameView.onGameStart(servingLeft)
    }

    override fun onPingUpdate(latencyMs: Int) {
        gameView.onPingUpdate(latencyMs)
    }

    override fun onError(message: String) {
        gameView.onError(message)
    }
}
