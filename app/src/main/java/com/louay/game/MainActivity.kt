package com.louay.game

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.Window
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var gameView: ZeroView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        gameView = ZeroView(this)
        setContentView(gameView)
    }

    override fun onBackPressed() {
        if (gameView.goBack()) return
        super.onBackPressed()
    }
}

private class ZeroView(private val ctx: Context) : View(ctx) {
    private enum class Screen { MENU, STORY, HOWTO, PLAY, RESULT }
    private data class Node(var x: Float, var y: Float, var r: Float)

    private val prefs = ctx.getSharedPreferences("zero_save", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())
    private val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45)

    private var screen = Screen.MENU
    private var arabic = prefs.getBoolean("arabic", true)
    private var bestScore = prefs.getInt("bestScore", 0)
    private var unlocked = prefs.getInt("unlocked", 1).coerceIn(1, 12)

    private var stage = 1
    private var score = 0
    private var streak = 0
    private var active = 0
    private var nodes = mutableListOf<Node>()
    private var ghostNodes = mutableListOf<Node>()
    private var ghostProgress = 0f
    private var roundStarted = 0L
    private var resultReason = ""
    private var pulse = 0f
    private var particles = mutableListOf<Particle>()

    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)

    init {
        isFocusable = true
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 3f
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        drawBackground(c, w, h)
        pulse += 0.045f
        updateParticles()
        drawParticles(c)

        when (screen) {
            Screen.MENU -> drawMenu(c, w, h)
            Screen.STORY -> drawStory(c, w, h)
            Screen.HOWTO -> drawHowTo(c, w, h)
            Screen.PLAY -> drawGame(c, w, h)
            Screen.RESULT -> drawResult(c, w, h)
        }

        postInvalidateDelayed(if (screen == Screen.PLAY) 16L else 60L)
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float) {
        val g = LinearGradient(0f, 0f, w, h, Color.rgb(4, 7, 15), Color.rgb(11, 15, 29), Shader.TileMode.CLAMP)
        paint.shader = g
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        paint.color = Color.argb(35, 125, 249, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        val grid = 54f
        var x = 0f
        while (x < w) { c.drawLine(x, 0f, x, h, paint); x += grid }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, paint); y += grid }
        paint.style = Paint.Style.FILL

        paint.color = Color.argb(16, 125, 249, 255)
        c.drawCircle(w * .82f, h * .18f, 190f, paint)
        c.drawCircle(w * .12f, h * .82f, 150f, paint)
    }

    private fun drawMenu(c: Canvas, w: Float, h: Float) {
        centerText(c, "ZERO", w / 2f, 132f, 68f, Color.WHITE, true)
        centerText(c, t("أنت شبحُك الخاص", "YOU ARE YOUR OWN GHOST"), w / 2f, 174f, 16f, Color.rgb(125,249,255), false)
        centerText(c, t("لعبة ذاكرة زمنية", "A MEMORY OF YOUR FUTURE"), w / 2f, 202f, 13f, Color.LTGRAY, false)

        button(c, w / 2f, h * .47f, w * .78f, 64f, t("ابدأ الرحلة", "START THE ECHO"), true)
        button(c, w / 2f, h * .47f + 82f, w * .78f, 56f, t("كيف تلعب؟", "HOW TO PLAY"), false)
        button(c, w / 2f, h * .47f + 154f, w * .78f, 56f, t("العربية / English", "العربية / English"), false)

        val best = t("أفضل نتيجة: $bestScore", "BEST SCORE: $bestScore")
        centerText(c, best, w / 2f, h - 78f, 15f, Color.rgb(185, 195, 214), false)
        centerText(c, "v1.0.0  •  OFFLINE  •  ZERO LABS", w / 2f, h - 42f, 10f, Color.rgb(100, 112, 132), false)
    }

    private fun drawStory(c: Canvas, w: Float, h: Float) {
        header(c, t("الرسالة الأولى", "FIRST TRANSMISSION"))
        centerText(c, "01", w / 2f, 168f, 74f, Color.rgb(125,249,255), true)
        wrapText(c,
            t("كل حركة تقوم بها تُحفظ. كل قرار يترك أثراً. وعندما تنتهي المرحلة، سيعود أثرُك ليلعب ضدك.",
              "Every move is recorded. Every decision leaves an echo. When a stage ends, your past returns to play against you."),
            w / 2f, 264f, w * .78f, 19f, Color.WHITE)
        wrapText(c,
            t("لا توجد أرواح إضافية. لا توجد صدفة. فقط أنت... والنسخة التي صنعتها بيديك.",
              "No extra lives. No luck. Only you... and the version of you that you created."),
            w / 2f, 360f, w * .78f, 18f, Color.LTGRAY)
        button(c, w / 2f, h - 116f, w * .78f, 62f, t("أدخل المتاهة", "ENTER THE MAZE"), true)
        button(c, w / 2f, h - 44f, w * .78f, 48f, t("رجوع", "BACK"), false)
    }

    private fun drawHowTo(c: Canvas, w: Float, h: Float) {
        header(c, t("طريقة اللعب", "HOW ZERO WORKS"))
        val titles = arrayOf(
            t("١ • احفظ المسار", "1 • READ THE SIGNAL"),
            t("٢ • اضغط العقد بالترتيب", "2 • HIT NODES IN ORDER"),
            t("٣ • راقب شبحك السابق", "3 • WATCH YOUR ECHO"),
            t("٤ • لا تثق بذاكرتك", "4 • DON'T TRUST MEMORY")
        )
        val bodies = arrayOf(
            t("الدوائر المضيئة هي مفاتيح المرحلة.", "The glowing nodes are the stage keys."),
            t("كل لمسة صحيحة تقرّبك من الصفر.", "Every correct tap pushes you closer to ZERO."),
            t("المسار السابق يعود كطيف ويتحرك فوق الخريطة.", "Your last route returns as a moving ghost."),
            t("كل مرحلة تصبح أسرع وأصعب وتغيّر شكل المتاهة.", "Every stage gets faster, harder and less predictable.")
        )
        var y = 176f
        for (i in titles.indices) {
            smallCard(c, 32f, y - 30f, w - 64f, 100f, titles[i], bodies[i])
            y += 112f
        }
        button(c, w / 2f, h - 50f, w * .78f, 48f, t("رجوع", "BACK"), false)
    }

    private fun drawGame(c: Canvas, w: Float, h: Float) {
        // HUD
        leftText(c, "ZERO", 28f, 38f, 18f, Color.WHITE, true)
        leftText(c, "STAGE ${stage.toString().padStart(2, '0')} / 12", 28f, 64f, 11f, Color.rgb(125,249,255), true)
        rightText(c, t("النتيجة $score", "SCORE $score"), w - 28f, 38f, 15f, Color.WHITE, true)
        rightText(c, "BEST $bestScore", w - 28f, 62f, 10f, Color.rgb(130,142,162), false)

        // Progress line
        paint.color = Color.argb(40, 255,255,255)
        c.drawRoundRect(28f, 83f, w - 28f, 87f, 3f, 3f, paint)
        paint.color = Color.rgb(125,249,255)
        c.drawRoundRect(28f, 83f, 28f + (w - 56f) * (active.toFloat() / nodes.size.coerceAtLeast(1)), 87f, 3f, 3f, paint)

        // Previous route = the ghost
        if (ghostNodes.size > 1) {
            linePaint.color = Color.argb(75, 170, 215, 225)
            linePaint.strokeWidth = 4f
            for (i in 1 until ghostNodes.size) c.drawLine(ghostNodes[i-1].x, ghostNodes[i-1].y, ghostNodes[i].x, ghostNodes[i].y, linePaint)
            for (n in ghostNodes) {
                paint.color = Color.argb(35, 125,249,255)
                c.drawCircle(n.x, n.y, n.r + 8f, paint)
                paint.color = Color.argb(100, 125,249,255)
                c.drawCircle(n.x, n.y, 4f, paint)
            }
            val idx = ((ghostProgress * (ghostNodes.size - 1)).toInt()).coerceIn(0, ghostNodes.size - 1)
            val gn = ghostNodes[idx]
            paint.color = Color.argb(90, 255,255,255)
            c.drawCircle(gn.x, gn.y, 7f + 3f * sin(pulse), paint)
        }

        // Current path
        if (active > 1) {
            linePaint.color = Color.argb(150, 125,249,255)
            linePaint.strokeWidth = 5f
            for (i in 1 until active) c.drawLine(nodes[i-1].x, nodes[i-1].y, nodes[i].x, nodes[i].y, linePaint)
        }

        nodes.forEachIndexed { i, n ->
            if (i < active) {
                paint.color = Color.argb(120, 125,249,255)
                c.drawCircle(n.x, n.y, n.r * .42f, paint)
                return@forEachIndexed
            }
            val isNext = i == active
            val glow = if (isNext) (14f + 8f * sin(pulse)) else 6f
            paint.setShadowLayer(glow, 0f, 0f, Color.rgb(125,249,255))
            paint.color = if (isNext) Color.rgb(125,249,255) else Color.rgb(48,75,91)
            c.drawCircle(n.x, n.y, n.r, paint)
            paint.clearShadowLayer()
            paint.color = Color.WHITE
            c.drawCircle(n.x, n.y, n.r * .28f, paint)
            centerText(c, "${i + 1}", n.x, n.y + 5f, 12f, Color.rgb(3,8,15), true)
        }

        centerText(c, t("اتبع الإشارة...", "FOLLOW THE SIGNAL..."), w / 2f, h - 42f, 12f, Color.rgb(144,155,173), false)
    }

    private fun drawResult(c: Canvas, w: Float, h: Float) {
        centerText(c, if (resultReason == "win") "ZERO" else t("انقطع الصدى", "ECHO LOST"), w / 2f, 164f, 44f, Color.WHITE, true)
        centerText(c, if (resultReason == "win") t("لقد وصلت إلى الصفر.", "YOU REACHED ZERO.") else t("شبحك كان أسرع منك.", "YOUR ECHO WAS FASTER."), w / 2f, 206f, 17f, Color.rgb(125,249,255), false)

        val panelTop = 274f
        paint.color = Color.argb(95, 10, 18, 32)
        paint.style = Paint.Style.FILL
        c.drawRoundRect(34f, panelTop, w - 34f, panelTop + 190f, 22f, 22f, paint)
        strokeRound(c, 34f, panelTop, w - 34f, panelTop + 190f, 22f, Color.argb(65,125,249,255), 2f)

        centerText(c, "$score", w / 2f, panelTop + 72f, 52f, Color.WHITE, true)
        centerText(c, t("نتيجتك", "YOUR SCORE"), w / 2f, panelTop + 104f, 11f, Color.rgb(135,148,169), false)
        centerText(c, t("السلسلة: $streak", "STREAK: $streak"), w / 2f, panelTop + 140f, 15f, Color.WHITE, false)
        centerText(c, t("أفضل نتيجة: $bestScore", "BEST: $bestScore"), w / 2f, panelTop + 165f, 11f, Color.rgb(125,249,255), false)

        button(c, w / 2f, h - 124f, w * .78f, 60f, t("محاولة أخرى", "RUN IT AGAIN"), true)
        button(c, w / 2f, h - 54f, w * .78f, 46f, t("القائمة الرئيسية", "MAIN MENU"), false)
    }

    private fun startGame() {
        screen = Screen.PLAY
        stage = 1
        score = 0
        streak = 0
        active = 0
        ghostNodes = mutableListOf()
        ghostProgress = 0f
        buildStage()
    }

    private fun buildStage() {
        val count = (3 + (stage - 1) / 3).coerceAtMost(6)
        val newNodes = mutableListOf<Node>()
        val top = 125f
        val bottom = height - 95f
        val minGap = (62f - stage * 1.5f).coerceAtLeast(42f)
        var tries = 0
        while (newNodes.size < count && tries < 1000) {
            tries++
            val r = (30f - stage * 0.75f).coerceAtLeast(22f)
            val nx = random.nextFloat() * (width - 70f - 40f) + 55f
            val ny = random.nextFloat() * (bottom - top - 40f) + top + 20f
            if (newNodes.all { hypot((it.x - nx).toDouble(), (it.y - ny).toDouble()) >= minGap }) {
                newNodes += Node(nx, ny, r)
            }
        }
        nodes = newNodes
        active = 0
        roundStarted = SystemClock.uptimeMillis()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val x = e.x
        val y = e.y
        when (screen) {
            Screen.MENU -> {
                if (hit(x, y, width/2f, height*.47f, width*.78f, 64f)) { startGame(); buzz(30); return true }
                if (hit(x, y, width/2f, height*.47f+82f, width*.78f, 56f)) { screen = Screen.HOWTO; return true }
                if (hit(x, y, width/2f, height*.47f+154f, width*.78f, 56f)) { arabic = !arabic; prefs.edit().putBoolean("arabic", arabic).apply(); buzz(18); return true }
            }
            Screen.STORY -> {
                if (hit(x, y, width/2f, height-116f, width*.78f, 62f)) { startGame(); return true }
                if (hit(x, y, width/2f, height-44f, width*.78f, 48f)) { screen = Screen.MENU; return true }
            }
            Screen.HOWTO -> {
                if (hit(x, y, width/2f, height-50f, width*.78f, 48f)) screen = Screen.MENU
            }
            Screen.PLAY -> tapGame(x, y)
            Screen.RESULT -> {
                if (hit(x, y, width/2f, height-124f, width*.78f, 60f)) { startGame(); return true }
                if (hit(x, y, width/2f, height-54f, width*.78f, 46f)) screen = Screen.MENU
            }
        }
        return true
    }

    private fun tapGame(x: Float, y: Float) {
        if (active >= nodes.size) return
        val n = nodes[active]
        val d = hypot((x - n.x).toDouble(), (y - n.y).toDouble())
        if (d <= n.r * 1.28) {
            particles += burst(n.x, n.y)
            score += 100 + (stage * 15) + if (d < n.r * .7) 20 else 0
            streak++
            active++
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
            buzz(22)
            if (active == nodes.size) finishStage()
        } else {
            streak = 0
            score = (score - 25).coerceAtLeast(0)
            tone.startTone(ToneGenerator.TONE_PROP_NACK, 70)
            buzz(80)
        }
    }

    private fun finishStage() {
        ghostNodes = nodes.map { Node(it.x, it.y, it.r) }.toMutableList()
        ghostProgress = 0f
        score += ((SystemClock.uptimeMillis() - roundStarted).let { if (it < 12000L) 80 else 25 })
        if (stage >= 12) {
            bestScore = maxOf(bestScore, score)
            prefs.edit().putInt("bestScore", bestScore).apply()
            resultReason = "win"
            screen = Screen.RESULT
            return
        }
        stage++
        unlocked = maxOf(unlocked, stage)
        prefs.edit().putInt("unlocked", unlocked).apply()
        buildStage()
    }

    private fun updateParticles() {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += .12f
            p.life -= .035f
            if (p.life <= 0f) it.remove()
        }
        if (screen == Screen.PLAY && ghostNodes.size > 1) {
            ghostProgress += .0045f + stage * .00018f
            if (ghostProgress > 1f) ghostProgress = 0f
        }
    }

    private fun burst(x: Float, y: Float): List<Particle> = buildList {
        repeat(12) {
            val a = random.nextFloat() * (Math.PI * 2).toFloat()
            val s = 1.8f + random.nextFloat() * 3.8f
            add(Particle(x, y, cos(a) * s, sin(a) * s, 1f))
        }
    }

    private fun drawParticles(c: Canvas) {
        particles.forEach {
            paint.color = Color.argb((it.life * 210).toInt().coerceIn(0,255), 125,249,255)
            c.drawCircle(it.x, it.y, 2f + it.life * 3f, paint)
        }
    }

    private fun button(c: Canvas, cx: Float, cy: Float, bw: Float, bh: Float, label: String, primary: Boolean) {
        val l = cx - bw/2f
        val t = cy - bh/2f
        paint.color = if (primary) Color.rgb(125,249,255) else Color.argb(55,125,249,255)
        paint.setShadowLayer(if (primary) 18f else 0f, 0f, 0f, Color.rgb(125,249,255))
        c.drawRoundRect(l, t, l+bw, t+bh, 16f, 16f, paint)
        paint.clearShadowLayer()
        if (!primary) strokeRound(c, l, t, l+bw, t+bh, 16f, Color.argb(95,125,249,255), 2f)
        centerText(c, label, cx, cy + 6f, if (primary) 17f else 14f, if (primary) Color.rgb(3,8,15) else Color.WHITE, primary)
    }

    private fun smallCard(c: Canvas, l: Float, t: Float, bw: Float, bh: Float, title: String, body: String) {
        paint.color = Color.argb(72, 9, 17, 30)
        c.drawRoundRect(l, t, l+bw, t+bh, 16f, 16f, paint)
        strokeRound(c, l, t, l+bw, t+bh, 16f, Color.argb(45,125,249,255), 1.5f)
        leftText(c, title, l+18f, t+27f, 13f, Color.rgb(125,249,255), true)
        leftText(c, body, l+18f, t+58f, 12f, Color.rgb(218,224,234), false)
    }

    private fun header(c: Canvas, title: String) {
        leftText(c, "ZERO", 28f, 42f, 19f, Color.WHITE, true)
        rightText(c, title, width-28f, 42f, 12f, Color.rgb(125,249,255), true)
        paint.color = Color.argb(65,125,249,255)
        c.drawRect(28f, 58f, width-28f, 60f, paint)
    }

    private fun centerText(c: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(text, x, baseline, paint)
    }

    private fun leftText(c: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean) {
        paint.shader = null; paint.color = color; paint.textSize = size; paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(text, x, baseline, paint)
    }

    private fun rightText(c: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean) {
        paint.shader = null; paint.color = color; paint.textSize = size; paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(text, x, baseline, paint)
    }

    private fun wrapText(c: Canvas, text: String, cx: Float, startY: Float, maxW: Float, lineH: Float, color: Int) {
        paint.textSize = 16f
        paint.typeface = Typeface.create("sans", Typeface.NORMAL)
        val words = text.split(" ")
        var line = ""
        var y = startY
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxW && line.isNotEmpty()) {
                centerText(c, line, cx, y, 16f, color, false)
                y += lineH
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) centerText(c, line, cx, y, 16f, color, false)
    }

    private fun strokeRound(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int, sw: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw; paint.color = color
        c.drawRoundRect(l,t,r,b,rad,rad,paint)
        paint.style = Paint.Style.FILL
    }

    private fun hit(x: Float, y: Float, cx: Float, cy: Float, bw: Float, bh: Float): Boolean =
        x >= cx-bw/2f && x <= cx+bw/2f && y >= cy-bh/2f && y <= cy+bh/2f

    private fun t(ar: String, en: String): String = if (arabic) ar else en

    private fun buzz(ms: Long) {
        try {
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
    }

    fun goBack(): Boolean {
        return when (screen) {
            Screen.MENU -> false
            Screen.PLAY, Screen.RESULT, Screen.STORY, Screen.HOWTO -> { screen = Screen.MENU; true }
        }
    }
}
