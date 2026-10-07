package com.vanish994.cairnsolo.rules

enum class DowntimeActionType { RESEARCH, TRAINING, RELATIONSHIP, FOLLOW_LEAD, CUSTOM }

data class DowntimeState(
    val safe: Boolean = true,
    val inRecovery: Boolean = false,
    val gold: Int = 0,
    val reputation: Int = 0,
    val resources: Set<String> = emptySet(),
    val milestones: List<Milestone> = emptyList(),
    val completedActions: Int = 0
) {
    init { require(gold >= 0); require(reputation >= 0); require(completedActions >= 0) }
}

data class Milestone(
    val id: String,
    val goal: String,
    val total: Int,
    val progress: Int = 0,
    val cost: DowntimeCost = DowntimeCost.None
) {
    init { require(id.isNotBlank()); require(goal.isNotBlank()); require(total in 1..5); require(progress in 0..total) }
    val complete: Boolean get() = progress >= total
}

sealed interface DowntimeCost {
    data object None : DowntimeCost
    data class Gold(val amount: Int) : DowntimeCost { init { require(amount > 0) } }
    data class Resource(val id: String) : DowntimeCost { init { require(id.isNotBlank()) } }
    data class Reputation(val amount: Int) : DowntimeCost { init { require(amount > 0) } }
    data class Loss(val resourceId: String) : DowntimeCost { init { require(resourceId.isNotBlank()) } }
}

data class DowntimeAction(
    val type: DowntimeActionType,
    val question: String? = null,
    val source: String? = null,
    val trainingGoal: String? = null,
    val master: String? = null,
    val contact: String? = null,
    val milestoneId: String? = null,
    val attemptWilSave: Boolean = false
)

sealed interface DowntimeEvent {
    data class ActionCompleted(val type: DowntimeActionType) : DowntimeEvent
    data class MilestoneAdvanced(val id: String, val progress: Int, val total: Int) : DowntimeEvent
    data class MilestoneCompleted(val id: String) : DowntimeEvent
    data class CostPaid(val cost: DowntimeCost) : DowntimeEvent
    data class CostAvoided(val cost: DowntimeCost, val roll: Int) : DowntimeEvent
    data class ResearchRequiresSource(val question: String) : DowntimeEvent
    data class WilSaveFailed(val roll: Int) : DowntimeEvent
}

data class DowntimeResult(
    val state: DowntimeState,
    val character: CharacterState,
    val events: List<DowntimeEvent>,
    val save: SaveResult? = null
)

class DowntimeRules(private val rules: RulesEngine) {
    fun perform(state: DowntimeState, character: CharacterState, action: DowntimeAction): DowntimeResult {
        require(state.safe) { "Downtime actions require safe conditions" }
        require(!state.inRecovery) { "Characters in recovery cannot perform Downtime actions" }
        require(!character.dead) { "Dead characters cannot perform Downtime actions" }
        validateAction(action)

        val events = mutableListOf<DowntimeEvent>()
        var currentState = state
        var currentCharacter = character
        var save: SaveResult? = null
        val milestone = action.milestoneId?.let { id -> state.milestones.firstOrNull { it.id == id } }
        if (action.milestoneId != null) require(milestone != null) { "Unknown milestone: ${action.milestoneId}" }
        if (milestone != null) require(!milestone.complete) { "Milestone is already complete" }

        if (milestone != null && milestone.cost !is DowntimeCost.None) {
            if (action.attemptWilSave) {
                save = rules.save(character, Attribute.WIL)
                if (save.success) {
                    events += DowntimeEvent.CostAvoided(milestone.cost, save.roll)
                } else {
                    events += DowntimeEvent.WilSaveFailed(save.roll)
                    val paid = pay(currentState, milestone.cost, currentCharacter)
                    currentState = paid.first
                    currentCharacter = paid.second
                    events += DowntimeEvent.CostPaid(milestone.cost)
                }
            } else {
                val paid = pay(currentState, milestone.cost, currentCharacter)
                currentState = paid.first
                currentCharacter = paid.second
                events += DowntimeEvent.CostPaid(milestone.cost)
            }
        }

        if (milestone != null) {
            val updated = milestone.copy(progress = milestone.progress + 1)
            currentState = currentState.copy(milestones = currentState.milestones.map { if (it.id == updated.id) updated else it })
            events += DowntimeEvent.MilestoneAdvanced(updated.id, updated.progress, updated.total)
            if (updated.complete) events += DowntimeEvent.MilestoneCompleted(updated.id)
        }
        currentState = currentState.copy(completedActions = currentState.completedActions + 1)
        events += DowntimeEvent.ActionCompleted(action.type)
        return DowntimeResult(currentState, currentCharacter, events, save)
    }

    fun addMilestone(state: DowntimeState, milestone: Milestone): DowntimeState {
        require(state.milestones.none { it.id == milestone.id }) { "Milestone already exists" }
        return state.copy(milestones = state.milestones + milestone)
    }

    private fun validateAction(action: DowntimeAction) {
        when (action.type) {
            DowntimeActionType.RESEARCH -> {
                require(!action.question.isNullOrBlank()) { "Research requires a clear question" }
                require(!action.source.isNullOrBlank()) { "Research requires a Source" }
            }
            DowntimeActionType.TRAINING -> {
                require(!action.trainingGoal.isNullOrBlank()) { "Training requires a precise goal" }
                require(!action.master.isNullOrBlank()) { "Training requires a Master" }
            }
            DowntimeActionType.RELATIONSHIP -> require(!action.contact.isNullOrBlank()) { "Relationship action requires a contact" }
            DowntimeActionType.FOLLOW_LEAD, DowntimeActionType.CUSTOM -> Unit
        }
    }

    private fun pay(state: DowntimeState, cost: DowntimeCost, character: CharacterState): Pair<DowntimeState, CharacterState> = when (cost) {
        DowntimeCost.None -> state to character
        is DowntimeCost.Gold -> {
            require(state.gold >= cost.amount) { "Not enough gold" }
            state.copy(gold = state.gold - cost.amount) to character
        }
        is DowntimeCost.Resource -> {
            require(cost.id in state.resources) { "Missing resource: ${cost.id}" }
            state.copy(resources = state.resources - cost.id) to character
        }
        is DowntimeCost.Reputation -> {
            require(state.reputation >= cost.amount) { "Not enough reputation" }
            state.copy(reputation = state.reputation - cost.amount) to character
        }
        is DowntimeCost.Loss -> {
            require(cost.resourceId in state.resources) { "Missing loss: ${cost.resourceId}" }
            state.copy(resources = state.resources - cost.resourceId) to character
        }
    }
}
