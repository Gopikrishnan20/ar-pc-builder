package com.arpcbuilder.ui.ar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.arpcbuilder.domain.model.ComponentType
import com.arpcbuilder.domain.model.Dimension
import com.arpcbuilder.domain.model.PCComponent
import com.arpcbuilder.domain.model.SocketLayout
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.node.Node as SceneNode

/** Shared placeholder materials, one per part type, so every box of a type reuses one instance. */
class PartMaterials(loader: MaterialLoader) {
    private val byType: Map<ComponentType, MaterialInstance> = ComponentType.entries.associateWith { type ->
        loader.createColorInstance(placeholderColor(type), metallic = 0.3f, roughness = 0.5f)
    }
    val chassis: MaterialInstance = loader.createColorInstance(Color(0xFF2B2F36), metallic = 0.6f, roughness = 0.4f)
    val fanFrame: MaterialInstance = loader.createColorInstance(Color(0xFF15171B), roughness = 0.7f)
    val fanBlade: MaterialInstance = loader.createColorInstance(Color(0xFFB0B5BD), roughness = 0.6f)
    val socketMarker: MaterialInstance = loader.createUnlitColorInstance(Color(0xFF00E676))
    val monitorBezel: MaterialInstance = loader.createColorInstance(Color(0xFF101114), roughness = 0.3f)

    val screw: MaterialInstance = loader.createColorInstance(Color(0xFFB0BEC5), metallic = 0.9f, roughness = 0.3f)
    val lever: MaterialInstance = loader.createColorInstance(Color(0xFFCFD8DC), metallic = 0.8f, roughness = 0.35f)
    val latch: MaterialInstance = loader.createColorInstance(Color(0xFFECEFF1), roughness = 0.6f)
    val port: MaterialInstance = loader.createColorInstance(Color(0xFF111111), roughness = 0.8f)
    val plug: MaterialInstance = loader.createColorInstance(Color(0xFFFFD54F), roughness = 0.6f)
    val cable: MaterialInstance = loader.createColorInstance(Color(0xFF1A1A1A), roughness = 0.9f)
    val bag: MaterialInstance = loader.createColorInstance(Color(0xFF9EA7AD), metallic = 0.4f, roughness = 0.5f)
    val wood: MaterialInstance = loader.createColorInstance(Color(0xFF8B5A2B), roughness = 0.7f)
    val tableLeg: MaterialInstance = loader.createColorInstance(Color(0xFF2E2E2E), metallic = 0.7f, roughness = 0.4f)
    val mat: MaterialInstance = loader.createColorInstance(Color(0xFF1E3A5F), roughness = 0.9f)

    operator fun get(type: ComponentType): MaterialInstance = byType.getValue(type)

    private fun placeholderColor(type: ComponentType) = when (type) {
        ComponentType.CASE -> Color(0xFF3A3F47)
        ComponentType.MOTHERBOARD -> Color(0xFF1B5E20)
        ComponentType.CPU -> Color(0xFFC0C4C9)
        ComponentType.RAM -> Color(0xFF263238)
        ComponentType.SSD -> Color(0xFF212121)
        ComponentType.COOLER -> Color(0xFF8D6E63)
        ComponentType.GPU -> Color(0xFF37474F)
        ComponentType.PSU -> Color(0xFF424242)
    }
}

/**
 * Renders one part: its glb if `assets/<modelUrl>` exists, otherwise a box of the part's real
 * dimensions. [onNode] hands back the created node, for the animations.
 *
 * @param rgbOn whether the ARGB lighting animates (boot simulation).
 */
@Composable
fun SceneScope.PartNode(
    component: PCComponent,
    position: Position,
    availableModels: Set<String>,
    materials: PartMaterials,
    rgbOn: Boolean,
    rotation: Rotation = Rotation(0f, 0f, 0f),
    onNode: (SceneNode) -> Unit = {},
) {
    val d = component.dimension
    key(component.id) {
        val instance = if (component.modelUrl in availableModels) rememberModelInstance(modelLoader, component.modelUrl) else null
        if (instance != null) {
            // Authoring convention: any glb material whose name contains "rgb" is an ARGB zone.
            val rgbZones = remember(instance) { mutableStateOf<List<MaterialInstance>>(emptyList()) }
            ModelNode(
                modelInstance = instance,
                scaleToUnits = maxOf(d.width, d.height, d.depth),
                centerOrigin = Position(0f, 0f, 0f),
                position = position,
                rotation = rotation,
                apply = {
                    onNode(this)
                    rgbZones.value = materialInstances.flatten().filter { it.name.contains("rgb", ignoreCase = true) }
                },
            )
            if (component.hasRgb) GlbRgbDriver(rgbZones.value, rgbOn)
        } else {
            CubeNode(
                size = Size(d.width, d.height, d.depth),
                materialInstance = materials[component.type],
                position = position,
                rotation = rotation,
                apply = { onNode(this) },
            ) {
                if (component.hasRgb) RgbStrip(component.type, d, rgbOn)
                if (component.type == ComponentType.COOLER && d.width > 0.1f) {
                    // Tower cooler: a 120 mm fan on the front face of the fin stack.
                    Fan(Position(0f, 0f, -d.depth / 2f - 0.013f), Rotation(x = 90f), 0.12f, rgbOn, materials)
                }
            }
        }
    }
}

@Composable
private fun GlbRgbDriver(zones: List<MaterialInstance>, on: Boolean) {
    LaunchedEffect(on, zones) {
        if (!on) {
            zones.forEach { runCatching { it.setParameter("emissiveFactor", 0f, 0f, 0f) } }
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { t ->
                val c = rainbow(t)
                zones.forEach { runCatching { it.setParameter("emissiveFactor", c.red, c.green, c.blue) } }
            }
        }
    }
}

/**
 * Thin light bar on the part's outward (+X) face that cycles through the rainbow while powered.
 * For the motherboard it is a short strip near the chipset so it does not cover the board.
 */
@Composable
private fun SceneScope.RgbStrip(type: ComponentType, d: Dimension, on: Boolean) {
    val material = remember(materialLoader) { materialLoader.createUnlitColorInstance(RGB_OFF) }
    LaunchedEffect(on) {
        if (!on) {
            material.setColor(RGB_OFF.toArgb())
            return@LaunchedEffect
        }
        while (true) withFrameNanos { t -> material.setColor(rainbow(t).toArgb()) }
    }
    val (size, offset) = when (type) {
        ComponentType.MOTHERBOARD -> Size(0.003f, d.height * 0.3f, 0.005f) to Position(d.width / 2f + 0.002f, -d.height * 0.2f, d.depth / 2f - 0.01f)
        ComponentType.CASE -> Size(0.004f, d.height * 0.9f, 0.004f) to Position(d.width / 2f, d.height / 2f, -d.depth / 2f)
        else -> Size(0.003f, d.height * 0.85f, d.depth * 0.85f) to Position(d.width / 2f + 0.002f, 0f, 0f)
    }
    CubeNode(size = size, materialInstance = material, position = offset)
}

/**
 * A case/cooler fan: frame ring plus two crossed blades that spin while [spinning].
 * The fan axis is the local Y axis, so pass a [rotation] to aim it.
 */
@Composable
fun SceneScope.Fan(position: Position, rotation: Rotation, diameter: Float, spinning: Boolean, materials: PartMaterials) {
    Node(position = position, rotation = rotation) {
        CylinderNode(radius = diameter / 2f, height = 0.02f, materialInstance = materials.fanFrame)
        if (spinning) {
            val angle by rememberInfiniteTransition(label = "fan").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 350, easing = LinearEasing), RepeatMode.Restart),
                label = "fanAngle",
            )
            Blades(diameter, angle, materials)
        } else {
            Blades(diameter, 0f, materials)
        }
    }
}

@Composable
private fun SceneScope.Blades(diameter: Float, angle: Float, materials: PartMaterials) {
    Node(position = Position(y = 0.012f), rotation = Rotation(y = angle)) {
        CubeNode(size = Size(diameter * 0.9f, 0.003f, 0.025f), materialInstance = materials.fanBlade)
        CubeNode(size = Size(0.025f, 0.003f, diameter * 0.9f), materialInstance = materials.fanBlade)
    }
}

/**
 * Placeholder open chassis drawn when the case has no glb: floor, motherboard tray (left wall),
 * rear wall and two front intake fans. Origin is the floor centre, matching the CASE frame.
 */
@Composable
fun SceneScope.ChassisPlaceholder(case: PCComponent, poweredOn: Boolean, materials: PartMaterials, onNode: (SceneNode) -> Unit) {
    val d = case.dimension
    val wall = 0.004f
    Node(apply = { onNode(this) }) {
        CubeNode(Size(d.width, wall, d.depth), materialInstance = materials.chassis, position = Position(y = wall / 2f))
        CubeNode(Size(wall, d.height, d.depth), materialInstance = materials.chassis, position = Position(x = -d.width / 2f, y = d.height / 2f))
        CubeNode(Size(d.width, d.height, wall), materialInstance = materials.chassis, position = Position(y = d.height / 2f, z = d.depth / 2f))
        val fan = minOf(0.12f, d.height / 3f)
        for (i in 0..1) {
            Fan(
                position = Position(y = d.height * (0.35f + 0.33f * i), z = -d.depth / 2f + 0.015f),
                rotation = Rotation(x = 90f),
                diameter = fan,
                spinning = poweredOn,
                materials = materials,
            )
        }
        if (case.hasRgb) RgbStrip(ComponentType.CASE, d, poweredOn)
    }
}

/**
 * A screw: [onNode] gets the outer node (moves along the screw axis), [onSpin] the inner one
 * (spins about it). [axisX] points the screw along +X (board screws) instead of +Z (rear panel).
 */
@Composable
fun SceneScope.Screw(position: Position, axisX: Boolean, materials: PartMaterials, scale: Float = 1f, onNode: (SceneNode) -> Unit = {}, onSpin: (SceneNode) -> Unit = {}) {
    // A cylinder's axis is Y: roll it onto X, or pitch it onto Z.
    val orient = if (axisX) Rotation(z = 90f) else Rotation(x = 90f)
    Node(position = position, scale = Scale(scale), apply = { onNode(this) }) {
        Node(rotation = orient) {
            Node(apply = { onSpin(this) }) {
                CylinderNode(radius = 0.0045f, height = 0.004f, materialInstance = materials.screw)
                // The cross slot makes the spin visible.
                CubeNode(Size(0.007f, 0.0012f, 0.0012f), materialInstance = materials.port, position = Position(y = 0.0022f))
                CubeNode(Size(0.0012f, 0.0012f, 0.007f), materialInstance = materials.port, position = Position(y = 0.0022f))
            }
        }
    }
}

/** CPU socket retention lever: a bar along the board's Y axis that swings up (about Z) to open. */
@Composable
fun SceneScope.Lever(position: Position, materials: PartMaterials, onNode: (SceneNode) -> Unit) {
    CubeNode(Size(0.003f, 0.05f, 0.003f), materialInstance = materials.lever, position = position, apply = { onNode(this) })
}

/** DIMM slot with its latch. The slot is a dark rail; the latch swings out (about Z) to open. */
@Composable
fun SceneScope.RamSlot(slotCentre: Position, latch: Position, length: Float, materials: PartMaterials, onLatch: (SceneNode) -> Unit) {
    CubeNode(Size(0.006f, length, 0.005f), materialInstance = materials.port, position = slotCentre)
    CubeNode(Size(0.009f, 0.006f, 0.006f), materialInstance = materials.latch, position = latch, apply = { onLatch(this) })
}

/** Expansion-slot cover on the case's rear panel. */
@Composable
fun SceneScope.SlotCover(position: Position, materials: PartMaterials, onNode: (SceneNode) -> Unit) {
    CubeNode(Size(0.018f, 0.1f, 0.002f), materialInstance = materials.lever, position = position, apply = { onNode(this) })
}

/** The paste syringe, pointing down the board normal (-X). */
@Composable
fun SceneScope.Syringe(position: Position, materials: PartMaterials, onNode: (SceneNode) -> Unit) {
    Node(position = position, scale = Scale(HIDDEN), apply = { onNode(this) }) {
        CylinderNode(radius = 0.006f, height = 0.07f, materialInstance = materials.latch, position = Position(x = 0.035f), rotation = Rotation(z = 90f))
        CylinderNode(radius = 0.002f, height = 0.012f, materialInstance = materials.screw, position = Position(x = -0.004f), rotation = Rotation(z = 90f))
    }
}

/** The pea-sized dot of thermal paste on the CPU. */
@Composable
fun SceneScope.PasteDot(position: Position, scale: Float, materials: PartMaterials, onNode: (SceneNode) -> Unit = {}) {
    CylinderNode(
        radius = 0.007f, height = 0.002f, materialInstance = materials.latch,
        position = position, rotation = Rotation(z = 90f), scale = Scale(scale), apply = { onNode(this) },
    )
}

/**
 * A cable drawn as beads along its route, plus the plug at the end. [visible] false composes it
 * hidden so the director can grow it bead by bead.
 */
@Composable
fun SceneScope.Cable(route: SocketLayout.CableRoute, beads: Int, visible: Boolean, materials: PartMaterials, onBead: (Int, SceneNode) -> Unit = { _, _ -> }, onPlug: (SceneNode) -> Unit = {}) {
    val s = Scale(if (visible) 1f else HIDDEN)
    for (i in 0 until beads) {
        val p = SocketLayout.pointOn(route, (i + 1f) / beads).toPosition()
        SphereNode(radius = 0.0035f, materialInstance = materials.cable, position = p, scale = s, apply = { onBead(i, this) })
    }
    CubeNode(
        Size(0.008f, 0.012f, 0.016f), materialInstance = materials.plug,
        position = route.to.toPosition(), scale = s, apply = { onPlug(this) },
    )
}

/**
 * The virtual workbench. Its top surface is at y = 0 of the anchor, which is the rig's floor,
 * so the case and the board on its bag rest exactly on it. It spans the case (at the origin)
 * and the bench (at +X), with a blue anti-static mat under the bench.
 */
@Composable
fun SceneScope.WorkbenchTable(materials: PartMaterials) {
    val width = 1.05f
    val depth = 0.62f
    val top = 0.03f
    val legHeight = 0.72f
    val centreX = 0.24f
    Node(position = Position(x = centreX)) {
        CubeNode(Size(width, top, depth), materialInstance = materials.wood, position = Position(y = -top / 2f))
        for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
            CylinderNode(
                radius = 0.018f, height = legHeight, materialInstance = materials.tableLeg,
                position = Position(sx * (width / 2f - 0.05f), -top - legHeight / 2f, sz * (depth / 2f - 0.05f)),
            )
        }
        // Anti-static mat under the bench area.
        CubeNode(Size(0.4f, 0.002f, 0.34f), materialInstance = materials.mat, position = Position(x = 0.42f - centreX, y = 0.001f))
    }
}

private val RGB_OFF = Color(0xFF202020)

/** Rainbow colour cycling once every 3 s. */
private fun rainbow(frameTimeNanos: Long): Color {
    val hue = ((frameTimeNanos / 1_000_000L) % 3000L) / 3000f * 360f
    return Color.hsv(hue, 1f, 1f)
}
