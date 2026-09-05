package com.louay.game

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.os.CountDownTimer
import kotlin.math.hypot
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView())
    }

    private inner class GameView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var score = 0
        private var timeLeft = 30
        private var running = false
        private var targetX = 0f
        private var targetY = 0f
        private var targetRadius = 75f
        private var timer: CountDownTimer? = null

        init {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            resetTarget()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(15, 18, 28))

            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 52f
            canvas.drawText("TAP RUSH", width / 2f, 75f, paint)

            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 32f
            canvas.drawText("Score: $score", 35f, 125f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Time: ${timeLeft}s", width - 35f, 125f, paint)

            paint.color = Color.rgb(40, 48, 68)
            canvas.drawCircle(targetX, targetY, targetRadius + 12f, paint)
            paint.color = Color.rgb(75, 190, 255)
            canvas.drawCircle(targetX, targetY, targetRadius, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(targetX, targetY, targetRadius * 0.35f, paint)

            if (!running) {
                paint.color = Color.argb(235, 10, 12, 18)
                canvas.drawRect(0f, 170f, width.toFloat(), height.toFloat(), paint)
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 54f
                canvas.drawText(if (timeLeft == 30) "READY?" else "TIME!", width / 2f, height / 2f - 45f, paint)
                paint.textSize = 30f
                canvas.drawText(if (timeLeft == 30) "Tap the target to start" else "Tap anywhere to play again", width / 2f, height / 2f + 15f, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN) return true

            if (!running) {
                startGame()
                return true
            }

            val distance = hypot(event.x - targetX, event.y - targetY)
            if (distance <= targetRadius) {
                score++
                targetRadius = (75f - score * 0.7f).coerceAtLeast(32f)
                resetTarget()
                invalidate()
            }
            return true
        }

        private fun startGame() {
            score = 0
            timeLeft = 30
            running = true
            targetRadius = 75f
            resetTarget()
            timer?.cancel()
            timer = object : CountDownTimer(30_000L, 1_000L) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeft = (millisUntilFinished / 1000L).toInt()
                    invalidate()
                }

                override fun onFinish() {
                    timeLeft = 0
                    running = false
                    invalidate()
                }
            }.start()
            invalidate()
        }

        private fun resetTarget() {
            val minX = targetRadius + 30f
            val maxX = width - targetRadius - 30f
            val minY = 180f + targetRadius
            val maxY = height - targetRadius - 50f
            if (maxX > minX && maxY > minY) {
                targetX = Random.nextFloat() * (maxX - minX) + minX
                targetY = Random.nextFloat() * (maxY - minY) + minY
            } else {
                targetX = width / 2f
                targetY = height / 2f
            }
        }
    }
}
