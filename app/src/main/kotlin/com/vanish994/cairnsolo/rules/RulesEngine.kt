package com.vanish994.cairnsolo.rules

enum class Attribute { STR, DEX, WIL }

data class InventoryItem(
    val id: String,
    val slots: Int = 1,
    val petty: Boolean = false
) {
    init {
        require(slots >= 0)
        require(petty || slots > 0)
    }

    val slotCost: Int get() = if (petty) 0 else slots
}

data class CharacterState(
    val str: Int,
    val dex: Int,
    val wil: Int,
    val hp: Int,
    val maxHp: Int,
    val armor: Int,
    val inventory: List<InventoryItem> = emptyList(),
    val fatigue: Int = 0,
    val deprived: Boolean = false,
    val critical: Boolean = false
) {
    init {
        require(str >= 0 && dex >= 0 && wil >= 0)
        require(hp >= 0 && maxHp >= 0 && hp <= maxHp)
        require(armor in 0..3)
        require(fatigue >= 0)
        require(inventory.sumOf { it.slotCost } + fatigue <= 10)
    }

    fun attribute(attribute: Attribute): Int = when (attribute) {
        Attribute.STR -> str
        Attribute.DEX -> dex
        Attribute.WIL -> wil
    }

    val usedSlots: Int get() = inventory.sumOf { it.slotCost } + fatigue
    val freeSlots: Int get() = 10 - usedSlots
}

interface RandomSource {
    fun d20(): Int
    fun d6(): Int = d20().coerceIn(1, 6)
}

class FixedRandomSource(private val value: Int) : RandomSource {
    init { require(value in 1..20) }
    override fun d20(): Int = value
}

sealed interface RuleEvent {
    data class SaveResolved(val attribute: Attribute, val roll: Int, val success: Boolean) : RuleEvent
    data class DamageApplied(val rawDamage: Int, val armorAbsorbed: Int, val hpDamage: Int) : RuleEvent
    data class CriticalDamage(
        val excessDamage: Int,
        val strAfter: Int,
        val saveRoll: Int,
        val saveSuccess: Boolean
    ) : RuleEvent
    data class InventoryChanged(val usedSlots: Int) : RuleEvent
    data class FatigueAdded(val amount: Int) : RuleEvent
    data class FatigueRecovered(val amount: Int) : RuleEvent
    data class HpRecovered(val amount: Int) : RuleEvent
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

        if (remainingHp >= 0) {
            return GameResult(
                state.copy(hp = remainingHp),
                listOf(RuleEvent.DamageApplied(rawDamage, armorAbsorbed, hpDamage))
            )
        }

        val excessDamage = -remainingHp
        val newStr = maxOf(0, state.str - excessDamage)
        val criticalState = state.copy(hp = 0, str = newStr, critical = true)
        val roll = random.d20()
        val success = when (roll) {
            1 -> true
            20 -> false
            else -> roll <= newStr
        }

        return GameResult(
            criticalState,
            listOf(
                RuleEvent.DamageApplied(rawDamage, armorAbsorbed, hpDamage),
                RuleEvent.CriticalDamage(excessDamage, newStr, roll, success)
            )
        )
    }

    fun setArmor(state: CharacterState, armor: Int): GameResult {
        require(armor in 0..3)
        return GameResult(state.copy(armor = armor), emptyList())
    }

    fun addItem(state: CharacterState, item: InventoryItem): GameResult {
        require(state.freeSlots >= item.slotCost)
        val updated = state.copy(inventory = state.inventory + item)
        return GameResult(updated, listOf(RuleEvent.InventoryChanged(updated.usedSlots)))
    }

    fun removeItem(state: CharacterState, itemId: String): GameResult {
        val index = state.inventory.indexOfFirst { it.id == itemId }
        require(index >= 0)
        val updatedInventory = state.inventory.toMutableList().also { it.removeAt(index) }
        val updated = state.copy(inventory = updatedInventory)
        return GameResult(updated, listOf(RuleEvent.InventoryChanged(updated.usedSlots)))
    }

    fun addFatigue(state: CharacterState, amount: Int = 1): GameResult {
        require(amount > 0)
        require(state.freeSlots >= amount)
        val updated = state.copy(fatigue = state.fatigue + amount)
        return GameResult(
            updated,
            listOf(RuleEvent.FatigueAdded(amount), RuleEvent.InventoryChanged(updated.usedSlots))
        )
    }

    fun markDeprived(state: CharacterState, deprived: Boolean): GameResult =
        GameResult(state.copy(deprived = deprived), emptyList())

    fun safeRest(state: CharacterState): GameResult {
        if (state.deprived) return GameResult(state, emptyList())

        val hpRecovered = state.maxHp - state.hp
        val fatigueRecovered = state.fatigue
        val updated = state.copy(
            hp = state.maxHp,
            fatigue = 0,
            critical = false
        )

        return GameResult(
            updated,
            buildList {
                if (hpRecovered > 0) add(RuleEvent.HpRecovered(hpRecovered))
                if (fatigueRecovered > 0) add(RuleEvent.FatigueRecovered(fatigueRecovered))
            }
        )
    }
}
