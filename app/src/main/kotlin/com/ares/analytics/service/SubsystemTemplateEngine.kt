package com.ares.analytics.service

import com.ares.analytics.viewmodel.SubsystemGeneratorState
import com.ares.analytics.viewmodel.HardwareEntry
import com.ares.analytics.viewmodel.HardwareType
import com.ares.analytics.viewmodel.StateFieldEntry
import com.ares.analytics.viewmodel.FieldType

/**
 * Boilerplate code generation engine producing complete Redux subsystem file suites for `ARESLib-Kotlin`.
 *
 * Generates decoupling architecture layers for new robot subsystems:
 * 1. **`State.kt`**: Immutable Redux state data classes.
 * 2. **`IO.kt`**: Decoupled hardware IO layer interface.
 * 3. **`FtcIO.kt`**: Physical FTC hardware SDK driver implementation.
 * 4. **`MockIO.kt`**: Mock hardware component implementation for offline simulation.
 * 5. **`Controller.kt`**: Subsystem state-space or PID controller facade.
 *
 * ### Thread Safety & Performance Guarantees:
 * Pure functional generator object executing string templating without side effects.
 *
 * @see com.ares.analytics.viewmodel.SubsystemGeneratorState
 */
object SubsystemTemplateEngine {

    /**
     * Generates a map of relative file paths to generated Kotlin source code string contents.
     *
     * @param state Subsystem generator parameters and hardware configuration state.
     * @return Map binding target file paths (e.g. `"hardware/FtcArmIO.kt"`) to source code string contents.
     */
    fun generateFiles(state: SubsystemGeneratorState): Map<String, String> {
        val rawName = state.subsystemName.trim()
        if (rawName.isEmpty()) return emptyMap()

        val name = rawName.replaceFirstChar { it.uppercase() }
        val lowerName = rawName.replaceFirstChar { it.lowercase() }
        val pkg = state.packageName.ifBlank { "org.firstinspires.ftc.teamcode" }

        val result = mutableMapOf<String, String>()

        // 1. State.kt
        result["state/${name}State.kt"] = generateStateFile(name, pkg, state.stateFields)

        // 2. IO Interface
        result["hardware/${name}IO.kt"] = generateIoInterfaceFile(name, pkg, state.hardwareEntries)

        // 3. FtcIO.kt
        result["hardware/Ftc${name}IO.kt"] = generateFtcIoFile(name, lowerName, pkg, state.hardwareEntries)

        // 4. MockIO.kt (if enabled)
        if (state.generateMockIO) {
            result["hardware/Mock${name}IO.kt"] = generateMockIoFile(name, pkg, state.hardwareEntries)
        }

        // 5. Controller.kt
        result["control/${name}Controller.kt"] = generateControllerFile(name, pkg, state.hardwareEntries, state.stateFields)

        // 6. Subsystem.kt
        result["subsystems/${name}Subsystem.kt"] = generateSubsystemFile(name, pkg, state.hardwareEntries)

        // 7. Dedicated Action & Reducer (if enabled)
        if (state.generateDedicatedActions) {
            result["state/${name}Action.kt"] = generateActionFile(name, pkg)
            result["state/${name}Reducer.kt"] = generateReducerFile(name, pkg)
        }

        // 8. Test Skeleton (if enabled)
        if (state.generateTestSkeleton) {
            result["test/${name}SubsystemTest.kt"] = generateTestFile(name, pkg)
        }

        return result
    }

    private fun generateStateFile(name: String, pkg: String, fields: List<StateFieldEntry>): String {
        val fieldsBlock = if (fields.isEmpty()) {
            "    val targetPosition: Double = 0.0,\n    val currentPosition: Double = 0.0"
        } else {
            fields.joinToString(",\n") { f ->
                val fieldName = f.name.ifBlank { "value" }
                val typeName = f.type.kotlinType
                val defaultVal = f.defaultValue.ifBlank { f.type.defaultLiteral }
                "    val $fieldName: $typeName = $defaultVal"
            }
        }

        return """
            package $pkg.state

            import com.areslib.state.SubsystemState

            /**
             * Immutable Redux state for the $name subsystem.
             */
            data class ${name}State(
            $fieldsBlock
            ) : SubsystemState
        """.trimIndent() + "\n"
    }

    private fun generateIoInterfaceFile(name: String, pkg: String, hardware: List<HardwareEntry>): String {
        val propsAndMethods = StringBuilder()

        if (hardware.isEmpty()) {
            propsAndMethods.append("    val position: Double\n")
            propsAndMethods.append("    val currentAmps: Double\n\n")
            propsAndMethods.append("    fun setVoltage(voltage: Double)\n")
        } else {
            hardware.forEach { h ->
                val hwName = h.name.ifBlank { "motor" }
                val capHwName = hwName.replaceFirstChar { it.uppercase() }

                when (h.type) {
                    HardwareType.DC_MOTOR_EX -> {
                        propsAndMethods.append("    val ${hwName}Position: Double\n")
                        propsAndMethods.append("    val ${hwName}CurrentAmps: Double\n")
                        propsAndMethods.append("    fun set${capHwName}Voltage(volts: Double)\n\n")
                    }
                    HardwareType.SERVO, HardwareType.CONTINUOUS_SERVO -> {
                        propsAndMethods.append("    fun set${capHwName}Position(position: Double)\n\n")
                    }
                    HardwareType.DIGITAL_CHANNEL -> {
                        propsAndMethods.append("    val is${capHwName}Triggered: Boolean\n\n")
                    }
                    HardwareType.ANALOG_INPUT -> {
                        propsAndMethods.append("    val ${hwName}Voltage: Double\n\n")
                    }
                    HardwareType.COLOR_SENSOR -> {
                        propsAndMethods.append("    val ${hwName}Red: Int\n")
                        propsAndMethods.append("    val ${hwName}Green: Int\n")
                        propsAndMethods.append("    val ${hwName}Blue: Int\n\n")
                    }
                }
            }
        }

        return """
            package $pkg.hardware

            import com.areslib.hardware.SubsystemIO

            /**
             * Abstract hardware interface for $name.
             */
            interface ${name}IO : SubsystemIO, AutoCloseable {
            $propsAndMethods}
        """.trimIndent() + "\n"
    }

    private fun generateFtcIoFile(name: String, lowerName: String, pkg: String, hardware: List<HardwareEntry>): String {
        val fields = StringBuilder()
        val getters = StringBuilder()
        val refreshBody = StringBuilder()
        val methods = StringBuilder()
        val safeBody = StringBuilder()

        if (hardware.isEmpty()) {
            fields.append("    private val motor: DcMotorEx? = try { hardwareMap.get(DcMotorEx::class.java, \"$lowerName\") } catch (_: Exception) { null }\n")
            fields.append("    private var _cachedPosition = 0.0\n")
            fields.append("    private var _cachedAmps = 0.0\n")

            getters.append("    override val position: Double get() = _cachedPosition\n")
            getters.append("    override val currentAmps: Double get() = _cachedAmps\n")

            refreshBody.append("        _cachedPosition = motor?.currentPosition?.toDouble() ?: 0.0\n")

            methods.append("""
                override fun setVoltage(voltage: Double) {
                    val power = (voltage / 12.0).coerceIn(-1.0, 1.0)
                    try { motor?.power = power } catch (_: Exception) {}
                }
            """.trimIndent()).append("\n")

            safeBody.append("        setVoltage(0.0)\n")
        } else {
            hardware.forEach { h ->
                val hwName = h.name.ifBlank { "device" }
                val capHwName = hwName.replaceFirstChar { it.uppercase() }

                when (h.type) {
                    HardwareType.DC_MOTOR_EX -> {
                        fields.append("    private val ${hwName}Motor: DcMotorEx? = try { hardwareMap.get(DcMotorEx::class.java, \"$hwName\") } catch (_: Exception) { null }\n")
                        fields.append("    private var _cached${capHwName}Pos = 0.0\n")
                        fields.append("    private var _cached${capHwName}Amps = 0.0\n")

                        getters.append("    override val ${hwName}Position: Double get() = _cached${capHwName}Pos\n")
                        getters.append("    override val ${hwName}CurrentAmps: Double get() = _cached${capHwName}Amps\n")

                        refreshBody.append("        _cached${capHwName}Pos = ${hwName}Motor?.currentPosition?.toDouble() ?: 0.0\n")

                        methods.append("""
                            override fun set${capHwName}Voltage(volts: Double) {
                                val power = (volts / 12.0).coerceIn(-1.0, 1.0)
                                try { ${hwName}Motor?.power = power } catch (_: Exception) {}
                            }
                        """.trimIndent()).append("\n\n")

                        safeBody.append("        set${capHwName}Voltage(0.0)\n")
                    }
                    HardwareType.SERVO -> {
                        fields.append("    private val ${hwName}Servo: Servo? = try { hardwareMap.get(Servo::class.java, \"$hwName\") } catch (_: Exception) { null }\n")
                        methods.append("""
                            override fun set${capHwName}Position(position: Double) {
                                try { ${hwName}Servo?.position = position.coerceIn(0.0, 1.0) } catch (_: Exception) {}
                            }
                        """.trimIndent()).append("\n\n")
                    }
                    HardwareType.CONTINUOUS_SERVO -> {
                        fields.append("    private val ${hwName}CRServo: CRServo? = try { hardwareMap.get(CRServo::class.java, \"$hwName\") } catch (_: Exception) { null }\n")
                        methods.append("""
                            override fun set${capHwName}Position(position: Double) {
                                try { ${hwName}CRServo?.power = position.coerceIn(-1.0, 1.0) } catch (_: Exception) {}
                            }
                        """.trimIndent()).append("\n\n")
                        safeBody.append("        set${capHwName}Position(0.0)\n")
                    }
                    HardwareType.DIGITAL_CHANNEL -> {
                        fields.append("    private val ${hwName}Channel: DigitalChannel? = try { hardwareMap.get(DigitalChannel::class.java, \"$hwName\") } catch (_: Exception) { null }\n")
                        fields.append("    private var _cached${capHwName}State = false\n")
                        getters.append("    override val is${capHwName}Triggered: Boolean get() = _cached${capHwName}State\n")
                        refreshBody.append("        _cached${capHwName}State = ${hwName}Channel?.state ?: false\n")
                    }
                    HardwareType.ANALOG_INPUT -> {
                        fields.append("    private val ${hwName}Analog: AnalogInput? = try { hardwareMap.get(AnalogInput::class.java, \"$hwName\") } catch (_: Exception) { null }\n")
                        fields.append("    private var _cached${capHwName}Voltage = 0.0\n")
                        getters.append("    override val ${hwName}Voltage: Double get() = _cached${capHwName}Voltage\n")
                        refreshBody.append("        _cached${capHwName}Voltage = ${hwName}Analog?.voltage ?: 0.0\n")
                    }
                    HardwareType.COLOR_SENSOR -> {
                        fields.append("    private val ${hwName}Color: ColorSensor? = try { hardwareMap.get(ColorSensor::class.java, \"$hwName\") } catch (_: Exception) { null }\n")
                        fields.append("    private var _cached${capHwName}R = 0\n")
                        fields.append("    private var _cached${capHwName}G = 0\n")
                        fields.append("    private var _cached${capHwName}B = 0\n")
                        getters.append("    override val ${hwName}Red: Int get() = _cached${capHwName}R\n")
                        getters.append("    override val ${hwName}Green: Int get() = _cached${capHwName}G\n")
                        getters.append("    override val ${hwName}Blue: Int get() = _cached${capHwName}B\n")
                        refreshBody.append("        _cached${capHwName}R = ${hwName}Color?.red() ?: 0\n")
                        refreshBody.append("        _cached${capHwName}G = ${hwName}Color?.green() ?: 0\n")
                        refreshBody.append("        _cached${capHwName}B = ${hwName}Color?.blue() ?: 0\n")
                    }
                }
            }
        }

        val hasServos = hardware.any { it.type == HardwareType.SERVO }
        val hasCRServos = hardware.any { it.type == HardwareType.CONTINUOUS_SERVO }
        val hasDigital = hardware.any { it.type == HardwareType.DIGITAL_CHANNEL }
        val hasAnalog = hardware.any { it.type == HardwareType.ANALOG_INPUT }
        val hasColor = hardware.any { it.type == HardwareType.COLOR_SENSOR }

        val imports = StringBuilder("import com.qualcomm.robotcore.hardware.HardwareMap\n")
        imports.append("import com.qualcomm.robotcore.hardware.DcMotorEx\n")
        if (hasServos) imports.append("import com.qualcomm.robotcore.hardware.Servo\n")
        if (hasCRServos) imports.append("import com.qualcomm.robotcore.hardware.CRServo\n")
        if (hasDigital) imports.append("import com.qualcomm.robotcore.hardware.DigitalChannel\n")
        if (hasAnalog) imports.append("import com.qualcomm.robotcore.hardware.AnalogInput\n")
        if (hasColor) imports.append("import com.qualcomm.robotcore.hardware.ColorSensor\n")

        return """
            package $pkg.hardware

            $imports
            class Ftc${name}IO(hardwareMap: HardwareMap) : ${name}IO {
            $fields
                init {
                    com.areslib.hardware.HardwareRegistry.registerCloseable(this)
                }

            $getters
                override fun refresh() {
            $refreshBody    }

            $methods
                override fun safe() {
            $safeBody    }

                override fun close() {
                    safe()
                }
            }
        """.trimIndent() + "\n"
    }

    private fun generateMockIoFile(name: String, pkg: String, hardware: List<HardwareEntry>): String {
        val properties = StringBuilder()
        val methods = StringBuilder()

        if (hardware.isEmpty()) {
            properties.append("    override var position: Double = 0.0\n        private set\n")
            properties.append("    override var currentAmps: Double = 0.0\n        private set\n")
            methods.append("""
                override fun setVoltage(voltage: Double) {
                    position += voltage * 0.05
                    currentAmps = kotlin.math.abs(voltage * 0.2)
                }
            """.trimIndent()).append("\n")
        } else {
            hardware.forEach { h ->
                val hwName = h.name.ifBlank { "device" }
                val capHwName = hwName.replaceFirstChar { it.uppercase() }

                when (h.type) {
                    HardwareType.DC_MOTOR_EX -> {
                        properties.append("    override var ${hwName}Position: Double = 0.0\n        private set\n")
                        properties.append("    override var ${hwName}CurrentAmps: Double = 0.0\n        private set\n")
                        methods.append("""
                            override fun set${capHwName}Voltage(volts: Double) {
                                ${hwName}Position += volts * 0.05
                                ${hwName}CurrentAmps = kotlin.math.abs(volts * 0.2)
                            }
                        """.trimIndent()).append("\n\n")
                    }
                    HardwareType.SERVO, HardwareType.CONTINUOUS_SERVO -> {
                        methods.append("""
                            override fun set${capHwName}Position(position: Double) {}
                        """.trimIndent()).append("\n\n")
                    }
                    HardwareType.DIGITAL_CHANNEL -> {
                        properties.append("    override var is${capHwName}Triggered: Boolean = false\n")
                    }
                    HardwareType.ANALOG_INPUT -> {
                        properties.append("    override var ${hwName}Voltage: Double = 0.0\n")
                    }
                    HardwareType.COLOR_SENSOR -> {
                        properties.append("    override var ${hwName}Red: Int = 0\n")
                        properties.append("    override var ${hwName}Green: Int = 0\n")
                        properties.append("    override var ${hwName}Blue: Int = 0\n")
                    }
                }
            }
        }

        return """
            package $pkg.hardware

            class Mock${name}IO : ${name}IO {
            $properties
                override fun refresh() {}

            $methods
                override fun safe() {}

                override fun close() {}
            }
        """.trimIndent() + "\n"
    }

    private fun generateControllerFile(name: String, pkg: String, hardware: List<HardwareEntry>, fields: List<StateFieldEntry>): String {
        val motorEntry = hardware.firstOrNull { it.type == HardwareType.DC_MOTOR_EX }
        val motorName = motorEntry?.name?.ifBlank { "motor" } ?: "motor"
        val capMotorName = motorName.replaceFirstChar { it.uppercase() }

        val targetProp = fields.firstOrNull { it.name.contains("target", ignoreCase = true) }?.name ?: "targetPosition"
        val currentProp = fields.firstOrNull { it.name.contains("current", ignoreCase = true) }?.name ?: "currentPosition"

        return """
            package $pkg.control

            import $pkg.state.${name}State
            import $pkg.hardware.${name}IO

            class ${name}Controller(private val io: ${name}IO) {

                fun update(state: ${name}State, dtSeconds: Double) {
                    val error = state.$targetProp - state.$currentProp
                    val kP = 0.5
                    val voltage = (error * kP).coerceIn(-12.0, 12.0)

                    io.set${if (hardware.isNotEmpty()) capMotorName else ""}Voltage(voltage)
                }
            }
        """.trimIndent() + "\n"
    }

    private fun generateSubsystemFile(name: String, pkg: String, hardware: List<HardwareEntry>): String {
        return """
            package $pkg.subsystems

            import com.areslib.Store
            import com.areslib.state.RobotState
            import com.areslib.subsystem.Subsystem
            import $pkg.hardware.${name}IO

            class ${name}Subsystem(private val io: ${name}IO) : Subsystem {

                override fun readSensors(store: Store, timestampMs: Long) {
                    io.refresh()
                }

                override fun writeOutputs(state: RobotState, scale: Double) {
                    // Implement output scaling and voltage commands here
                }

                override fun close() {
                    (io as? AutoCloseable)?.close()
                }
            }
        """.trimIndent() + "\n"
    }

    private fun generateActionFile(name: String, pkg: String): String {
        return """
            package $pkg.state

            import com.areslib.action.RobotAction

            sealed class ${name}Action : RobotAction {
                data class SetTarget(
                    val position: Double,
                    override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
                ) : ${name}Action()

                data class UpdateSensors(
                    val position: Double,
                    val currentAmps: Double,
                    override val timestampMs: Long = com.areslib.util.RobotClock.currentTimeMillis()
                ) : ${name}Action()
            }
        """.trimIndent() + "\n"
    }

    private fun generateReducerFile(name: String, pkg: String): String {
        return """
            package $pkg.state

            object ${name}Reducer {
                fun reduce(state: ${name}State, action: ${name}Action): ${name}State {
                    return when (action) {
                        is ${name}Action.SetTarget -> state.copy(targetPosition = action.position)
                        is ${name}Action.UpdateSensors -> state.copy(currentPosition = action.position)
                    }
                }
            }
        """.trimIndent() + "\n"
    }

    private fun generateTestFile(name: String, pkg: String): String {
        return """
            package $pkg

            import org.junit.jupiter.api.Test
            import org.junit.jupiter.api.Assertions.*
            import $pkg.hardware.Mock${name}IO
            import $pkg.state.${name}State

            class ${name}SubsystemTest {

                @Test
                fun testInitialState() {
                    val io = Mock${name}IO()
                    val state = ${name}State()
                    assertNotNull(state)
                    assertNotNull(io)
                }
            }
        """.trimIndent() + "\n"
    }
}
