package com.ares.analytics.ui.components.dashboard
import kotlinx.coroutines.launch
import com.ares.analytics.ui.components.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.Nt4ClientService
import kotlinx.coroutines.flow.Flow
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan

@Composable
fun TuningCard(
    nt4ClientService: Nt4ClientService,
    modifier: Modifier = Modifier
) {
    val groups = listOf(
        TuningGroup("Drivetrain Kinematics", listOf("drive/trackWidthMeters", "drive/wheelBaseMeters", "drive/ftc/ticksPerMeter")),
        TuningGroup("Path Translation PID", listOf("drive/pathTranslationGains/kP", "drive/pathTranslationGains/kI", "drive/pathTranslationGains/kD")),
        TuningGroup("Path Rotation PID", listOf("drive/pathRotationGains/kP", "drive/pathRotationGains/kI", "drive/pathRotationGains/kD")),
        TuningGroup("Heading Lock PID", listOf("drive/headingGains/kP", "drive/headingGains/kI", "drive/headingGains/kD", "drive/headingDeadzoneDeg")),
        TuningGroup("Linear Feedforward", listOf("drive/driveFeedforward/kS", "drive/driveFeedforward/kV", "drive/driveFeedforward/kA")),
        TuningGroup("Angular Feedforward", listOf("drive/angularFeedforward/kS", "drive/angularFeedforward/kV", "drive/angularFeedforward/kA")),
        TuningGroup("Motor Closed-Loop PIDF", listOf("drive/ftc/motorGains/kP", "drive/ftc/motorGains/kI", "drive/ftc/motorGains/kD", "drive/ftc/motorGains/kF")),
        TuningGroup("Odometry & EKF Localization", listOf("localization/ekfNoise/qX", "localization/ekfNoise/qY", "localization/ekfNoise/qTheta", "localization/ftcPinpoint/xOffsetMm", "localization/ftcPinpoint/yOffsetMm", "localization/ftcPinpoint/encoderResolution")),
        TuningGroup("Vision Filtering & Thresholds", listOf("vision/stdDevsX", "vision/stdDevsY", "vision/stdDevsHeading", "vision/maxDistanceMeters", "vision/maxAmbiguity", "vision/mahalanobisThreshold")),
        TuningGroup("Driver Profile Configuration", listOf("driver/deadbandExponent", "driver/slewRateLimit")),
        TuningGroup("Flywheel Auto-Tuning", listOf("subsystem/flywheel/feedforward/kS", "subsystem/flywheel/feedforward/kV", "subsystem/flywheel/feedforward/kA", "subsystem/flywheel/velocityGains/kP", "subsystem/flywheel/velocityGains/kI", "subsystem/flywheel/velocityGains/kD"))
    )

    AnalyticsCard(
        modifier = modifier.fillMaxSize(),
        backgroundColor = AresSurface,
        contentPadding = 12.dp
    ) {
        CardHeader(
            title = "Live Tuning",
            showDivider = false
        )

Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groups.forEach { group ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = group.title,
                            color = AresCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        group.variables.forEach { varName ->
                            TuningRow(nt4ClientService, varName)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = AresBorder.copy(alpha = 0.5f))
                    }
                }
        }
    }
}

private data class TuningGroup(
    val title: String,
    val variables: List<String>
)

@Composable
private fun TuningRow(nt4ClientService: Nt4ClientService, name: String) {
    val ntKey = "Tuning/$name"

    // Create state that updates when the value from NT4 changes, but also allows local edits
    val ntValue = nt4ClientService.subscribeDouble(ntKey).collectAsState(initial = 0.0)
    var textValue by remember(ntValue.value) { mutableStateOf(ntValue.value.toString()) }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = AresTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                newValue.toDoubleOrNull()?.let {
                    coroutineScope.launch {
                        nt4ClientService.publishDouble(ntKey, it)
                    }
                }
            },
            modifier = Modifier.width(120.dp).height(48.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            singleLine = true
        )
    }
}
