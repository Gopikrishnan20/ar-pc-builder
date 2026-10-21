package com.arpcbuilder.ui.ar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Expand
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arpcbuilder.domain.model.AssemblyState
import com.arpcbuilder.domain.model.AssemblyStep
import com.arpcbuilder.domain.model.ComponentType
import com.arpcbuilder.domain.model.PCComponent
import com.arpcbuilder.domain.model.SocketLayout
import com.arpcbuilder.domain.model.Vec3
import com.arpcbuilder.ui.components.BiosScreen
import com.arpcbuilder.ui.components.ComponentSheet
import com.arpcbuilder.ui.components.HudOverlay
import com.arpcbuilder.ui.theme.ARPCBuilderTheme
import com.arpcbuilder.ui.theme.StatusGreen
import com.arpcbuilder.ui.theme.StatusRed
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.TrackingFailureReason
import io.github.sceneview.SceneScope
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberViewNodeManager
import kotlinx.coroutines.launch
import io.github.sceneview.node.ViewNode as ViewNodeImpl

/**
 * Caption text lives in its own state holder and is read only by [CaptionCard], so changing it
 * mid-animation redraws the card alone, not the AR scene.
 */
@Stable
private class CaptionState {
    var text by mutableStateOf<String?>(null)
}

@Composable
fun ARBuilderScreen(viewModel: BuilderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val latestState by rememberUpdatedState(state)

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val feedback = remember { SnapFeedback() }
    DisposableEffect(Unit) { onDispose { feedback.release() } }

    // Which glb files actually ship; everything else falls back to placeholder boxes.
    val availableModels = remember {
        context.assets.list("models").orEmpty().filter { it.endsWith(".glb") }.map { "models/$it" }.toSet()
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val materials = remember(materialLoader) { PartMaterials(materialLoader) }
    val viewNodeManager = rememberViewNodeManager()
    val refs = remember { SceneRefs() }
    val caption = remember { CaptionState() }

    var anchor by remember { mutableStateOf<Anchor?>(null) }
    // The latest AR frame, written every tick. A plain field, not Compose state: it must never
    // trigger recomposition, and it must always hold the newest frame for taps and the camera focus.
    val frames = remember { FrameHolder() }
    var trackingHint by remember { mutableStateOf<String?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var exploding by remember { mutableStateOf(false) }

    val director = remember {
        InstallDirector(
            refs = refs,
            camera = CameraFocus(rig = { refs.rig }, anchor = { anchor }, frame = { frames.latest }),
            cb = object : DirectorCallbacks {
                override fun caption(text: String?) {
                    caption.text = text
                }

                override fun click() = feedback.click(view)
                override fun tick() = feedback.tick(view)
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            if (m.isError) feedback.reject(view)
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(m.text)
        }
    }

    // Play each approved install, then commit it. The frame wait lets the scene compose the
    // actors (and register their nodes) before the first animation frame.
    LaunchedEffect(state.installing) {
        val inst = state.installing ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        director.play(inst, latestState)
        viewModel.completeInstall()
    }

    // A reset or mode switch clears the build: drop the anchor so a new table can be created.
    LaunchedEffect(state.workbenchPlaced) {
        if (!state.workbenchPlaced) {
            anchor?.detach()
            anchor = null
            refs.nodes.clear()
            refs.explodable.clear()
        }
    }

    /**
     * Puts a virtual workbench table in front of the student. It doesn't need ARCore to find a
     * real surface first: the anchor is created from the camera pose, so it works on any phone,
     * on a blank desk or in a classroom. The table faces the student, ~75 cm ahead and at a
     * comfortable working height below the phone.
     */
    fun createTable() {
        val session = frames.session
        val f = frames.latest
        if (session == null || f == null || f.camera.trackingState != TrackingState.TRACKING) {
            feedback.reject(view)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("AR is still starting. Move the phone slowly for a second, then try again.")
            }
            return
        }
        val cam = f.camera.pose
        val z = cam.zAxis
        // Camera looks down -Z. Flatten it so the table stands level whatever the phone's tilt.
        var fx = -z[0]
        var fz = -z[2]
        val len = kotlin.math.sqrt(fx * fx + fz * fz).takeIf { it > 1e-3f } ?: 1f
        fx /= len
        fz /= len
        val t = cam.translation
        val position = floatArrayOf(t[0] + fx * TABLE_DISTANCE_M, t[1] - TABLE_DROP_M, t[2] + fz * TABLE_DISTANCE_M)
        // Turn the table so its local +Z points away from the student (its front faces them).
        val half = kotlin.math.atan2(fx, fz) / 2f
        val rotation = floatArrayOf(0f, kotlin.math.sin(half), 0f, kotlin.math.cos(half))
        anchor = runCatching { session.createAnchor(Pose(position, rotation)) }.getOrNull() ?: return
        viewModel.onWorkbenchPlaced()
        feedback.click(view)
    }

    Box(Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            planeRenderer = false,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
            sessionConfiguration = { _, config ->
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            viewNodeWindowManager = viewNodeManager,
            onTrackingFailureChanged = { reason -> trackingHint = reason?.let(::trackingMessage) },
            onSessionUpdated = { session, f ->
                frames.session = session
                frames.latest = f
            },
        ) {
            anchor?.let { a ->
                AnchorNode(anchor = a) {
                    // The table stays put when the build flies up for a close-up.
                    WorkbenchTable(materials)
                    BuildRig(state, availableModels, materials, refs, viewNodeManager)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(state.installing == null, enter = fadeIn(), exit = fadeOut()) {
                HudOverlay(state = state, onModeChange = viewModel::setMode)
            }
            val hint = trackingHint ?: if (!state.workbenchPlaced) AssemblyStep.WORKBENCH.instruction else null
            AnimatedVisibility(hint != null, enter = fadeIn(), exit = fadeOut()) {
                InfoCard(hint.orEmpty())
            }
            CaptionCard(caption)
        }

        BottomControls(
            state = state,
            exploding = exploding,
            onPrimary = {
                when {
                    state.currentStep == AssemblyStep.WORKBENCH -> createTable()
                    state.currentStep.componentType != null -> showSheet = true
                    state.currentStep == AssemblyStep.DONE -> viewModel.togglePower()
                    else -> viewModel.startAction()
                }
            },
            onExplode = {
                val out = !state.exploded
                exploding = true
                scope.launch {
                    director.explode(out)
                    viewModel.setExploded(out)
                    exploding = false
                }
            },
            onPower = viewModel::togglePower,
            onReset = viewModel::reset,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp),
        )

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
    }

    if (showSheet) {
        val type = state.currentStep.componentType
        if (type != null) {
            ComponentSheet(
                type = type,
                catalog = catalog,
                state = state,
                onSelect = {
                    showSheet = false
                    viewModel.choose(it)
                },
                onDismiss = { showSheet = false },
            )
        }
    }
}

private class FrameHolder {
    var latest: Frame? = null
    var session: Session? = null
}

/** How far ahead of the phone the table appears, and how far below it its top sits. */
private const val TABLE_DISTANCE_M = 0.75f
private const val TABLE_DROP_M = 0.45f

/**
 * Everything on the table. The rig is the root the camera focus moves; inside it the case stands
 * at the origin and the motherboard group sits on the bench or in the case, carrying every part
 * plugged into it. Actors for the install in progress are composed at their animation start pose.
 */
@Composable
private fun SceneScope.BuildRig(
    state: AssemblyState,
    availableModels: Set<String>,
    materials: PartMaterials,
    refs: SceneRefs,
    viewNodeManager: ViewNodeImpl.WindowManager,
) {
    val inst = state.installing
    val step = inst?.step
    val k = if (state.exploded) 1f else 0f
    fun register(name: String) = { n: io.github.sceneview.node.Node -> refs.nodes[name] = n }

    Node(apply = { refs.rig = this }) {
        // ---------------- case
        val case = state.case ?: inst?.component?.takeIf { step == AssemblyStep.CASE }
        if (case != null) {
            Node(scale = Scale(if (step == AssemblyStep.CASE) HIDDEN else 1f), apply = { refs.caseGroup = this }) {
                if (case.modelUrl in availableModels) {
                    PartNode(case, Position(y = case.dimension.height / 2f), availableModels, materials, rgbOn = state.poweredOn)
                } else {
                    ChassisPlaceholder(case, state.poweredOn, materials) {}
                }
                CaseFixtures(state, case, availableModels, materials, refs, register = ::register)
            }
        }

        // ---------------- motherboard group
        val board = state.board ?: inst?.component?.takeIf { step == AssemblyStep.MOTHERBOARD }
        if (board != null) {
            val onBench = !state.boardInCase
            val basePos = if (onBench) SocketLayout.BENCH_POSITION else board.snapPositionOffset
            val pos = when {
                step == AssemblyStep.MOTHERBOARD -> SocketLayout.BENCH_POSITION + Choreography.BOARD_DROP
                onBench -> basePos
                else -> basePos + SocketLayout.explodeOffset(ComponentType.MOTHERBOARD) * k
            }
            Node(
                position = pos.toPosition(),
                rotation = Rotation(z = if (onBench) SocketLayout.BENCH_ROLL_DEG else 0f),
                apply = { refs.boardGroup = this },
            ) {
                BoardContents(state, board, k, availableModels, materials, refs, register = ::register)
            }
            // The board only explodes out of the case once it's in it.
            SideEffect {
                val group = refs.boardGroup
                if (!onBench && group != null) {
                    refs.explodable["board"] = Triple(group, basePos.toPosition(), SocketLayout.explodeOffset(ComponentType.MOTHERBOARD).toPosition())
                } else {
                    refs.explodable.remove("board")
                }
            }
        }

        // ---------------- cables (rig frame, board in case)
        val cooler = state.first(ComponentType.COOLER)
        val gpu = state.first(ComponentType.GPU)
        val psu = state.first(ComponentType.PSU)
        if (state.board != null && cooler != null && gpu != null && psu != null && (state.cablesConnected || step == AssemblyStep.CABLES) && !state.exploded) {
            SocketLayout.cableRoutes(state.board!!, cooler, gpu, psu).forEach { route ->
                key(route.cable, state.cablesConnected) {
                    Cable(
                        route, InstallDirector.CABLE_BEADS, visible = state.cablesConnected, materials = materials,
                        onBead = { i, n -> refs.nodes["bead_${route.cable.name}_$i"] = n },
                        onPlug = register("plug_${route.cable.name}"),
                    )
                }
            }
        }

        if (state.poweredOn && case != null) Monitor(state, case, materials, viewNodeManager)
    }
}

/** Parts and fixtures mounted on the motherboard, in the BOARD frame. */
@Composable
private fun SceneScope.BoardContents(
    state: AssemblyState,
    board: PCComponent,
    k: Float,
    availableModels: Set<String>,
    materials: PartMaterials,
    refs: SceneRefs,
    register: (String) -> (io.github.sceneview.node.Node) -> Unit,
) {
    val inst = state.installing
    val step = inst?.step
    PartNode(board, Position(0f, 0f, 0f), availableModels, materials, rgbOn = state.poweredOn)
    if (!state.boardInCase) {
        // The anti-static bag the board rests on.
        CubeNode(Size(0.002f, board.dimension.height + 0.04f, board.dimension.depth + 0.04f), materialInstance = materials.bag, position = Position(x = -0.004f))
    }

    Lever(SocketLayout.CPU_LEVER.toPosition(), materials, register("lever"))
    for (slot in 0 until board.ramSlots) {
        RamSlot(SocketLayout.ramSlotCentre(slot).toPosition(), SocketLayout.ramLatch(slot).toPosition(), SocketLayout.RAM_SLOT_LENGTH, materials, register("latch_$slot"))
    }

    // Installed parts on the board.
    state.installed.filter { SocketLayout.mountFrameOf(it.component.type) == ComponentType.MOTHERBOARD }.forEach { ic ->
        key(ic.component.id, ic.slotIndex) {
            val base = SocketLayout.mountPosition(ic.component, ic.slotIndex)
            val offset = SocketLayout.explodeOffset(ic.component.type)
            PartNode(
                component = ic.component,
                position = (base + offset * k).toPosition(),
                availableModels = availableModels,
                materials = materials,
                rgbOn = state.poweredOn,
                onNode = { refs.explodable["${ic.component.type}_${ic.slotIndex}"] = Triple(it, base.toPosition(), offset.toPosition()) },
            )
        }
    }

    // Fixtures that come with installed parts. Hidden in the exploded view, where they'd float.
    if (!state.exploded) {
        if (state.boardInCase) {
            SocketLayout.boardScrews(board).forEach { Screw(it.toPosition(), axisX = true, materials = materials) }
        }
        state.installedOf(ComponentType.SSD).firstOrNull()?.let { Screw(SocketLayout.m2Screw(it.component).toPosition(), axisX = true, materials = materials) }
        val cpu = state.first(ComponentType.CPU)
        if (cpu != null && state.thermalPasteApplied && !state.isInstalled(ComponentType.COOLER) && step != AssemblyStep.COOLER) {
            PasteDot((cpu.snapPositionOffset + Vec3(x = 0.003f)).toPosition(), 1f, materials)
        }
    }

    // Actors for the install in progress, at their animation start poses.
    val c = inst?.component
    val travel = Choreography.SCREW_TRAVEL
    when (step) {
        AssemblyStep.CPU, AssemblyStep.COOLER, AssemblyStep.GPU -> if (c != null) {
            key("actor", c.id) {
                PartNode(c, (c.snapPositionOffset + Choreography.approach(c.type)).toPosition(), availableModels, materials, rgbOn = false, onNode = register(c.type.name.lowercase()))
            }
        }
        AssemblyStep.RAM -> if (c != null) {
            inst.slots.forEach { slot ->
                key("actor_ram", slot) {
                    PartNode(c, (SocketLayout.mountPosition(c, slot) + Choreography.approach(ComponentType.RAM)).toPosition(), availableModels, materials, rgbOn = false, onNode = register("ram_$slot"))
                }
            }
        }
        AssemblyStep.NVME -> if (c != null) {
            key("actor_ssd", c.id) {
                PartNode(
                    c, (c.snapPositionOffset + Choreography.approach(ComponentType.SSD)).toPosition(), availableModels, materials, rgbOn = false,
                    rotation = Rotation(y = Choreography.SSD_TILT_DEG), onNode = register("ssd"),
                )
                val hole = SocketLayout.m2Screw(c)
                Screw((hole + Vec3(x = travel)).toPosition(), axisX = true, materials = materials, scale = HIDDEN, onNode = register("screw_m2"), onSpin = register("screw_m2_spin"))
            }
        }
        AssemblyStep.MOUNT_BOARD -> SocketLayout.boardScrews(board).forEachIndexed { i, hole ->
            key("actor_screw", i) {
                Screw((hole + Vec3(x = travel)).toPosition(), axisX = true, materials = materials, scale = HIDDEN, onNode = register("screw_board_$i"), onSpin = register("screw_board_${i}_spin"))
            }
        }
        AssemblyStep.THERMAL_PASTE -> state.first(ComponentType.CPU)?.let { cpu ->
            key("actor_paste") {
                Syringe((cpu.snapPositionOffset + Vec3(x = 0.14f)).toPosition(), materials, register("syringe"))
                PasteDot((cpu.snapPositionOffset + Vec3(x = 0.003f)).toPosition(), HIDDEN, materials, register("paste"))
            }
        }
        else -> Unit
    }
}

/** Rear-panel slot cover, PSU and their screws, in the RIG (case) frame. */
@Composable
private fun SceneScope.CaseFixtures(
    state: AssemblyState,
    case: PCComponent,
    availableModels: Set<String>,
    materials: PartMaterials,
    refs: SceneRefs,
    register: (String) -> (io.github.sceneview.node.Node) -> Unit,
) {
    val step = state.installing?.step
    val c = state.installing?.component
    val board = state.board
    val travel = Choreography.SCREW_TRAVEL
    val k = if (state.exploded) 1f else 0f

    state.first(ComponentType.PSU)?.let { psu ->
        key("psu", psu.id) {
            val base = psu.snapPositionOffset
            val offset = SocketLayout.explodeOffset(ComponentType.PSU)
            PartNode(
                psu, (base + offset * k).toPosition(), availableModels, materials, rgbOn = false,
                onNode = { refs.explodable["psu"] = Triple(it, base.toPosition(), offset.toPosition()) },
            )
        }
    }
    if (board != null && state.boardInCase) {
        if (!state.isInstalled(ComponentType.GPU)) {
            key("cover") { SlotCover(SocketLayout.pcieCover(case, board).toPosition(), materials, register("pcie_cover")) }
        } else if (!state.exploded) {
            Screw(SocketLayout.gpuScrew(case, board).toPosition(), axisX = false, materials = materials)
        }
    }
    state.first(ComponentType.PSU)?.takeIf { !state.exploded }?.let { psu ->
        SocketLayout.psuScrews(psu).forEach { Screw(it.toPosition(), axisX = false, materials = materials) }
    }

    when (step) {
        AssemblyStep.GPU -> if (board != null) {
            key("actor_gpu_screw") {
                Screw((SocketLayout.gpuScrew(case, board) + Vec3(z = travel)).toPosition(), axisX = false, materials = materials, scale = HIDDEN, onNode = register("screw_gpu"), onSpin = register("screw_gpu_spin"))
            }
        }
        AssemblyStep.PSU -> if (c != null) {
            key("actor_psu", c.id) {
                PartNode(c, (c.snapPositionOffset + Choreography.approach(ComponentType.PSU)).toPosition(), availableModels, materials, rgbOn = false, onNode = register("psu"))
                SocketLayout.psuScrews(c).forEachIndexed { i, hole ->
                    Screw((hole + Vec3(z = travel)).toPosition(), axisX = false, materials = materials, scale = HIDDEN, onNode = register("screw_psu_$i"), onSpin = register("screw_psu_${i}_spin"))
                }
            }
        }
        else -> Unit
    }
}

/** A monitor beside the case showing the simulated POST screen (Compose rendered into 3D). */
@Composable
private fun SceneScope.Monitor(state: AssemblyState, case: PCComponent, materials: PartMaterials, viewNodeManager: ViewNodeImpl.WindowManager) {
    val screenW = 0.40f
    val screenH = 0.24f
    val density = LocalDensity.current
    val widthDp = 320.dp
    val heightDp = 192.dp
    val pxPerUnit = with(density) { widthDp.toPx() } / screenW
    Node(
        position = Position(x = case.dimension.width / 2f + 0.32f, y = 0.28f, z = -0.12f),
        rotation = Rotation(y = -20f),
    ) {
        CubeNode(Size(screenW + 0.02f, screenH + 0.02f, 0.015f), materialInstance = materials.monitorBezel)
        CubeNode(Size(0.04f, 0.16f, 0.03f), materialInstance = materials.monitorBezel, position = Position(y = -0.2f, z = 0.02f))
        ViewNode(
            windowManager = viewNodeManager,
            unlit = true,
            position = Position(z = -0.0085f),
            rotation = Rotation(y = 180f),
            apply = { this.pxPerUnits = pxPerUnit },
        ) {
            // ViewNode content composes off-screen without our CompositionLocals: re-apply the theme.
            ARPCBuilderTheme { BiosScreen(state, widthDp, heightDp) }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xCC111318)) {
        Text(text, Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Narration during an install; slides between lines. */
@Composable
private fun CaptionCard(caption: CaptionState) {
    val text = caption.text
    AnimatedVisibility(text != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xE6111318), modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = text.orEmpty(),
                transitionSpec = {
                    (slideInVertically(tween(300)) { it / 2 } + fadeIn(tween(300))) togetherWith
                        (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(tween(200)))
                },
                label = "caption",
            ) { line ->
                Text(line, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BottomControls(
    state: AssemblyState,
    exploding: Boolean,
    onPrimary: () -> Unit,
    onExplode: () -> Unit,
    onPower: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state.installing != null || exploding
    AnimatedVisibility(!busy, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.poweredOn) {
                val step = state.currentStep
                ExtendedFloatingActionButton(
                    onClick = onPrimary,
                    icon = {
                        Icon(
                            when (step) {
                                AssemblyStep.DONE -> Icons.Rounded.PowerSettingsNew
                                AssemblyStep.WORKBENCH -> Icons.Rounded.TableRestaurant
                                else -> Icons.Rounded.PlayArrow
                            },
                            null,
                        )
                    },
                    text = { Text(step.actionLabel) },
                )
            }
            if (state.workbenchPlaced) Surface(shape = RoundedCornerShape(28.dp), color = Color(0xCC111318)) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(onClick = onExplode, enabled = state.board != null) {
                        Icon(if (state.exploded) Icons.Rounded.Compress else Icons.Rounded.Expand, "Explode view")
                    }
                    FilledTonalIconButton(
                        onClick = onPower,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            contentColor = if (state.poweredOn) StatusGreen else if (state.isComplete) Color.White else StatusRed.copy(alpha = 0.8f),
                        ),
                    ) { Icon(Icons.Rounded.PowerSettingsNew, "Power") }
                    FilledTonalIconButton(onClick = onReset) { Icon(Icons.Rounded.RestartAlt, "Start over") }
                }
            }
        }
    }
}

private fun trackingMessage(reason: TrackingFailureReason): String? = when (reason) {
    TrackingFailureReason.NONE -> null
    TrackingFailureReason.BAD_STATE -> "AR tracking lost. Hold still for a moment."
    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark. Turn on a light."
    TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast. Slow down."
    TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at a textured surface, not a blank wall or glossy desk."
    TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera is in use by another app."
}
