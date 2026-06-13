package rs.vn.blobvolley

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rs.vn.blobvolley.game.GameEngine
import rs.vn.blobvolley.game.GameView

class MainActivity : ComponentActivity() {

    private var gameView: GameView? = null
    private var menuLayout: FrameLayout? = null
    private var selectedDifficulty = GameEngine.AIDifficulty.HARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        showMenu()
    }

    private fun showMenu() {
        gameView?.let {
            (it.parent as? FrameLayout)?.removeView(it)
        }
        gameView = null

        val dp = resources.displayMetrics.density

        menuLayout = FrameLayout(this).apply {
            setBackgroundColor(0xFF1C2733.toInt())
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * dp).toInt(), (32 * dp).toInt(), (32 * dp).toInt(), (32 * dp).toInt())
        }

        // Title
        val titleText = TextView(this).apply {
            text = "BLOB VOLLEY"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        contentLayout.addView(titleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (40 * dp).toInt() })

        // Single Player button
        val singlePlayerBtn = Button(this).apply {
            text = "Single Player"
            textSize = 16f
            setOnClickListener { startSinglePlayer() }
        }
        contentLayout.addView(singlePlayerBtn, LinearLayout.LayoutParams(
            (250 * dp).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (12 * dp).toInt() })

        // Difficulty selector
        val difficultyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val difficultyLabel = TextView(this).apply {
            text = "AI: "
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
        }
        difficultyLayout.addView(difficultyLabel)

        val difficulties = GameEngine.AIDifficulty.entries.toTypedArray()
        val difficultyButtons = mutableListOf<Button>()

        difficulties.forEach { diff ->
            val btn = Button(this).apply {
                text = diff.name.lowercase().replaceFirstChar { it.uppercase() }
                textSize = 12f
                alpha = if (diff == selectedDifficulty) 1f else 0.5f
                setOnClickListener {
                    selectedDifficulty = diff
                    difficultyButtons.forEach { b -> b.alpha = 0.5f }
                    this.alpha = 1f
                }
            }
            difficultyButtons.add(btn)
            difficultyLayout.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (4 * dp).toInt(); marginEnd = (4 * dp).toInt() })
        }

        contentLayout.addView(difficultyLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (24 * dp).toInt() })

        // Multiplayer section title
        val multiplayerTitle = TextView(this).apply {
            text = "Multiplayer (LAN)"
            textSize = 18f
            setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
        }
        contentLayout.addView(multiplayerTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (12 * dp).toInt() })

        // Host button
        val hostBtn = Button(this).apply {
            text = "Host Game"
            textSize = 16f
            setOnClickListener { startLobby(isHost = true) }
        }
        contentLayout.addView(hostBtn, LinearLayout.LayoutParams(
            (250 * dp).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (12 * dp).toInt() })

        // Join button
        val joinBtn = Button(this).apply {
            text = "Join Game"
            textSize = 16f
            setOnClickListener { startLobby(isHost = false) }
        }
        contentLayout.addView(joinBtn, LinearLayout.LayoutParams(
            (250 * dp).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        menuLayout!!.addView(contentLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        setContentView(menuLayout)
    }

    private fun startSinglePlayer() {
        gameView = GameView(this, selectedDifficulty) {
            // On back to menu callback
            runOnUiThread { showMenu() }
        }
        setContentView(gameView)
    }

    private fun startLobby(isHost: Boolean) {
        val intent = Intent(this, LobbyActivity::class.java).apply {
            putExtra(LobbyActivity.EXTRA_IS_HOST, isHost)
            putExtra(LobbyActivity.EXTRA_PLAYER_NAME, android.os.Build.MODEL)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        gameView?.resume()
    }

    override fun onPause() {
        gameView?.pause()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gameView != null) {
            showMenu()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
