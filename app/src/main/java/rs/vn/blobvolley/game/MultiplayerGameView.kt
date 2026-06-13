package rs.vn.blobvolley.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import rs.vn.blobvolley.game.GameEngine.Companion.GROUND
import rs.vn.blobvolley.game.GameEngine.Companion.H
import rs.vn.blobvolley.game.GameEngine.Companion.MAX_TOUCHES
import rs.vn.blobvolley.game.GameEngine.Companion.NET_TOP
import rs.vn.blobvolley.game.GameEngine.Companion.NET_W
import rs.vn.blobvolley.game.GameEngine.Companion.NET_X
import rs.vn.blobvolley.game.GameEngine.Companion.STEP_MS
import rs.vn.blobvolley.game.GameEngine.Companion.TIME_SCALE
import rs.vn.blobvolley.game.GameEngine.Companion.W
import rs.vn.blobvolley.network.GameStateSnapshot
import rs.vn.blobvolley.network.NetworkManager

class MultiplayerGameView(
    context: Context,
    private val networkManager: NetworkManager,
    private val isHost: Boolean,
    private val onDisconnected: () -> Unit
) : SurfaceView(context), SurfaceHolder.Callback, NetworkManager.NetworkListener {

    private val mode = if (isHost) GameEngine.GameMode.MULTIPLAYER_HOST else GameEngine.GameMode.MULTIPLAYER_CLIENT
    private val engine = GameEngine(mode)
    private val input = GameEngine.Input()

    private var thread: GameThread? = null
    private var inputSeq = 0

    // Scaling
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    // Touch controls
    private val dp = resources.displayMetrics.density
    private val joyW = 150 * dp
    private val joyH = 78 * dp
    private val joyMargin = 16 * dp
    private val joyMaxOff = 46 * dp
    private val joyDead = 8 * dp
    private val jumpR = 44 * dp
    private var joyRect = RectF()
    private var jumpCx = 0f
    private var jumpCy = 0f
    private var joyPointer = -1
    private var jumpPointer = -1
    private var joyKnobDx = 0f

    // State sync
    private var lastStateSendTick = 0L
    private var lastPingTick = 0L
    var latencyMs = 0
        private set
    var opponentName = "Opponent"
    var connected = true
        private set

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    private inner class GameThread : Thread("MultiplayerGameLoop") {
        @Volatile var alive = true
        override fun run() {
            var last = System.nanoTime()
            var acc = 0.0
            while (alive) {
                val now = System.nanoTime()
                val dtMs = (now - last) / 1_000_000.0
                last = now
                acc += min(50.0, dtMs * TIME_SCALE)
                while (acc >= STEP_MS) {
                    synchronized(input) {
                        engine.step(input)

                        // Send input to remote (every frame)
                        val seq = inputSeq++
                        if (isHost) {
                            networkManager.sendInput(seq, input.left, input.right, input.jump)
                        } else {
                            networkManager.sendInput(seq, input.left, input.right, input.jump)
                        }

                        // Host sends state periodically (every 3 frames ~40Hz)
                        if (isHost && engine.tick - lastStateSendTick >= 3) {
                            lastStateSendTick = engine.tick
                            val snapshot = GameStateSnapshot(
                                tick = engine.tick.toInt(),
                                playerX = engine.player.x,
                                playerY = engine.player.y,
                                playerVx = engine.player.vx,
                                playerVy = engine.player.vy,
                                opponentX = engine.bot.x,
                                opponentY = engine.bot.y,
                                opponentVx = engine.bot.vx,
                                opponentVy = engine.bot.vy,
                                ballX = engine.ball.x,
                                ballY = engine.ball.y,
                                ballVx = engine.ball.vx,
                                ballVy = engine.ball.vy,
                                scoreL = engine.scoreL,
                                scoreR = engine.scoreR,
                                touchesL = engine.touchesL,
                                touchesR = engine.touchesR,
                                frozen = engine.frozen,
                                servingLeft = engine.servingLeft
                            )
                            networkManager.sendGameState(snapshot)
                        }

                        // Ping every ~2 seconds
                        if (engine.tick - lastPingTick >= 240) {
                            lastPingTick = engine.tick
                            networkManager.sendPing()
                        }
                    }
                    acc -= STEP_MS
                }

                val canvas: Canvas = try {
                    holder.lockHardwareCanvas()
                } catch (_: Throwable) {
                    holder.lockCanvas() ?: continue
                }
                try {
                    render(canvas)
                } finally {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Throwable) {}
                }
            }
        }
    }

    fun startGame(servingLeft: Boolean) {
        engine.startMultiplayerGame(servingLeft)
    }

    fun resume() {
        if (thread == null && holder.surface.isValid) startThread()
    }

    fun pause() = stopThread()

    private fun startThread() {
        thread = GameThread().also { it.start() }
    }

    private fun stopThread() {
        thread?.let { t ->
            t.alive = false
            try { t.join(500) } catch (_: InterruptedException) {}
        }
        thread = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) = startThread()

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        scale = min(w / W, h / H)
        offX = (w - W * scale) / 2f
        offY = (h - H * scale) / 2f
        joyRect = RectF(
            joyMargin, h - joyMargin - joyH,
            joyMargin + joyW, h - joyMargin
        )
        jumpCx = w - joyMargin - jumpR
        jumpCy = h - joyMargin - jumpR
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = stopThread()

    // ================= NetworkManager.NetworkListener =================

    override fun onHostDiscovered(host: NetworkManager.HostInfo) {}

    override fun onClientConnected(clientName: String, clientAddress: java.net.InetAddress) {
        opponentName = clientName
    }

    override fun onClientDisconnected() {
        connected = false
        post { onDisconnected() }
    }

    override fun onConnectionAccepted() {}

    override fun onConnectionRejected() {
        connected = false
        post { onDisconnected() }
    }

    override fun onRemoteInput(seq: Int, left: Boolean, right: Boolean, jump: Boolean) {
        synchronized(input) {
            engine.remoteInput.left = left
            engine.remoteInput.right = right
            engine.remoteInput.jump = jump
        }
    }

    override fun onGameStateReceived(snapshot: GameStateSnapshot) {
        if (!isHost) {
            synchronized(input) {
                engine.applySnapshot(
                    snapshot.playerX, snapshot.playerY, snapshot.playerVx, snapshot.playerVy,
                    snapshot.opponentX, snapshot.opponentY, snapshot.opponentVx, snapshot.opponentVy,
                    snapshot.ballX, snapshot.ballY, snapshot.ballVx, snapshot.ballVy,
                    snapshot.scoreL, snapshot.scoreR, snapshot.touchesL, snapshot.touchesR,
                    snapshot.frozen, snapshot.servingLeft
                )
            }
        }
    }

    override fun onGameStart(servingLeft: Boolean) {
        startGame(servingLeft)
    }

    override fun onPingUpdate(latencyMs: Int) {
        this.latencyMs = latencyMs
    }

    override fun onError(message: String) {}

    // ================= Input =================

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                handleDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                if (joyPointer != -1) {
                    val idx = e.findPointerIndex(joyPointer)
                    if (idx != -1) updateJoy(e.getX(idx))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                handleUp(e.getPointerId(e.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                releaseJoy(); releaseJump()
            }
        }
        return true
    }

    private fun handleDown(pid: Int, x: Float, y: Float) {
        if (engine.state == GameEngine.State.GAME_OVER) {
            // Tap to restart in multiplayer? Let the host decide
            return
        }
        val grow = 20 * dp
        if (joyPointer == -1 &&
            x in (joyRect.left - grow)..(joyRect.right + grow) &&
            y in (joyRect.top - grow)..(joyRect.bottom + grow)
        ) {
            joyPointer = pid
            updateJoy(x)
        } else if (jumpPointer == -1 &&
            hypot(x - jumpCx, y - jumpCy) < jumpR + grow
        ) {
            jumpPointer = pid
            synchronized(input) { input.jump = true }
        }
    }

    private fun handleUp(pid: Int) {
        if (pid == joyPointer) releaseJoy()
        if (pid == jumpPointer) releaseJump()
    }

    private fun updateJoy(x: Float) {
        val dx = (x - joyRect.centerX()).coerceIn(-joyMaxOff, joyMaxOff)
        joyKnobDx = dx
        synchronized(input) {
            input.left = dx < -joyDead
            input.right = dx > joyDead
        }
    }

    private fun releaseJoy() {
        joyPointer = -1
        joyKnobDx = 0f
        synchronized(input) { input.left = false; input.right = false }
    }

    private fun releaseJump() {
        jumpPointer = -1
        synchronized(input) { input.jump = false }
    }

    // ================= Render =================

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private var skyShader: Shader? = null
    private val blobPath = Path()
    private val seamPath = Path()
    private val tmpRect = RectF()

    private val colRed = Color.parseColor("#E0454F")
    private val colGreen = Color.parseColor("#3FAE5A")
    private val colInk = Color.parseColor("#1C2733")
    private val colSand = Color.parseColor("#E8C87A")
    private val colNet = Color.parseColor("#5B4632")
    private val colSeam = Color.parseColor("#E0A13A")

    private fun render(c: Canvas) {
        c.drawColor(colInk)
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)
        drawWorld(c)
        c.restore()
        drawControls(c)
        drawOverlay(c)
    }

    private fun drawWorld(c: Canvas) {
        // Sky
        if (skyShader == null) {
            skyShader = LinearGradient(
                0f, 0f, 0f, GROUND,
                Color.parseColor("#7EC8E3"), Color.parseColor("#CFEEFB"),
                Shader.TileMode.CLAMP
            )
        }
        pFill.shader = skyShader
        c.drawRect(0f, 0f, W, GROUND, pFill)
        pFill.shader = null

        // Clouds
        val cloudT = engine.tick * 0.15f
        pFill.color = Color.argb(204, 255, 255, 255)
        drawCloud(c, (150 + cloudT * 1.0f) % (W + 160) - 80, 70f, 1.0f)
        drawCloud(c, (480 + cloudT * 0.7f) % (W + 160) - 80, 110f, 0.7f)
        drawCloud(c, (760 + cloudT * 1.2f) % (W + 160) - 80, 60f, 1.2f)

        // Sun
        pFill.color = Color.argb(230, 255, 220, 120)
        c.drawCircle(W - 90, 70f, 34f, pFill)

        // Sand
        pFill.color = colSand
        c.drawRect(0f, GROUND, W, H, pFill)
        pFill.color = Color.argb(20, 0, 0, 0)
        c.drawRect(0f, GROUND, W, GROUND + 5, pFill)

        // Net
        pFill.color = colNet
        c.drawRect(NET_X - NET_W / 2, NET_TOP, NET_X + NET_W / 2, GROUND, pFill)
        pStroke.color = Color.argb(216, 255, 255, 255)
        pStroke.strokeWidth = 2f
        var y = NET_TOP + 6
        while (y < GROUND) {
            c.drawLine(NET_X - NET_W / 2 - 6, y, NET_X + NET_W / 2 + 6, y, pStroke)
            y += 16
        }
        pFill.color = Color.WHITE
        c.drawRect(NET_X - NET_W / 2 - 2, NET_TOP - 4, NET_X + NET_W / 2 + 2, NET_TOP + 2, pFill)

        // Shadows
        pFill.color = Color.argb(38, 0, 0, 0)
        drawShadow(c, engine.player.x, engine.player.r * 0.9f, 7f)
        drawShadow(c, engine.bot.x, engine.bot.r * 0.9f, 7f)
        val ball = engine.ball
        val shScale = max(0.3f, 1 - (GROUND - ball.y) / 400f)
        drawShadow(c, ball.x, ball.r * shScale, 5 * shScale)

        // Player colors: Host is red (left), Client is green (right)
        val playerColor = if (isHost) colRed else colGreen
        val opponentColor = if (isHost) colGreen else colRed

        drawBlob(c, engine.player, if (isHost) colRed else colRed)
        drawBlob(c, engine.bot, if (isHost) colGreen else colGreen)
        drawBall(c)
        drawHud(c)
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, s: Float) {
        c.drawCircle(x, y, 22 * s, pFill)
        c.drawCircle(x + 26 * s, y - 8 * s, 17 * s, pFill)
        c.drawCircle(x + 50 * s, y, 20 * s, pFill)
    }

    private fun drawShadow(c: Canvas, x: Float, rx: Float, ry: Float) {
        tmpRect.set(x - rx, GROUND + 8 - ry, x + rx, GROUND + 8 + ry)
        c.drawOval(tmpRect, pFill)
    }

    private fun drawBlob(c: Canvas, b: GameEngine.Blob, color: Int) {
        c.save()
        c.translate(b.x, b.y)
        c.scale(1 + b.squash * 0.01f, 1 - b.squash * 0.01f)

        val r = b.r
        blobPath.reset()
        tmpRect.set(-r, -r - 1.15f * r, r, -r + 1.15f * r)
        blobPath.arcTo(tmpRect, 180f, 180f)
        tmpRect.set(-r, -1.1f * r, r, 0f)
        blobPath.arcTo(tmpRect, 0f, 180f)
        blobPath.close()
        pFill.color = color
        c.drawPath(blobPath, pFill)

        // Shine
        pFill.color = Color.argb(64, 255, 255, 255)
        tmpRect.set(-r * 0.65f, -r * 1.7f, -r * 0.05f, -r * 0.8f)
        c.save(); c.rotate(-23f, -r * 0.35f, -r * 1.25f)
        c.drawOval(tmpRect, pFill)
        c.restore()

        // Eyes looking at ball
        val ang = atan2(engine.ball.y - (b.y - r), engine.ball.x - b.x)
        val ex = cos(ang) * 4
        val ey = sin(ang) * 4
        for (ox in floatArrayOf(-12f, 12f)) {
            pFill.color = Color.WHITE
            c.drawCircle(ox, -r * 1.15f, 9f, pFill)
            pFill.color = colInk
            c.drawCircle(ox + ex, -r * 1.15f + ey, 4.5f, pFill)
        }
        c.restore()
    }

    private fun drawBall(c: Canvas) {
        val ball = engine.ball
        c.save()
        c.translate(ball.x, ball.y)
        c.rotate(Math.toDegrees(ball.rot.toDouble()).toFloat())
        pFill.color = Color.WHITE
        c.drawCircle(0f, 0f, ball.r, pFill)
        pStroke.color = colSeam
        pStroke.strokeWidth = 3f
        c.drawCircle(0f, 0f, ball.r - 2, pStroke)
        seamPath.reset()
        seamPath.moveTo(-ball.r + 2, 0f)
        seamPath.quadTo(0f, ball.r * 0.9f, ball.r - 2, 0f)
        seamPath.moveTo(-ball.r + 2, 0f)
        seamPath.quadTo(0f, -ball.r * 0.9f, ball.r - 2, 0f)
        c.drawPath(seamPath, pStroke)
        c.restore()
    }

    private fun drawHud(c: Canvas) {
        pText.textSize = 44f
        pText.color = colRed
        c.drawText("${engine.scoreL}", W / 2 - 70, 56f, pText)
        pText.color = colInk
        c.drawText(":", W / 2, 54f, pText)
        pText.color = colGreen
        c.drawText("${engine.scoreR}", W / 2 + 70, 56f, pText)

        for (i in 0 until MAX_TOUCHES) {
            pFill.color = if (i < engine.touchesL) colRed else Color.argb(38, 0, 0, 0)
            c.drawCircle(W / 2 - 70 + (i - 1) * 16, 76f, 5f, pFill)
            pFill.color = if (i < engine.touchesR) colGreen else Color.argb(38, 0, 0, 0)
            c.drawCircle(W / 2 + 70 + (i - 1) * 16, 76f, 5f, pFill)
        }

        // Serve message
        if (engine.frozen && engine.state == GameEngine.State.PLAYING) {
            pText.textSize = 20f
            pText.color = Color.argb(191, 28, 39, 51)
            val serveMsg = when {
                engine.servingLeft && isHost -> "Your serve — hit the ball!"
                !engine.servingLeft && !isHost -> "Your serve — hit the ball!"
                else -> "$opponentName serves…"
            }
            c.drawText(serveMsg, W / 2, 116f, pText)
        }

        // Flash message
        if (engine.msgT > 0) {
            pText.textSize = 34f
            val a = (min(1f, engine.msgT / 30f) * 255).toInt()
            pText.color = Color.argb(a, 28, 39, 51)
            c.drawText(engine.msg, W / 2, H / 2 - 80, pText)
        }

        // Latency indicator
        pText.textSize = 14f
        pText.color = Color.argb(128, 28, 39, 51)
        c.drawText("${latencyMs}ms", W - 40, 24f, pText)
    }

    private fun drawControls(c: Canvas) {
        if (engine.state != GameEngine.State.PLAYING) return
        // Joystick
        pFill.color = Color.argb(140, 28, 39, 51)
        c.drawRoundRect(joyRect, joyH / 2, joyH / 2, pFill)
        pStroke.color = Color.argb(204, 255, 255, 255)
        pStroke.strokeWidth = 3 * dp
        c.drawRoundRect(joyRect, joyH / 2, joyH / 2, pStroke)
        pFill.color = Color.argb(217, 255, 255, 255)
        c.drawCircle(joyRect.centerX() + joyKnobDx, joyRect.centerY(), 29 * dp, pFill)

        // Jump button
        pFill.color = Color.argb(140, 28, 39, 51)
        c.drawCircle(jumpCx, jumpCy, jumpR, pFill)
        pStroke.strokeWidth = 3 * dp
        c.drawCircle(jumpCx, jumpCy, jumpR, pStroke)
        pText.textSize = 30 * dp
        pText.color = Color.WHITE
        c.drawText("↑", jumpCx, jumpCy + 11 * dp, pText)
    }

    private fun drawOverlay(c: Canvas) {
        if (engine.state == GameEngine.State.PLAYING) return

        c.drawColor(Color.argb(184, 15, 25, 35))
        pText.color = Color.WHITE
        val cx = width / 2f
        val cy = height / 2f

        when (engine.state) {
            GameEngine.State.READY -> {
                pText.textSize = 20 * dp
                c.drawText("Waiting for game to start...", cx, cy, pText)
            }
            GameEngine.State.GAME_OVER -> {
                pText.textSize = 26 * dp
                val youWon = (isHost && engine.scoreL > engine.scoreR) ||
                             (!isHost && engine.scoreR > engine.scoreL)
                c.drawText(
                    if (youWon) "YOU WIN!" else "$opponentName WINS",
                    cx, cy - 30 * dp, pText
                )
                pText.textSize = 14 * dp
                c.drawText("${engine.scoreL} : ${engine.scoreR}", cx, cy + 6 * dp, pText)
                pText.textSize = 12 * dp
                c.drawText("Returning to lobby...", cx, cy + 36 * dp, pText)
            }
            else -> Unit
        }
    }
}
