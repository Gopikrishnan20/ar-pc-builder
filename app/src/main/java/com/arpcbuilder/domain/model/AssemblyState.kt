package com.arpcbuilder.domain.model

enum class BuildMode { GUIDED, SANDBOX }

/**
 * The build order, the way a technician does it: the CPU goes into the motherboard on the bench,
 * then the whole board goes into the case. Steps with a [componentType] start by choosing that
 * part; the others are a single action.
 */
enum class AssemblyStep(
    val title: String,
    val actionLabel: String,
    val instruction: String,
    val why: String,
    val componentType: ComponentType?,
) {
    WORKBENCH(
        "Workbench", "Create AR table",
        "Point the phone at an open space and press Create AR table.",
        "Build on a hard surface, never carpet: carpet builds up static that can kill parts.",
        null,
    ),
    MOTHERBOARD(
        "Motherboard", "Choose motherboard",
        "Pick a motherboard. It's laid on its anti-static bag on the bench.",
        "Everything plugs into the motherboard, so it decides which CPU, memory and case you can use.",
        ComponentType.MOTHERBOARD,
    ),
    CPU(
        "Processor", "Install processor",
        "Pick a CPU to drop into the socket.",
        "The CPU goes in on the bench, where the board is flat and easy to reach.",
        ComponentType.CPU,
    ),
    CASE(
        "Case", "Choose case",
        "Pick a case for the board to live in.",
        "The case has to match the board's size: an ATX board won't fit a Mini-ITX case.",
        ComponentType.CASE,
    ),
    MOUNT_BOARD(
        "Into the case", "Install motherboard in case",
        "Lower the motherboard, with its CPU, onto the case's standoffs and screw it down.",
        "Standoffs hold the board off the metal case so its solder joints can't short out.",
        null,
    ),
    THERMAL_PASTE(
        "Thermal paste", "Apply thermal paste",
        "Squeeze a pea-sized dot of paste onto the centre of the CPU.",
        "Paste fills microscopic gaps between the CPU and cooler. Without it the CPU overheats.",
        null,
    ),
    COOLER(
        "CPU cooler", "Install CPU cooler",
        "Pick a cooler to mount on the CPU.",
        "The cooler presses the paste into a thin layer and carries the CPU's heat away.",
        ComponentType.COOLER,
    ),
    RAM(
        "Memory", "Install RAM",
        "Pick a memory kit. Two sticks go into slots A2 and B2.",
        "A2 and B2 run the sticks in dual channel, nearly doubling memory bandwidth.",
        ComponentType.RAM,
    ),
    NVME(
        "Storage", "Install NVMe SSD",
        "Pick an SSD for the M.2 slot.",
        "M.2 drives connect straight to the CPU's PCIe lanes, many times faster than SATA.",
        ComponentType.SSD,
    ),
    GPU(
        "Graphics card", "Install graphics card",
        "Pick a graphics card for the top PCIe x16 slot.",
        "The top x16 slot is wired straight to the CPU and gets all 16 lanes.",
        ComponentType.GPU,
    ),
    PSU(
        "Power supply", "Install power supply",
        "Pick a power supply for the bottom of the case.",
        "The PSU turns wall AC into the 12 V, 5 V and 3.3 V DC the parts run on.",
        ComponentType.PSU,
    ),
    CABLES(
        "Cables", "Connect cables",
        "Connect the CPU fan, 24-pin, 8-pin CPU and GPU power cables.",
        "Each part needs its own power: the CPU alone can draw over 200 W through its 8-pin cable.",
        null,
    ),
    DONE(
        "Ready", "Power on",
        "Everything is installed. Press the power button!",
        "When the PC powers on, the BIOS runs a self-test (POST) before booting.",
        null,
    );

    val number: Int get() = ordinal + 1

    companion object {
        /** Steps a student performs (DONE isn't one). */
        val workSteps = entries.filter { it != DONE }
    }
}

enum class Severity { INFO, WARNING, ERROR }

data class CompatibilityIssue(
    val severity: Severity,
    val message: String,
    val componentIds: List<String> = emptyList(),
)

data class CompatibilityReport(val issues: List<CompatibilityIssue> = emptyList()) {
    val hasErrors: Boolean get() = issues.any { it.severity == Severity.ERROR }
    val hasWarnings: Boolean get() = issues.any { it.severity == Severity.WARNING }
    val isCompatible: Boolean get() = !hasErrors
}

/** Power budget summary. [headroomPercent] is how much spare capacity the PSU has. */
data class PowerBudget(
    val totalDrawW: Int,
    val psuCapacityW: Int?,
) {
    val headroomPercent: Int?
        get() = psuCapacityW?.takeIf { it > 0 }?.let { ((it - totalDrawW) * 100) / it }
}

/**
 * An install that has been approved and is now playing its animation. The AR layer plays it and
 * then reports back, which commits it to [AssemblyState.installed].
 *
 * @param component the part going in, or null for action steps (mount board, paste, cables).
 * @param slots slot indices it occupies: two DIMM slots for a RAM kit, otherwise `[0]`.
 */
data class Installation(
    val step: AssemblyStep,
    val component: PCComponent?,
    val slots: List<Int> = listOf(0),
)

/**
 * Everything the UI needs to render the build. Produced by the ViewModel; never mutated in place.
 */
data class AssemblyState(
    val mode: BuildMode = BuildMode.GUIDED,
    val installed: List<InstalledComponent> = emptyList(),
    val currentStep: AssemblyStep = AssemblyStep.WORKBENCH,
    val compatibility: CompatibilityReport = CompatibilityReport(),
    val power: PowerBudget = PowerBudget(0, null),
    val workbenchPlaced: Boolean = false,
    val boardInCase: Boolean = false,
    val thermalPasteApplied: Boolean = false,
    val cablesConnected: Boolean = false,
    /** Non-null while an install animation plays; the UI blocks other actions meanwhile. */
    val installing: Installation? = null,
    val exploded: Boolean = false,
    val poweredOn: Boolean = false,
) {
    val case: PCComponent? get() = first(ComponentType.CASE)
    val board: PCComponent? get() = first(ComponentType.MOTHERBOARD)

    fun first(type: ComponentType): PCComponent? = installed.firstOrNull { it.component.type == type }?.component

    fun installedOf(type: ComponentType): List<InstalledComponent> = installed.filter { it.component.type == type }

    fun isInstalled(type: ComponentType): Boolean = installed.any { it.component.type == type }

    /** First step not done yet, following the build order. */
    fun firstIncompleteStep(): AssemblyStep = AssemblyStep.workSteps.firstOrNull { !isDone(it) } ?: AssemblyStep.DONE

    fun isDone(step: AssemblyStep): Boolean = when (step) {
        AssemblyStep.WORKBENCH -> workbenchPlaced
        AssemblyStep.MOUNT_BOARD -> boardInCase
        AssemblyStep.THERMAL_PASTE -> thermalPasteApplied
        AssemblyStep.CABLES -> cablesConnected
        AssemblyStep.DONE -> false
        else -> isInstalled(step.componentType!!)
    }

    val isComplete: Boolean get() = firstIncompleteStep() == AssemblyStep.DONE

    /** Work steps finished so far, for the progress bar. */
    val stepsDone: Int get() = AssemblyStep.workSteps.count(::isDone)
}
