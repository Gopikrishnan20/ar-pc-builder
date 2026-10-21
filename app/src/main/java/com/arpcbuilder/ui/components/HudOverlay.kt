package com.arpcbuilder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arpcbuilder.domain.engine.CompatibilityEngine
import com.arpcbuilder.domain.model.AssemblyState
import com.arpcbuilder.domain.model.AssemblyStep
import com.arpcbuilder.domain.model.BuildMode
import com.arpcbuilder.domain.model.Severity
import com.arpcbuilder.ui.theme.StatusAmber
import com.arpcbuilder.ui.theme.StatusGreen
import com.arpcbuilder.ui.theme.StatusRed

private val HudShape = RoundedCornerShape(16.dp)
private val HudBackground = Color(0xCC111318)

/**
 * Top overlay: mode switch, assembly step, live wattage and a compatibility badge that
 * expands into the full issue list when tapped.
 */
@Composable
fun HudOverlay(
    state: AssemblyState,
    onModeChange: (BuildMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeSwitch(state.mode, onModeChange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepCard(state, Modifier.weight(1f))
            WattageCard(state)
        }
        CompatibilityBadge(state)
    }
}

@Composable
private fun ModeSwitch(mode: BuildMode, onModeChange: (BuildMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().background(HudBackground, HudShape)) {
        BuildMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { onModeChange(m) },
                shape = SegmentedButtonDefaults.itemShape(i, BuildMode.entries.size),
                label = { Text(if (m == BuildMode.GUIDED) "Guided" else "Sandbox") },
            )
        }
    }
}

/** The current step: what to do, why it matters, and how far through the build the student is. */
@Composable
private fun StepCard(state: AssemblyState, modifier: Modifier) {
    val step = state.currentStep
    val total = AssemblyStep.workSteps.size
    val progress by animateFloatAsState(state.stepsDone / total.toFloat(), tween(600), label = "progress")
    Surface(modifier, shape = HudShape, color = HudBackground) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Text(
                if (step == AssemblyStep.DONE) "Build complete" else "Step ${step.number} of $total · ${step.title}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(step.instruction, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
            if (state.mode == BuildMode.GUIDED) {
                Text("Why: ${step.why}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            Spacer(Modifier.size(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
        }
    }
}

@Composable
private fun WattageCard(state: AssemblyState) {
    val power = state.power
    val capacity = power.psuCapacityW
    val headroom = power.headroomPercent
    val color by animateColorAsState(
        when {
            capacity == null -> Color.White
            headroom != null && headroom < 0 -> StatusRed
            headroom != null && headroom < CompatibilityEngine.MIN_HEADROOM_PERCENT -> StatusAmber
            else -> StatusGreen
        },
        label = "wattColor",
    )
    Surface(shape = HudShape, color = HudBackground) {
        Column(Modifier.padding(12.dp).width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = color)
            Text("${power.totalDrawW} W", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(
                capacity?.let { "of $it W" } ?: "no PSU",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CompatibilityBadge(state: AssemblyState) {
    var expanded by remember { mutableStateOf(false) }
    val report = state.compatibility
    val (color, icon, label) = when {
        report.hasErrors -> Triple(StatusRed, Icons.Rounded.Error, "${report.issues.count { it.severity == Severity.ERROR }} incompatible")
        report.hasWarnings -> Triple(StatusAmber, Icons.Rounded.Warning, "Compatible with warnings")
        else -> Triple(StatusGreen, Icons.Rounded.CheckCircle, "All parts compatible")
    }
    Surface(
        shape = HudShape,
        color = HudBackground,
        modifier = Modifier.clickable(enabled = report.issues.isNotEmpty()) { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    report.issues.forEach { issue ->
                        val c = when (issue.severity) {
                            Severity.ERROR -> StatusRed
                            Severity.WARNING -> StatusAmber
                            Severity.INFO -> Color.White.copy(alpha = 0.7f)
                        }
                        Text("• ${issue.message}", color = c, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
