package com.arpcbuilder.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arpcbuilder.domain.engine.CompatibilityEngine
import com.arpcbuilder.domain.engine.InstallCheck
import com.arpcbuilder.domain.model.AssemblyState
import com.arpcbuilder.domain.model.ComponentType
import com.arpcbuilder.domain.model.PCComponent
import com.arpcbuilder.domain.model.Severity
import com.arpcbuilder.ui.theme.StatusAmber
import com.arpcbuilder.ui.theme.StatusGreen
import com.arpcbuilder.ui.theme.StatusRed

/**
 * Parts picker. Each card previews whether the part can go in *now*, using the same
 * [CompatibilityEngine.checkInstall] the ViewModel uses, so the sheet never promises what the build will refuse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentSheet(
    type: ComponentType,
    catalog: List<PCComponent>,
    state: AssemblyState,
    onSelect: (PCComponent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "Choose a ${type.displayName.lowercase()}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(catalog.filter { it.type == type }, key = { it.id }) { part ->
                PartCard(part, CompatibilityEngine.checkInstall(part, state)) { onSelect(part) }
            }
        }
    }
}

@Composable
private fun PartCard(part: PCComponent, check: InstallCheck, onClick: () -> Unit) {
    val (hint, hintColor) = when (check) {
        is InstallCheck.Blocked -> check.reason to StatusRed
        is InstallCheck.Allowed -> {
            val worst = check.issues.maxByOrNull { it.severity.ordinal }
            when (worst?.severity) {
                Severity.ERROR -> worst.message to StatusRed
                Severity.WARNING -> worst.message to StatusAmber
                else -> "Compatible" to StatusGreen
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(part.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(specLine(part), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            Text(hint, style = MaterialTheme.typography.labelMedium, color = hintColor)
        }
    }
}

private fun specLine(p: PCComponent): String = listOfNotNull(
    p.socketType?.name,
    p.formFactor?.displayName,
    p.memoryType?.name,
    when (p.type) {
        ComponentType.PSU -> "${p.wattage} W capacity"
        ComponentType.CASE -> null
        else -> p.wattage.takeIf { it > 0 }?.let { "$it W" }
    },
    p.lengthMm?.let { if (p.type == ComponentType.COOLER) "$it mm tall" else "$it mm long" },
    p.coolingCapacityW?.let { "cools $it W" },
    p.maxGpuLengthMm?.let { "GPU ≤ $it mm" },
    p.ramSlots.takeIf { it > 0 }?.let { "$it DIMM" },
    p.m2Slots.takeIf { it > 0 }?.let { "$it× M.2" },
    "ARGB".takeIf { p.hasRgb },
).joinToString(" · ")
