package com.arpcbuilder.domain

import com.arpcbuilder.data.SampleCatalog
import com.arpcbuilder.domain.engine.CompatibilityEngine
import com.arpcbuilder.domain.engine.InstallCheck
import com.arpcbuilder.domain.model.AssemblyState
import com.arpcbuilder.domain.model.AssemblyStep
import com.arpcbuilder.domain.model.BuildMode
import com.arpcbuilder.domain.model.InstalledComponent
import com.arpcbuilder.domain.model.PCComponent
import com.arpcbuilder.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityEngineTest {

    private val c = SampleCatalog

    /** A build with [parts] installed; [inCase] and [paste] mark the action steps done. */
    private fun state(mode: BuildMode, vararg parts: PCComponent, inCase: Boolean = false, paste: Boolean = false): AssemblyState {
        val installed = parts.groupBy { it.type }.flatMap { (_, list) -> list.mapIndexed { i, p -> InstalledComponent(p, i) } }
        val s = AssemblyState(mode = mode, installed = installed, workbenchPlaced = true, boardInCase = inCase, thermalPasteApplied = paste)
        return s.copy(currentStep = s.firstIncompleteStep())
    }

    @Test
    fun `matching Intel build is compatible`() {
        val report = CompatibilityEngine.validate(listOf(c.lancool216, c.z790, c.i5, c.ddr5Rgb, c.ddr5Rgb, c.samsung990, c.nhD15, c.rtx4070s, c.rm850))
        assertTrue(report.issues.toString(), report.isCompatible)
        assertFalse(report.hasWarnings)
    }

    @Test
    fun `socket mismatch is an error`() {
        val report = CompatibilityEngine.validate(listOf(c.z790, c.r7))
        assertTrue(report.issues.any { it.severity == Severity.ERROR && "AM5" in it.message && "LGA1700" in it.message })
    }

    @Test
    fun `ATX board in Mini-ITX case is an error`() {
        assertTrue(CompatibilityEngine.validate(listOf(c.nr200p, c.z790)).hasErrors)
        assertFalse(CompatibilityEngine.validate(listOf(c.lancool216, c.b550m)).hasErrors)
    }

    @Test
    fun `DDR4 in DDR5 board is an error`() {
        assertTrue(CompatibilityEngine.validate(listOf(c.z790, c.ddr4)).hasErrors)
    }

    @Test
    fun `GPU too long for case is an error`() {
        assertTrue(CompatibilityEngine.validate(listOf(c.nr200p, c.rtx4090)).hasErrors)
    }

    @Test
    fun `undersized PSU is an error and tight PSU is a warning`() {
        val big = listOf(c.z790, c.i9, c.rtx4090, c.ddr5Rgb, c.samsung990)
        // 35 + 253 + 450 + 5 + 8 + 50 base = 801 W
        assertEquals(801, CompatibilityEngine.powerBudget(big).totalDrawW)
        assertTrue(CompatibilityEngine.validate(big + c.rm650).hasErrors)
        val tight = CompatibilityEngine.validate(big + c.rm850)
        assertFalse(tight.hasErrors)
        assertTrue(tight.hasWarnings)
        // 1000 W leaves 19 % headroom: still a warning, which is why 1050 W is recommended.
        assertFalse(CompatibilityEngine.validate(big + c.focus1000).hasErrors)
        assertEquals(1050, CompatibilityEngine.recommendedPsuWattage(big))
    }

    @Test
    fun `weak cooler warns and wrong-socket cooler errors`() {
        assertTrue(CompatibilityEngine.validate(listOf(c.z790, c.i9, c.laminar)).hasWarnings)
        assertTrue(CompatibilityEngine.validate(listOf(c.b650, c.r7, c.laminar)).hasErrors)
    }

    @Test
    fun `build order is board, CPU, case, mount, paste, cooler, RAM`() {
        var s = state(BuildMode.GUIDED)
        assertEquals(AssemblyStep.MOTHERBOARD, s.currentStep)
        s = state(BuildMode.GUIDED, c.z790)
        assertEquals(AssemblyStep.CPU, s.currentStep)
        s = state(BuildMode.GUIDED, c.z790, c.i5)
        assertEquals(AssemblyStep.CASE, s.currentStep)
        s = state(BuildMode.GUIDED, c.z790, c.i5, c.lancool216)
        assertEquals(AssemblyStep.MOUNT_BOARD, s.currentStep)
        s = state(BuildMode.GUIDED, c.z790, c.i5, c.lancool216, inCase = true)
        assertEquals(AssemblyStep.THERMAL_PASTE, s.currentStep)
        s = state(BuildMode.GUIDED, c.z790, c.i5, c.lancool216, inCase = true, paste = true)
        assertEquals(AssemblyStep.COOLER, s.currentStep)
        s = state(BuildMode.GUIDED, c.z790, c.i5, c.lancool216, c.nhD15, inCase = true, paste = true)
        assertEquals(AssemblyStep.RAM, s.currentStep)
    }

    @Test
    fun `no workbench means nothing can be installed`() {
        val s = AssemblyState()
        assertEquals(AssemblyStep.WORKBENCH, s.firstIncompleteStep())
        assertTrue(CompatibilityEngine.checkInstall(c.z790, s) is InstallCheck.Blocked)
    }

    @Test
    fun `parts out of order are blocked in both modes`() {
        for (mode in BuildMode.entries) {
            val s = state(mode, c.z790)
            assertTrue(CompatibilityEngine.checkInstall(c.lancool216, s) is InstallCheck.Blocked)
            assertTrue(CompatibilityEngine.checkInstall(c.i5, s) is InstallCheck.Allowed)
        }
    }

    @Test
    fun `guided blocks incompatible part but sandbox allows it with an error`() {
        val guided = state(BuildMode.GUIDED, c.z790)
        assertTrue(CompatibilityEngine.checkInstall(c.r7, guided) is InstallCheck.Blocked)

        val sandbox = state(BuildMode.SANDBOX, c.z790)
        val check = CompatibilityEngine.checkInstall(c.r7, sandbox)
        assertTrue(check is InstallCheck.Allowed)
        assertTrue((check as InstallCheck.Allowed).issues.any { it.severity == Severity.ERROR })
    }

    @Test
    fun `board too big for case is caught when choosing the case`() {
        val s = state(BuildMode.GUIDED, c.z790, c.i5)
        assertTrue(CompatibilityEngine.checkInstall(c.nr200p, s) is InstallCheck.Blocked)
        assertTrue(CompatibilityEngine.checkInstall(c.lancool216, s) is InstallCheck.Allowed)
    }

    @Test
    fun `a RAM kit fills the dual-channel slots A2 and B2`() {
        val s = state(BuildMode.GUIDED, c.z790, c.i5, c.lancool216, c.nhD15, inCase = true, paste = true)
        val check = CompatibilityEngine.checkInstall(c.ddr5Rgb, s) as InstallCheck.Allowed
        assertEquals(listOf(1, 3), check.slots)
        assertEquals(listOf("A2", "B2"), check.slots.map(CompatibilityEngine::slotLabel))
    }

    @Test
    fun `cooler with no mount for the socket is blocked in guided mode`() {
        val s = state(BuildMode.GUIDED, c.b650, c.r7, c.lancool216, inCase = true, paste = true)
        assertTrue(CompatibilityEngine.checkInstall(c.laminar, s) is InstallCheck.Blocked)
        assertTrue(CompatibilityEngine.checkInstall(c.nhD15, s) is InstallCheck.Allowed)
    }

    @Test
    fun `complete build is ready to power on`() {
        val s = AssemblyState(
            installed = listOf(c.z790, c.i5, c.lancool216, c.nhD15, c.samsung990, c.rtx4070s, c.rm850).map { InstalledComponent(it) } +
                listOf(InstalledComponent(c.ddr5Rgb, 1), InstalledComponent(c.ddr5Rgb, 3)),
            workbenchPlaced = true, boardInCase = true, thermalPasteApplied = true,
        )
        assertFalse(s.isComplete)
        assertEquals(AssemblyStep.CABLES, s.firstIncompleteStep())
        assertTrue(s.copy(cablesConnected = true).isComplete)
    }
}
