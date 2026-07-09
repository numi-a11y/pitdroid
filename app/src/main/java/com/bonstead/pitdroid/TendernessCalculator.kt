package com.bonstead.pitdroid

object TendernessCalculator {
    // Temperature (°F) to % Done Per Hour mapping based on the Smoke Trails BBQ model
    private val renderingCurve = listOf(
        140.0 to 1.0,
        150.0 to 2.0,
        160.0 to 3.0,
        170.0 to 5.0,
        180.0 to 9.0,
        190.0 to 18.0,
        195.0 to 25.0,
        200.0 to 35.0,
        205.0 to 55.0,
        210.0 to 75.0
    )

    /**
     * Calculates the rendering rate (% per hour) for a given temperature using linear interpolation.
     */
    fun getRenderingRatePerHour(temperatureF: Double): Double {
        // Below 140°F, rendering is effectively 0%
        if (temperatureF < renderingCurve.first().first) return 0.0

        // Above 210°F, we cap it at the maximum known rate to prevent runaway math
        if (temperatureF >= renderingCurve.last().first) return renderingCurve.last().second

        for (i in 0 until renderingCurve.size - 1) {
            val (temp1, rate1) = renderingCurve[i]
            val (temp2, rate2) = renderingCurve[i + 1]

            // Find which temperature bracket we are currently inside
            if (temperatureF >= temp1 && temperatureF < temp2) {
                // Calculate exactly how far we are between the two temperatures
                val fraction = (temperatureF - temp1) / (temp2 - temp1)
                return rate1 + fraction * (rate2 - rate1)
            }
        }
        return 0.0
    }

    /**
     * Calculates the percentage points to add based on the current temp and time elapsed.
     */
    fun calculateAddedPercentage(currentTempF: Double, timePassedMillis: Long): Double {
        if (currentTempF.isNaN()) return 0.0

        val ratePerHour = getRenderingRatePerHour(currentTempF)

        // Convert milliseconds to a fractional hour
        val hoursPassed = timePassedMillis.toDouble() / (1000.0 * 60.0 * 60.0)

        return ratePerHour * hoursPassed
    }
}