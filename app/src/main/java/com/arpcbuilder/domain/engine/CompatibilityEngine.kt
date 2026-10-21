package com.arpcbuilder.domain.engine

import com.arpcbuilder.domain.model.AssemblyState
import com.arpcbuilder.domain.model.AssemblyStep
import com.arpcbuilder.domain.model.BuildMode
import com.arpcbuilder.domain.model.CompatibilityIssue
import com.arpcbuilder.domain.model.CompatibilityReport
import com.arpcbuilder.domain.model.ComponentType
import com.arpcbuilder.domain.model.PCComponent
import com.arpcbuilder.domain.model.PowerBudget
import com.arpcbuilder.domain.model.Severity

/** Result of asking "may this part be installed right now?". */
sealed interface InstallCheck {
    /**
     * The part can go in. [slots] are the slots it will occupy (two for a RAM kit);
     * [issues] are non-blocking in the current mode.
     */
    data class Allowed(val slots: List<Int>, val issues: List<CompatibilityIssue> = emptyList()) : InstallCheck

    data class Blocked(val reason: String) : InstallCheck
}

/**
 * Pure, stateless compatibility rules. No Android dependencies: everything here is unit-tested on the JVM.
 */
object CompatibilityEngine {

    /** Below this much spare PSU capacity we warn; transient GPU spikes can trip the PSU's protection. */
    const val MIN_HEADROOM_PERCENT = 20

    /** Draw from fans, USB, RGB controllers and the like, which no single part reports. */
    const val BASE_SYSTEM_DRAW_W = 50

    /** Sticks in a memory kit. Two, so students learn dual channel. */
    const val RAM_KIT_STICKS = 2

    /** Which part each type physically plugs into. The board starts on the bench, so it has no host. */
    fun hostOf(type: ComponentType): ComponentType? = when (type) {
        ComponentType.CASE, ComponentType.MOTHERBOARD -> null
        ComponentType.PSU -> ComponentType.CASE
        ComponentType.CPU, ComponentType.RAM, ComponentType.SSD, ComponentType.GPU -> ComponentType.MOTHERBOARD
        ComponentType.COOLER -> ComponentType.CPU
    }

    fun powerBudget(parts: List<PCComponent>): PowerBudget {
        val draw = parts.filter { it.type != ComponentType.PSU }.sumOf { it.wattage }
        val psu = parts.firstOrNull { it.type == ComponentType.PSU }
        return PowerBudget(totalDrawW = if (draw > 0) draw + BASE_SYSTEM_DRAW_W else 0, psuCapacityW = psu?.wattage)
    }

    /** Smallest PSU (rounded up to 50 W) that leaves [MIN_HEADROOM_PERCENT] headroom. */
    fun recommendedPsuWattage(parts: List<PCComponent>): Int {
        val draw = powerBudget(parts).totalDrawW
        val raw = draw * 100 / (100 - MIN_HEADROOM_PERCENT)
        return ((raw + 49) / 50) * 50
    }

    /** Validates a whole build. Missing parts are not errors; only conflicts between present parts are. */
    fun validate(parts: List<PCComponent>): CompatibilityReport {
        val issues = mutableListOf<CompatibilityIssue>()
        fun first(type: ComponentType) = parts.firstOrNull { it.type == type }
        val case = first(ComponentType.CASE)
        val board = first(ComponentType.MOTHERBOARD)
        val cpu = first(ComponentType.CPU)
        val cooler = first(ComponentType.COOLER)
        val gpu = first(ComponentType.GPU)
        val ram = parts.filter { it.type == ComponentType.RAM }
        val ssds = parts.filter { it.type == ComponentType.SSD }

        // Socket: CPU ↔ motherboard
        if (cpu != null && board != null && cpu.socketType != board.socketType) {
            issues += error(
                "${cpu.name} needs ${cpu.socketType}, but ${board.name} has ${board.socketType}.",
                cpu, board,
            )
        }

        // Form factor: motherboard ↔ case
        if (board != null && case != null) {
            val bf = board.formFactor
            val cf = case.formFactor
            if (bf != null && cf != null && !bf.fitsIn(cf)) {
                issues += error("${bf.displayName} board does not fit a ${cf.displayName} case.", board, case)
            }
        }

        // Memory type and slot count
        if (board != null) {
            ram.filter { it.memoryType != board.memoryType }.forEach {
                issues += error("${it.name} is ${it.memoryType}; ${board.name} only takes ${board.memoryType}.", it, board)
            }
            if (ram.size > board.ramSlots) {
                issues += error("${ram.size} RAM sticks but the board has ${board.ramSlots} DIMM slots.", board)
            }
            if (ssds.size > board.m2Slots) {
                issues += error("${ssds.size} NVMe drives but the board has ${board.m2Slots} M.2 slots.", board)
            }
        }
        if (ram.map { it.id }.distinct().size > 1) {
            issues += warning("Mixed RAM kits may not run at their rated speed.", *ram.toTypedArray())
        }
        if (ram.size == 1) {
            issues += info("A single RAM stick runs in single-channel mode; two sticks are faster.", ram.first())
        }

        // Cooler: mounting, capacity, clearance
        if (cooler != null) {
            val socket = cpu?.socketType ?: board?.socketType
            if (socket != null && socket !in cooler.supportedSockets) {
                issues += error("${cooler.name} has no mounting kit for $socket.", cooler)
            }
            val capacity = cooler.coolingCapacityW
            if (cpu != null && capacity != null && capacity < cpu.wattage) {
                issues += warning(
                    "${cooler.name} is rated for $capacity W but ${cpu.name} can draw ${cpu.wattage} W; expect throttling.",
                    cooler, cpu,
                )
            }
            val height = cooler.lengthMm
            val maxHeight = case?.maxCoolerHeightMm
            if (height != null && maxHeight != null && height > maxHeight) {
                issues += error("Cooler is $height mm tall; the case fits $maxHeight mm.", cooler, case)
            }
        }

        // GPU clearance
        if (gpu != null && case != null) {
            val len = gpu.lengthMm
            val max = case.maxGpuLengthMm
            if (len != null && max != null && len > max) {
                issues += error("${gpu.name} is $len mm long; ${case.name} fits up to $max mm.", gpu, case)
            }
        }

        // Power
        val power = powerBudget(parts)
        val capacity = power.psuCapacityW
        if (capacity != null) {
            val psu = first(ComponentType.PSU)!!
            when {
                power.totalDrawW > capacity -> issues += error(
                    "System draws ~${power.totalDrawW} W but the PSU is $capacity W. " +
                        "Use at least ${recommendedPsuWattage(parts)} W.",
                    psu,
                )
                (power.headroomPercent ?: 100) < MIN_HEADROOM_PERCENT -> issues += warning(
                    "Only ${power.headroomPercent}% PSU headroom. ${recommendedPsuWattage(parts)} W is recommended.",
                    psu,
                )
            }
        }

        return CompatibilityReport(issues)
    }

    /** DIMM slots for a two-stick kit: A2/B2 on four-slot boards (the dual-channel pair). */
    fun dualChannelSlots(ramSlots: Int): List<Int> = if (ramSlots >= 4) listOf(1, 3) else (0 until minOf(ramSlots, RAM_KIT_STICKS)).toList()

    /** DIMM slots are labelled A1, A2, B1, B2 on four-slot boards. */
    fun slotLabel(slot: Int): String = "${if (slot < 2) 'A' else 'B'}${slot % 2 + 1}"

    /**
     * Decides whether [candidate] can be installed into [state] now, and into which slots.
     *
     * Both modes follow the build order. Guided mode also blocks incompatible parts; Sandbox lets
     * them in so students can see what goes wrong (red badge, no POST).
     */
    fun checkInstall(candidate: PCComponent, state: AssemblyState): InstallCheck {
        val parts = state.installed.map { it.component }
        val type = candidate.type

        val expected = state.currentStep.componentType
        if (expected != type) {
            return InstallCheck.Blocked(
                when (state.currentStep) {
                    AssemblyStep.WORKBENCH -> "Create the AR table first."
                    AssemblyStep.DONE -> "The build is complete."
                    else -> "Next: ${state.currentStep.actionLabel.lowercase()}."
                },
            )
        }

        hostOf(type)?.let { host ->
            if (parts.none { it.type == host }) {
                return InstallCheck.Blocked("Install the ${host.displayName.lowercase()} before the ${type.displayName.lowercase()}.")
            }
        }
        if (state.isInstalled(type)) {
            return InstallCheck.Blocked("A ${type.displayName.lowercase()} is already installed.")
        }

        val board = parts.firstOrNull { it.type == ComponentType.MOTHERBOARD }
        val slots = when (type) {
            ComponentType.RAM -> dualChannelSlots(board?.ramSlots ?: 0)
            ComponentType.SSD -> if ((board?.m2Slots ?: 0) > 0) listOf(0) else emptyList()
            else -> listOf(0)
        }
        if (slots.isEmpty()) return InstallCheck.Blocked("${board?.name} has no free ${type.displayName} slot.")

        // Only report issues that the new part(s) introduce.
        val before = validate(parts).issues.toSet()
        val introduced = validate(parts + List(slots.size) { candidate }).issues.filter { it !in before }
        if (state.mode == BuildMode.GUIDED) {
            introduced.firstOrNull { it.severity == Severity.ERROR }?.let { return InstallCheck.Blocked(it.message) }
        }
        return InstallCheck.Allowed(slots, introduced)
    }

    private fun error(msg: String, vararg parts: PCComponent?) = issue(Severity.ERROR, msg, parts)
    private fun warning(msg: String, vararg parts: PCComponent?) = issue(Severity.WARNING, msg, parts)
    private fun info(msg: String, vararg parts: PCComponent?) = issue(Severity.INFO, msg, parts)
    private fun issue(severity: Severity, msg: String, parts: Array<out PCComponent?>) =
        CompatibilityIssue(severity, msg, parts.filterNotNull().map { it.id })
}
