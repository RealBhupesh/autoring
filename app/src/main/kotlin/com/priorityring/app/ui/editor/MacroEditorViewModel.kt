package com.priorityring.app.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.priorityring.app.navigation.MacroEditorRoute
import com.priorityring.app.ui.macros.MacroSummaries
import com.priorityring.blocks.android.AndroidDeviceCapabilities
import com.priorityring.blocks.android.DeviceVariables
import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.StoredMacro
import com.priorityring.engine.block.Availability
import com.priorityring.engine.block.Block
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.block.Requirement
import com.priorityring.engine.block.availability
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.BlockInstance
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.FailurePolicy
import com.priorityring.engine.model.LogicOp
import com.priorityring.engine.model.Macro
import com.priorityring.engine.model.MacroSettings
import com.priorityring.engine.variables.VariableSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val macro: Macro? = null,
    /** Problems shown after a save attempt. */
    val errors: List<String> = emptyList(),
    /** True when the stored macro contained parts the basic builder can't edit (shown read-only). */
    val hasAdvancedParts: Boolean = false,
)

/**
 * Holds the macro being edited. All edits are pure transformations of the draft [Macro], so
 * nothing is saved until the user taps Save — and leaving discards the draft.
 *
 * The basic builder edits the top-level lists: triggers, one condition group (ALL / ANY, each
 * condition can be negated) and the action list. Nested groups / If / Repeat in imported macros are
 * preserved and shown read-only.
 */
@HiltViewModel
class MacroEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: MacroRepository,
    val registry: BlockRegistry,
    private val capabilities: AndroidDeviceCapabilities,
) : ViewModel() {

    private val macroId: String? = savedState.toRoute<MacroEditorRoute>().id
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val summaries = MacroSummaries(registry)

    /** One-off events for the screen (navigate back after save/delete). */
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val existing = macroId?.let { repo.get(it) as? StoredMacro.Ok }?.macro
            val macro = existing ?: Macro(id = MacroRepository.newId(), name = "")
            _state.value = EditorUiState(
                loading = false,
                isNew = existing == null,
                macro = macro,
                hasAdvancedParts = macro.conditions.children.any { it !is ConditionNode.Leaf } ||
                    macro.actions.any { it !is ActionNode.Step },
            )
        }
    }

    // ---- block library --------------------------------------------------------------------------

    fun blocks(kind: BlockKind): List<Block> =
        registry.ofKind(kind).sortedWith(compareBy({ it.meta.category.order }, { it.meta.name }))

    fun availability(block: Block): Availability = block.availability(capabilities)

    /** System screen that grants [r], or null for runtime permissions (asked with a dialog). */
    fun fixIntent(r: Requirement) = capabilities.settingsIntent(r)

    /** Variables offered by the variable picker: the triggers' outputs plus built-ins. */
    fun variables(): List<VariableSpec> {
        val m = _state.value.macro ?: return DeviceVariables.SPECS
        val fromTriggers = m.triggers.flatMap { registry[it.blockId]?.meta?.outputs.orEmpty() }
        return (fromTriggers + DeviceVariables.SPECS).distinctBy { it.name }
    }

    // ---- edits ----------------------------------------------------------------------------------

    private fun edit(f: (Macro) -> Macro) = _state.update { s -> s.copy(macro = s.macro?.let(f), errors = emptyList()) }

    fun rename(name: String) = edit { it.copy(name = name) }
    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }
    fun setSettings(settings: MacroSettings) = edit { it.copy(settings = settings) }

    /** Adds a block with its default config; returns the new instance's uid so the UI can open it. */
    fun add(block: Block): String {
        val inst = BlockInstance(UUID.randomUUID().toString(), block.meta.id, BlockConfig.defaults(block.meta))
        edit { m ->
            when (block.meta.kind) {
                BlockKind.TRIGGER -> m.copy(triggers = m.triggers + inst)
                BlockKind.CONDITION -> m.copy(conditions = m.conditions.copy(children = m.conditions.children + ConditionNode.Leaf(inst)))
                BlockKind.ACTION -> m.copy(actions = m.actions + ActionNode.Step(inst))
            }
        }
        return inst.uid
    }

    fun updateConfig(uid: String, config: JsonObject) = edit { m ->
        fun BlockInstance.upd() = if (this.uid == uid) copy(config = config) else this
        m.copy(
            triggers = m.triggers.map { it.upd() },
            conditions = m.conditions.copy(children = m.conditions.children.map { c -> if (c is ConditionNode.Leaf) c.copy(block = c.block.upd()) else c }),
            actions = m.actions.map { a -> if (a is ActionNode.Step) a.copy(block = a.block.upd()) else a },
        )
    }

    fun remove(uid: String) = edit { m ->
        m.copy(
            triggers = m.triggers.filterNot { it.uid == uid },
            conditions = m.conditions.copy(children = m.conditions.children.filterNot { it is ConditionNode.Leaf && it.block.uid == uid }),
            actions = m.actions.filterNot { it.uid == uid },
        )
    }

    fun setConditionOp(op: LogicOp) = edit { it.copy(conditions = it.conditions.copy(op = op)) }

    fun toggleNegate(uid: String) = edit { m ->
        m.copy(conditions = m.conditions.copy(children = m.conditions.children.map { c ->
            if (c is ConditionNode.Leaf && c.block.uid == uid) c.copy(negate = !c.negate) else c
        }))
    }

    fun moveAction(uid: String, delta: Int) = edit { m ->
        val list = m.actions.toMutableList()
        val i = list.indexOfFirst { it.uid == uid }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@edit m
        list.add(j, list.removeAt(i))
        m.copy(actions = list)
    }

    fun setOnFailure(uid: String, policy: FailurePolicy) = edit { m ->
        m.copy(actions = m.actions.map { a -> if (a is ActionNode.Step && a.uid == uid) a.copy(onFailure = policy) else a })
    }

    fun findInstance(uid: String): BlockInstance? {
        val m = _state.value.macro ?: return null
        return m.triggers.firstOrNull { it.uid == uid }
            ?: m.conditions.children.filterIsInstance<ConditionNode.Leaf>().firstOrNull { it.block.uid == uid }?.block
            ?: m.actions.filterIsInstance<ActionNode.Step>().firstOrNull { it.uid == uid }?.block
    }

    // ---- save / delete --------------------------------------------------------------------------

    fun validate(m: Macro): List<String> = buildList {
        if (m.name.isBlank()) add("Give the macro a name.")
        if (m.triggers.isEmpty()) add("Add at least one trigger (WHEN).")
        if (m.actions.isEmpty()) add("Add at least one action (THEN).")
        val instances = m.triggers + m.conditions.children.filterIsInstance<ConditionNode.Leaf>().map { it.block } +
            m.actions.filterIsInstance<ActionNode.Step>().map { it.block }
        for (inst in instances) {
            val block = registry[inst.blockId] ?: continue // missing blocks are kept as they are
            block.validate(BlockConfig.of(inst.config, block.meta)).forEach { add("${block.meta.name}: $it") }
        }
    }

    fun save() {
        val m = _state.value.macro ?: return
        val trimmed = m.copy(name = m.name.trim())
        val errors = validate(trimmed)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            repo.save(trimmed)
            _events.send("saved")
        }
    }

    fun delete() {
        val m = _state.value.macro ?: return
        viewModelScope.launch {
            repo.delete(m.id)
            _events.send("deleted")
        }
    }
}
