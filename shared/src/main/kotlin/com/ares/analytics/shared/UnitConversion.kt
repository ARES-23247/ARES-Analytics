package com.ares.analytics.shared

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
enum class UnitCategory {
    LENGTH, ANGLE, ANGULAR_VELOCITY, TIME, VOLTAGE, CURRENT, TEMPERATURE, NONE
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
enum class RobotUnit(val symbol: String, val category: UnitCategory, val factorToBase: Double) {
    // Length (Base: Meter)
    METER("m", UnitCategory.LENGTH, 1.0),
    INCH("in", UnitCategory.LENGTH, 0.0254),
    FOOT("ft", UnitCategory.LENGTH, 0.3048),
    CENTIMETER("cm", UnitCategory.LENGTH, 0.01),

    // Angle (Base: Radian)
    RADIAN("rad", UnitCategory.ANGLE, 1.0),
    DEGREE("deg", UnitCategory.ANGLE, Math.PI / 180.0),
    ROTATION("rot", UnitCategory.ANGLE, 2 * Math.PI),

    // Angular Velocity (Base: Radian/sec)
    RAD_PER_SEC("rad/s", UnitCategory.ANGULAR_VELOCITY, 1.0),
    DEG_PER_SEC("deg/s", UnitCategory.ANGULAR_VELOCITY, Math.PI / 180.0),
    RPM("rpm", UnitCategory.ANGULAR_VELOCITY, (2 * Math.PI) / 60.0),

    // Time (Base: Second)
    SECOND("s", UnitCategory.TIME, 1.0),
    MILLISECOND("ms", UnitCategory.TIME, 0.001),
    MINUTE("min", UnitCategory.TIME, 60.0),

    // Voltage (Base: Volt)
    VOLT("V", UnitCategory.VOLTAGE, 1.0),
    MILLIVOLT("mV", UnitCategory.VOLTAGE, 0.001),

    // Current (Base: Ampere)
    AMPERE("A", UnitCategory.CURRENT, 1.0),
    MILLIAMPERE("mA", UnitCategory.CURRENT, 0.001),

    // Temperature (Base: Celsius)
    CELSIUS("°C", UnitCategory.TEMPERATURE, 1.0),
    FAHRENHEIT("°F", UnitCategory.TEMPERATURE, 1.0),
    KELVIN("K", UnitCategory.TEMPERATURE, 1.0);

    companion object {
        /**

         * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

         *

         */
        fun fromSymbol(symbol: String): RobotUnit? {
            val clean = symbol.trim()
            return entries.find { it.symbol.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true) }
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
object UnitConversion {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun convert(value: Double, from: RobotUnit, to: RobotUnit): Double {
        if (from.category != to.category) return value

        if (from.category == UnitCategory.TEMPERATURE) {
            val celsius = when (from) {
                RobotUnit.CELSIUS -> value
                RobotUnit.FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0
                RobotUnit.KELVIN -> value - 273.15
                else -> value
            }
            return when (to) {
                RobotUnit.CELSIUS -> celsius
                RobotUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
                RobotUnit.KELVIN -> celsius + 273.15
                else -> celsius
            }
        }
        val baseValue = value * from.factorToBase
        return baseValue / to.factorToBase
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun detectUnitFromKey(key: String): RobotUnit? {
        val lowerKey = key.lowercase()
        return when {
            lowerKey.contains("voltage") || lowerKey.contains("volt") -> RobotUnit.VOLT
            lowerKey.contains("current") || lowerKey.contains("amp") -> RobotUnit.AMPERE
            lowerKey.contains("temp") || lowerKey.contains("celsius") -> RobotUnit.CELSIUS
            lowerKey.contains("fahrenheit") -> RobotUnit.FAHRENHEIT
            lowerKey.contains("velocity") || lowerKey.contains("vel") -> {
                if (lowerKey.contains("rot") || lowerKey.contains("rpm") || lowerKey.contains("ang")) {
                    RobotUnit.RAD_PER_SEC
                } else {
                    RobotUnit.METER
                }
            }
            lowerKey.contains("angle") || lowerKey.contains("heading") || lowerKey.contains("yaw") || lowerKey.contains("pitch") || lowerKey.contains("roll") || lowerKey.contains("deg") -> RobotUnit.DEGREE
            lowerKey.contains("rad") -> RobotUnit.RADIAN
            lowerKey.contains("rot") -> RobotUnit.ROTATION
            lowerKey.contains("time") || lowerKey.contains("sec") -> RobotUnit.SECOND
            lowerKey.contains("ms") -> RobotUnit.MILLISECOND
            lowerKey.contains("distance") || lowerKey.contains("pos") || lowerKey.contains("x") || lowerKey.contains("y") || lowerKey.contains("z") -> RobotUnit.METER
            else -> null
        }
    }
}
