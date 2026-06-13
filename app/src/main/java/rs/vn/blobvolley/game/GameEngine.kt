package rs.vn.blobvolley.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Čista fizika/logika igre — bez Android zavisnosti, lako testabilna.
 * Logički teren 900x500, koordinate identične HTML prototipu.
 */
class GameEngine(val mode: GameMode = GameMode.SINGLE_PLAYER) {

    enum class GameMode {
        SINGLE_PLAYER,      // Player vs AI (original)
        MULTIPLAYER_HOST,   // Host controls left blob, receives right blob input
        MULTIPLAYER_CLIENT  // Client controls right blob, receives left blob state
    }

    companion object {
        const val W = 900f
        const val H = 500f
        const val GROUND = H - 40f
        const val NET_X = W / 2f
        const val NET_W = 10f
        val NET_TOP = GROUND - 160f
        const val GRAV = 0.28f
        const val BALL_GRAV = 0.18f
        const val WIN_SCORE = 15
        const val MAX_TOUCHES = 3
        const val BLOB_R = 34f
        const val BALL_R = 33f
        const val SERVE_DELAY = 300      // koraka @120Hz ≈ 2.5s realnog (pre time-scale)
        const val STEP_MS = 1000.0 / 120.0
        const val TIME_SCALE = 0.63      // igra ~37% sporija
    }

    enum class State { READY, PLAYING, GAME_OVER }

    data class Input(
        var left: Boolean = false,
        var right: Boolean = false,
        var jump: Boolean = false
    )

    class Blob(var x: Float) {
        var y = GROUND
        var vx = 0f
        var vy = 0f
        val r = BLOB_R
        var onGround = true
        var squash = 0f
    }

    class Ball {
        var x = W * 0.25f
        var y = 300f
        var vx = 0f
        var vy = 0f
        val r = BALL_R
        var rot = 0f
    }

    enum class AIDifficulty(val speed: Float, val reactionDelay: Int, val predictionFrames: Int, val jumpAccuracy: Float) {
        EASY(3.2f, 15, 120, 100f),      // Slow, delayed reactions, less prediction
        MEDIUM(3.8f, 8, 180, 80f),      // Moderate speed and prediction
        HARD(4.1f, 3, 240, 60f),        // Fast, quick reactions, full prediction
        INSANE(4.5f, 0, 300, 40f)       // Maximum speed, instant reactions, extended prediction
    }

    val player = Blob(W * 0.25f)
    val bot = Blob(W * 0.75f)
    val ball = Ball()

    var state = State.READY
        private set
    var scoreL = 0; private set
    var scoreR = 0; private set
    var touchesL = 0; private set
    var touchesR = 0; private set
    var servingLeft = true; private set
    var frozen = true; private set
    var msg = ""; private set
    var msgT = 0; private set
    var tick = 0L; private set   // za animacije (oblaci, lebdenje)

    private var serveTimer = 0
    private val aiInput = Input()
    private var aiReactionCounter = 0
    private var lastAiDecision = Input()

    // Remote player input for multiplayer
    val remoteInput = Input()

    // AI difficulty (configurable)
    var aiDifficulty = AIDifficulty.HARD

    fun startGame() {
        scoreL = 0; scoreR = 0
        state = State.PLAYING
        resetBall(Random.nextBoolean())
    }

    private fun resetBall(left: Boolean) {
        servingLeft = left
        frozen = true
        serveTimer = SERVE_DELAY
        touchesL = 0; touchesR = 0
        ball.x = if (left) W * 0.25f else W * 0.75f
        ball.y = 300f; ball.vx = 0f; ball.vy = 0f
        player.x = W * 0.25f; player.vx = 0f; player.y = GROUND; player.onGround = true
        bot.x = W * 0.75f; bot.vx = 0f; bot.y = GROUND; bot.onGround = true
    }

    private fun flash(t: String) { msg = t; msgT = 90 }

    private fun pointFor(left: Boolean) {
        if (left) scoreL++ else scoreR++
        if (scoreL >= WIN_SCORE || scoreR >= WIN_SCORE) {
            state = State.GAME_OVER
        } else {
            val pointMsg = when (mode) {
                GameMode.SINGLE_PLAYER -> if (left) "POINT — YOU!" else "POINT — BOT"
                GameMode.MULTIPLAYER_HOST -> if (left) "POINT — YOU!" else "POINT — OPPONENT"
                GameMode.MULTIPLAYER_CLIENT -> if (left) "POINT — OPPONENT" else "POINT — YOU!"
            }
            flash(pointMsg)
            resetBall(left)
        }
    }

    fun step(input: Input) {
        tick++
        if (state != State.PLAYING) return
        if (msgT > 0) msgT--

        when (mode) {
            GameMode.SINGLE_PLAYER -> {
                moveBlob(player, input.left, input.right, input.jump, 4.4f, isLeft = true)
                botThink(aiInput)
                moveBlob(bot, aiInput.left, aiInput.right, aiInput.jump, aiDifficulty.speed, isLeft = false)
            }
            GameMode.MULTIPLAYER_HOST -> {
                // Host controls left player, remote input controls right
                moveBlob(player, input.left, input.right, input.jump, 4.4f, isLeft = true)
                moveBlob(bot, remoteInput.left, remoteInput.right, remoteInput.jump, 4.4f, isLeft = false)
            }
            GameMode.MULTIPLAYER_CLIENT -> {
                // Client controls right player, remote input controls left
                moveBlob(player, remoteInput.left, remoteInput.right, remoteInput.jump, 4.4f, isLeft = true)
                moveBlob(bot, input.left, input.right, input.jump, 4.4f, isLeft = false)
            }
        }
        stepBall()
    }

    // For multiplayer: apply state received from host
    fun applySnapshot(
        pX: Float, pY: Float, pVx: Float, pVy: Float,
        oX: Float, oY: Float, oVx: Float, oVy: Float,
        bX: Float, bY: Float, bVx: Float, bVy: Float,
        sL: Int, sR: Int, tL: Int, tR: Int,
        isFrozen: Boolean, isServingLeft: Boolean
    ) {
        player.x = pX; player.y = pY; player.vx = pVx; player.vy = pVy
        bot.x = oX; bot.y = oY; bot.vx = oVx; bot.vy = oVy
        ball.x = bX; ball.y = bY; ball.vx = bVx; ball.vy = bVy
        scoreL = sL; scoreR = sR
        touchesL = tL; touchesR = tR
        frozen = isFrozen; servingLeft = isServingLeft
    }

    fun startMultiplayerGame(servingLeft: Boolean) {
        scoreL = 0; scoreR = 0
        state = State.PLAYING
        resetBall(servingLeft)
    }

    private fun moveBlob(b: Blob, left: Boolean, right: Boolean, jump: Boolean,
                         speed: Float, isLeft: Boolean) {
        when {
            left -> b.vx = -speed
            right -> b.vx = speed
            else -> b.vx *= 0.7f
        }
        if (jump && b.onGround) { b.vy = -9.2f; b.onGround = false; b.squash = -6f }
        b.vy += GRAV * 1.6f
        b.x += b.vx; b.y += b.vy
        if (b.y >= GROUND) {
            if (!b.onGround) b.squash = 6f
            b.y = GROUND; b.vy = 0f; b.onGround = true
        }
        val minX = if (isLeft) b.r else NET_X + NET_W / 2 + b.r
        val maxX = if (isLeft) NET_X - NET_W / 2 - b.r else W - b.r
        b.x = b.x.coerceIn(minX, maxX)
        b.squash *= 0.85f
    }

    /** Blob kolizioni krug: centar (x, y - r). */
    private fun collideBallBlob(b: Blob, isLeftSide: Boolean): Boolean {
        val cx = b.x
        val cy = b.y - b.r
        val dx = ball.x - cx
        val dy = ball.y - cy
        val dist = hypot(dx, dy)
        val minD = ball.r + b.r
        if (dist < minD && dist > 0.001f) {
            val nx = dx / dist
            val ny = dy / dist
            ball.x = cx + nx * minD
            ball.y = cy + ny * minD
            val dot = ball.vx * nx + ball.vy * ny
            ball.vx = ball.vx - 2 * dot * nx + b.vx * 0.6f
            ball.vy = ball.vy - 2 * dot * ny + b.vy * 0.6f
            if (ball.vy > -4f) ball.vy -= 4.5f
            val cap = 11f
            val sp = hypot(ball.vx, ball.vy)
            if (sp > cap) { ball.vx *= cap / sp; ball.vy *= cap / sp }

            if (frozen) frozen = false
            if (isLeftSide) {
                touchesL++; touchesR = 0
                if (touchesL > MAX_TOUCHES) pointFor(left = false)
            } else {
                touchesR++; touchesL = 0
                if (touchesR > MAX_TOUCHES) pointFor(left = true)
            }
            return true
        }
        return false
    }

    private fun stepBall() {
        if (frozen) {
            // lopta lebdi statično na mestu servisa
            ball.x = if (servingLeft) W * 0.25f else W * 0.75f
            ball.y = 300f + sin(tick / 36f) * 6f
            collideBallBlob(if (servingLeft) player else bot, servingLeft)
            if (--serveTimer <= 0) { frozen = false; ball.vx = 0f; ball.vy = 0f }
            return
        }
        ball.vy += BALL_GRAV
        ball.x += ball.vx; ball.y += ball.vy
        ball.rot += ball.vx / ball.r

        // zidovi i plafon
        if (ball.x < ball.r) { ball.x = ball.r; ball.vx = abs(ball.vx) * 0.9f }
        if (ball.x > W - ball.r) { ball.x = W - ball.r; ball.vx = -abs(ball.vx) * 0.9f }
        if (ball.y < ball.r) { ball.y = ball.r; ball.vy = abs(ball.vy) * 0.9f }

        // mreža — vrh kao krug, stranice kao zid
        val nx1 = NET_X - NET_W / 2
        val nx2 = NET_X + NET_W / 2
        if (ball.x + ball.r > nx1 && ball.x - ball.r < nx2 && ball.y + ball.r > NET_TOP) {
            val dTop = hypot(ball.x - NET_X, ball.y - NET_TOP)
            if (ball.y < NET_TOP) {
                // iznad mreže — slobodan prelet
            } else if (dTop < ball.r + NET_W) {
                val d = if (dTop == 0f) 1f else dTop
                val nxn = (ball.x - NET_X) / d
                val nyn = (ball.y - NET_TOP) / d
                val dot = ball.vx * nxn + ball.vy * nyn
                ball.vx -= 2 * dot * nxn
                ball.vy -= 2 * dot * nyn
                ball.x = NET_X + nxn * (ball.r + NET_W)
            } else {
                if (ball.vx > 0) ball.x = nx1 - ball.r else ball.x = nx2 + ball.r
                ball.vx = -ball.vx * 0.85f
            }
        }

        // pod = poen
        if (ball.y + ball.r >= GROUND + 6f) {
            pointFor(left = ball.x >= NET_X)
            return
        }

        collideBallBlob(player, isLeftSide = true)
        collideBallBlob(bot, isLeftSide = false)
    }

    private fun botThink(out: Input) {
        // Apply reaction delay based on difficulty
        aiReactionCounter++
        if (aiReactionCounter < aiDifficulty.reactionDelay) {
            out.left = lastAiDecision.left
            out.right = lastAiDecision.right
            out.jump = lastAiDecision.jump
            return
        }
        aiReactionCounter = 0

        out.left = false; out.right = false; out.jump = false
        val targetX: Float
        val ballComing = ball.x > NET_X || ball.vx > 2f

        if (frozen && !servingLeft) {
            targetX = ball.x
            if (abs(bot.x - ball.x) < 18f && bot.onGround) {
                out.jump = true
                lastAiDecision.left = out.left
                lastAiDecision.right = out.right
                lastAiDecision.jump = out.jump
                return
            }
        } else if (ballComing) {
            // Ball prediction with difficulty-based frame limit
            var px = ball.x; var py = ball.y
            var pvx = ball.vx; var pvy = ball.vy
            var t = 0
            val maxPrediction = aiDifficulty.predictionFrames
            while (py < GROUND - 60f && t < maxPrediction) {
                pvy += BALL_GRAV; px += pvx; py += pvy
                if (px < ball.r || px > W - ball.r) pvx = -pvx
                t++
            }
            targetX = max(NET_X + 60f, min(W - 40f, px)) - 6f
        } else {
            targetX = W * 0.75f
        }

        val dx = targetX - bot.x
        out.left = dx < -8f
        out.right = dx > 8f

        // Jump with difficulty-based accuracy threshold
        val jumpThreshold = aiDifficulty.jumpAccuracy
        out.jump = !frozen && ball.x > NET_X && ball.y > 140f && ball.y < 330f &&
                abs(ball.x - bot.x) < jumpThreshold && bot.onGround && ball.vy > -1f

        // Store decision for reaction delay
        lastAiDecision.left = out.left
        lastAiDecision.right = out.right
        lastAiDecision.jump = out.jump
    }
}
