package com.vanish994.cairnsolo.rules

/** Resultado de uma rolagem de reação 2d6; a narrativa do efeito fica com o Guardião. */
enum class ReactionDisposition { ATTACK, HOSTILE, UNCERTAIN, INDIFFERENT, HELPFUL }

data class ReactionResult(val roll: Int, val disposition: ReactionDisposition)

/** Resultado de um teste de moral: 2d6 <= moral mantém a criatura em combate. */
enum class MoraleOutcome { STAND, RETREAT, SURRENDER }

data class MoraleResult(val roll: Int, val morale: Int, val outcome: MoraleOutcome)

data class HirelingState(
    val id: String,
    val name: String,
    val role: String,
    val wageGp: Int,
    val loyalty: Int = 7,
    val morale: Int = 7,
    val active: Boolean = true,
    val injured: Boolean = false
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(role.isNotBlank())
        require(wageGp >= 0)
        require(loyalty in 2..12)
        require(morale in 2..12)
    }
}

enum class HirelingEventType { HIRED, WAGES_PAID, MORALE_STOOD, MORALE_RETREATED, MORALE_SURRENDERED, DISMISSED }

data class HirelingEvent(val type: HirelingEventType, val hirelingId: String, val amountGp: Int = 0)

data class HirelingPaymentResult(val hirelings: List<HirelingState>, val goldRemaining: Int, val events: List<HirelingEvent>)

data class HirelingMoraleResult(val hireling: HirelingState, val result: MoraleResult, val events: List<HirelingEvent>)

class ReactionRules(private val random: RandomSource) {
    fun roll(): ReactionResult {
        val total = random.d6() + random.d6()
        val disposition = when (total) {
            2 -> ReactionDisposition.ATTACK
            in 3..5 -> ReactionDisposition.HOSTILE
            in 6..8 -> ReactionDisposition.UNCERTAIN
            in 9..11 -> ReactionDisposition.INDIFFERENT
            else -> ReactionDisposition.HELPFUL
        }
        return ReactionResult(total, disposition)
    }
}

class MoraleRules(private val random: RandomSource) {
    fun check(morale: Int, failureOutcome: MoraleOutcome = MoraleOutcome.RETREAT): MoraleResult {
        require(morale in 2..12)
        val roll = random.d6() + random.d6()
        val outcome = if (roll <= morale) MoraleOutcome.STAND else failureOutcome
        return MoraleResult(roll, morale, outcome)
    }
}

class HirelingRules(private val moraleRules: MoraleRules) {
    fun hire(entry: MarketplaceEntry, id: String, name: String, loyalty: Int = 7, morale: Int = 7): HirelingState {
        require(entry.category == MarketplaceCategory.HIRELING) { "Only hireling entries can create a hireling" }
        return HirelingState(id, name, entry.name, entry.priceGp, loyalty, morale)
    }

    fun payWages(hirelings: List<HirelingState>, gold: Int): HirelingPaymentResult {
        require(gold >= 0)
        val active = hirelings.filter { it.active }
        val total = active.sumOf { it.wageGp }
        require(gold >= total) { "Insufficient gold to pay hirelings" }
        return HirelingPaymentResult(hirelings, gold - total, active.map { HirelingEvent(HirelingEventType.WAGES_PAID, it.id, it.wageGp) })
    }

    fun checkMorale(hireling: HirelingState, failureOutcome: MoraleOutcome = MoraleOutcome.RETREAT): HirelingMoraleResult {
        val result = moraleRules.check(hireling.morale, failureOutcome)
        val eventType = when (result.outcome) {
            MoraleOutcome.STAND -> HirelingEventType.MORALE_STOOD
            MoraleOutcome.RETREAT -> HirelingEventType.MORALE_RETREATED
            MoraleOutcome.SURRENDER -> HirelingEventType.MORALE_SURRENDERED
        }
        val updated = if (result.outcome == MoraleOutcome.STAND) hireling else hireling.copy(active = false)
        return HirelingMoraleResult(updated, result, listOf(HirelingEvent(eventType, hireling.id)))
    }
}
