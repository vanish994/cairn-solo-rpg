package com.vanish994.cairnsolo.rules

enum class Attribute { STR, DEX, WIL }

data class CharacterState(
    val str: Int,
    val dex: Int,
    val wil: Int,
    val hp: Int,
    val maxHp: Int,
    val armor: Int
) {
    init {
        require(str >= 0 && dex >= 0 && wil >= 0)
        require(hp >= 0 && maxHp >= 0 && hp <= maxHp)
        require(armor in 0..3)
    }

    fun attribute(attribute: Attribute): Int = when (attribute) {
        Attribute.STR -> str
        Attribute.DEX -> dex
        Attribute.WIL -> wil
    }
}

interface RandomSource { fun d20(): Int }

class FixedRandomSource(private val value: Int) : RandomSource {
    init { require(value in 1..20) }
    override fun d20(): Int = value
}

sealed interface RuleEvent {
    data class SaveResolved(val attribute: Attribute, val roll: Int, val success: Boolean) : RuleEvent
    data class DamageApplied(val rawDamage: Int, val armorAbsorbed: Int, val hpDamage: Int) : RuleEvent
    data class CriticalDamage(val excessDamage: Int, val strAfter: Int) : RuleEvent
}

data class SaveResult(
    val newState: CharacterState,
    val events: List<RuleEvent>,
    val roll: Int,
    val success: Boolean
)

data class GameResult(val newState: CharacterState, val events: List<RuleEvent>)

class RulesEngine(private val random: RandomSource) {

    fun save(state: CharacterState, attribute: Attribute): SaveResult {
        val value = state.attribute(attribute)
        val roll = random.d20()
        val success = when (roll) {
            1 -> true
            20 -> false
            else -> roll <= value
        }
        return SaveResult(
            newState = state,
            events = listOf(RuleEvent.SaveResolved(attribute, roll, success)),
            roll = roll,
            success = success
        )
    }

    fun applyDamage(state: CharacterState, rawDamage: Int): GameResult {
        require(rawDamage >= 0)
        val armorAbsorbed = minOf(rawDamage, state.armor)
        val hpDamage = rawDamage - armorAbsorbed
        val remainingHp = state.hp - hpDamage

        return if (remainingHp >= 0) {
            GameResult(
                state.copy(hp = remainingHp),
                listOf(RuleEvent.DamageApplied(rawDamage, armorAbsorbed, hpDamage))
            )
        } else {
            val excessDamage = -remainingHp
            val newStr = maxOf(0, state.str - excessDamage)
            GameResult(
                state.copy(hp = 0, str = newStr),
                listOf(
                    RuleEvent.DamageApplied(rawDamage, armorAbsorbed, hpDamage),
                    RuleEvent.CriticalDamage(excessDamage, newStr)
                )
            )
        }
    }

    fun setArmor(state: CharacterState, armor: Int): GameResult {
        require(armor >= 0)
        return GameResult(state.copy(armor = minOf(3, armor)), emptyList())
    }
}
