package com.arpcbuilder.domain.model

/**
 * Where everything sits, independent of any 3D engine. Metres, Y up.
 *
 * Frames:
 * - RIG frame: origin on the table where the student tapped. The case stands with its floor
 *   centre here; the workbench is beside it at [BENCH_POSITION].
 * - BOARD frame: origin at the motherboard centre; the component side faces +X, Y runs along the
 *   board's height (top edge at +Y), Z along its width (rear I/O at -Z, 24-pin edge at +Z).
 *
 * On the bench the board lies flat (its +X turned to face up); in the case it stands upright at
 * the board's [PCComponent.snapPositionOffset].
 */
object SocketLayout {

    /** Where the motherboard lies on the workbench, beside the case. */
    val BENCH_POSITION = Vec3(x = 0.42f, y = 0.006f, z = 0f)

    /** Roll about Z that lays the board flat, component side up. */
    const val BENCH_ROLL_DEG = 90f

    /** The frame a part's [PCComponent.snapPositionOffset] is expressed in. */
    fun mountFrameOf(type: ComponentType): ComponentType? = when (type) {
        ComponentType.CASE -> null
        ComponentType.MOTHERBOARD, ComponentType.PSU -> ComponentType.CASE
        else -> ComponentType.MOTHERBOARD
    }

    /** Distance between neighbouring DIMM slots. */
    private val RAM_SLOT_STEP = Vec3(z = 0.009f)

    /** Final position of a part in its mount frame. */
    fun mountPosition(component: PCComponent, slotIndex: Int): Vec3 = when (component.type) {
        ComponentType.RAM -> component.snapPositionOffset + RAM_SLOT_STEP * slotIndex.toFloat()
        else -> component.snapPositionOffset
    }

    /**
     * Converts a BOARD-frame point to the RIG frame, for the board on the bench or in the case.
     * On the bench a +90° roll about Z maps board (x, y, z) to (−y, x, z).
     */
    fun boardToRig(v: Vec3, board: PCComponent, inCase: Boolean): Vec3 =
        if (inCase) board.snapPositionOffset + v else BENCH_POSITION + Vec3(-v.y, v.x, v.z)

    /** Board-frame direction to rig frame (rotation only). */
    fun boardDirToRig(v: Vec3, inCase: Boolean): Vec3 = if (inCase) v else Vec3(-v.y, v.x, v.z)

    /** How far each part moves in the exploded view, in its own mount frame. */
    fun explodeOffset(type: ComponentType): Vec3 = when (type) {
        ComponentType.CASE -> Vec3.ZERO
        ComponentType.MOTHERBOARD -> Vec3(x = -0.12f)
        ComponentType.CPU -> Vec3(x = 0.05f)
        ComponentType.COOLER -> Vec3(x = 0.22f)
        ComponentType.RAM -> Vec3(x = 0.12f, y = 0.05f)
        ComponentType.SSD -> Vec3(x = 0.08f, y = -0.03f)
        ComponentType.GPU -> Vec3(x = 0.18f, y = -0.10f)
        ComponentType.PSU -> Vec3(y = -0.02f, z = 0.25f)
    }

    // ------------------------------------------------------------------ fixtures (BOARD frame unless noted)

    /** CPU socket retention lever, beside the socket. */
    val CPU_LEVER = Vec3(0.008f, 0.06f, -0.004f)

    /** Top PCIe x16 slot: where every graphics card's [PCComponent.snapPositionOffset] points. */
    val PCIE_X16 = Vec3(x = 0.065f, y = -0.06f, z = 0.03f)

    /** DIMM slot rails. */
    const val RAM_SLOT_LENGTH = 0.133f
    fun ramSlotCentre(slot: Int) = Vec3(0.003f, 0.06f, 0.04f) + RAM_SLOT_STEP * slot.toFloat()

    /** Latch at the top end of DIMM slot [slot]. */
    fun ramLatch(slot: Int) = ramSlotCentre(slot) + Vec3(x = 0.003f, y = RAM_SLOT_LENGTH / 2f + 0.004f)

    /** The four corner screws that hold the board to the standoffs. */
    fun boardScrews(board: PCComponent): List<Vec3> {
        val h = board.dimension.height / 2f - 0.012f
        val w = board.dimension.depth / 2f - 0.012f
        return listOf(Vec3(0.004f, h, -w), Vec3(0.004f, h, w), Vec3(0.004f, -h, -w), Vec3(0.004f, -h, w))
    }

    /** M.2 retaining screw at the far end of the SSD. */
    fun m2Screw(ssd: PCComponent) = ssd.snapPositionOffset + Vec3(x = 0.003f, z = ssd.dimension.depth / 2f + 0.004f)

    /** Cooler mounting screws, diagonal corners of the CPU. */
    fun coolerScrews(cooler: PCComponent): List<Vec3> {
        val base = cooler.snapPositionOffset.copy(x = 0.012f)
        return listOf(base + Vec3(y = 0.045f, z = 0.045f), base + Vec3(y = -0.045f, z = -0.045f))
    }

    // Case-frame fixtures. The GPU's bracket is on the rear panel (+Z) at the GPU's height.

    /** Expansion-slot cover on the rear panel (RIG frame). */
    fun pcieCover(case: PCComponent, board: PCComponent) =
        (board.snapPositionOffset + PCIE_X16).copy(z = case.dimension.depth / 2f + 0.002f)

    /** Screw holding the GPU bracket to the rear panel (RIG frame). */
    fun gpuScrew(case: PCComponent, board: PCComponent) = pcieCover(case, board) + Vec3(y = 0.03f, z = 0.004f)

    /** PSU screws on the rear panel (RIG frame). */
    fun psuScrews(psu: PCComponent): List<Vec3> {
        val back = psu.snapPositionOffset + Vec3(z = psu.dimension.depth / 2f + 0.004f)
        return listOf(back + Vec3(x = -0.06f, y = 0.03f), back + Vec3(x = 0.06f, y = -0.03f))
    }

    // ------------------------------------------------------------------ cables (RIG frame, board in case)

    enum class Cable(val label: String) {
        CPU_FAN("CPU fan"),
        ATX_24PIN("24-pin motherboard"),
        EPS_8PIN("8-pin CPU"),
        PCIE("GPU power"),
    }

    /** A cable route: from [from] to [to], bulging through [via] so it doesn't cut through parts. */
    data class CableRoute(val cable: Cable, val from: Vec3, val via: Vec3, val to: Vec3)

    fun cableRoutes(board: PCComponent, cooler: PCComponent, gpu: PCComponent, psu: PCComponent): List<CableRoute> {
        val b = board.snapPositionOffset
        val psuFront = psu.snapPositionOffset + Vec3(x = psu.dimension.width / 2f, y = 0.02f)
        fun route(c: Cable, from: Vec3, to: Vec3, bulge: Vec3) = CableRoute(c, from, (from + to) * 0.5f + bulge, to)

        val fan = b + cooler.snapPositionOffset + Vec3(x = 0.02f, y = 0.075f)
        val fanHeader = b + Vec3(0.008f, 0.135f, -0.03f)
        val atx = b + Vec3(0.008f, 0.03f, board.dimension.depth / 2f - 0.006f)
        val eps = b + Vec3(0.008f, board.dimension.height / 2f - 0.01f, -0.08f)
        val pcie = b + gpu.snapPositionOffset + Vec3(x = gpu.dimension.width / 2f, z = 0.06f)
        return listOf(
            route(Cable.CPU_FAN, fan, fanHeader, Vec3(x = 0.03f)),
            route(Cable.ATX_24PIN, psuFront + Vec3(z = -0.03f), atx, Vec3(x = 0.06f, z = 0.04f)),
            route(Cable.EPS_8PIN, psuFront + Vec3(z = 0.00f), eps, Vec3(x = 0.08f, z = -0.05f)),
            route(Cable.PCIE, psuFront + Vec3(z = 0.03f), pcie, Vec3(x = 0.08f)),
        )
    }

    /** Point [t] (0..1) along a cable route (quadratic Bézier). */
    fun pointOn(r: CableRoute, t: Float): Vec3 {
        val u = 1f - t
        return r.from * (u * u) + r.via * (2f * u * t) + r.to * (t * t)
    }
}
