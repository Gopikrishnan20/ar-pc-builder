package com.arpcbuilder.domain.model

/**
 * Pure-Kotlin domain model. Nothing in `domain` may import Android, Compose or SceneView,
 * so the compatibility rules can be unit-tested on the JVM.
 */

enum class ComponentType(val displayName: String) {
    CASE("Case"),
    MOTHERBOARD("Motherboard"),
    CPU("Processor"),
    RAM("Memory"),
    SSD("NVMe SSD"),
    COOLER("CPU Cooler"),
    GPU("Graphics Card"),
    PSU("Power Supply"),
}

enum class SocketType { LGA1700, LGA1851, AM4, AM5 }

/** Ordered from largest to smallest, so "fits" means `board.ordinal >= case.ordinal`. */
enum class FormFactor(val displayName: String) {
    E_ATX("E-ATX"),
    ATX("ATX"),
    MICRO_ATX("Micro-ATX"),
    MINI_ITX("Mini-ITX");

    /** True if a board of this form factor physically fits a case whose largest supported board is [caseMax]. */
    fun fitsIn(caseMax: FormFactor): Boolean = ordinal >= caseMax.ordinal
}

enum class MemoryType { DDR4, DDR5 }

/** A simple 3-float vector in metres. Kept separate from SceneView's Float3 so the domain stays engine-free. */
data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    companion object {
        val ZERO = Vec3()
    }
}

/** Physical bounding box in metres; used for placeholder geometry and clearance checks. */
data class Dimension(val width: Float, val height: Float, val depth: Float)

/**
 * One purchasable part.
 *
 * Only the spec fields relevant to a [type] are filled in; the rest stay null.
 *
 * @param modelUrl glb path under `assets/` (e.g. "models/rtx4070.glb") or an https URL.
 *   When the file is missing the AR layer renders a coloured box of [dimension] instead.
 * @param wattage Typical power draw in watts. For a PSU this is its rated *capacity*.
 * @param snapPositionOffset Where this part mounts (its first slot), in the frame given by
 *   [SocketLayout.mountFrameOf]: the case for MOTHERBOARD/PSU, the motherboard for everything else.
 * @param hasRgb Whether the part has ARGB lighting that the boot simulation animates.
 */
data class PCComponent(
    val id: String,
    val type: ComponentType,
    val name: String,
    val modelUrl: String,
    val wattage: Int = 0,
    val socketType: SocketType? = null,
    val formFactor: FormFactor? = null,
    val memoryType: MemoryType? = null,
    val dimension: Dimension,
    val snapPositionOffset: Vec3 = Vec3.ZERO,
    val hasRgb: Boolean = false,
    // ---- type-specific specs ----
    /** MOTHERBOARD: number of DIMM slots. */
    val ramSlots: Int = 0,
    /** MOTHERBOARD: number of M.2 slots. */
    val m2Slots: Int = 0,
    /** CASE: longest graphics card that fits, in mm. */
    val maxGpuLengthMm: Int? = null,
    /** CASE: tallest air cooler that fits, in mm. */
    val maxCoolerHeightMm: Int? = null,
    /** GPU: card length in mm. COOLER: cooler height in mm. */
    val lengthMm: Int? = null,
    /** COOLER: heat it can dissipate, in watts. */
    val coolingCapacityW: Int? = null,
    /** COOLER: sockets its mounting kit supports. */
    val supportedSockets: Set<SocketType> = emptySet(),
)

/** A part that has been snapped in. [slotIndex] distinguishes RAM sticks / M.2 drives sharing a slot type. */
data class InstalledComponent(val component: PCComponent, val slotIndex: Int = 0)
