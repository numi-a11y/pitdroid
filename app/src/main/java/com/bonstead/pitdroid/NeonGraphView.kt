package com.bonstead.pitdroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class NeonGraphView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    // Probe 0: Pit (Neon Orange)
    private val pitPaint = Paint().apply {
        color = Color.parseColor("#FF9800")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, Color.parseColor("#99FF9800"))
    }

    // Probe 1: Meat (Neon Red)
    private val meatPaint = Paint().apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, Color.parseColor("#99FF5252"))
    }

    // Target Pit Temp (Setpoint) - Dashed Pink/Grey line
    private val targetPaint = Paint().apply {
        color = Color.parseColor("#E91E63") // Bright Pink to stand out
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 15f), 0f)
    }

    // Tenderness Line (Neon Green)
    private val tendernessLinePaint = Paint().apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, Color.parseColor("#9900E676"))
    }

    // Tenderness Gradient Fill (Set dynamically in onDraw)
    private val tendernessFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#333333") // Faint charcoal grid
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#8E8E93")
        textSize = 32f
        isAntiAlias = true
    }

    private val pitPath = Path()
    private val meatPath = Path()
    private val targetPath = Path()
    private val tendernessPath = Path()

    init {
        // Required for setShadowLayer to work properly
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1A1A1D")) // Deep charcoal background

        val samples = HeaterMeter.mSamples
        if (samples.isEmpty()) return

        val minTime = HeaterMeter.minTime
        val maxTime = HeaterMeter.maxTime
        val timeRange = (maxTime - minTime).toFloat()

        if (timeRange <= 0) return

        // Set up padding so axes text has room to draw on the edges
        val padLeft = 90f
        val padRight = 100f
        val padBottom = 80f
        val padTop = 40f

        val graphW = width - padLeft - padRight
        val graphH = height - padTop - padBottom

        val minTempGraph = HeaterMeter.getOriginal(0.0)
        val maxTempGraph = HeaterMeter.getOriginal(1.0)

        // --- DRAW AXES & GRID LABELS ---
        val numGrids = 4
        for (i in 0..numGrids) {
            val fraction = i / numGrids.toFloat()
            val y = padTop + graphH - (graphH * fraction)

            // Horizontal Grid Line
            canvas.drawLine(padLeft, y, width - padRight, y, gridPaint)

            // Left Y-Axis Label (Temperature)
            val tempVal = minTempGraph + (maxTempGraph - minTempGraph) * fraction
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${tempVal.toInt()}°", padLeft - 15f, y + 10f, textPaint)

            // Right Y-Axis Label (Tenderness %)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${(fraction * 100).toInt()}%", width - padRight + 15f, y + 10f, textPaint)
        }

        // Bottom X-Axis Labels (Elapsed Time)
        val numTimeGrids = 4
        for (i in 0..numTimeGrids) {
            val fraction = i / numTimeGrids.toFloat()
            val x = padLeft + (graphW * fraction)

            val elapsedSeconds = (timeRange * fraction).toLong()
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60

            val timeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(timeText, x, height - padBottom + 50f, textPaint)
        }

        // --- PREPARE DATA PATHS ---
        pitPath.reset()
        meatPath.reset()
        targetPath.reset()
        tendernessPath.reset()

        var pitStarted = false
        var meatStarted = false
        var targetStarted = false
        var tendernessStarted = false

        var currentTenderness = 0.0
        var lastSampleTime = samples.first().mTime

        var firstX = -1f
        var lastX = -1f

        for (sample in samples) {
            val x = padLeft + graphW * ((sample.mTime - minTime) / timeRange)
            lastX = x
            if (firstX == -1f) firstX = x

            // 1. Plot Pit Target / Setpoint (Dashed Line)
            if (!sample.mSetPoint.isNaN()) {
                val yTarget = padTop + graphH - (graphH * HeaterMeter.getNormalized(sample.mSetPoint)).toFloat()
                if (!targetStarted) {
                    targetPath.moveTo(x, yTarget)
                    targetStarted = true
                } else {
                    targetPath.lineTo(x, yTarget)
                }
            }

            // 2. Plot Pit Temp (Probe 0)
            val pitTemp = sample.mProbes[0]
            if (!pitTemp.isNaN()) {
                val yPit = padTop + graphH - (graphH * HeaterMeter.getNormalized(pitTemp)).toFloat()
                if (!pitStarted) {
                    pitPath.moveTo(x, yPit)
                    pitStarted = true
                } else {
                    pitPath.lineTo(x, yPit)
                }
            }

            // 3. Plot Meat Temp (Probe 1)
            val meatTemp = sample.mProbes[1]
            if (!meatTemp.isNaN()) {
                val yMeat = padTop + graphH - (graphH * HeaterMeter.getNormalized(meatTemp)).toFloat()
                if (!meatStarted) {
                    meatPath.moveTo(x, yMeat)
                    meatStarted = true
                } else {
                    meatPath.lineTo(x, yMeat)
                }
            }

            // 4. Calculate & Plot Tenderness
            val dtMillis = (sample.mTime - lastSampleTime) * 1000L
            if (dtMillis > 0 && dtMillis < (30 * 60 * 1000)) {
                val meatTemp = sample.mProbes[1]
                if (!meatTemp.isNaN()) {
                    // Convert to Fahrenheit for the math engine
                    var mathTemp = meatTemp
                    if (mathTemp in 1.0..110.0) {
                        mathTemp = (mathTemp * 9.0 / 5.0) + 32.0
                    }

                    currentTenderness += TendernessCalculator.calculateAddedPercentage(mathTemp, dtMillis)
                    if (currentTenderness > 100.0) currentTenderness = 100.0
                }
            }
            lastSampleTime = sample.mTime

            val yTenderness = padTop + graphH - (graphH * (currentTenderness / 100.0)).toFloat()
            if (!tendernessStarted) {
                tendernessPath.moveTo(x, yTenderness)
                tendernessStarted = true
            } else {
                tendernessPath.lineTo(x, yTenderness)
            }
        }

        // --- DRAW TENDERNESS GRADIENT FILL ---
        if (tendernessStarted && firstX != -1f) {
            // Create a gradient that fades from semi-transparent Neon Green at the top to completely transparent at the bottom
            tendernessFillPaint.shader = LinearGradient(
                0f, padTop, 0f, padTop + graphH,
                Color.parseColor("#6600E676"), // 40% opacity Neon Green
                Color.parseColor("#0000E676"), // 0% opacity
                Shader.TileMode.CLAMP
            )

            // Copy the tenderness path, drop lines down to the bottom axis, and close it to create a filled shape
            val fillPath = Path(tendernessPath)
            fillPath.lineTo(lastX, padTop + graphH)
            fillPath.lineTo(firstX, padTop + graphH)
            fillPath.close()

            canvas.drawPath(fillPath, tendernessFillPaint)
        }

        // --- RENDER THE LINES ON TOP ---
        if (targetStarted) canvas.drawPath(targetPath, targetPaint)
        if (pitStarted) canvas.drawPath(pitPath, pitPaint)
        if (meatStarted) canvas.drawPath(meatPath, meatPaint)
        if (tendernessStarted) canvas.drawPath(tendernessPath, tendernessLinePaint)
    }
}