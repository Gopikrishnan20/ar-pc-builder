package com.arpcbuilder.ui.ar

import com.arpcbuilder.domain.engine.CompatibilityEngine
import com.arpcbuilder.domain.model.AssemblyState
import com.arpcbuilder.domain.model.AssemblyStep
import com.arpcbuilder.domain.model.ComponentType
import com.arpcbuilder.domain.model.Installation
import com.arpcbuilder.domain.model.PCComponent
import com.arpcbuilder.domain.model.SocketLayout
import com.arpcbuilder.domain.model.Vec3
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import io.github.sceneview.node.Node as SceneNode

/** Scale used for props that exist in the scene but must not be seen yet (screws, syringe, cable). */
const val HIDDEN = 0.001f

/**
 * Where each moving piece starts. The scene composes actors at these poses and the director
 * animates them from there, so the first frame of every animation matches what's on screen.
 * Positions are in the frame the actor lives in (BOARD frame for board parts, RIG otherwise).
 */
object Choreography {
    /** The motherboard drops onto the bench from this high. */
    val BOARD_DROP = Vec3(y = 0.22f)

    /** Board-mounted parts arrive along the board's normal (+X), from this far out. */
    fun approach(type: ComponentType): Vec3 = when (type) {
        ComponentType.CPU -> Vec3(x = 0.12f)
        ComponentType.COOLER -> Vec3(x = 0.22f)
        ComponentType.RAM -> Vec3(x = 0.11f)
        ComponentType.SSD -> Vec3(x = 0.03f, z = -0.03f)
        ComponentType.GPU -> Vec3(x = 0.22f)
        ComponentType.PSU -> Vec3(z = 0.35f)
        else -> Vec3.ZERO
    }

    /** M.2 drives go in at an angle, then get pressed flat. */
    const val SSD_TILT_DEG = 25f

    /** Screws start this far out along their axis, then spin in. */
    const val SCREW_TRAVEL = 0.03f
}

/** Nodes the director animates, registered by the scene as it composes them. */
class SceneRefs {
    var rig: SceneNode? = null
    var boardGroup: SceneNode? = null
    var caseGroup: SceneNode? = null

    /** Moving pieces and fixtures by name: "cpu", "lever", "latch_1", "screw_board_0", "bead_PCIE_7"… */
    val nodes = mutableMapOf<String, SceneNode>()

    /** Installed parts with their assembled position and explode offset, for the exploded view. */
    val explodable = mutableMapOf<String, Triple<SceneNode, Position, Position>>()

    operator fun get(name: String): SceneNode? = nodes[name]
}

/** What the director needs from the screen: captions for students and physical feedback. */
interface DirectorCallbacks {
    fun caption(text: String?)
    fun click()
    fun tick()
}

/**
 * Plays the install animation for each step: fly in close, show the physical motions a
 * technician makes, fly back out. Each script ends with every node exactly where the scene will
 * draw it once the install is committed, so nothing jumps when the animation hands over.
 */
class InstallDirector(
    private val refs: SceneRefs,
    private val camera: CameraFocus,
    private val cb: DirectorCallbacks,
) {
    suspend fun play(inst: Installation, state: AssemblyState) {
        try {
            when (inst.step) {
                AssemblyStep.MOTHERBOARD -> motherboard()
                AssemblyStep.CPU -> cpu(state.board!!, inst.component!!)
                AssemblyStep.CASE -> case()
                AssemblyStep.MOUNT_BOARD -> mountBoard(state.board!!)
                AssemblyStep.THERMAL_PASTE -> paste(state.board!!, state.first(ComponentType.CPU)!!)
                AssemblyStep.COOLER -> cooler(state.board!!, inst.component!!)
                AssemblyStep.RAM -> ram(state.board!!, inst.component!!, inst.slots)
                AssemblyStep.NVME -> ssd(state.board!!, inst.component!!)
                AssemblyStep.GPU -> gpu(state.board!!, inst.component!!)
                AssemblyStep.PSU -> psu(inst.component!!)
                AssemblyStep.CABLES -> cables()
                AssemblyStep.WORKBENCH, AssemblyStep.DONE -> Unit
            }
            if (camera.isFocused) camera.home()
        } finally {
            cb.caption(null)
        }
    }

    /** Explodes or reassembles every installed part at once. */
    suspend fun explode(out: Boolean) {
        val parts = refs.explodable.values.toList()
        tween(700, Ease::inOutCubic) { e ->
            val k = if (out) e else 1f - e
            parts.forEach { (node, base, offset) -> node.position = base + offset * k }
        }
    }

    // ------------------------------------------------------------------ steps

    private suspend fun motherboard() {
        val group = refs.boardGroup ?: return
        camera.focus(SocketLayout.BENCH_POSITION, viewNormal = null, distance = 0.55f)
        cb.caption("Lay the motherboard on its anti-static bag")
        group.moveTo(SocketLayout.BENCH_POSITION.toPosition(), 1000, Ease::outCubic)
        cb.click()
        pause(400)
    }

    private suspend fun cpu(board: PCComponent, cpu: PCComponent) {
        val node = refs["cpu"] ?: return
        val lever = refs["lever"]
        val mount = cpu.snapPositionOffset
        camera.focus(SocketLayout.boardToRig(mount, board, inCase = false), viewNormal = null, distance = 0.24f)

        cb.caption("Lift the socket's retention lever")
        lever?.rotateTo(Rotation(z = -100f), 500)
        pause(150)
        cb.caption("Line up the gold triangle and lower the CPU straight in. No force!")
        node.moveTo((mount + Vec3(x = 0.012f)).toPosition(), 900, Ease::outCubic)
        node.moveTo(mount.toPosition(), 350, Ease::inOutCubic)
        cb.click()
        pause(200)
        cb.caption("Close the lever to lock the CPU in")
        lever?.rotateTo(Rotation(0f, 0f, 0f), 500)
        cb.click()
        pause(500)
    }

    private suspend fun case() {
        val group = refs.caseGroup ?: return
        cb.caption("Here's the case. Next, the board goes inside")
        group.scaleTo(1f, 750, Ease::outBack)
        pause(500)
    }

    private suspend fun mountBoard(board: PCComponent) {
        val group = refs.boardGroup ?: return
        val inCase = board.snapPositionOffset.toPosition()
        camera.focus(Vec3(x = 0.18f, y = 0.24f), viewNormal = Vec3(x = 1f), distance = 0.95f, durationMs = 900)

        cb.caption("Lift the board, holding it by the edges")
        group.moveTo(group.position + Position(y = 0.18f), 600, Ease::inOutCubic)
        cb.caption("Tilt it upright and lower it onto the standoffs")
        val from = group.position
        val above = inCase + Position(y = 0.32f)
        tween(1200, Ease::inOutCubic) { e ->
            group.position = lerp(from, above, e)
            group.rotation = Rotation(z = lerp(SocketLayout.BENCH_ROLL_DEG, 0f, e))
        }
        group.moveTo(inCase, 800, Ease::outCubic)
        cb.click()

        camera.focus(SocketLayout.boardToRig(Vec3.ZERO, board, inCase = true), viewNormal = Vec3(x = 1f), distance = 0.6f)
        cb.caption("Screw the board to the standoffs. Snug, not tight")
        val screws = SocketLayout.boardScrews(board)
        coroutineScope {
            screws.indices.forEach { i ->
                launch {
                    pause(i * 220L)
                    driveScrew("screw_board_$i", screws[i])
                }
            }
        }
        pause(400)
    }

    private suspend fun paste(board: PCComponent, cpu: PCComponent) {
        val syringe = refs["syringe"] ?: return
        val dot = refs["paste"] ?: return
        val mount = cpu.snapPositionOffset
        camera.focus(SocketLayout.boardToRig(mount, board, inCase = true), viewNormal = Vec3(x = 1f), distance = 0.24f)

        cb.caption("A pea-sized dot in the centre is all it takes")
        syringe.scaleTo(1f, 200)
        syringe.moveTo((mount + Vec3(x = 0.03f)).toPosition(), 600, Ease::outCubic)
        coroutineScope {
            launch { dot.scaleTo(1f, 800, Ease::outCubic) }
            launch { syringe.moveTo((mount + Vec3(x = 0.045f)).toPosition(), 800, Ease::linear) }
        }
        cb.caption("The cooler's pressure will spread it into a thin, even layer")
        syringe.moveTo((mount + Vec3(x = 0.14f)).toPosition(), 500, Ease::inCubic)
        syringe.scaleTo(HIDDEN, 150)
        pause(400)
    }

    private suspend fun cooler(board: PCComponent, cooler: PCComponent) {
        val node = refs["cooler"] ?: return
        val mount = cooler.snapPositionOffset
        camera.focus(SocketLayout.boardToRig(mount, board, inCase = true), viewNormal = Vec3(x = 1f), distance = 0.45f)

        cb.caption("Fan facing the front, so air flows toward the rear exhaust")
        node.moveTo((mount + Vec3(x = 0.015f)).toPosition(), 1000, Ease::outCubic)
        node.moveTo(mount.toPosition(), 300, Ease::inOutCubic)
        cb.click()
        cb.caption("Tighten the mounting screws a little at a time, in a diagonal pattern")
        repeat(4) {
            pause(260)
            cb.tick()
        }
        pause(400)
    }

    private suspend fun ram(board: PCComponent, ram: PCComponent, slots: List<Int>) {
        val centre = slots.map { SocketLayout.mountPosition(ram, it) }.reduce { a, b -> a + b } * (1f / slots.size)
        camera.focus(SocketLayout.boardToRig(centre, board, inCase = true), viewNormal = Vec3(x = 1f), distance = 0.3f)

        cb.caption("Open the latches on slots ${slots.joinToString(" and ") { CompatibilityEngine.slotLabel(it) }}")
        coroutineScope { slots.forEach { s -> launch { refs["latch_$s"]?.rotateTo(Rotation(z = 40f), 350) } } }
        for (slot in slots) {
            val stick = refs["ram_$slot"] ?: continue
            val mount = SocketLayout.mountPosition(ram, slot)
            cb.caption("Line up the notch, then press down firmly until the latch clicks")
            stick.moveTo((mount + Vec3(x = 0.006f)).toPosition(), 700, Ease::outCubic)
            stick.moveTo(mount.toPosition(), 220, Ease::inCubic)
            refs["latch_$slot"]?.rotateTo(Rotation(0f, 0f, 0f), 110, Ease::linear)
            cb.click()
            pause(250)
        }
        cb.caption("Dual channel: the two sticks share the load")
        pause(600)
    }

    private suspend fun ssd(board: PCComponent, ssd: PCComponent) {
        val node = refs["ssd"] ?: return
        val mount = ssd.snapPositionOffset
        camera.focus(SocketLayout.boardToRig(mount, board, inCase = true), viewNormal = Vec3(x = 1f), distance = 0.26f)

        cb.caption("Slide the drive into the M.2 slot at an angle")
        node.moveTo((mount + Vec3(x = 0.01f)).toPosition(), 700, Ease::outCubic)
        cb.caption("Press it flat")
        coroutineScope {
            launch { node.rotateTo(Rotation(0f, 0f, 0f), 450) }
            launch { node.moveTo(mount.toPosition(), 450) }
        }
        cb.click()
        cb.caption("One small screw holds it down")
        driveScrew("screw_m2", SocketLayout.m2Screw(ssd))
        pause(400)
    }

    private suspend fun gpu(board: PCComponent, gpu: PCComponent) {
        val node = refs["gpu"] ?: return
        val mount = gpu.snapPositionOffset
        camera.focus(SocketLayout.boardToRig(mount, board, inCase = true), viewNormal = Vec3(x = 1f), distance = 0.55f)

        refs["pcie_cover"]?.let { cover ->
            cb.caption("Remove the slot cover on the back of the case")
            coroutineScope {
                launch { cover.moveTo(cover.position + Position(z = 0.05f, y = 0.02f), 450) }
                launch { cover.scaleTo(HIDDEN, 450, Ease::inCubic) }
            }
        }
        cb.caption("Line the card up with the top PCIe x16 slot")
        node.moveTo((mount + Vec3(x = 0.012f)).toPosition(), 950, Ease::outCubic)
        cb.caption("Push it in evenly until the slot's clip clicks")
        node.moveTo(mount.toPosition(), 260, Ease::inCubic)
        cb.click()
        cb.caption("Screw the bracket to the case so the card can't sag")
        refs["screw_gpu"]?.let { driveScrew("screw_gpu", null) }
        pause(400)
    }

    private suspend fun psu(psu: PCComponent) {
        val node = refs["psu"] ?: return
        val mount = psu.snapPositionOffset
        camera.focus(mount, viewNormal = Vec3(z = 1f), distance = 0.55f)

        cb.caption("Slide the PSU in from the back, fan facing down")
        node.moveTo(mount.toPosition(), 1100, Ease::outCubic)
        cb.click()
        cb.caption("Fan down: it breathes cool air through the bottom filter")
        val screws = SocketLayout.psuScrews(psu)
        coroutineScope {
            screws.indices.forEach { i ->
                launch {
                    pause(i * 250L)
                    driveScrew("screw_psu_$i", null)
                }
            }
        }
        pause(400)
    }

    private suspend fun cables() {
        camera.focus(Vec3(x = -0.02f, y = 0.2f), viewNormal = Vec3(x = 1f), distance = 0.75f)
        for (cable in SocketLayout.Cable.entries) {
            cb.caption("Connect the ${cable.label} cable")
            runCable(cable)
            cb.click()
            pause(200)
        }
        cb.caption("Everything has power. Time to switch it on!")
        pause(700)
    }

    // ------------------------------------------------------------------ building blocks

    /**
     * Screws appear above their hole, then travel in while spinning three turns. [hole] is
     * where the screw ends up in its parent's frame; null uses the composed position, already
     * set back by [Choreography.SCREW_TRAVEL] along its axis.
     */
    private suspend fun driveScrew(name: String, hole: Vec3?) {
        val screw = refs[name] ?: return
        val spin = refs["${name}_spin"]
        val start = screw.position
        val end = hole?.toPosition() ?: (start + screwAxis(name) * -Choreography.SCREW_TRAVEL)
        screw.scaleTo(1f, 150)
        var lastTurn = 0
        tween(650, Ease::outCubic) { e ->
            screw.position = lerp(start, end, e)
            spin?.rotation = Rotation(y = e * 1080f)
            val turn = (e * 3).toInt()
            if (turn > lastTurn) {
                lastTurn = turn
                cb.tick()
            }
        }
    }

    /** Unit axis a screw travels along in its parent frame: board screws face +X, rear-panel screws +Z. */
    private fun screwAxis(name: String) = if (name == "screw_gpu" || name.startsWith("screw_psu")) Position(z = 1f) else Position(x = 1f)

    /** Grows a cable bead by bead along its route, with the plug riding at the front. */
    private suspend fun runCable(cable: SocketLayout.Cable) {
        val beads = (0 until CABLE_BEADS).mapNotNull { refs["bead_${cable.name}_$it"] }
        val plug = refs["plug_${cable.name}"]
        plug?.scaleTo(1f, 120)
        tween(900, Ease::inOutCubic) { e ->
            val shown = (e * beads.size).toInt()
            beads.forEachIndexed { i, b -> b.scale = Scale(if (i < shown) 1f else HIDDEN) }
            beads.getOrNull((shown - 1).coerceAtLeast(0))?.let { head -> plug?.position = head.position }
        }
        beads.forEach { it.scale = Scale(1f) }
        beads.lastOrNull()?.let { plug?.position = it.position }
    }

    companion object {
        const val CABLE_BEADS = 22
    }
}
