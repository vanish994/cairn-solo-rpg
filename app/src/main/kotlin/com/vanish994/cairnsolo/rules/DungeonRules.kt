package com.vanish994.cairnsolo.rules

enum class DungeonLight { DARK, TORCH, LANTERN }

enum class DungeonActionKind { MOVE, SEARCH, LISTEN, FORCE_DOOR, DISARM_TRAP, REST, CAST_SPELL, RETREAT, OTHER }

data class DungeonAction(
    val kind: DungeonActionKind,
    val fastMovement: Boolean = false,
    val loud: Boolean = false,
    val enteredNewArea: Boolean = false
)

data class DungeonState(
    val locationId: String,
    val turn: Int = 0,
    val cyclesInLocation: Int = 0,
    val light: DungeonLight = DungeonLight.DARK,
    val torchIgnitionsRemaining: Int = 3,
    val lanternOilUses: Int = 0,
    val safeLocation: Boolean = false,
    val dangerPresent: Boolean = false,
    val panicked: Boolean = false
) {
    init {
        require(turn >= 0)
        require(cyclesInLocation >= 0)
        require(torchIgnitionsRemaining in 0..3)
        require(lanternOilUses in 0..6)
    }

    val hasLight: Boolean get() = light != DungeonLight.DARK
}

sealed interface DungeonEvent {
    data class TurnAdvanced(val turn: Int, val action: DungeonActionKind) : DungeonEvent
    data class EventRollRequired(val roll: Int) : DungeonEvent
    data object Encounter : DungeonEvent
    data object Sign : DungeonEvent
    data object Environment : DungeonEvent
    data object Loss : DungeonEvent
    data object Exhaustion : DungeonEvent
    data object Quiet : DungeonEvent
    data class HpRestored(val amount: Int) : DungeonEvent
    data object RestDenied : DungeonEvent
    data class TorchLit(val ignitionsRemaining: Int) : DungeonEvent
    data object TorchExtinguished : DungeonEvent
    data class LanternLit(val oilUsesRemaining: Int) : DungeonEvent
    data object PanicStarted : DungeonEvent
    data object PanicOvercome : DungeonEvent
    data object PanicBlockedAction : DungeonEvent
}

data class DungeonTurnResult(val state: DungeonState, val character: CharacterState, val events: List<DungeonEvent>)

data class DungeonLightResult(val state: DungeonState, val events: List<DungeonEvent>)

class DungeonRules(private val random: RandomSource, private val rules: RulesEngine = RulesEngine(random)) {
    fun resolveTurn(state: DungeonState, character: CharacterState, action: DungeonAction): DungeonTurnResult {
        if (state.panicked && action.kind != DungeonActionKind.OTHER) {
            return DungeonTurnResult(state.copy(turn = state.turn + 1), character, listOf(DungeonEvent.PanicBlockedAction))
        }
        val nextCycles = if (action.enteredNewArea) 1 else state.cyclesInLocation + 1
        var nextState = state.copy(turn = state.turn + 1, cyclesInLocation = nextCycles)
        var nextCharacter = character
        val events = mutableListOf<DungeonEvent>(DungeonEvent.TurnAdvanced(nextState.turn, action.kind))

        if (action.kind == DungeonActionKind.REST) {
            if (!state.hasLight || state.dangerPresent || !state.safeLocation) {
                events += DungeonEvent.RestDenied
            } else {
                val result = rules.quickRest(character)
                nextCharacter = result.newState
                val recovered = result.events.filterIsInstance<RuleEvent.HpRecovered>().sumOf { it.amount }
                if (recovered > 0) events += DungeonEvent.HpRestored(recovered)
            }
        }

        if (action.fastMovement || action.loud || action.enteredNewArea || nextCycles > 1) {
            val roll = random.d6()
            events += DungeonEvent.EventRollRequired(roll)
            events += dungeonEvent(roll)
        }
        return DungeonTurnResult(nextState, nextCharacter, events)
    }

    fun lightTorch(state: DungeonState): DungeonLightResult {
        require(state.light == DungeonLight.DARK) { "Another light source is already active" }
        require(state.torchIgnitionsRemaining > 0) { "Torch has degraded permanently" }
        val next = state.copy(light = DungeonLight.TORCH, torchIgnitionsRemaining = state.torchIgnitionsRemaining - 1)
        return DungeonLightResult(next, listOf(DungeonEvent.TorchLit(next.torchIgnitionsRemaining)))
    }

    fun lightLantern(state: DungeonState): DungeonLightResult {
        require(state.light == DungeonLight.DARK) { "Another light source is already active" }
        require(state.lanternOilUses > 0) { "Lantern requires an Oil Can use" }
        val next = state.copy(light = DungeonLight.LANTERN, lanternOilUses = state.lanternOilUses - 1)
        return DungeonLightResult(next, listOf(DungeonEvent.LanternLit(next.lanternOilUses)))
    }

    fun extinguish(state: DungeonState): DungeonLightResult =
        DungeonLightResult(state.copy(light = DungeonLight.DARK), listOf(DungeonEvent.TorchExtinguished))

    fun addLanternOil(state: DungeonState, uses: Int = 6): DungeonState {
        require(uses > 0)
        return state.copy(lanternOilUses = (state.lanternOilUses + uses).coerceAtMost(6))
    }

    fun startPanic(state: DungeonState, character: CharacterState): DungeonTurnResult =
        DungeonTurnResult(state.copy(panicked = true), character.copy(hp = 0), listOf(DungeonEvent.PanicStarted))

    fun overcomePanic(state: DungeonState, character: CharacterState): DungeonTurnResult {
        val save = rules.save(character, Attribute.WIL)
        return if (save.success) DungeonTurnResult(state.copy(panicked = false), character, listOf(DungeonEvent.PanicOvercome))
        else DungeonTurnResult(state, character, emptyList())
    }

    private fun dungeonEvent(roll: Int): DungeonEvent = when (roll) {
        1 -> DungeonEvent.Encounter
        2 -> DungeonEvent.Sign
        3 -> DungeonEvent.Environment
        4 -> DungeonEvent.Loss
        5 -> DungeonEvent.Exhaustion
        else -> DungeonEvent.Quiet
    }
}
