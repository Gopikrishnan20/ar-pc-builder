package com.arpcbuilder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arpcbuilder.domain.model.AssemblyState
import com.arpcbuilder.domain.model.ComponentType
import kotlinx.coroutines.delay

/**
 * Simulated POST screen shown on the AR monitor. Lines print one by one, built from the real parts.
 * Rendered inside a SceneView ViewNode, which composes off-screen, so it sets its own colours and size.
 */
@Composable
fun BiosScreen(state: AssemblyState, width: Dp, height: Dp) {
    val lines = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        lines.clear()
        bootScript(state).forEach { line ->
            delay(if (line.isEmpty()) 150 else 380)
            lines += line
        }
    }
    Column(
        Modifier
            .size(width, height)
            .background(Color(0xFF000814))
            .padding(10.dp),
    ) {
        lines.forEach { line ->
            Text(
                line,
                color = if (line.startsWith("OK")) Color(0xFF00E676) else Color(0xFFCFD8DC),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
            )
        }
    }
}

private fun bootScript(s: AssemblyState): List<String> {
    fun name(t: ComponentType) = s.installedOf(t).firstOrNull()?.component?.name ?: "—"
    val ram = s.installedOf(ComponentType.RAM)
    val ssd = s.installedOf(ComponentType.SSD)
    return listOf(
        "AR-BIOS v1.0  (C) AR PC Builder",
        "Board : ${name(ComponentType.MOTHERBOARD)}",
        "",
        "CPU   : ${name(ComponentType.CPU)}",
        "Memory: ${ram.size} x 16 GB ${ram.firstOrNull()?.component?.memoryType ?: ""}  (${ram.size * 16} GB)",
        "NVMe  : ${ssd.joinToString { it.component.name }}",
        "GPU   : ${name(ComponentType.GPU)}",
        "PSU   : ${s.power.psuCapacityW} W  (load ~${s.power.totalDrawW} W)",
        "",
        "OK  POST complete",
        "OK  Booting from NVMe0 ...",
    )
}
