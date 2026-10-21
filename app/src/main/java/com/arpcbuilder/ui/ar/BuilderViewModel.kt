package com.arpcbuilder.ui.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arpcbuilder.domain.engine.CompatibilityEngine
import com.arpcbuilder.domain.engine.InstallCheck
import com.arpcbuilder.domain.model.AssemblyState
import com.arpcbuilder.domain.model.AssemblyStep
import com.arpcbuilder.domain.model.BuildMode
import com.arpcbuilder.domain.model.InstalledComponent
import com.arpcbuilder.domain.model.Installation
import com.arpcbuilder.domain.model.PCComponent
import com.arpcbuilder.domain.model.Severity
import com.arpcbuilder.domain.repository.ComponentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One-shot messages for the snackbar. */
data class BuilderMessage(val text: String, val isError: Boolean)

/**
 * Holds the build. An install is two-phase: [choose] or [startAction] approves it and puts it in
 * [AssemblyState.installing]; the AR layer plays its animation and calls [completeInstall].
 */
@HiltViewModel
class BuilderViewModel @Inject constructor(
    repository: ComponentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AssemblyState())
    val state: StateFlow<AssemblyState> = _state.asStateFlow()

    val catalog: StateFlow<List<PCComponent>> = repository.observeCatalog()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = Channel<BuilderMessage>(Channel.BUFFERED)
    val messages: Flow<BuilderMessage> = _messages.receiveAsFlow()

    /** Switching mode starts a fresh build. */
    fun setMode(mode: BuildMode) {
        if (mode == _state.value.mode || _state.value.installing != null) return
        _state.value = AssemblyState(mode = mode)
    }

    fun reset() {
        if (_state.value.installing != null) return
        _state.value = AssemblyState(mode = _state.value.mode)
    }

    fun onWorkbenchPlaced() {
        if (!_state.value.workbenchPlaced) commit(_state.value.copy(workbenchPlaced = true))
    }

    /** The student picked [component] in the parts sheet for the current step. */
    fun choose(component: PCComponent) {
        val s = _state.value
        if (s.installing != null) return
        when (val check = CompatibilityEngine.checkInstall(component, s)) {
            is InstallCheck.Blocked -> send(check.reason, isError = true)
            is InstallCheck.Allowed -> {
                check.issues.firstOrNull { it.severity != Severity.INFO }?.let {
                    send(it.message, isError = it.severity == Severity.ERROR)
                }
                _state.update { it.copy(installing = Installation(it.currentStep, component, check.slots), exploded = false) }
            }
        }
    }

    /** Starts an action step: mounting the board, thermal paste or cables. */
    fun startAction() {
        val s = _state.value
        if (s.installing != null || s.currentStep !in ACTION_STEPS) return
        _state.update { it.copy(installing = Installation(it.currentStep, null), exploded = false) }
    }

    /** The install animation finished: commit it to the build. */
    fun completeInstall() {
        val s = _state.value
        val inst = s.installing ?: return
        val next = when (inst.step) {
            AssemblyStep.MOUNT_BOARD -> s.copy(boardInCase = true)
            AssemblyStep.THERMAL_PASTE -> s.copy(thermalPasteApplied = true)
            AssemblyStep.CABLES -> s.copy(cablesConnected = true)
            else -> {
                val c = inst.component ?: return
                s.copy(installed = s.installed + inst.slots.map { InstalledComponent(c, it) })
            }
        }
        commit(next.copy(installing = null))
    }

    /** Called after the explode/reassemble animation, so the scene settles where it ended. */
    fun setExploded(exploded: Boolean) = _state.update { it.copy(exploded = exploded) }

    fun togglePower() {
        val s = _state.value
        if (s.installing != null) return
        if (s.poweredOn) return _state.update { it.copy(poweredOn = false) }
        val firstError = s.compatibility.issues.firstOrNull { it.severity == Severity.ERROR }
        when {
            !s.isComplete -> send("Not yet. Next: ${s.currentStep.actionLabel.lowercase()}.", isError = true)
            firstError != null -> send("No POST: ${firstError.message}", isError = true)
            else -> _state.update { it.copy(poweredOn = true, exploded = false) }
        }
    }

    /** Recomputes every derived field so they can never drift out of sync with the build. */
    private fun commit(next: AssemblyState) {
        val parts = next.installed.map { it.component }
        _state.value = next.copy(
            compatibility = CompatibilityEngine.validate(parts),
            power = CompatibilityEngine.powerBudget(parts),
            currentStep = next.firstIncompleteStep(),
        )
    }

    private fun send(text: String, isError: Boolean) {
        _messages.trySend(BuilderMessage(text, isError))
    }

    companion object {
        val ACTION_STEPS = setOf(AssemblyStep.MOUNT_BOARD, AssemblyStep.THERMAL_PASTE, AssemblyStep.CABLES)
    }
}
