package com.arpcbuilder.ui.ar

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.withFrameNanos
import com.arpcbuilder.domain.model.Vec3
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import io.github.sceneview.node.Node as SceneNode

/**
 * Animation building blocks. Everything moves nodes directly on the display's frame clock, so
 * an animation never recomposes the UI and runs at the screen's refresh rate.
 */
object Ease {
    fun linear(t: Float) = t
    fun inOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    fun outCubic(t: Float) = 1f - (1f - t).pow(3)
    fun inCubic(t: Float) = t * t * t

    /** Slight overshoot, for things that "pop" into place. */
    fun outBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }
}

/**
 * Runs [block] with eased progress 0→1 over [durationMs], once per display frame.
 * Must run in a coroutine with a frame clock, e.g. from `rememberCoroutineScope()`.
 */
suspend fun tween(durationMs: Long, easing: (Float) -> Float = Ease::inOutCubic, block: (Float) -> Unit) {
    val start = withFrameNanos { it }
    var t = 0f
    while (t < 1f) {
        t = withFrameNanos { now -> ((now - start) / 1_000_000f / durationMs).coerceIn(0f, 1f) }
        block(easing(t))
    }
}

suspend fun pause(ms: Long) = tween(ms, Ease::linear) {}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun lerp(a: Position, b: Position, t: Float) = Position(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t))

fun Vec3.toPosition() = Position(x, y, z)

/** Moves [node] (local position) from where it is to [to]. */
suspend fun SceneNode.moveTo(to: Position, durationMs: Long, easing: (Float) -> Float = Ease::inOutCubic) {
    val from = position
    tween(durationMs, easing) { position = lerp(from, to, it) }
}

suspend fun SceneNode.rotateTo(to: Rotation, durationMs: Long, easing: (Float) -> Float = Ease::inOutCubic) {
    val from = rotation
    tween(durationMs, easing) { rotation = lerp(from, to, it) }
}

suspend fun SceneNode.scaleTo(to: Float, durationMs: Long, easing: (Float) -> Float = Ease::inOutCubic) {
    val from = scale.x
    tween(durationMs, easing) { scale = Scale(lerp(from, to, it)) }
}

/**
 * "Zooms" by flying the whole build to the camera: the rig moves so [target] (a rig-frame point)
 * ends up [distance] metres in front of the lens, and turns so [viewNormal] faces the student.
 * In AR the camera is the phone, so moving the model is the only way to get a close-up.
 *
 * All maths is in the anchor's frame (the rig's parent): rig point T lands at
 * `p + Ry(yaw)·T`, so for a wanted landing point F the rig position is `F − Ry(yaw)·T`.
 */
class CameraFocus(
    private val rig: () -> SceneNode?,
    private val anchor: () -> Anchor?,
    private val frame: () -> Frame?,
) {
    var isFocused = false
        private set

    suspend fun focus(target: Vec3, viewNormal: Vec3?, distance: Float, durationMs: Long = 850) {
        val r = rig() ?: return
        val a = anchor() ?: return
        val f = frame() ?: return
        val inv = a.pose.inverse()
        val cam = f.camera.displayOrientedPose
        val camA = inv.transformPoint(cam.translation).toPos()
        val fwdA = inv.rotateVector(cam.zAxis.map { -it }.toFloatArray()).toPos()
        // Land slightly below the screen centre, leaving room for the caption card above.
        val landing = camA + fwdA * distance - Position(y = distance * 0.08f)

        val t = target.toPosition()
        val p0 = r.position
        val yaw0 = r.rotation.y
        val start = p0 + rotY(t, yaw0)

        val toCam = camA - landing
        val yaw1 = if (viewNormal != null && toCam.x * toCam.x + toCam.z * toCam.z > 1e-4f) {
            yaw0 + wrap(heading(toCam) - heading(viewNormal.toPosition()) - yaw0)
        } else {
            yaw0
        }
        tween(durationMs) { e ->
            val yaw = lerp(yaw0, yaw1, e)
            r.rotation = Rotation(y = yaw)
            r.position = lerp(start, landing, e) - rotY(t, yaw)
        }
        isFocused = true
    }

    /** Flies the build back onto the table. */
    suspend fun home(durationMs: Long = 750) {
        val r = rig() ?: return
        val p0 = r.position
        val yaw0 = r.rotation.y
        tween(durationMs) { e ->
            r.position = lerp(p0, Position(0f, 0f, 0f), e)
            r.rotation = Rotation(y = lerp(yaw0, 0f, e))
        }
        isFocused = false
    }

    private companion object {
        fun FloatArray.toPos() = Position(this[0], this[1], this[2])

        /** Rotates [v] by [deg] about +Y (right-handed). */
        fun rotY(v: Position, deg: Float): Position {
            val r = deg * PI.toFloat() / 180f
            val c = cos(r)
            val s = sin(r)
            return Position(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
        }

        /** Angle of [v] in the XZ plane, measured from +Z toward +X, in degrees. */
        fun heading(v: Position) = (atan2(v.x, v.z) * 180.0 / PI).toFloat()

        fun wrap(d: Float): Float {
            var x = d % 360f
            if (x > 180f) x -= 360f
            if (x < -180f) x += 360f
            return x
        }
    }
}

/** Haptic "click" plus a short tone when something seats, and a softer tick for screws. */
class SnapFeedback {
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 50) }.getOrNull()

    fun click(view: View) {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
        view.performHapticFeedback(effect)
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 90)
    }

    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun reject(view: View) {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
        view.performHapticFeedback(effect)
    }

    fun release() {
        tone?.release()
    }
}
